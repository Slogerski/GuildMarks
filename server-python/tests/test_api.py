from __future__ import annotations

import importlib
import json
import os
import tempfile
import time
import unittest
from pathlib import Path

from fastapi.testclient import TestClient


class GuildMarksApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temp = tempfile.TemporaryDirectory()
        root = Path(cls.temp.name)
        catalog = Path(__file__).resolve().parents[1] / "guilds.json"
        cls.rate_config = root / "rate-limits.json"
        cls.write_rate_config(1_000)
        os.environ.update(
            {
                "GUILDMARKS_RATE_SECRET": "test-secret-which-is-at-least-32-characters-long",
                "GUILDMARKS_DB_PATH": str(root / "guildmarks.sqlite3"),
                "GUILDMARKS_CATALOG_PATH": str(catalog),
                "GUILDMARKS_RATE_CONFIG_PATH": str(cls.rate_config),
                "GUILDMARKS_ALLOWED_HOSTS": "testserver",
                "GUILDMARKS_REQUIRE_HTTPS": "0",
            }
        )
        cls.api = importlib.import_module("guildmarks_api.app")
        cls.client = TestClient(cls.api.app)
        cls.api.verify_with_mojang = lambda username, _server_id: (
            "0123456789abcdef0123456789abcdef",
            username,
        )

    @classmethod
    def write_rate_config(cls, maximum: int) -> None:
        cls.rate_config.write_text(
            json.dumps(
                {
                    "formatVersion": 1,
                    "maxRequests": maximum,
                    "windowSeconds": 600,
                    "blockDurationsSeconds": [900, 900, 900, 1800, 1800, 1800, 3600],
                    "offenseResetSeconds": 604800,
                    "reloadCheckSeconds": 60,
                }
            ),
            encoding="utf-8",
        )

    def setUp(self) -> None:
        self.write_rate_config(1_000)
        self.api.RATE_LIMITS._mtime_ns = -1
        self.api.RATE_LIMITS.load(force=True)
        with self.api.database() as db:
            db.execute("DELETE FROM spam_limits")
            db.execute("DELETE FROM challenges")
            db.execute("DELETE FROM player_capes")

    @classmethod
    def tearDownClass(cls) -> None:
        cls.client.close()
        cls.temp.cleanup()

    def challenge(self, cape_id: str) -> str:
        response = self.client.post("/guildmarks_api.php", json={"action": "challenge", "capeId": cape_id})
        self.assertEqual(response.status_code, 201, response.text)
        server_id = response.json()["serverId"]
        self.assertRegex(server_id, r"^[a-f0-9]{40}$")
        return server_id

    def confirm(self, server_id: str, username: str = "TestPlayer"):
        return self.client.post(
            "/guildmarks_api.php",
            json={"action": "confirm", "serverId": server_id, "username": username},
        )

    def test_catalog_contract(self) -> None:
        guilds = self.client.get("/guilds.json")
        self.assertEqual(guilds.status_code, 200)
        self.assertEqual(guilds.json()["formatVersion"], 1)
        capes = self.client.get("/guildmarks_api.php?action=capes")
        self.assertEqual(capes.status_code, 200)
        self.assertEqual(len(capes.json()["capes"]), 3)
        self.assertTrue(all(item["url"].startswith("https://raw.githubusercontent.com/") for item in capes.json()["capes"]))

    def test_assignment_proof_and_28_hour_window(self) -> None:
        confirmed = self.confirm(self.challenge("asceria"))
        self.assertEqual(confirmed.status_code, 200, confirmed.text)
        self.assertNotIn("heartbeatToken", confirmed.json())

        active = self.client.get("/players.json").json()["players"]
        self.assertEqual([player["capeId"] for player in active], ["asceria"])

        with self.api.database() as db:
            db.execute("UPDATE player_capes SET last_seen = ?", (int(time.time()) - 100_801,))
        self.assertEqual(self.client.get("/players.json").json()["players"], [])

        refreshed = self.confirm(self.challenge("asceria"))
        self.assertEqual(refreshed.status_code, 200, refreshed.text)
        active = self.client.get("/players.json").json()["players"]
        self.assertEqual([player["capeId"] for player in active], ["asceria"])

    def test_challenge_replay_is_rejected(self) -> None:
        server_id = self.challenge("tester")
        first = self.confirm(server_id, "AnotherPlayer")
        self.assertEqual(first.status_code, 200)
        replay = self.confirm(server_id, "AnotherPlayer")
        self.assertEqual(replay.status_code, 409)

    def test_none_removes_assignment(self) -> None:
        response = self.confirm(self.challenge("none"))
        self.assertEqual(response.status_code, 200)
        self.assertNotIn("heartbeatToken", response.json())
        self.assertEqual(self.client.get("/players.json").json()["players"], [])

    def test_validation(self) -> None:
        invalid_cape = self.client.post("/guildmarks_api.php", json={"action": "challenge", "capeId": "missing"})
        self.assertEqual(invalid_cape.status_code, 422)
        wrong_type = self.client.post("/guildmarks_api.php", content="{}", headers={"Content-Type": "text/plain"})
        self.assertEqual(wrong_type.status_code, 415)

    def test_z_spam_limit_escalation(self) -> None:
        self.write_rate_config(1)
        self.api.RATE_LIMITS._mtime_ns = -1
        self.api.RATE_LIMITS.load(force=True)
        expected = [15, 15, 15, 30, 30, 30, 60, 60]

        self.assertEqual(self.client.get("/guildmarks_api.php?action=capabilities").status_code, 200)
        blocked = self.client.get("/guildmarks_api.php?action=capabilities")
        self.assertEqual(blocked.status_code, 429)
        self.assertEqual(blocked.json()["blockMinutes"], expected[0])
        self.assertEqual(blocked.headers["retry-after"], "900")

        repeated = self.client.get("/guildmarks_api.php?action=capabilities")
        self.assertEqual(repeated.status_code, 429)
        self.assertEqual(repeated.json()["offenseCount"], 1)

        for offense, minutes in enumerate(expected[1:], start=2):
            now = int(time.time())
            with self.api.database() as db:
                db.execute("UPDATE spam_limits SET blocked_until = 0, window_start = ?, hits = 1", (now,))
            blocked = self.client.get("/guildmarks_api.php?action=capabilities")
            self.assertEqual(blocked.status_code, 429)
            self.assertEqual(blocked.json()["offenseCount"], offense)
            self.assertEqual(blocked.json()["blockMinutes"], minutes)


if __name__ == "__main__":
    unittest.main()
