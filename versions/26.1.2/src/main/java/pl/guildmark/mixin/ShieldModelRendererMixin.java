package pl.guildmark.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildShieldModel;
import pl.guildmark.GuildShieldRenderContext;

@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldModelRendererMixin {
    private static final GuildShieldModel GUILDMARK_MODEL = GuildShieldModel.create();

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("TAIL"))
    private void guildmark$renderShieldMark(DataComponentMap data, PoseStack matrices,
                                             SubmitNodeCollector collector, int light, int overlay, boolean glint, int outlineColor,
                                             CallbackInfo callback) {
        Identifier texture = GuildShieldRenderContext.texture();
        if (texture == null) return;
        GUILDMARK_MODEL.submit(matrices, collector, texture, light);
    }
}
