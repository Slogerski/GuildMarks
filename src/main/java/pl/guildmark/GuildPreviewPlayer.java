package pl.guildmark;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;

import java.util.function.Supplier;

public final class GuildPreviewPlayer extends OtherClientPlayerEntity {
    private final Supplier<SkinTextures> textures;
    private long lastAnimationTick = -1;

    public GuildPreviewPlayer(ClientWorld world, GameProfile profile, Supplier<SkinTextures> textures) {
        super(world, profile);
        this.textures = textures == null ? () -> DefaultSkinHelper.getSkinTextures(profile) : textures;
    }

    @Override public SkinTextures getSkinTextures() { return textures.get(); }

    public void animateInPlace() {
        long tick = net.minecraft.util.Util.getMeasuringTimeMs() / 50L;
        if (tick != lastAnimationTick) {
            lastAnimationTick = tick;
            limbAnimator.updateLimbs(0.55F, 0.45F, 1.0F);
        }
    }
}
