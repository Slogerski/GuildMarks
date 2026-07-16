package pl.guildmark;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GuildShieldRenderContext {
    private static final ThreadLocal<Identifier> TEXTURE = new ThreadLocal<>();
    private GuildShieldRenderContext() {}

    public static void begin(AvatarRenderState state, ItemStack stack) {
        clear();
        if (!GuildMarkFeatureRenderer.shouldRenderCosmetics(state)) return;
        if (!stack.is(Items.SHIELD)) return;
        GuildData.Guild guild = GuildMarkFeatureRenderer.guildFor(GuildMarkFeatureRenderer.playerName(state));
        if (guild == null || !guild.showOnShield) return;
        GuildMarkTextures.Pair textures = GuildMarkTextures.get(guild.markFile);
        if (textures != null) TEXTURE.set(textures.original());
    }

    public static Identifier texture() { return TEXTURE.get(); }
    public static void clear() { TEXTURE.remove(); }
}
