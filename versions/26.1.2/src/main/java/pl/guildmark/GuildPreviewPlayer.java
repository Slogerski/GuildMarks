package pl.guildmark;

import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

public final class GuildPreviewPlayer extends RemotePlayer {
    private final Supplier<PlayerSkin> textures;
    private long lastAnimationTick = -1;

    public GuildPreviewPlayer(ClientLevel world, GameProfile profile, Supplier<PlayerSkin> textures) {
        super(world, profile);
        this.textures = textures == null ? () -> DefaultPlayerSkin.get(profile) : textures;
    }

    @Override public PlayerSkin getSkin() { return textures.get(); }

    public void animateInPlace() {
        long tick = net.minecraft.util.Util.getMillis() / 50L;
        if (tick != lastAnimationTick) {
            lastAnimationTick = tick;
            walkAnimation.update(0.55F, 0.45F, 1.0F);
        }
    }
}
