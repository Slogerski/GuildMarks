package pl.guildmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

public final class GuildShieldRenderContext {
    private static final ThreadLocal<Identifier> TEXTURE = new ThreadLocal<>();
    private GuildShieldRenderContext() {}

    public static void begin(PlayerEntityRenderState state, Arm arm) {
        clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!(client.world.getEntityById(state.id) instanceof AbstractClientPlayerEntity player)) return;
        if (!player.getStackInArm(arm).isOf(Items.SHIELD)) return;
        GuildData.Guild guild = GuildMarkFeatureRenderer.guildFor(state.name);
        if (guild == null || !guild.showOnShield) return;
        GuildMarkTextures.Pair textures = GuildMarkTextures.get(guild.markFile);
        if (textures != null) TEXTURE.set(textures.original());
    }

    public static Identifier texture() { return TEXTURE.get(); }
    public static void clear() { TEXTURE.remove(); }
}
