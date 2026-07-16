package pl.guildmark.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildHeadMarker;
import pl.guildmark.GuildMarkFeatureRenderer;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Unique private static final ThreadLocal<ItemStack> GUILDMARK_HELMET = new ThreadLocal<>();

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"))
    private void guildmark$hideHelmet(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                      BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo callback) {
        GUILDMARK_HELMET.remove();
        if (state instanceof PlayerEntityRenderState playerState && GuildMarkFeatureRenderer.shouldRenderCosmetics(playerState)
            && GuildHeadMarker.kind(playerState.name) != GuildHeadMarker.Kind.NONE) {
            GUILDMARK_HELMET.set(state.equippedHeadStack);
            state.equippedHeadStack = ItemStack.EMPTY;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("RETURN"))
    private void guildmark$restoreHelmet(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                         BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo callback) {
        ItemStack saved = GUILDMARK_HELMET.get();
        if (saved != null) state.equippedHeadStack = saved;
        GUILDMARK_HELMET.remove();
    }
}
