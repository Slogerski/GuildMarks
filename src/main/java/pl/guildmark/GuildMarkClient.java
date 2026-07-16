package pl.guildmark;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;

public final class GuildMarkClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("GuildMark");
    private static final String OPTIONS_BUTTON_LABEL = "✦ GuildMark";
    public static GuildStore STORE;
    public static GuildMarkSettings SETTINGS;
    private static KeyBinding openKey;
    private static boolean startupUpdateChecked;

    @Override public void onInitializeClient() {
        SETTINGS = new GuildMarkSettings();
        STORE = new GuildStore();
        ImageIO.scanForPlugins();
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) helper.register(new GuildMarkFeatureRenderer(playerRenderer, context));
        });
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.guildmark.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.guildmark"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GuildRenderLimiter.tick(client);
            if (!startupUpdateChecked) {
                startupUpdateChecked = true;
                startAutomaticUpdate();
            }
            while (openKey.wasPressed()) client.setScreen(new GuildMarkScreen(client.currentScreen));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof OptionsScreen)) return;
            var buttons = Screens.getButtons(screen);
            buttons.removeIf(widget -> OPTIONS_BUTTON_LABEL.equals(widget.getMessage().getString()));
            if (width < 80) return;
            int buttonWidth = Math.min(116, width - 16);
            int buttonX = Math.max(8, width - buttonWidth - 8);
            buttons.add(new CosmicButton(buttonX, 8, buttonWidth, 20, Text.literal(OPTIONS_BUTTON_LABEL),
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
