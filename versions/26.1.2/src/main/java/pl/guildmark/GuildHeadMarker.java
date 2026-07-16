package pl.guildmark;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class GuildHeadMarker {
    public enum Kind { NONE, OWN, ALLY }
    private static Identifier ownTexture;
    private static Identifier allyTexture;
    private static int ownTextureColor;
    private static int allyTextureColor;
    private GuildHeadMarker() {}

    public static Kind kind(String targetPlayerName) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof GuildMarkScreen) return Kind.NONE;
        GuildData.Guild targetGuild = GuildMarkFeatureRenderer.guildFor(targetPlayerName);
        if (targetGuild == null || !targetGuild.showOnHelmet) return Kind.NONE;
        if ("own".equals(targetGuild.relation)) return Kind.OWN;
        if ("ally".equals(targetGuild.relation)) return Kind.ALLY;
        return Kind.NONE;
    }

    public static Identifier texture(Kind kind) {
        if (kind == Kind.OWN) {
            int color = GuildMarkClient.SETTINGS == null ? 0xFF24F05A : GuildMarkClient.SETTINGS.ownHeadColor();
            if (ownTexture == null || ownTextureColor != color) {
                ownTexture = register("head_marker_own", color);
                ownTextureColor = color;
            }
            return ownTexture;
        }
        if (kind == Kind.ALLY) {
            int color = GuildMarkClient.SETTINGS == null ? 0xFFFFD629 : GuildMarkClient.SETTINGS.allyHeadColor();
            if (allyTexture == null || allyTextureColor != color) {
                allyTexture = register("head_marker_ally", color);
                allyTextureColor = color;
            }
            return allyTexture;
        }
        return null;
    }

    public static void invalidateTextures() {
        ownTexture = null;
        allyTexture = null;
    }

    private static Identifier register(String name, int color) {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, color);
        Identifier id = Identifier.fromNamespaceAndPath("guildmark", name);
        DynamicTexture texture = new DynamicTexture(() -> "GuildMark " + name, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }
}
