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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("guildmark/settings.json");
    private Data data = new Data();

    public GuildMarkSettings() { load(); }

    public String language() { return "pl".equals(data.language) ? "pl" : "en"; }
    public boolean isPolish() { return "pl".equals(language()); }
    public int ownHeadHue() { return normalizeHue(data.ownHeadHue); }
    public int allyHeadHue() { return normalizeHue(data.allyHeadHue); }
    public int bannerResolutionDivisor() { return normalizeDivisor(data.bannerResolutionDivisor); }
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
    public void persist() { save(); }

    public static int previewHueColor(int hue) { return colorForHue(normalizeHue(hue), 1.0F); }

    private static int normalizeHue(int hue) { return Math.floorMod(hue, 360); }
    private static int normalizeDivisor(int divisor) {
        return switch (divisor) { case 2 -> 2; case 4 -> 4; case 8 -> 8; case 16 -> 16; case 32 -> 32; case 64 -> 64; default -> 1; };
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
                data.language = "pl".equalsIgnoreCase(data.language) ? "pl" : "en";
                data.ownHeadHue = normalizeHue(data.ownHeadHue);
                data.allyHeadHue = normalizeHue(data.allyHeadHue);
                data.bannerResolutionDivisor = normalizeDivisor(data.bannerResolutionDivisor);
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
        int formatVersion = 3;
        String language = "en";
        int ownHeadHue = 136;
        int allyHeadHue = 49;
        int bannerResolutionDivisor = 1;
    }
}
