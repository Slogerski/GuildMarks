package pl.guildmark.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildHeadMarker;
import pl.guildmark.GuildMarkFeatureRenderer;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin {
    @Unique private static final ThreadLocal<ItemStack> GUILDMARK_HELMET = new ThreadLocal<>();

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"))
    private void guildmark$hideHelmet(PoseStack matrices, SubmitNodeCollector collector, int light,
                                      HumanoidRenderState state, float limbAngle, float limbDistance, CallbackInfo callback) {
        GUILDMARK_HELMET.remove();
        if (state instanceof AvatarRenderState avatarState && GuildHeadMarker.kind(GuildMarkFeatureRenderer.playerName(avatarState)) != GuildHeadMarker.Kind.NONE) {
            GUILDMARK_HELMET.set(state.headEquipment);
            state.headEquipment = ItemStack.EMPTY;
        }
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("RETURN"))
    private void guildmark$restoreHelmet(PoseStack matrices, SubmitNodeCollector collector, int light,
                                         HumanoidRenderState state, float limbAngle, float limbDistance, CallbackInfo callback) {
        ItemStack saved = GUILDMARK_HELMET.get();
        if (saved != null) state.headEquipment = saved;
        GUILDMARK_HELMET.remove();
    }
}
