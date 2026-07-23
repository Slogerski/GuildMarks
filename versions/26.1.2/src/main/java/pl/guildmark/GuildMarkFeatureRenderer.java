package pl.guildmark;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

public final class GuildMarkFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final GuildCapeModel capeModel;
    private final GuildChestModel chestModel;
    private final GuildElytraModel elytraModel;
    private final GuildHeadMarkerModel headMarkerModel;
    public GuildMarkFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context, EntityRendererProvider.Context factory) {
        super(context); capeModel = GuildCapeModel.create(); chestModel = GuildChestModel.create(); elytraModel = GuildElytraModel.create(); headMarkerModel = GuildHeadMarkerModel.create();
    }
    public static GuildData.Guild guildFor(String playerName) {
        if (GuildMarkClient.STORE == null || playerName == null) return null;
        if (Minecraft.getInstance().screen instanceof GuildMarkScreen screen && screen.overridesProfileGuild(playerName))
            return screen.profilePreviewGuild();
        if (DedicatedServerMode.isActive()) return DedicatedApiClient.guildForPlayer(playerName);
        return GuildMarkClient.STORE.guildForPlayer(playerName);
    }
    public static boolean isWithinRenderDistance(AvatarRenderState state) {
        if (GuildMarkClient.SETTINGS == null) return true;
        int blocks = GuildMarkClient.SETTINGS.cosmeticRenderDistance();
        return blocks == 0 || state.distanceToCameraSq <= (double) blocks * blocks;
    }
    public static boolean shouldRenderCosmetics(AvatarRenderState state) {
        return isWithinRenderDistance(state) && GuildRenderLimiter.shouldRender(playerName(state));
    }
    public static boolean overridesVanillaCape(AvatarRenderState state) {
        if (!shouldRenderCosmetics(state)) return false;
        GuildData.Guild guild = guildFor(playerName(state));
        return guild != null && globalCapeEnabled() && guild.showOnCape && GuildMarkTextures.get(guild.markFile) != null;
    }
    public static String playerName(AvatarRenderState state) {
        return ((GuildMarkAvatarState) state).guildmark$getPlayerName();
    }
    @Override public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        if (state.isInvisible || !shouldRenderCosmetics(state)) return;
        String playerName = playerName(state);
        GuildData.Guild guild = guildFor(playerName);
        if (guild == null) return;
        GuildHeadMarker.Kind marker = GuildHeadMarker.kind(playerName);
        if (globalHelmetEnabled() && marker != GuildHeadMarker.Kind.NONE) {
            collector.submitModel(headMarkerModel, state, matrices, RenderTypes.entityCutout(GuildHeadMarker.texture(marker)),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
        GuildMarkTextures.Pair textures = GuildMarkTextures.get(guild.markFile); if (textures == null) return;
        if (globalChestEnabled() && guild.showOnChest) {
            collector.submitModel(chestModel, state, matrices, RenderTypes.entityCutout(textures.original()),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
        if (globalCapeEnabled() && guild.showOnCape && !state.chestEquipment.is(Items.ELYTRA)) {
            collector.submitModel(capeModel, state, matrices, RenderTypes.entityCutout(textures.original()),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
        if (globalElytraEnabled() && guild.showOnElytra && state.chestEquipment.is(Items.ELYTRA)) {
            matrices.pushPose();
            matrices.translate(0.0F, 0.0F, 0.125F);
            collector.submitModel(elytraModel, state, matrices, RenderTypes.entityCutout(textures.original()),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
            matrices.popPose();
        }
    }
    public static boolean globalChestEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderChestEnabled(); }
    public static boolean globalHelmetEnabled() { return !DedicatedServerMode.isActive(); }
    public static boolean globalCapeEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderCapeEnabled(); }
    public static boolean globalShieldEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderShieldEnabled(); }
    public static boolean globalElytraEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderElytraEnabled(); }
}
