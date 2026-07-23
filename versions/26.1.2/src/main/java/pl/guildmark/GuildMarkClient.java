package pl.guildmark;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.platform.InputConstants;
import javax.imageio.ImageIO;

public final class GuildMarkClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("GuildMark");
    private static final String OPTIONS_BUTTON_LABEL = "✦ GuildMark";
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.parse("guildmark:guildmark"));
    public static GuildStore STORE;
    public static GuildMarkSettings SETTINGS;
    private static KeyMapping openKey;
    private static boolean startupUpdateChecked;

    @Override public void onInitializeClient() {
        SETTINGS = new GuildMarkSettings();
        STORE = new GuildStore();
        ImageIO.scanForPlugins();
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatarRenderer) helper.register(new GuildMarkFeatureRenderer(avatarRenderer, context));
        });
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.guildmark.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GuildRenderLimiter.tick(client);
            DedicatedApiClient.tick();
            if (!startupUpdateChecked) {
                startupUpdateChecked = true;
                startAutomaticUpdate();
            }
            while (openKey.consumeClick()) client.setScreen(new GuildMarkScreen(client.screen));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof OptionsScreen)) return;
            var buttons = Screens.getWidgets(screen);
            buttons.removeIf(widget -> OPTIONS_BUTTON_LABEL.equals(widget.getMessage().getString()));
            if (width < 80) return;
            int buttonWidth = Math.min(116, width - 16);
            int buttonX = Math.max(8, width - buttonWidth - 8);
            buttons.add(new CosmicButton(buttonX, 8, buttonWidth, 20, Component.literal(OPTIONS_BUTTON_LABEL),
                CosmicButton.Style.NAVIGATION, false, () -> client.setScreen(new GuildMarkScreen(screen))));
        });
    }

    private static void startAutomaticUpdate() {
        long now = System.currentTimeMillis();
        if (!SETTINGS.autoUpdateDue(now)) return;
        String url = SETTINGS.autoImportUrl();
        SETTINGS.recordAutoUpdateAttempt(now);
        LOGGER.info("Checking saved guild database for updates: {}", url);
        GuildAutoImporter.start(url,
            summary -> LOGGER.info("Automatic guild update complete: {} guilds, {} images", summary.guilds(), summary.images()),
            error -> LOGGER.warn("Automatic guild update failed; keeping the current database", GuildAutoImporter.rootCause(error)));
    }
}
