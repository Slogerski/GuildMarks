package pl.guildmark.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildMarkFeatureRenderer;

@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin {
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void guildmark$replaceVanillaCape(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                               PlayerEntityRenderState state, float limbAngle, float limbDistance,
                                               CallbackInfo callback) {
        if (GuildMarkFeatureRenderer.overridesVanillaCape(state)) callback.cancel();
    }
}
