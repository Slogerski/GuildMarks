package pl.guildmark.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildMarkFeatureRenderer;

@Mixin(CapeLayer.class)
public abstract class CapeFeatureRendererMixin {
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void guildmark$replaceVanillaCape(PoseStack matrices, SubmitNodeCollector collector, int light,
                                               AvatarRenderState state, float limbAngle, float limbDistance,
                                               CallbackInfo callback) {
        if (GuildMarkFeatureRenderer.overridesVanillaCape(state)) callback.cancel();
    }
}
