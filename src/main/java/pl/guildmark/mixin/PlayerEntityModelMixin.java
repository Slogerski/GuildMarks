package pl.guildmark.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildHeadMarker;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void guildmark$hideAllyHead(PlayerEntityRenderState state, CallbackInfo callback) {
        PlayerEntityModel model = (PlayerEntityModel)(Object)this;
        boolean marked = GuildHeadMarker.kind(state.name) != GuildHeadMarker.Kind.NONE;
        model.head.visible = !marked;
        model.hat.visible = !marked && state.hatVisible;
    }
}
