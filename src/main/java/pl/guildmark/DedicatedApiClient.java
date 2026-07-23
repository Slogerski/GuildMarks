package pl.guildmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DedicatedApiClient {
    public static final String MCEXTREME_DEFAULT_API = "http://51.83.132.88:8765";
    public record Cape(String id, String name, String file, String url, GuildData.Guild guild) {}

    private enum ApiProtocol { UNKNOWN, APACHE_PHP, PYTHON_V2 }

    private static final long PLAYER_REFRESH_MILLIS = 5L * 60L * 1000L;
    private static final int MAX_JSON_BYTES = 2_000_000;
    private static final int MAX_IMAGE_BYTES = 5_000_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient API_HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private static final HttpClient PUBLIC_ASSET_HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GuildMark-DedicatedApi");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean CATALOG_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean PLAYERS_RUNNING = new AtomicBoolean();
    private static volatile List<Cape> capes = List.of();
    private static volatile Map<String, Cape> capesById = Map.of();
    private static volatile Map<String, String> capeByPlayer = Map.of();
    private static volatile ApiProtocol protocol = ApiProtocol.UNKNOWN;
    private static volatile String protocolEndpoint = "";
    private static volatile long restrictedUntilMillis;
    private static volatile boolean sessionProofComplete;
    private static boolean initialized;
    private static boolean wasDedicated;
    private static long nextPlayerRefresh;

    private DedicatedApiClient() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        loadCaches();
    }

    public static void tick() {
        initialize();
        boolean dedicated = DedicatedServerMode.isActive();
        long now = System.currentTimeMillis();
        if (dedicated && !wasDedicated && !apiUrl().isBlank()) {
            syncCatalog(message -> refreshSessionProof(null, null), null);
            nextPlayerRefresh = now + PLAYER_REFRESH_MILLIS;
        } else if (dedicated && now >= nextPlayerRefresh && !apiUrl().isBlank()) {
            syncPlayers(null, null);
            nextPlayerRefresh = now + PLAYER_REFRESH_MILLIS;
        }
        wasDedicated = dedicated;
    }

    public static String normalizeApiUrl(String raw) {
        URI uri = URI.create(raw == null ? "" : raw.strip());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme)) || host.isBlank() || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Dedicated API address must use HTTP or HTTPS");
        try {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.isBlank() || "/".equals(path)) path = "/guildmarks_api.php";
            else if (path.endsWith("/")) path += "guildmarks_api.php";
            else if (!path.toLowerCase(Locale.ROOT).endsWith(".php")) path += "/guildmarks_api.php";
            return new URI(scheme, null, host, uri.getPort(), path, null, null).normalize().toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid API address", error);
        }
    }

    public static void configure(String raw, Consumer<String> success, Consumer<Throwable> failure) {
        String normalized = normalizeApiUrl(raw);
        boolean endpointChanged = !normalized.equals(apiUrl());
        if (endpointChanged) {
            protocol = ApiProtocol.UNKNOWN;
            protocolEndpoint = "";
            sessionProofComplete = false;
        }
        GuildMarkClient.SETTINGS.setDedicatedApiUrl(normalized);
        syncCatalog(endpointChanged, message -> refreshSessionProof(ignored -> accept(success, message), failure), failure);
    }

    public static void probe(String raw, Consumer<String> success, Consumer<Throwable> failure) {
        final String normalized;
        try {
            normalized = normalizeApiUrl(raw);
        } catch (Throwable error) {
            fail(failure, error);
            return;
        }
        WORKER.execute(() -> {
            try {
                ensureProtocol(normalized);
                acceptOnClient(success, normalized);
            } catch (Throwable error) {
                fail(failure, error);
            }
        });
    }

    public static void deactivate() {
        if (GuildMarkClient.SETTINGS != null) GuildMarkClient.SETTINGS.setDedicatedApiUrl("");
        protocol = ApiProtocol.UNKNOWN;
        protocolEndpoint = "";
        sessionProofComplete = false;
        capes = List.of();
        capesById = Map.of();
        capeByPlayer = Map.of();
        nextPlayerRefresh = 0L;
        GuildMarkTextures.invalidateAll();
    }

    public static String restrictionNotice() {
        long remaining = restrictedUntilMillis - System.currentTimeMillis();
        if (remaining <= 0) return "";
        long minutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
        return GuildMarkI18n.tr(
            "Error: spam protection blocked the API for " + minutes + " min.",
            "Błąd: ograniczenie za spam — API zablokowane na " + minutes + " min."
        );
    }

    private static void refreshSessionProof(Consumer<String> success, Consumer<Throwable> failure) {
        String endpoint = apiUrl();
        if (endpoint.isBlank()) return;
        if (protocol == ApiProtocol.PYTHON_V2 && !sessionProofComplete) {
            String selected = GuildMarkClient.SETTINGS.dedicatedProfileCapeId();
            selectCape(selected.isBlank() ? "none" : selected, success, error -> {
                syncPlayers(null, null);
                fail(failure, error);
            });
        } else {
            syncPlayers(success, failure);
        }
    }

    public static List<Cape> availableCapes() { initialize(); return capes; }

    public static Cape capeById(String id) {
        return id == null ? null : capesById.get(id.toLowerCase(Locale.ROOT));
    }

    public static String capeIdForGuild(GuildData.Guild guild) {
        if (guild == null) return "none";
        for (Cape cape : capes) if (cape.guild() == guild) return cape.id();
        return "none";
    }

    public static GuildData.Guild guildForPlayer(String playerName) {
        if (playerName == null) return null;
        return guildForCapeId(capeByPlayer.get(playerName.toLowerCase(Locale.ROOT)));
    }

    public static GuildData.Guild guildForCapeId(String capeId) {
        Cape cape = capeById(capeId);
        return cape == null ? null : cape.guild();
    }

    public static void selectCape(String capeId, Consumer<String> success, Consumer<Throwable> failure) {
        String requested = capeId == null || capeId.isBlank() ? "none" : capeId;
        if (!"none".equals(requested) && capeById(requested) == null) {
            fail(failure, new IllegalArgumentException("Unknown cape: " + requested));
            return;
        }
        String endpoint = apiUrl();
        if (endpoint.isBlank()) {
            fail(failure, new IllegalStateException("Configure the dedicated API in Auto Import first"));
            return;
        }
        WORKER.execute(() -> {
            try {
                ensureProtocol(endpoint);
                ChallengeResponse challenge = requestCapeSelectionChallenge(endpoint, requested);
                if (challenge == null || challenge.serverId == null || !challenge.serverId.matches("[a-f0-9]{40}"))
                    throw new IllegalStateException("API returned an invalid challenge");
                MinecraftClient client = MinecraftClient.getInstance();
                Session session = client.getSession();
                MojangSessionProof.submitDirectlyToMojang(client, session, challenge.serverId);
                ConfirmResponse confirmed = confirmCapeSelection(endpoint, challenge.serverId, session.getUsername());
                if (confirmed == null || !confirmed.ok) throw new IllegalStateException("API did not confirm the profile change");
                GuildMarkClient.SETTINGS.setDedicatedProfileCapeId("none".equals(requested) ? "" : requested);
                sessionProofComplete = true;
                PlayersResponse players = loadPlayers(endpoint);
                installPlayers(players);
                writeAtomic(playersCachePath(), GSON.toJson(players));
                client.execute(() -> {
                    accept(success, "Profile verified by sessionserver.mojang.com");
                });
            } catch (Throwable error) {
                fail(failure, error);
            }
        });
    }

    public static void syncCatalog(Consumer<String> success, Consumer<Throwable> failure) {
        syncCatalog(false, success, failure);
    }

    private static void syncCatalog(boolean refreshExisting, Consumer<String> success, Consumer<Throwable> failure) {
        String endpoint = apiUrl();
        if (endpoint.isBlank() || !CATALOG_RUNNING.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                ensureProtocol(endpoint);
                URI catalogUri = catalogUri(endpoint);
                CatalogResponse response = parseJson(getJsonFromConfiguredApi(catalogUri.toString(), 20), CatalogResponse.class);
                int downloaded = installCatalog(response, true, refreshExisting, catalogUri);
                writeAtomic(capesCachePath(), GSON.toJson(response));
                if (downloaded > 0) MinecraftClient.getInstance().execute(GuildMarkTextures::invalidateAll);
                acceptOnClient(success, "API connected: " + capes.size() + " capes, " + downloaded + " downloaded");
            } catch (Throwable error) {
                fail(failure, error);
            } finally {
                CATALOG_RUNNING.set(false);
            }
        });
    }

    public static void syncPlayers(Consumer<String> success, Consumer<Throwable> failure) {
        String endpoint = apiUrl();
        if (endpoint.isBlank() || !PLAYERS_RUNNING.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                ensureProtocol(endpoint);
                PlayersResponse response = loadPlayers(endpoint);
                installPlayers(response);
                writeAtomic(playersCachePath(), GSON.toJson(response));
                acceptOnClient(success, "Updated " + capeByPlayer.size() + " player profiles");
            } catch (Throwable error) {
                fail(failure, error);
            } finally {
                PLAYERS_RUNNING.set(false);
            }
        });
    }

    private static int installCatalog(CatalogResponse response, boolean downloadMissing, boolean refreshExisting, URI catalogUri) throws Exception {
        if (response == null || response.guilds == null) throw new IllegalArgumentException("Invalid guilds.json response");
        Path root = markRoot();
        Files.createDirectories(root);
        List<Cape> installed = new ArrayList<>();
        Map<String, Cape> byId = new LinkedHashMap<>();
        int downloaded = 0;
        for (GuildDto item : response.guilds) {
            if (item == null || "none".equals(item.id)) continue;
            if (item.id == null || !item.id.matches("[A-Za-z0-9_-]{1,64}") || item.name == null || item.name.isBlank())
                throw new IllegalArgumentException("Invalid cape entry");
            String file = safeFileName(item.file);
            String imageUrl = normalizeImageUrl(item.image, catalogUri);
            Path target = root.resolve(file).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Unsafe cape filename");
            if (refreshExisting || !Files.isRegularFile(target)) {
                if (!downloadMissing) continue;
                byte[] imageBytes = downloadPublicCapeImage(imageUrl);
                validateImage(imageBytes);
                writeAtomic(target, imageBytes);
                downloaded++;
            }
            GuildData.Guild guild = new GuildData.Guild(item.name.strip());
            guild.markFile = file;
            guild.markPath = "GuildMark/Guilds/" + file;
            guild.markUrl = imageUrl;
            guild.showOnChest = true;
            guild.showOnCape = true;
            guild.showOnShield = true;
            guild.showOnElytra = true;
            guild.showOnHelmet = false;
            Cape cape = new Cape(item.id.toLowerCase(Locale.ROOT), guild.name, file, imageUrl, guild);
            if (byId.putIfAbsent(cape.id(), cape) != null) throw new IllegalArgumentException("Duplicate cape ID: " + cape.id());
            installed.add(cape);
        }
        capes = List.copyOf(installed);
        capesById = Map.copyOf(byId);
        return downloaded;
    }

    private static void installPlayers(PlayersResponse response) {
        if (response == null || !response.ok || response.players == null) throw new IllegalArgumentException("Invalid players response");
        Map<String, String> assignments = new LinkedHashMap<>();
        for (PlayerDto player : response.players) {
            if (player == null || player.username == null || player.capeId == null) continue;
            if (!player.username.matches("[A-Za-z0-9_]{1,16}")) continue;
            assignments.put(player.username.toLowerCase(Locale.ROOT), player.capeId.toLowerCase(Locale.ROOT));
        }
        capeByPlayer = Map.copyOf(assignments);
    }

    private static void loadCaches() {
        try {
            if (Files.isRegularFile(capesCachePath()) && !apiUrl().isBlank())
                installCatalog(GSON.fromJson(Files.readString(capesCachePath()), CatalogResponse.class), false, false, catalogUri(apiUrl()));
        } catch (Exception error) {
            GuildMarkClient.LOGGER.warn("Cannot load dedicated cape cache", error);
        }
        try {
            if (Files.isRegularFile(playersCachePath())) installPlayers(GSON.fromJson(Files.readString(playersCachePath()), PlayersResponse.class));
        } catch (Exception error) {
            GuildMarkClient.LOGGER.warn("Cannot load dedicated player cache", error);
        }
    }

    private static PlayersResponse loadPlayers(String endpoint) throws Exception {
        return parseJson(getJsonFromConfiguredApi(URI.create(endpoint).resolve("players.json").toString(), 20), PlayersResponse.class);
    }

    private static ApiProtocol ensureProtocol(String endpoint) throws Exception {
        ApiProtocol current = protocol;
        if (current != ApiProtocol.UNKNOWN && endpoint.equals(protocolEndpoint)) return current;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?action=capabilities"))
            .timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
            .header("User-Agent", "GuildMark/1.1.1 Minecraft/1.21.8").GET().build();
        HttpResponse<byte[]> response = sendGetWithSingleRetry(API_HTTP, request);
        if (response.body().length > MAX_JSON_BYTES) throw new IllegalStateException("API response is too large");
        ApiProtocol detected;
        if (response.statusCode() / 100 == 2) {
            CapabilitiesResponse capabilities = parseJson(response.body(), CapabilitiesResponse.class);
            if (capabilities == null || !capabilities.ok || !"guildmarks-python".equals(capabilities.api) || capabilities.protocolVersion < 2)
                throw new IllegalStateException("Unsupported dedicated API capabilities");
            detected = ApiProtocol.PYTHON_V2;
        } else if (response.statusCode() == 404) {
            detected = ApiProtocol.APACHE_PHP;
        } else {
            ErrorResponse error = parseJson(response.body(), ErrorResponse.class);
            int retryAfter = error == null ? 0 : error.retryAfter;
            if (retryAfter <= 0) retryAfter = response.headers().firstValue("Retry-After").map(DedicatedApiClient::parsePositiveInt).orElse(0);
            ApiHttpException exception = new ApiHttpException(response.statusCode(), error == null ? null : error.error,
                error != null && error.message != null ? error.message : "API HTTP " + response.statusCode(),
                retryAfter, error == null ? 0 : error.blockMinutes);
            if (response.statusCode() == 429) registerRestriction(exception);
            throw exception;
        }
        protocolEndpoint = endpoint;
        protocol = detected;
        return detected;
    }

    private static int parsePositiveInt(String value) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static void registerRestriction(ApiHttpException error) {
        int seconds = error.retryAfter > 0 ? error.retryAfter : Math.max(1, error.blockMinutes) * 60;
        restrictedUntilMillis = Math.max(restrictedUntilMillis, System.currentTimeMillis() + seconds * 1000L);
    }

    private static ChallengeResponse requestCapeSelectionChallenge(String endpoint, String capeId) throws Exception {
        return postJsonToConfiguredApi(endpoint, new ChallengeRequest("challenge", capeId), ChallengeResponse.class);
    }

    private static ConfirmResponse confirmCapeSelection(String endpoint, String serverId, String username) throws Exception {
        return postJsonToConfiguredApi(endpoint, new ConfirmRequest("confirm", serverId, username), ConfirmResponse.class);
    }

    private static <T> T postJsonToConfiguredApi(String endpoint, Object body, Class<T> type) throws Exception {
        byte[] json = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(25))
            .header("Accept", "application/json").header("Content-Type", "application/json")
            .header("User-Agent", "GuildMark/1.1.1 Minecraft/1.21.8")
            .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
        return parseJson(sendConfiguredApiRequest(request, MAX_JSON_BYTES), type);
    }

    private static byte[] sendConfiguredApiRequest(HttpRequest request, int maximumBytes) throws Exception {
        HttpResponse<byte[]> response = "GET".equals(request.method())
            ? sendGetWithSingleRetry(API_HTTP, request)
            : API_HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.body().length > maximumBytes) throw new IllegalStateException("API response is too large");
        if (response.statusCode() / 100 != 2) {
            ErrorResponse error = parseJson(response.body(), ErrorResponse.class);
            int retryAfter = error == null ? 0 : error.retryAfter;
            if (retryAfter <= 0) retryAfter = response.headers().firstValue("Retry-After").map(DedicatedApiClient::parsePositiveInt).orElse(0);
            ApiHttpException exception = new ApiHttpException(response.statusCode(), error == null ? null : error.error,
                error != null && error.message != null ? error.message : "API HTTP " + response.statusCode(),
                retryAfter, error == null ? 0 : error.blockMinutes);
            if (response.statusCode() == 429) registerRestriction(exception);
            throw exception;
        }
        return response.body();
    }

    private static byte[] getJsonFromConfiguredApi(String url, int timeoutSeconds) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Accept", "application/json").header("User-Agent", "GuildMark/1.1.1 Minecraft/1.21.8").GET().build();
        return sendConfiguredApiRequest(request, MAX_JSON_BYTES);
    }

    private static byte[] downloadPublicCapeImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
            .header("Accept", "image/*").header("User-Agent", "GuildMark/1.1.1 Minecraft/1.21.8").GET().build();
        HttpResponse<byte[]> response = sendGetWithSingleRetry(PUBLIC_ASSET_HTTP, request);
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("Cape image HTTP " + response.statusCode());
        if (response.body().length > MAX_IMAGE_BYTES) throw new IllegalStateException("Cape image is too large");
        return response.body();
    }

    private static HttpResponse<byte[]> sendGetWithSingleRetry(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException firstFailure) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static <T> T parseJson(byte[] bytes, Class<T> type) {
        return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    private static void validateImage(byte[] bytes) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 4096 || image.getHeight() > 4096)
                throw new IllegalArgumentException("Invalid or oversized cape image");
        }
    }

    private static String normalizeImageUrl(String raw, URI catalogUri) {
        URI uri = catalogUri.resolve(URI.create(raw == null ? "" : raw.strip())).normalize();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!("https".equals(scheme) || ("http".equals(scheme) && loopback)) || host.isBlank())
            throw new IllegalArgumentException("Cape images require HTTPS; HTTP is allowed only for localhost");
        return uri.normalize().toString();
    }

    private static URI catalogUri(String endpoint) {
        return URI.create(endpoint).resolve("guilds.json");
    }

    private static String safeFileName(String file) {
        if (file == null || !file.matches("[A-Za-z0-9._-]{1,128}") || file.equals(".") || file.equals(".."))
            throw new IllegalArgumentException("Invalid cape filename");
        return file;
    }

    private static String apiUrl() {
        return GuildMarkClient.SETTINGS == null ? "" : GuildMarkClient.SETTINGS.dedicatedApiUrl();
    }

    private static Path configRoot() { return FabricLoader.getInstance().getConfigDir().resolve("guildmark"); }
    private static Path markRoot() { return FabricLoader.getInstance().getConfigDir().resolve("GuildMark/Guilds").toAbsolutePath().normalize(); }
    private static Path capesCachePath() { return configRoot().resolve("dedicated-capes.json"); }
    private static Path playersCachePath() { return configRoot().resolve("dedicated-players.json"); }

    private static void writeAtomic(Path target, String text) throws Exception { writeAtomic(target, text.getBytes(StandardCharsets.UTF_8)); }
    private static void writeAtomic(Path target, byte[] bytes) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".guildmark-", ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void acceptOnClient(Consumer<String> consumer, String value) {
        if (consumer != null) MinecraftClient.getInstance().execute(() -> consumer.accept(value));
    }
    private static void accept(Consumer<String> consumer, String value) { if (consumer != null) consumer.accept(value); }
    private static void fail(Consumer<Throwable> consumer, Throwable error) {
        GuildMarkClient.LOGGER.warn("Dedicated API request failed", error);
        if (consumer != null) MinecraftClient.getInstance().execute(() -> consumer.accept(rootCause(error)));
    }
    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static final class CatalogResponse { int formatVersion; List<GuildDto> guilds; }
    private static final class GuildDto { String id; String name; String image; String file; }
    private static final class PlayersResponse { boolean ok; List<PlayerDto> players; }
    private static final class PlayerDto { String uuid; String username; String capeId; String updatedAt; }
    private record ChallengeRequest(String action, String capeId) {}
    private static final class ChallengeResponse { boolean ok; String serverId; }
    private record ConfirmRequest(String action, String serverId, String username) {}
    private static final class ConfirmResponse { boolean ok; String capeId; }
    private static final class CapabilitiesResponse { boolean ok; String api; int protocolVersion; }
    private static final class ErrorResponse { String error; String message; int retryAfter; int blockMinutes; }
    private static final class ApiHttpException extends Exception {
        final int statusCode;
        final String errorCode;
        final int retryAfter;
        final int blockMinutes;
        ApiHttpException(int statusCode, String errorCode, String message, int retryAfter, int blockMinutes) {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.retryAfter = retryAfter;
            this.blockMinutes = blockMinutes;
        }
    }
}
