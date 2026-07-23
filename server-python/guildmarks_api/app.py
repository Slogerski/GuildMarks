from __future__ import annotations

import hashlib
import hmac
import ipaddress
import json
import logging
import math
import os
import random
import re
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator
from urllib.parse import quote, urlparse

import httpx
from fastapi import FastAPI, Request
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse


CHALLENGE_TTL_SECONDS = 90
PROCESSING_RECOVERY_SECONDS = 20
ACTIVE_PLAYER_SECONDS = 28 * 60 * 60
PLAYER_REFRESH_SECONDS = 5 * 60
MAX_REQUEST_BODY_BYTES = 16_384
MAX_JSON_RESPONSE_BYTES = 2_000_000
MOJANG_HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined"

STATE_PENDING = 0
STATE_PROCESSING = 1
STATE_USED = 2

ID_PATTERN = re.compile(r"^[a-z0-9_-]{1,64}$")
FILE_PATTERN = re.compile(r"^[A-Za-z0-9._-]{1,128}\.(?:png|jpe?g|webp)$", re.IGNORECASE)
USERNAME_PATTERN = re.compile(r"^[A-Za-z0-9_]{1,16}$")
SERVER_ID_PATTERN = re.compile(r"^[a-f0-9]{40}$")

logger = logging.getLogger("guildmarks.api")


@dataclass(frozen=True)
class Settings:
    database_path: Path
    catalog_path: Path
    rate_config_path: Path
    rate_secret: bytes
    require_https: bool
    allowed_hosts: list[str]


def load_settings() -> Settings:
    secret = os.environ.get("GUILDMARKS_RATE_SECRET", "")
    if len(secret) < 32:
        raise RuntimeError("GUILDMARKS_RATE_SECRET must contain at least 32 characters")
    allowed = [item.strip() for item in os.environ.get("GUILDMARKS_ALLOWED_HOSTS", "localhost,127.0.0.1").split(",") if item.strip()]
    if not allowed:
        raise RuntimeError("GUILDMARKS_ALLOWED_HOSTS must not be empty")
    return Settings(
        database_path=Path(os.environ.get("GUILDMARKS_DB_PATH", "/var/lib/guildmarks/guildmarks.sqlite3")).resolve(),
        catalog_path=Path(os.environ.get("GUILDMARKS_CATALOG_PATH", "/etc/guildmarks/guilds.json")).resolve(),
        rate_config_path=Path(os.environ.get("GUILDMARKS_RATE_CONFIG_PATH", "/etc/guildmarks/rate-limits.json")).resolve(),
        rate_secret=secret.encode("utf-8"),
        require_https=os.environ.get("GUILDMARKS_REQUIRE_HTTPS", "1") != "0",
        allowed_hosts=allowed,
    )


SETTINGS = load_settings()


class ApiError(Exception):
    def __init__(
        self,
        status: int,
        code: str,
        message: str,
        headers: dict[str, str] | None = None,
        extra: dict[str, Any] | None = None,
    ):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.headers = headers or {}
        self.extra = extra or {}


class MojangUnavailable(Exception):
    pass


