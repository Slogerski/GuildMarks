package pl.guildmark;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuildMarkTextures {
    public record Pair(Identifier original, int width, int height) {}
    private static final int MAX_CACHED_TEXTURES = 128;
    private static final long MAX_CACHE_BYTES = 256L * 1024L * 1024L;
    private static final Map<String, Pair> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private GuildMarkTextures() {}

    public static void invalidate(String file) {
        Pair removed = CACHE.remove(file);
        if (removed != null) Minecraft.getInstance().getTextureManager().release(removed.original());
    }
    public static void invalidateAll() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        CACHE.values().stream().map(Pair::original).distinct().forEach(textureManager::release);
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
            Pair pair = new Pair(register("mark_original_" + key, renderImage), renderImage.getWidth(), renderImage.getHeight());
            CACHE.put(file, pair);
            evictOldTextures();
            return pair;
        } catch (Exception e) { GuildMarkClient.LOGGER.warn("Cannot load guild mark {}", file, e); return null; }
    }
    private static void evictOldTextures() {
        long bytes = CACHE.values().stream().mapToLong(pair -> (long) pair.width() * pair.height() * 4L).sum();
        var iterator = CACHE.entrySet().iterator();
        var textureManager = Minecraft.getInstance().getTextureManager();
        while (CACHE.size() > 1 && (CACHE.size() > MAX_CACHED_TEXTURES || bytes > MAX_CACHE_BYTES) && iterator.hasNext()) {
            var entry = iterator.next();
            Pair removed = entry.getValue();
            bytes -= (long) removed.width() * removed.height() * 4L;
            iterator.remove();
            textureManager.release(removed.original());
        }
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
        NativeImage nativeImage = NativeImage.read(out.toByteArray()); Identifier id = Identifier.fromNamespaceAndPath("guildmark", name);
        DynamicTexture texture = new LinearDynamicTexture("GuildMark HQ " + name, nativeImage);
        Minecraft.getInstance().getTextureManager().register(id, texture); return id;
    }

    private static final class LinearDynamicTexture extends DynamicTexture {
        private LinearDynamicTexture(String label, NativeImage image) {
            super(() -> label, image);
            sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        }
    }
}
