package pl.guildmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;

public final class GuildMarkFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private final GuildCapeModel capeModel;
    private final GuildChestModel chestModel;
    private final GuildElytraModel elytraModel;
    private final GuildHeadMarkerModel headMarkerModel;
    public GuildMarkFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context, EntityRendererFactory.Context factory) {
        super(context); capeModel = GuildCapeModel.create(); chestModel = GuildChestModel.create(); elytraModel = GuildElytraModel.create(); headMarkerModel = GuildHeadMarkerModel.create();
    }
    public static GuildData.Guild guildFor(String playerName) {
        if (GuildMarkClient.STORE == null || playerName == null) return null;
        if (MinecraftClient.getInstance().currentScreen instanceof GuildMarkScreen screen && screen.overridesProfileGuild(playerName))
            return screen.profilePreviewGuild();
        if (DedicatedServerMode.isActive()) return DedicatedApiClient.guildForPlayer(playerName);
        return GuildMarkClient.STORE.guildForPlayer(playerName);
    }
    public static boolean isWithinRenderDistance(PlayerEntityRenderState state) {
        if (GuildMarkClient.SETTINGS == null) return true;
        int blocks = GuildMarkClient.SETTINGS.cosmeticRenderDistance();
        return blocks == 0 || state.squaredDistanceToCamera <= (double) blocks * blocks;
    }
    public static boolean shouldRenderCosmetics(PlayerEntityRenderState state) {
        return isWithinRenderDistance(state) && GuildRenderLimiter.shouldRender(state.name);
    }
    public static boolean overridesVanillaCape(PlayerEntityRenderState state) {
        if (!shouldRenderCosmetics(state)) return false;
        GuildData.Guild guild = guildFor(state.name);
        return guild != null && globalCapeEnabled() && guild.showOnCape && GuildMarkTextures.get(guild.markFile) != null;
    }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible || !shouldRenderCosmetics(state)) return;
        GuildData.Guild guild = guildFor(state.name);
        if (guild == null) return;
        GuildHeadMarker.Kind marker = GuildHeadMarker.kind(state.name);
        if (globalHelmetEnabled() && marker != GuildHeadMarker.Kind.NONE) {
            matrices.push(); getContextModel().copyTransforms(headMarkerModel); headMarkerModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GuildHeadMarker.texture(marker)));
            headMarkerModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        GuildMarkTextures.Pair textures = GuildMarkTextures.get(guild.markFile); if (textures == null) return;
        if (globalChestEnabled() && guild.showOnChest) {
            matrices.push(); getContextModel().copyTransforms(chestModel); chestModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            chestModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        if (globalCapeEnabled() && guild.showOnCape && !state.equippedChestStack.isOf(Items.ELYTRA)) {
            matrices.push(); getContextModel().copyTransforms(capeModel); capeModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            capeModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        if (globalElytraEnabled() && guild.showOnElytra && state.equippedChestStack.isOf(Items.ELYTRA)) {
            matrices.push(); matrices.translate(0.0F, 0.0F, 0.125F); elytraModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            elytraModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
    }
    public static boolean globalChestEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderChestEnabled(); }
    public static boolean globalHelmetEnabled() { return !DedicatedServerMode.isActive(); }
    public static boolean globalCapeEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderCapeEnabled(); }
    public static boolean globalShieldEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderShieldEnabled(); }
    public static boolean globalElytraEnabled() { return !DedicatedServerMode.isActive() || GuildMarkClient.SETTINGS == null || GuildMarkClient.SETTINGS.renderElytraEnabled(); }
}