class CatalogStore:
    def __init__(self, path: Path):
        self.path = path
        self._lock = threading.Lock()
        self._mtime_ns = -1
        self._document: dict[str, Any] = {}
        self._by_id: dict[str, dict[str, str]] = {}

    def load(self) -> tuple[dict[str, Any], dict[str, dict[str, str]]]:
        try:
            mtime_ns = self.path.stat().st_mtime_ns
        except OSError as error:
            raise RuntimeError("Cannot read the configured guild catalog") from error
        with self._lock:
            if mtime_ns != self._mtime_ns:
                document, by_id = self._parse(self.path.read_text(encoding="utf-8"))
                self._document = document
                self._by_id = by_id
                self._mtime_ns = mtime_ns
            return self._document, self._by_id

    @staticmethod
    def _parse(source: str) -> tuple[dict[str, Any], dict[str, dict[str, str]]]:
        if len(source.encode("utf-8")) > MAX_JSON_RESPONSE_BYTES:
            raise RuntimeError("guilds.json is too large")
        parsed = json.loads(source)
        if not isinstance(parsed, dict) or parsed.get("formatVersion") != 1 or not isinstance(parsed.get("guilds"), list):
            raise RuntimeError("guilds.json must use formatVersion 1 and contain a guilds array")
        if len(parsed["guilds"]) > 2_000:
            raise RuntimeError("guilds.json contains too many guilds")
        normalized: list[dict[str, str]] = []
        by_id: dict[str, dict[str, str]] = {}
        for item in parsed["guilds"]:
            if not isinstance(item, dict):
                raise RuntimeError("Invalid guild entry")
            guild_id = str(item.get("id", "")).strip().lower()
            name = str(item.get("name", "")).strip()
            file_name = str(item.get("file", "")).strip()
            image = str(item.get("image", "")).strip()
            url = urlparse(image)
            if not ID_PATTERN.fullmatch(guild_id) or guild_id == "none" or guild_id in by_id:
                raise RuntimeError("Invalid or duplicate guild ID")
            if not name or len(name) > 120:
                raise RuntimeError("Invalid guild name")
            if not FILE_PATTERN.fullmatch(file_name):
                raise RuntimeError("Invalid cape filename")
            if url.scheme != "https" or not url.hostname or len(image) > 2_048:
                raise RuntimeError("Cape image must use an absolute HTTPS URL")
            if Path(url.path).name != file_name:
                raise RuntimeError("Cape URL filename must match the file field")
            entry = {"id": guild_id, "name": name, "file": file_name, "image": image}
            normalized.append(entry)
            by_id[guild_id] = entry
        return {"formatVersion": 1, "guilds": normalized}, by_id


@dataclass(frozen=True)
class RateLimitConfig:
    maximum_requests: int
    window_seconds: int
    block_durations: tuple[int, ...]
    offense_reset_seconds: int
    reload_check_seconds: int


class RateLimitConfigStore:
    def __init__(self, path: Path):
        self.path = path
        self._lock = threading.Lock()
        self._mtime_ns = -1
        self._last_check = 0.0
        self._config: RateLimitConfig | None = None

    def load(self, force: bool = False) -> RateLimitConfig:
        now = time.monotonic()
        current = self._config
        check_seconds = current.reload_check_seconds if current is not None else 60
        if not force and current is not None and now - self._last_check < check_seconds:
            return current
        with self._lock:
            current = self._config
            check_seconds = current.reload_check_seconds if current is not None else 60
            now = time.monotonic()
            if not force and current is not None and now - self._last_check < check_seconds:
                return current
            self._last_check = now
            try:
                mtime_ns = self.path.stat().st_mtime_ns
            except OSError as error:
                if current is not None:
                    logger.error("Cannot check rate-limit configuration; keeping the previous valid configuration")
                    return current
                raise RuntimeError("Cannot read rate-limits.json") from error
            if current is not None and mtime_ns == self._mtime_ns:
                return current
            try:
                parsed = json.loads(self.path.read_text(encoding="utf-8"))
                loaded = self._parse(parsed)
            except Exception:
                if current is not None:
                    logger.exception("Invalid rate-limit configuration; keeping the previous valid configuration")
                    return current
                raise
            self._config = loaded
            self._mtime_ns = mtime_ns
            return loaded

    @staticmethod
    def _parse(value: Any) -> RateLimitConfig:
        if not isinstance(value, dict) or value.get("formatVersion") != 1:
            raise RuntimeError("rate-limits.json must use formatVersion 1")
        maximum = int(value.get("maxRequests", 0))
        window = int(value.get("windowSeconds", 0))
        reset = int(value.get("offenseResetSeconds", 0))
        reload_seconds = int(value.get("reloadCheckSeconds", 60))
        raw_durations = value.get("blockDurationsSeconds")
        if maximum < 1 or maximum > 10_000 or window < 10 or window > 86_400:
            raise RuntimeError("Invalid request limit or window")
        if reset < 3_600 or reset > 365 * 24 * 3_600:
            raise RuntimeError("Invalid offense reset interval")
        if reload_seconds < 10 or reload_seconds > 3_600:
            raise RuntimeError("reloadCheckSeconds must be between 10 and 3600")
        if not isinstance(raw_durations, list) or not raw_durations or len(raw_durations) > 32:
            raise RuntimeError("blockDurationsSeconds must be a non-empty array")
        durations = tuple(int(item) for item in raw_durations)
        if any(item < 60 or item > 24 * 3_600 for item in durations):
            raise RuntimeError("Invalid block duration")
        return RateLimitConfig(maximum, window, durations, reset, reload_seconds)


