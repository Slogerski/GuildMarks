package pl.guildmark;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.LivingEntity;

public final class GuildPlayerPreviewCache {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static ClientLevel cachedWorld;
    private GuildPlayerPreviewCache() {}

    public static LivingEntity get(String nickname) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null || nickname == null || nickname.isBlank()) return null;
        if (cachedWorld != world) { CACHE.clear(); cachedWorld = world; }
        for (AbstractClientPlayer player : world.players()) {
            if (player.getGameProfile().name().equalsIgnoreCase(nickname)) return player;
        }
        String key = nickname.toLowerCase(Locale.ROOT);
        Entry entry = CACHE.computeIfAbsent(key, ignored -> createFallback(world, nickname));
        if (!entry.requested) {
            entry.requested = true;
            fetchProfile(nickname).thenAccept(profile -> {
                if (profile == null) return;
                client.execute(() -> {
                    if (client.level != world) return;
                    var supplier = client.getSkinManager().createLookup(profile, false);
                    entry.entity = new GuildPreviewPlayer(world, profile, supplier);
                });
            }).exceptionally(error -> { GuildMarkClient.LOGGER.debug("Skin lookup failed for {}", nickname, error); return null; });
        }
        return entry.entity;
    }

    private static Entry createFallback(ClientLevel world, String nickname) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, nickname);
        return new Entry(new GuildPreviewPlayer(world, profile, () -> DefaultPlayerSkin.get(profile)));
    }

    private static java.util.concurrent.CompletableFuture<GameProfile> fetchProfile(String nickname) {
        String encoded = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
            .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").header("User-Agent", "GuildMark/1.0.1").GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            if (response.statusCode() / 100 != 2 || response.body().isBlank()) return null;
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            UUID uuid = parseUuid(json.get("id").getAsString());
            ProfileResult result = Minecraft.getInstance().services().sessionService().fetchProfile(uuid, true);
            return result == null ? new GameProfile(uuid, nickname) : result.profile();
        });
    }

    private static UUID parseUuid(String value) {
        String compact = value.replace("-", "");
        if (compact.length() != 32) throw new IllegalArgumentException("Invalid Mojang UUID");
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12, 16)
            + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
    }

    private static final class Entry {
        private volatile GuildPreviewPlayer entity;
        private volatile boolean requested;
        private Entry(GuildPreviewPlayer entity) { this.entity = entity; }
    }
}
