package pl.guildmark.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.model.special.ShieldModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildShieldModel;
import pl.guildmark.GuildShieldRenderContext;

@Mixin(ShieldModelRenderer.class)
public abstract class ShieldModelRendererMixin {
    private static final GuildShieldModel GUILDMARK_MODEL = GuildShieldModel.create();

    @Inject(method = "render(Lnet/minecraft/component/ComponentMap;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IIZ)V", at = @At("TAIL"))
    private void guildmark$renderShieldMark(ComponentMap data, ItemDisplayContext displayContext, MatrixStack matrices,
                                             VertexConsumerProvider consumers, int light, int overlay, boolean glint,
                                             CallbackInfo callback) {
        Identifier texture = GuildShieldRenderContext.texture();
        if (texture == null) return;
        matrices.push();
        matrices.scale(1.0F, -1.0F, -1.0F);
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        GUILDMARK_MODEL.render(matrices, vertices, light);
        matrices.pop();
    }
}
