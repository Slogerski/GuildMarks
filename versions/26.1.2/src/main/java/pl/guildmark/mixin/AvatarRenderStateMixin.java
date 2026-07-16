package pl.guildmark.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.guildmark.GuildMarkAvatarState;

@Mixin(AvatarRenderState.class)
public final class AvatarRenderStateMixin implements GuildMarkAvatarState {
    @Unique private String guildmark$playerName;

    @Override public String guildmark$getPlayerName() {
        return guildmark$playerName;
    }

    @Override public void guildmark$setPlayerName(String playerName) {
        guildmark$playerName = playerName;
    }
}