CATALOG = CatalogStore(SETTINGS.catalog_path)
RATE_LIMITS = RateLimitConfigStore(SETTINGS.rate_config_path)


def utc_iso(epoch_seconds: int | float | None = None) -> str:
    value = time.time() if epoch_seconds is None else epoch_seconds
    return datetime.fromtimestamp(value, tz=timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def uuid_with_hyphens(value: str) -> str:
    if not re.fullmatch(r"[a-f0-9]{32}", value):
        raise RuntimeError("Invalid UUID stored in database")
    return f"{value[:8]}-{value[8:12]}-{value[12:16]}-{value[16:20]}-{value[20:]}"


@contextmanager
def database() -> Iterator[sqlite3.Connection]:
    SETTINGS.database_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    connection = sqlite3.connect(SETTINGS.database_path, timeout=5, isolation_level=None)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA journal_mode = WAL")
    connection.execute("PRAGMA synchronous = NORMAL")
    connection.execute("PRAGMA busy_timeout = 5000")
    try:
        yield connection
    finally:
        connection.close()


def initialize_database() -> None:
    with database() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS challenges (
                server_id TEXT PRIMARY KEY,
                cape_id TEXT NOT NULL,
                state INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                processing_at INTEGER,
                used_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_challenges_expires_at ON challenges(expires_at);

            CREATE TABLE IF NOT EXISTS player_capes (
                uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                cape_id TEXT NOT NULL,
                assigned_at INTEGER NOT NULL,
                last_seen INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_player_capes_last_seen ON player_capes(last_seen);
            CREATE INDEX IF NOT EXISTS idx_player_capes_username ON player_capes(username COLLATE NOCASE);

            CREATE TABLE IF NOT EXISTS spam_limits (
                rate_key TEXT PRIMARY KEY,
                window_start INTEGER NOT NULL,
                hits INTEGER NOT NULL,
                blocked_until INTEGER NOT NULL,
                offense_count INTEGER NOT NULL,
                last_offense INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_spam_limits_last_offense ON spam_limits(last_offense);
            """
        )
    try:
        os.chmod(SETTINGS.database_path, 0o600)
    except OSError:
        pass


initialize_database()
CATALOG.load()
RATE_LIMITS.load(force=True)


app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
app.add_middleware(TrustedHostMiddleware, allowed_hosts=SETTINGS.allowed_hosts, www_redirect=False)


def json_response(payload: dict[str, Any], status: int = 200, headers: dict[str, str] | None = None) -> JSONResponse:
    response_headers = {
        "Cache-Control": "no-store, max-age=0",
        "Pragma": "no-cache",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
    }
    if headers:
        response_headers.update(headers)
    return JSONResponse(payload, status_code=status, headers=response_headers)


@app.middleware("http")
async def security_middleware(request: Request, call_next):
    if SETTINGS.require_https and request.url.scheme != "https":
        return json_response({"ok": False, "error": "https_required", "message": "HTTPS is required."}, 426)
    response = await call_next(request)
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    return response


@app.exception_handler(ApiError)
async def api_error_handler(_request: Request, error: ApiError):
    payload = {"ok": False, "error": error.code, "message": error.message}
    payload.update(error.extra)
    return json_response(payload, error.status, error.headers)


@app.exception_handler(Exception)
async def internal_error_handler(_request: Request, error: Exception):
    request_id = secrets.token_hex(8)
    logger.exception("Internal API error %s", request_id, exc_info=error)
    return json_response(
        {"ok": False, "error": "internal_server_error", "message": "Internal server error.", "requestId": request_id},
        500,
    )


def client_ip(request: Request) -> str:
    raw = request.client.host if request.client else "unknown"
    try:
        return str(ipaddress.ip_address(raw))
    except ValueError:
        return "unknown"


def rate_key(scope: str, ip: str) -> str:
    return hmac.new(SETTINGS.rate_secret, f"{scope}|{ip}".encode("utf-8"), hashlib.sha256).hexdigest()


def spam_block_error(retry_after: int, offense_count: int) -> ApiError:
    minutes = max(1, math.ceil(retry_after / 60))
    return ApiError(
        429,
        "spam_blocked",
        f"Spam protection: blocked for {minutes} minutes.",
        {"Retry-After": str(retry_after)},
        {"retryAfter": retry_after, "blockMinutes": minutes, "offenseCount": offense_count},
    )


def enforce_spam_limit(request: Request) -> None:
    config = RATE_LIMITS.load()
    now = int(time.time())
    key = rate_key("global", client_ip(request))
    with database() as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute(
            "SELECT window_start, hits, blocked_until, offense_count, last_offense FROM spam_limits WHERE rate_key = ?",
            (key,),
        ).fetchone()
        if row is None:
            db.execute(
                "INSERT INTO spam_limits(rate_key, window_start, hits, blocked_until, offense_count, last_offense) "
                "VALUES(?, ?, 1, 0, 0, 0)",
                (key, now),
            )
            db.commit()
            return
        blocked_until = int(row["blocked_until"])
        offense_count = int(row["offense_count"])
        last_offense = int(row["last_offense"])
        if blocked_until > now:
            db.rollback()
            raise spam_block_error(blocked_until - now, offense_count)
        if last_offense > 0 and last_offense < now - config.offense_reset_seconds:
            offense_count = 0
        window_start = int(row["window_start"])
        hits = int(row["hits"])
        if window_start <= now - config.window_seconds:
            window_start = now
            hits = 0
        hits += 1
        if hits > config.maximum_requests:
            offense_count += 1
            duration = config.block_durations[min(offense_count - 1, len(config.block_durations) - 1)]
            blocked_until = now + duration
            db.execute(
                "UPDATE spam_limits SET window_start = ?, hits = 0, blocked_until = ?, offense_count = ?, last_offense = ? "
                "WHERE rate_key = ?",
                (now, blocked_until, offense_count, now, key),
            )
            db.commit()
            raise spam_block_error(duration, offense_count)
        db.execute(
            "UPDATE spam_limits SET window_start = ?, hits = ?, blocked_until = 0, offense_count = ?, last_offense = ? "
            "WHERE rate_key = ?",
            (window_start, hits, offense_count, last_offense, key),
        )
        db.commit()


def cleanup_old_rows() -> None:
    if random.randrange(100) != 0:
        return
    now = int(time.time())
    with database() as db:
        db.execute("DELETE FROM challenges WHERE expires_at < ?", (now - 3_600,))
        config = RATE_LIMITS.load()
        db.execute(
            "DELETE FROM spam_limits WHERE (last_offense = 0 AND window_start < ?) "
            "OR (last_offense > 0 AND last_offense < ?)",
            (now - config.offense_reset_seconds, now - config.offense_reset_seconds),
        )


async def json_body(request: Request) -> dict[str, Any]:
    content_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if content_type != "application/json":
        raise ApiError(415, "unsupported_media_type", "POST requests require Content-Type: application/json.")
    content_length = request.headers.get("content-length")
    if content_length and content_length.isdigit() and int(content_length) > MAX_REQUEST_BODY_BYTES:
        raise ApiError(413, "request_too_large", "Request body is too large.")
    raw = await request.body()
    if len(raw) > MAX_REQUEST_BODY_BYTES:
        raise ApiError(413, "request_too_large", "Request body is too large.")
    try:
        parsed = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ApiError(400, "invalid_json", "Invalid JSON.") from error
    if not isinstance(parsed, dict):
        raise ApiError(400, "invalid_json_object", "Request body must be a JSON object.")
    return parsed


def public_players() -> list[dict[str, Any]]:
    _, catalog = CATALOG.load()
    cutoff = int(time.time()) - ACTIVE_PLAYER_SECONDS
    with database() as db:
        rows = db.execute(
            "SELECT uuid, username, cape_id, last_seen FROM player_capes "
            "WHERE last_seen >= ? ORDER BY username COLLATE NOCASE ASC",
            (cutoff,),
        ).fetchall()
    return [
        {
            "uuid": uuid_with_hyphens(str(row["uuid"])),
            "username": str(row["username"]),
            "capeId": str(row["cape_id"]),
            "updatedAt": utc_iso(int(row["last_seen"])),
        }
        for row in rows
        if str(row["cape_id"]) in catalog
    ]


@app.get("/")
def root():
    return json_response({"ok": True, "service": "GuildMarks API"})


@app.get("/healthz")
def health():
    CATALOG.load()
    with database() as db:
        db.execute("SELECT 1").fetchone()
    return json_response({"ok": True})


@app.get("/guilds.json")
def guilds_json(request: Request):
    enforce_spam_limit(request)
    document, _ = CATALOG.load()
    return json_response(document)


@app.get("/players.json")
def players_json(request: Request):
    enforce_spam_limit(request)
    return json_response({"ok": True, "generatedAt": utc_iso(), "players": public_players()})


@app.get("/guildmarks_api.php")
def legacy_get(request: Request, action: str = ""):
    cleanup_old_rows()
    enforce_spam_limit(request)
    if action == "capabilities":
        return json_response(
            {
                "ok": True,
                "api": "guildmarks-python",
                "protocolVersion": 2,
                "playerRefreshInterval": PLAYER_REFRESH_SECONDS,
                "activeWindow": ACTIVE_PLAYER_SECONDS,
                "activityProof": "once-per-client-session",
            }
        )
    if action == "capes":
        _, catalog = CATALOG.load()
        return json_response(
            {
                "ok": True,
                "capes": [
                    {"id": entry["id"], "name": entry["name"], "url": entry["image"], "file": entry["file"]}
                    for entry in catalog.values()
                ],
            }
        )
    if action == "players":
        return json_response({"ok": True, "players": public_players()})
    raise ApiError(404, "endpoint_not_found", "Endpoint not found.")


@app.post("/guildmarks_api.php")
async def legacy_post(request: Request):
    cleanup_old_rows()
    enforce_spam_limit(request)
    body = await json_body(request)
    action = body.get("action")
    if action == "challenge":
        return create_challenge(body)
    if action == "confirm":
        return confirm_challenge(body)
    raise ApiError(404, "endpoint_not_found", "Endpoint not found.")


def create_challenge(body: dict[str, Any]) -> JSONResponse:
    cape_id = str(body.get("capeId", "")).strip().lower()
    _, catalog = CATALOG.load()
    if cape_id != "none" and cape_id not in catalog:
        raise ApiError(422, "invalid_cape", "Selected cape does not exist.")
    server_id = secrets.token_hex(20)
    now = int(time.time())
    expires_at = now + CHALLENGE_TTL_SECONDS
    with database() as db:
        db.execute(
            "INSERT INTO challenges(server_id, cape_id, state, created_at, expires_at) VALUES(?, ?, ?, ?, ?)",
            (server_id, cape_id, STATE_PENDING, now, expires_at),
        )
    return json_response(
        {"ok": True, "serverId": server_id, "expiresAt": utc_iso(expires_at), "expiresIn": CHALLENGE_TTL_SECONDS},
        201,
    )


def reserve_challenge(server_id: str) -> str:
    now = int(time.time())
    with database() as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute(
            "SELECT cape_id, state, expires_at, processing_at FROM challenges WHERE server_id = ?",
            (server_id,),
        ).fetchone()
        if row is None:
            db.rollback()
            raise ApiError(404, "challenge_not_found", "Challenge does not exist.")
        if int(row["expires_at"]) < now:
            db.rollback()
            raise ApiError(410, "challenge_expired", "Challenge expired.")
        state = int(row["state"])
        processing_at = row["processing_at"]
        if state == STATE_PROCESSING and processing_at is not None and int(processing_at) < now - PROCESSING_RECOVERY_SECONDS:
            state = STATE_PENDING
        if state == STATE_USED:
            db.rollback()
            raise ApiError(409, "challenge_already_used", "Challenge was already used.")
        if state != STATE_PENDING:
            db.rollback()
            raise ApiError(409, "challenge_in_progress", "Challenge is already being verified.")
        result = db.execute(
            "UPDATE challenges SET state = ?, processing_at = ? WHERE server_id = ? AND state != ?",
            (STATE_PROCESSING, now, server_id, STATE_USED),
        )
        if result.rowcount != 1:
            db.rollback()
            raise ApiError(409, "challenge_in_progress", "Challenge is already being verified.")
        db.commit()
        return str(row["cape_id"])


def release_challenge(server_id: str) -> None:
    now = int(time.time())
    with database() as db:
        db.execute(
            "UPDATE challenges SET state = ?, processing_at = NULL "
            "WHERE server_id = ? AND state = ? AND expires_at >= ?",
            (STATE_PENDING, server_id, STATE_PROCESSING, now),
        )


def verify_with_mojang(username: str, server_id: str) -> tuple[str, str] | None:
    url = f"{MOJANG_HAS_JOINED_URL}?username={quote(username, safe='')}&serverId={server_id}"
    try:
        with httpx.Client(
            timeout=httpx.Timeout(10.0, connect=5.0),
            follow_redirects=False,
            headers={"Accept": "application/json", "User-Agent": "GuildMarks-API/1.0"},
        ) as client:
            response = client.get(url)
    except httpx.HTTPError as error:
        raise MojangUnavailable from error
    if response.status_code == 204:
        return None
    if response.status_code == 429 or response.status_code >= 500:
        raise MojangUnavailable
    if response.status_code != 200:
        return None
    try:
        profile = response.json()
    except ValueError as error:
        raise MojangUnavailable from error
    profile_id = str(profile.get("id", "")).lower() if isinstance(profile, dict) else ""
    official_name = str(profile.get("name", "")) if isinstance(profile, dict) else ""
    if not re.fullmatch(r"[a-f0-9]{32}", profile_id) or not USERNAME_PATTERN.fullmatch(official_name):
        raise MojangUnavailable
    if official_name.lower() != username.lower():
        return None
    return profile_id, official_name


def confirm_challenge(body: dict[str, Any]) -> JSONResponse:
    server_id = str(body.get("serverId", "")).strip().lower()
    username = str(body.get("username", "")).strip()
    if not SERVER_ID_PATTERN.fullmatch(server_id):
        raise ApiError(422, "invalid_server_id", "Invalid serverId.")
    if not USERNAME_PATTERN.fullmatch(username):
        raise ApiError(422, "invalid_username", "Invalid Minecraft Java username.")
    cape_id = reserve_challenge(server_id)
    try:
        profile = verify_with_mojang(username, server_id)
    except MojangUnavailable as error:
        release_challenge(server_id)
        raise ApiError(503, "minecraft_session_server_unavailable", "Minecraft session server is temporarily unavailable.") from error
    if profile is None:
        release_challenge(server_id)
        raise ApiError(401, "minecraft_proof_failed", "Minecraft session server did not confirm this session.")

    uuid, official_name = profile
    now = int(time.time())
    with database() as db:
        db.execute("BEGIN IMMEDIATE")
        state = db.execute("SELECT state FROM challenges WHERE server_id = ?", (server_id,)).fetchone()
        if state is None or int(state["state"]) != STATE_PROCESSING:
            db.rollback()
            raise ApiError(409, "challenge_in_progress", "Challenge is no longer reserved.")
        if cape_id == "none":
            db.execute("DELETE FROM player_capes WHERE uuid = ?", (uuid,))
        else:
            _, catalog = CATALOG.load()
            if cape_id not in catalog:
                db.rollback()
                raise ApiError(422, "invalid_cape", "Selected cape no longer exists.")
            existing = db.execute("SELECT cape_id, assigned_at FROM player_capes WHERE uuid = ?", (uuid,)).fetchone()
            assigned_at = int(existing["assigned_at"]) if existing is not None and str(existing["cape_id"]) == cape_id else now
            db.execute(
                "INSERT INTO player_capes(uuid, username, cape_id, assigned_at, last_seen) VALUES(?, ?, ?, ?, ?) "
                "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, cape_id = excluded.cape_id, "
                "assigned_at = excluded.assigned_at, last_seen = excluded.last_seen",
                (uuid, official_name, cape_id, assigned_at, now),
            )
        consumed = db.execute(
            "UPDATE challenges SET state = ?, used_at = ? WHERE server_id = ? AND state = ?",
            (STATE_USED, now, server_id, STATE_PROCESSING),
        )
        if consumed.rowcount != 1:
            db.rollback()
            raise RuntimeError("Cannot consume challenge")
        db.commit()

    payload: dict[str, Any] = {
        "ok": True,
        "verifiedBy": "sessionserver.mojang.com",
        "player": {"uuid": uuid_with_hyphens(uuid), "username": official_name},
        "capeId": cape_id,
        "updatedAt": utc_iso(now),
    }
    return json_response(payload)
