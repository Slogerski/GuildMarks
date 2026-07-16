package pl.guildmark;

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
        return GuildMarkClient.STORE.data().guilds.stream()
            .filter(g -> g.players.stream().anyMatch(n -> n.equalsIgnoreCase(playerName))).findFirst().orElse(null);
    }
    public static boolean overridesVanillaCape(PlayerEntityRenderState state) {
        GuildData.Guild guild = guildFor(state.name);
        return guild != null && guild.showOnCape && GuildMarkTextures.get(guild.markFile) != null;
    }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible) return;
        GuildData.Guild guild = guildFor(state.name);
        if (guild == null) return;
        GuildHeadMarker.Kind marker = GuildHeadMarker.kind(state.name);
        if (marker != GuildHeadMarker.Kind.NONE) {
            matrices.push(); getContextModel().copyTransforms(headMarkerModel); headMarkerModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GuildHeadMarker.texture(marker)));
            headMarkerModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        GuildMarkTextures.Pair textures = GuildMarkTextures.get(guild.markFile); if (textures == null) return;
        if (guild.showOnChest) {
            matrices.push(); getContextModel().copyTransforms(chestModel); chestModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            chestModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        if (guild.showOnCape && !state.equippedChestStack.isOf(Items.ELYTRA)) {
            matrices.push(); getContextModel().copyTransforms(capeModel); capeModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            capeModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
        if (guild.showOnElytra && state.equippedChestStack.isOf(Items.ELYTRA)) {
            matrices.push(); matrices.translate(0.0F, 0.0F, 0.125F); elytraModel.setAngles(state);
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.original()));
            elytraModel.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV); matrices.pop();
        }
    }
}
