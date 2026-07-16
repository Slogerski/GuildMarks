package pl.guildmark;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class GuildMarkTextures {
    public record Pair(Identifier original, int width, int height) {}
    private static final Map<String, Pair> CACHE = new HashMap<>();
    private GuildMarkTextures() {}

    public static void invalidate(String file) {
        Pair removed = CACHE.remove(file);
        if (removed != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(removed.original());
    }
    public static void invalidateAll() {
        var textureManager = MinecraftClient.getInstance().getTextureManager();
        CACHE.values().stream().map(Pair::original).distinct().forEach(textureManager::destroyTexture);
        CACHE.clear();
    }
    public static Pair get(String file) {
        if (file == null || file.isBlank()) return null;
        if (CACHE.containsKey(file)) return CACHE.get(file);
        try {
            Path root = FabricLoader.getInstance().getConfigDir().resolve("GuildMark/Guilds").normalize();
            Path path = root.resolve(file).normalize(); if (!path.startsWith(root) || !Files.isRegularFile(path)) return null;
            BufferedImage logo = ImageIO.read(path.toFile()); if (logo == null) return null;
            int divisor = GuildMarkClient.SETTINGS == null ? 1 : GuildMarkClient.SETTINGS.bannerResolutionDivisor();
            BufferedImage renderImage = downscale(logo, divisor);
            String key = Integer.toHexString(file.hashCode());
            Pair pair = new Pair(register("mark_original_" + key, renderImage), renderImage.getWidth(), renderImage.getHeight()); CACHE.put(file, pair); return pair;
        } catch (Exception e) { GuildMarkClient.LOGGER.warn("Cannot load guild mark {}", file, e); return null; }
    }
    private static BufferedImage downscale(BufferedImage source, int divisor) {
        if (divisor <= 1) return source;
        int width = Math.max(1, (source.getWidth() + divisor - 1) / divisor);
        int height = Math.max(1, (source.getHeight() + divisor - 1) / divisor);
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return scaled;
    }
    private static Identifier register(String name, BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", out);
        NativeImage nativeImage = NativeImage.read(out.toByteArray()); Identifier id = Identifier.of("guildmark", name);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "GuildMark HQ " + name, nativeImage);
        texture.setFilter(true, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture); return id;
    }
}
