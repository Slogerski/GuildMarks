package pl.guildmark.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.guildmark.GuildHeadMarker;
import pl.guildmark.GuildMarkFeatureRenderer;

@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void guildmark$hideAllyHead(AvatarRenderState state, CallbackInfo callback) {
        PlayerModel model = (PlayerModel)(Object)this;
        boolean marked = GuildMarkFeatureRenderer.shouldRenderCosmetics(state)
            && GuildHeadMarker.kind(GuildMarkFeatureRenderer.playerName(state)) != GuildHeadMarker.Kind.NONE;
        model.head.visible = !marked;
        model.hat.visible = !marked && state.showHat;
    }
}
