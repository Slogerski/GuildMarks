package pl.guildmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

public final class GuildMarkSettings {
    private static final long AUTO_UPDATE_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("guildmark/settings.json");
    private Data data = new Data();

    public GuildMarkSettings() { load(); }

    public String language() { return "pl".equals(data.language) ? "pl" : "en"; }
    public boolean isPolish() { return "pl".equals(language()); }
    public int ownHeadHue() { return normalizeHue(data.ownHeadHue); }
    public int allyHeadHue() { return normalizeHue(data.allyHeadHue); }
    public int bannerResolutionDivisor() { return normalizeDivisor(data.bannerResolutionDivisor); }
    public int cosmeticRenderDistance() { return normalizeRenderDistance(data.cosmeticRenderDistance); }
    public int maxRenderedPlayers() { return normalizePlayerLimit(data.maxRenderedPlayers); }
    public String autoImportUrl() { return data.autoImportUrl == null ? "" : data.autoImportUrl; }
    public boolean autoUpdateEnabled() { return data.autoUpdateEnabled; }
    public String dedicatedProfileCapeId() { return data.dedicatedProfileCapeId == null ? "" : data.dedicatedProfileCapeId; }
    public String dedicatedApiUrl() { return data.dedicatedApiUrl == null ? "" : data.dedicatedApiUrl; }
    public boolean renderChestEnabled() { return data.renderChestEnabled; }
    public boolean renderHelmetEnabled() { return data.renderHelmetEnabled; }
    public boolean renderCapeEnabled() { return data.renderCapeEnabled; }
    public boolean renderShieldEnabled() { return data.renderShieldEnabled; }
    public boolean renderElytraEnabled() { return data.renderElytraEnabled; }
    public int ownHeadColor() { return colorForHue(ownHeadHue(), 0.94F); }
    public int allyHeadColor() { return colorForHue(allyHeadHue(), 1.0F); }

    public void setLanguage(String language) {
        String normalized = "pl".equalsIgnoreCase(language) ? "pl" : "en";
        if (normalized.equals(data.language)) return;
        data.language = normalized;
        save();
    }

    public void setOwnHeadHue(int hue) { data.ownHeadHue = normalizeHue(hue); }
    public void setAllyHeadHue(int hue) { data.allyHeadHue = normalizeHue(hue); }
    public void setBannerResolutionDivisor(int divisor) { data.bannerResolutionDivisor = normalizeDivisor(divisor); }
    public void setCosmeticRenderDistance(int blocks) { data.cosmeticRenderDistance = normalizeRenderDistance(blocks); }
    public void setMaxRenderedPlayers(int players) { data.maxRenderedPlayers = normalizePlayerLimit(players); }
    public void setAutoImportUrl(String url) { data.autoImportUrl = url == null ? "" : url.strip(); save(); }
    public void setAutoUpdateEnabled(boolean enabled) { data.autoUpdateEnabled = enabled; save(); }
    public void setDedicatedProfileCapeId(String capeId) { data.dedicatedProfileCapeId = capeId == null ? "" : capeId.strip(); save(); }
    public void setDedicatedApiUrl(String url) { data.dedicatedApiUrl = url == null ? "" : url.strip(); save(); }
    public void setRenderChestEnabled(boolean enabled) { data.renderChestEnabled = enabled; save(); }
    public void setRenderHelmetEnabled(boolean enabled) { data.renderHelmetEnabled = enabled; save(); }
    public void setRenderCapeEnabled(boolean enabled) { data.renderCapeEnabled = enabled; save(); }
    public void setRenderShieldEnabled(boolean enabled) { data.renderShieldEnabled = enabled; save(); }
    public void setRenderElytraEnabled(boolean enabled) { data.renderElytraEnabled = enabled; save(); }
    public boolean autoUpdateDue(long now) {
        long previous = data.lastAutoUpdateEpochMillis;
        return data.autoUpdateEnabled && !autoImportUrl().isBlank() && (previous <= 0L || now < previous || now - previous >= AUTO_UPDATE_INTERVAL_MILLIS);
    }
    public void recordAutoUpdateAttempt(long now) { data.lastAutoUpdateEpochMillis = Math.max(0L, now); save(); }
    public void persist() { save(); }

    public static int previewHueColor(int hue) { return colorForHue(normalizeHue(hue), 1.0F); }

    private static int normalizeHue(int hue) { return Math.floorMod(hue, 360); }
    private static int normalizeDivisor(int divisor) {
        return switch (divisor) { case 2 -> 2; case 4 -> 4; case 8 -> 8; case 16 -> 16; case 32 -> 32; case 64 -> 64; default -> 1; };
    }
    private static int normalizeRenderDistance(int blocks) {
        return switch (blocks) { case 10, 16, 24, 32, 48, 64, 96, 128, 192, 256 -> blocks; case 0 -> 0; default -> 128; };
    }
    private static int normalizePlayerLimit(int players) {
        return switch (players) { case 8, 16, 32, 64, 128 -> players; case 0 -> 0; default -> 64; };
    }

    private static int colorForHue(int hue, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue / 360.0F, 0.85F, brightness);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private void load() {
        try {
            if (Files.exists(path)) {
                Data loaded = GSON.fromJson(Files.readString(path), Data.class);
                if (loaded != null) data = loaded;
                if (data.formatVersion < 4) data.autoUpdateEnabled = true;
                if (data.formatVersion < 5) data.cosmeticRenderDistance = 128;
                if (data.formatVersion < 6) data.maxRenderedPlayers = 64;
                if (data.formatVersion < 7) {
                    data.renderChestEnabled = true;
                    data.renderHelmetEnabled = true;
                    data.renderCapeEnabled = true;
                    data.renderShieldEnabled = true;
                    data.renderElytraEnabled = true;
                }
                if (data.formatVersion < 8) data.dedicatedProfileCapeId = "";
                data.formatVersion = 9;
                data.language = "pl".equalsIgnoreCase(data.language) ? "pl" : "en";
                data.ownHeadHue = normalizeHue(data.ownHeadHue);
                data.allyHeadHue = normalizeHue(data.allyHeadHue);
                data.bannerResolutionDivisor = normalizeDivisor(data.bannerResolutionDivisor);
                data.cosmeticRenderDistance = normalizeRenderDistance(data.cosmeticRenderDistance);
                data.maxRenderedPlayers = normalizePlayerLimit(data.maxRenderedPlayers);
                if (data.autoImportUrl == null) data.autoImportUrl = "";
                if (data.dedicatedProfileCapeId == null) data.dedicatedProfileCapeId = "";
                if (data.dedicatedApiUrl == null) data.dedicatedApiUrl = "";
            } else save();
        } catch (Exception error) {
            data = new Data();
            GuildMarkClient.LOGGER.error("Cannot load {}", path, error);
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            GuildMarkClient.LOGGER.error("Cannot save {}", path, error);
        }
    }

    private static final class Data {
        int formatVersion = 9;
        String language = "en";
        int ownHeadHue = 136;
        int allyHeadHue = 49;
        int bannerResolutionDivisor = 1;
        int cosmeticRenderDistance = 128;
        int maxRenderedPlayers = 64;
        String autoImportUrl = "";
        boolean autoUpdateEnabled = true;
        long lastAutoUpdateEpochMillis;
        String dedicatedProfileCapeId = "";
        String dedicatedApiUrl = "";
        boolean renderChestEnabled = true;
        boolean renderHelmetEnabled = true;
        boolean renderCapeEnabled = true;
        boolean renderShieldEnabled = true;
        boolean renderElytraEnabled = true;
    }
}
