package pl.guildmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static pl.guildmark.GuildMarkI18n.tr;

public final class GuildMarkScreen extends Screen {
    private static final int UI_WIDTH = 760;
    private static final int UI_HEIGHT = 400;
    private static final int[] RENDER_DISTANCES = {10, 16, 24, 32, 48, 64, 96, 128, 192, 256, 0};
    private static final int[] PLAYER_LIMITS = {8, 16, 32, 64, 128, 0};
    private enum Page { PROFILE, RENDER, MAP, SETTINGS, EDITOR, AUTO_IMPORT, AUTHOR }
    private final Screen parent;
    private Page page = Page.MAP;
    private int selectedGuild = -1;
    private int scroll;
    private int editorGuildScroll;
    private String status = tr("Data is stored locally", "Dane zapisują się lokalnie");
    private TextFieldWidget guildInput, playerInput, urlInput, apiInput, markSourceInput;
    private CosmicButton chestToggleButton, helmetToggleButton, capeToggleButton, shieldToggleButton, elytraToggleButton, autoUpdateButton;
    private CosmicButton profilePickerButton, globalChestButton, globalHelmetButton, globalCapeButton, globalShieldButton, globalElytraButton;
    private boolean capePickerOpen;
    private int profileGridScroll;
    private boolean draggingModel;
    private boolean draggingEditorScrollbar;
    private boolean draggingOwnHeadHue;
    private boolean draggingAllyHeadHue;
    private boolean draggingBannerResolution;
    private boolean draggingRenderDistance;
    private boolean draggingPlayerLimit;
    private boolean apiDiscoveryStarted;
    private String discoveredApiUrl = DedicatedApiClient.MCEXTREME_DEFAULT_API;
    private Boolean recommendedApiAvailable;
    private int autoImportRequest;
    private float modelYaw;
    private float modelPitch;

    public GuildMarkScreen(Screen parent) { super(Text.literal("GuildMark")); this.parent = parent; }

    private float responsiveUiScale() {
        float horizontal = (width - 24.0F) / UI_WIDTH;
        float vertical = (height - 24.0F) / UI_HEIGHT;
        return Math.max(0.35F, Math.min(1.0F, Math.min(horizontal, vertical)));
    }

    private int layoutWidth() { return Math.max(UI_WIDTH, (int)Math.floor(width / responsiveUiScale())); }
    private int layoutHeight() { return Math.max(UI_HEIGHT, (int)Math.floor(height / responsiveUiScale())); }
    private double layoutMouse(double coordinate) { return coordinate / responsiveUiScale(); }

    @Override protected void init() {
        boolean dedicated = DedicatedServerMode.isActive();
        if (dedicated && page != Page.PROFILE && page != Page.RENDER && page != Page.AUTO_IMPORT && page != Page.SETTINGS && page != Page.AUTHOR) page = Page.PROFILE;
        if (!dedicated && (page == Page.PROFILE || page == Page.RENDER)) page = Page.MAP;
        String guildDraft = fieldText(guildInput), playerDraft = fieldText(playerInput);
        String urlDraft = urlInput == null ? GuildMarkClient.SETTINGS.autoImportUrl() : fieldText(urlInput);
        String apiDraft = apiInput == null ? GuildMarkClient.SETTINGS.dedicatedApiUrl() : fieldText(apiInput);
        String markSourceDraft = fieldText(markSourceInput);
        int previousEditorGuildScroll = editorGuildScroll;
        clearChildren();
        guildInput = null; playerInput = null; urlInput = null; apiInput = null; markSourceInput = null;
        draggingModel = false; draggingEditorScrollbar = false; draggingOwnHeadHue = false; draggingAllyHeadHue = false; draggingBannerResolution = false; draggingRenderDistance = false; draggingPlayerLimit = false;
        chestToggleButton = null; helmetToggleButton = null; capeToggleButton = null; shieldToggleButton = null; elytraToggleButton = null; autoUpdateButton = null;
        profilePickerButton = null; globalChestButton = null; globalHelmetButton = null; globalCapeButton = null; globalShieldButton = null; globalElytraButton = null;
        int layoutWidth = layoutWidth(), layoutHeight = layoutHeight();
        int left = Math.max(12, (layoutWidth - UI_WIDTH) / 2), top = Math.max(12, (layoutHeight - UI_HEIGHT) / 2);
        int navX = left + 18, navY = top + (dedicated ? 112 : 186);
        if (dedicated) {
            addNav(tr("PROFILE", "PROFIL"), Page.PROFILE, navX, navY);
            addNav("RENDER", Page.RENDER, navX, navY + 34);
            addNav("AUTO IMPORT", Page.AUTO_IMPORT, navX, navY + 68);
            addNav(tr("SETTINGS", "USTAWIENIA"), Page.SETTINGS, navX, navY + 102);
            addNav(tr("ABOUT", "O MODZIE"), Page.AUTHOR, navX, navY + 136);
            addDrawableChild(new CosmicButton(navX, navY + 170, 142, 24, Text.literal(tr("EXIT", "WYJŚCIE")), CosmicButton.Style.DANGER, false, this::close));
        } else {
            addNav(tr("MAP", "MAPA"), Page.MAP, navX, navY);
            addNav(tr("SETTINGS", "USTAWIENIA"), Page.SETTINGS, navX, navY + 34);
            addNav(tr("EDITOR", "EDYTOR"), Page.EDITOR, navX, navY + 68);
            addNav("AUTO IMPORT", Page.AUTO_IMPORT, navX, navY + 102);
            addNav(tr("ABOUT", "O MODZIE"), Page.AUTHOR, navX, navY + 136);
            addDrawableChild(new CosmicButton(navX, navY + 170, 142, 24, Text.literal(tr("EXIT", "WYJŚCIE")), CosmicButton.Style.DANGER, false, this::close));
        }
        int cx = left + 190, cy = top + 76;
        if (page == Page.PROFILE) initProfile(cx, cy);
        if (page == Page.RENDER) initGlobalRender(cx, cy);
        if (page == Page.EDITOR) {
            initEditor(cx, cy);
            guildInput.setText(guildDraft); playerInput.setText(playerDraft); markSourceInput.setText(markSourceDraft);
            editorGuildScroll = previousEditorGuildScroll;
        }
        if (page == Page.AUTO_IMPORT) {
            initAutoImport(cx, cy);
            if (urlInput != null) urlInput.setText(urlDraft);
            if (apiInput != null) apiInput.setText(apiDraft);
        }
        if (page == Page.AUTHOR) initAuthor(cx, cy);
        if (page == Page.SETTINGS) {
            if (!dedicated) {
                addDrawableChild(new CosmicButton(cx, cy, 230, 22, Text.literal(tr("Export JSON to clipboard", "Eksportuj JSON do schowka")), CosmicButton.Style.EDITOR, false, this::exportClipboard));
                addDrawableChild(new CosmicButton(cx, cy + 30, 230, 22, Text.literal(tr("Import JSON from clipboard", "Importuj JSON ze schowka")), CosmicButton.Style.EDITOR, false, this::importClipboard));
            }
            addDrawableChild(new CosmicButton(cx + 250, cy + 16, 120, 22, Text.literal("ENGLISH"), CosmicButton.Style.EDITOR, !GuildMarkClient.SETTINGS.isPolish(), () -> setLanguage("en")));
            addDrawableChild(new CosmicButton(cx + 378, cy + 16, 120, 22, Text.literal("POLSKI"), CosmicButton.Style.EDITOR, GuildMarkClient.SETTINGS.isPolish(), () -> setLanguage("pl")));
        }
    }

    private void initProfile(int x, int y) {
        profilePickerButton = new CosmicButton(x + 280, y, 230, 24, Text.literal(""), CosmicButton.Style.EDITOR, false, () -> {
            capePickerOpen = !capePickerOpen;
            profileGridScroll = 0;
            updateProfilePickerButton();
        });
        addDrawableChild(profilePickerButton);
        addDrawableChild(new CosmicButton(x + 280, y + 30, 230, 20, Text.literal(tr("REMOVE SELECTION", "USUŃ WYBÓR")), CosmicButton.Style.EDITOR_DANGER, false, () -> selectProfileGuild(null)));
        updateProfilePickerButton();
    }

    private void initGlobalRender(int x, int y) {
        globalCapeButton = globalToggle(x, y + 30, tr("CAPE", "PELERYNA"), () -> GuildMarkClient.SETTINGS.setRenderCapeEnabled(!GuildMarkClient.SETTINGS.renderCapeEnabled()));
        globalChestButton = globalToggle(x + 260, y + 30, tr("CHEST", "KLATKA"), () -> GuildMarkClient.SETTINGS.setRenderChestEnabled(!GuildMarkClient.SETTINGS.renderChestEnabled()));
        globalShieldButton = globalToggle(x, y + 76, tr("SHIELD", "TARCZA"), () -> GuildMarkClient.SETTINGS.setRenderShieldEnabled(!GuildMarkClient.SETTINGS.renderShieldEnabled()));
        globalElytraButton = globalToggle(x + 260, y + 76, "ELYTRA", () -> GuildMarkClient.SETTINGS.setRenderElytraEnabled(!GuildMarkClient.SETTINGS.renderElytraEnabled()));
        updateGlobalRenderButtons();
    }

    private CosmicButton globalToggle(int x, int y, String label, Runnable toggle) {
        CosmicButton button = new CosmicButton(x, y, 246, 30, Text.literal(label), CosmicButton.Style.EDITOR, false, () -> { toggle.run(); updateGlobalRenderButtons(); });
        addDrawableChild(button);
        return button;
    }

    private void addNav(String label, Page target, int x, int y) {
        addDrawableChild(new CosmicButton(x, y, 142, 26, Text.literal((page == target ? "◆ " : "◇ ") + label), CosmicButton.Style.NAVIGATION, page == target,
            () -> { page = target; scroll = 0; init(); }));
    }

    private void initEditor(int x, int y) {
        guildInput = styledField(x, y, 190, 20, tr("Guild name", "Nazwa gildii"), tr("New guild name / search", "Nazwa nowej gildii / szukaj"));
        guildInput.setChangedListener(value -> editorGuildScroll = 0); addDrawableChild(guildInput);
        addDrawableChild(new CosmicButton(x + 198, y, 82, 20, Text.literal(tr("+ Guild", "+ Gildia")), CosmicButton.Style.EDITOR, false, this::addGuild));
        playerInput = styledField(x, y + 140, 190, 20, tr("Player name", "Nick gracza"), tr("Minecraft username", "Nick Minecraft")); addDrawableChild(playerInput);
        addDrawableChild(new CosmicButton(x + 198, y + 140, 82, 20, Text.literal(tr("+ Player", "+ Gracz")), CosmicButton.Style.EDITOR, false, this::addPlayer));
        addDrawableChild(new CosmicButton(x + 288, y + 140, 105, 20, Text.literal(tr("Remove player", "Usuń gracza")), CosmicButton.Style.EDITOR_DANGER, false, this::removePlayer));
        addDrawableChild(new CosmicButton(x + 401, y + 140, 105, 20, Text.literal(tr("Remove guild", "Usuń gildię")), CosmicButton.Style.EDITOR_DANGER, false, this::removeGuild));
        addDrawableChild(new CosmicButton(x, y + 178, 110, 20, Text.literal(tr("Paste image", "Wklej obraz")), CosmicButton.Style.EDITOR, false, this::pasteMark));
        addDrawableChild(new CosmicButton(x + 116, y + 178, 100, 20, Text.literal(tr("Remove mark", "Usuń znak")), CosmicButton.Style.EDITOR_DANGER, false, this::removeMark));
        chestToggleButton = new CosmicButton(x + 222, y + 178, 140, 20, Text.literal(tr("Chest —", "Klatka —")), CosmicButton.Style.EDITOR, false, this::toggleChest);
        helmetToggleButton = new CosmicButton(x + 368, y + 178, 138, 20, Text.literal(tr("Head —", "Głowa —")), CosmicButton.Style.EDITOR, false, this::toggleHelmet);
        capeToggleButton = new CosmicButton(x, y + 202, 160, 20, Text.literal(tr("Cape —", "Peleryna —")), CosmicButton.Style.EDITOR, false, this::toggleCape);
        shieldToggleButton = new CosmicButton(x + 173, y + 202, 160, 20, Text.literal(tr("Shield —", "Tarcza —")), CosmicButton.Style.EDITOR, false, this::toggleShield);
        elytraToggleButton = new CosmicButton(x + 346, y + 202, 160, 20, Text.literal("Elytra —"), CosmicButton.Style.EDITOR, false, this::toggleElytra);
        addDrawableChild(chestToggleButton); addDrawableChild(helmetToggleButton); addDrawableChild(capeToggleButton); addDrawableChild(shieldToggleButton); addDrawableChild(elytraToggleButton);
        markSourceInput = styledField(x, y + 240, 370, 20, tr("Image path or URL", "Ścieżka lub Link do grafiki"), tr("C:\\images\\mark.webp or https://domain.com/mark.webp", "C:\\obrazy\\znak.webp lub https://domena.pl/znak.webp"));
        addDrawableChild(markSourceInput);
        addDrawableChild(new CosmicButton(x + 378, y + 240, 128, 20, Text.literal(tr("Load", "Wczytaj")), CosmicButton.Style.EDITOR, false, this::loadMarkSource));
    }

    private void initAutoImport(int x, int y) {
        if (DedicatedServerMode.isActive()) {
            apiInput = styledField(x, y + 44, 390, 22, tr("Cape server address", "Adres serwera peleryn"), "https://domain.com");
            addDrawableChild(apiInput);
            addDrawableChild(new CosmicButton(x + 400, y + 44, 120, 22, Text.literal(tr("SAVE & REFRESH", "ZAPISZ I ODŚW.")), CosmicButton.Style.EDITOR, false, this::connectDedicatedApi));
            boolean recommendedActive = recommendedApiActive();
            addDrawableChild(new CosmicButton(x + 400, y + 105, 120, 22,
                Text.literal(recommendedActive ? tr("DEACTIVATE", "DEZAKTYWUJ") : tr("ACTIVATE", "AKTYWUJ")),
                recommendedActive ? CosmicButton.Style.EDITOR_DANGER : CosmicButton.Style.EDITOR, false, this::toggleRecommendedApi));
            if (!apiDiscoveryStarted) {
                apiDiscoveryStarted = true;
                status = tr("Checking the recommended API address…", "Sprawdzanie zalecanego adresu API…");
                DedicatedApiClient.probe(DedicatedApiClient.MCEXTREME_DEFAULT_API, address -> {
                    discoveredApiUrl = address;
                    recommendedApiAvailable = true;
                    status = tr("Available API address found!", "Znaleziono dostępny adres dla Twojego API!");
                    init();
                }, error -> {
                    recommendedApiAvailable = false;
                    status = tr("Recommended API address is currently unavailable", "Zalecany adres API jest obecnie niedostępny");
                    init();
                });
            }
            return;
        }
        urlInput = styledField(x, y + 44, 390, 22, tr("JSON address", "Adres JSON"), tr("https://your-domain.com/guilds.json", "https://twoja-domena.pl/guilds.json")); addDrawableChild(urlInput);
        addDrawableChild(new CosmicButton(x + 400, y + 44, 120, 22, Text.literal(tr("IMPORT JSON", "IMPORTUJ JSON")), CosmicButton.Style.EDITOR, false, this::autoImport));
        autoUpdateButton = new CosmicButton(x, y + 78, 166, 22, Text.literal(""), CosmicButton.Style.EDITOR, false, this::toggleAutoUpdate);
        addDrawableChild(autoUpdateButton);
        updateAutoUpdateButton();
        apiInput = styledField(x, y + 126, 390, 22, tr("Dedicated API address", "Adres dedykowanego API"), "https://domain.com/api");
        addDrawableChild(apiInput);
        addDrawableChild(new CosmicButton(x + 400, y + 126, 120, 22, Text.literal(tr("CONNECT API", "POŁĄCZ API")), CosmicButton.Style.EDITOR, false, this::connectDedicatedApi));
    }

    private void initAuthor(int x, int y) {
        addDrawableChild(new CosmicButton(x + 390, y + 32, 130, 20, Text.literal(tr("COPY DISCORD", "KOPIUJ DISCORD")), CosmicButton.Style.EDITOR, false, this::copyAuthorDiscord));
        addDrawableChild(new CosmicButton(x, y + 74, 250, 24, Text.literal("BUY ME A COFFEE"), CosmicButton.Style.EDITOR, false,
            () -> openAuthorLink("https://buymeacoffee.com/slogerski", "Buy Me a Coffee")));
        addDrawableChild(new CosmicButton(x + 260, y + 74, 260, 24, Text.literal(tr("MODRINTH PROFILE", "PROFIL MODRINTH")), CosmicButton.Style.EDITOR, false,
            () -> openAuthorLink("https://modrinth.com/user/Slogerski", "Modrinth")));
    }

    private TextFieldWidget styledField(int x, int y, int w, int h, String title, String placeholder) {
        TextFieldWidget field = new CosmicTextField(textRenderer, x + 5, y, w - 10, h, Text.literal(title));
        field.setPlaceholder(Text.literal(placeholder)); field.setDrawsBackground(false);
        field.setEditableColor(0xFFF4E9FF); field.setUneditableColor(0xFF82768D);
        field.setTextShadow(true); field.setMaxLength(placeholder.contains("https://") ? 512 : 64);
        return field;
    }

    private void drawFieldBackground(DrawContext c, TextFieldWidget field) {
        if (field != null && field.visible) {
            if (page == Page.EDITOR || page == Page.AUTO_IMPORT || page == Page.SETTINGS) CosmicUi.editorTextField(c, field.getX() - 5, field.getY(), field.getWidth() + 10, field.getHeight(), field.isFocused());
            else CosmicUi.textField(c, field.getX() - 5, field.getY(), field.getWidth() + 10, field.getHeight(), field.isFocused());
        }
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderCosmos(c);
        float uiScale = responsiveUiScale();
        int layoutWidth = layoutWidth(), layoutHeight = layoutHeight();
        int layoutMouseX = (int)Math.floor(mouseX / uiScale), layoutMouseY = (int)Math.floor(mouseY / uiScale);
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(uiScale, uiScale);
        int left = Math.max(12, (layoutWidth - UI_WIDTH) / 2), top = Math.max(12, (layoutHeight - UI_HEIGHT) / 2), right = Math.min(layoutWidth - 12, left + UI_WIDTH), bottom = Math.min(layoutHeight - 12, top + UI_HEIGHT);
        panel(c, left, top, right, bottom, 0xEB0B0912, 0xFF915CFF);
        panel(c, left + 10, top + 10, left + 174, bottom - 10, 0xD912101E, 0xFF4C327A);
        Text author = Text.literal("by Slogerski");
        c.drawText(textRenderer, author, right - textRenderer.getWidth(author) - 12, top + 13, 0x99000000, false);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("GUILD"), left + 92, top + 20, 0xFFE9E1FF);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("MARK"), left + 92, top + 33, 0xFFB17AFF);
        boolean dedicated = DedicatedServerMode.isActive();
        if (!dedicated && client != null && client.player != null) {
            int mx = left + 92, my = top + 174;
            InventoryScreen.drawEntity(c, mx - 55, top + 51, mx + 55, my, 62, 0.0625F, layoutMouseX, layoutMouseY, client.player);
        } else if (dedicated) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("DEDICATED"), left + 92, top + 61, 0xFFB77AFF);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("mcextreme.pl"), left + 92, top + 76, 0xFF8F7CA6);
            CosmicUi.roundedGradient(c, left + 38, top + 91, 108, 2, 1, 0x008F5EC8, 0xFF8F5EC8);
        }
        drawFieldBackground(c, guildInput); drawFieldBackground(c, playerInput); drawFieldBackground(c, markSourceInput); drawFieldBackground(c, urlInput); drawFieldBackground(c, apiInput);
        c.drawTextWithShadow(textRenderer, Text.literal(titleForPage()), left + 190, top + 24, 0xFFF0E9FF);
        String apiNotice = dedicated ? DedicatedApiClient.restrictionNotice() : "";
        String visibleStatus = apiNotice.isBlank() ? status : apiNotice;
        c.drawTextWithShadow(textRenderer, Text.literal(visibleStatus), left + 190, bottom - 24, isErrorStatus(visibleStatus) ? 0xFFFF6B8A : 0xFFA98AD8);
        if (page == Page.PROFILE) renderProfile(c, left + 190, top + 58, layoutMouseX, layoutMouseY);
        if (page == Page.RENDER) renderGlobalRender(c, left + 190, top + 76);
        if (page == Page.MAP) renderMap(c, left + 190, top + 58, right - 14, bottom - 40, layoutMouseX, layoutMouseY);
        if (page == Page.EDITOR) renderEditor(c, left + 190, top + 76, right - 14);
        if (page == Page.SETTINGS) renderSettings(c, left + 190, top + 148);
        if (page == Page.AUTO_IMPORT) renderAutoImport(c, left + 190, top + 76, layoutMouseX, layoutMouseY);
        if (page == Page.AUTHOR) renderAuthor(c, left + 190, top + 76);
        updatePlacementButtonLabels();
        super.render(c, layoutMouseX, layoutMouseY, delta);
        c.getMatrices().popMatrix();
        if (page == Page.PROFILE && client != null && client.player != null) {
            int profileX = left + 190, profileY = top + 58;
            renderProfilePlayer(c,
                Math.round((profileX + 18) * uiScale), Math.round((profileY + 34) * uiScale),
                Math.round((profileX + 240) * uiScale), Math.round((profileY + 270) * uiScale), uiScale);
        }
    }

    private void renderProfile(DrawContext c, int x, int y, int mouseX, int mouseY) {
        CosmicUi.editorPanel(c, x, y, 258, 304);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("YOUR PROFILE", "TWÓJ PROFIL")), x + 129, y + 12, 0xFFCBB4EA);
        GuildData.Guild selected = profilePreviewGuild();
        String selectedName = selected == null ? tr("No cape selected", "Nie wybrano peleryny") : selected.name;
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(shorten(selectedName, 30)), x + 129, y + 278, selected == null ? 0xFF8D7E9B : 0xFFE5D7FA);

        int pickerX = x + 270, pickerY = y + 62;
        CosmicUi.editorPanel(c, pickerX, pickerY, 250, 242);
        if (!capePickerOpen) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("SELECTED COSMETIC", "WYBRANY KOSMETYK")), pickerX + 125, pickerY + 14, 0xFF9F82C8);
            if (selected == null) {
                c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("Open the cape list to choose", "Otwórz listę peleryn, aby wybrać")), pickerX + 125, pickerY + 112, 0xFF8D7E9B);
            } else {
                renderMarkPreview(c, selected, pickerX + 66, pickerY + 38, 118, 150);
                c.drawCenteredTextWithShadow(textRenderer, Text.literal(shorten(selected.name, 28)), pickerX + 125, pickerY + 204, 0xFFE6D9F8);
            }
            return;
        }

        List<GuildData.Guild> choices = profileGuildChoices();
        c.drawTextWithShadow(textRenderer, Text.literal(tr("AVAILABLE CAPES", "DOSTĘPNE PELERYNY")), pickerX + 10, pickerY + 9, 0xFFB69AD6);
        if (choices.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("No downloaded graphics", "Brak pobranych grafik")), pickerX + 125, pickerY + 112, 0xFF8D7E9B);
            return;
        }
        int maxScroll = Math.max(0, (choices.size() + 2) / 3 - 2);
        profileGridScroll = clamp(profileGridScroll, 0, maxScroll);
        int start = profileGridScroll * 3;
        for (int slot = 0; slot < 6 && start + slot < choices.size(); slot++) {
            GuildData.Guild guild = choices.get(start + slot);
            int col = slot % 3, row = slot / 3;
            int cardX = pickerX + 8 + col * 80, cardY = pickerY + 27 + row * 100;
            boolean active = selected != null && selected.name.equalsIgnoreCase(guild.name);
            CosmicUi.roundedRect(c, cardX, cardY, 74, 92, 7, active ? 0xFF9A65D0 : 0xFF3E2B50);
            CosmicUi.roundedGradient(c, cardX + 1, cardY + 1, 72, 90, 6, active ? 0xFF2D1C3C : 0xFF191220, 0xFF0C0911);
            renderMarkPreview(c, guild, cardX + 7, cardY + 7, 60, 60);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(shorten(guild.name, 10)), cardX + 37, cardY + 76, active ? 0xFFFFFFFF : 0xFFC6B5D8);
        }
        if (maxScroll > 0) c.drawCenteredTextWithShadow(textRenderer, Text.literal((profileGridScroll + 1) + " / " + (maxScroll + 1)), pickerX + 125, pickerY + 225, 0xFF887899);
    }

    private void renderProfilePlayer(DrawContext c, int x1, int y1, int x2, int y2, float uiScale) {
        EntityRenderState state = client.getEntityRenderDispatcher().getRenderer(client.player).getAndUpdateRenderState(client.player, 1.0F);
        state.displayName = null;
        state.nameLabelPos = null;
        state.hitbox = null;
        if (state instanceof PlayerEntityRenderState playerState) playerState.playerName = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyYaw = 180.0F + modelYaw;
            living.relativeHeadYaw = 0.0F;
            living.pitch = 0.0F;
        }
        float entityScale = client.player.getScale();
        Quaternionf cameraRotation = new Quaternionf().rotateX((float)Math.toRadians(modelPitch));
        Quaternionf modelRotation = new Quaternionf().rotateZ((float)Math.PI).mul(cameraRotation);
        Vector3f translation = new Vector3f(0.0F, client.player.getHeight() / 2.0F + 0.0625F * entityScale, 0.0F);
        c.enableScissor(x1, y1, x2, y2);
        c.addEntity(state, 88.0F * uiScale / entityScale, translation, modelRotation, cameraRotation, x1, y1, x2, y2);
        c.disableScissor();
    }

    private void renderGlobalRender(DrawContext c, int x, int y) {
        CosmicUi.editorPanel(c, x - 10, y - 12, 530, 246);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("GLOBAL COSMETIC VISIBILITY", "GLOBALNA WIDOCZNOŚĆ KOSMETYKÓW")), x, y, 0xFFDCCCF7);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("Choose which layers GuildMark renders on every player.", "Wybierz warstwy wyświetlane przez GuildMark u wszystkich graczy.")), x, y + 16, 0xFF998BA9);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("Green means enabled. Red means disabled.", "Zielony oznacza włączone. Czerwony oznacza wyłączone.")), x, y + 174, 0xFF8F829B);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("These switches do not change your selected guild cosmetic.", "Te przełączniki nie zmieniają wybranego kosmetyku gildii.")), x, y + 191, 0xFF8F829B);
    }

    private void renderCosmos(DrawContext c) {
        c.fillGradient(0, 0, width, height, 0xFF08060D, 0xFF171024);
        for (int i = 0; i < 46; i++) { int x = Math.floorMod(i * 97 + 31, Math.max(1, width)); int y = Math.floorMod(i * 53 + 19, Math.max(1, height)); int a = 55 + (i % 4) * 35; c.fill(x, y, x + 1, y + 1, (a << 24) | 0xC8A8FF); }
    }

    private void renderMap(DrawContext c, int x, int y, int right, int bottom, int mouseX, int mouseY) {
        List<GuildData.Guild> guilds = GuildMarkClient.STORE.data().guilds;
        if (guilds.isEmpty()) { c.drawTextWithShadow(textRenderer, Text.literal(tr("No guilds — create the first one in Editor.", "Brak gildii — utwórz pierwszą w Edytorze.")), x + 12, y + 20, 0xFF9E91AE); return; }
        int yy = y - scroll;
        for (GuildData.Guild g : guilds) {
            int h = 38 + ((g.players.size() + 4) / 5) * 66;
            if (yy + h > y && yy < bottom) {
                CosmicUi.editorPanel(c, x, yy, right - x, h - 8);
                c.drawTextWithShadow(textRenderer, Text.literal("◆ " + shorten(g.name, 22) + "  ·  " + g.players.size()), x + 12, yy + 12, 0xFFE8DCFF);
                int relationStart = right - 153;
                drawRelationChoice(c, relationStart, yy + 7, 45, "MY", "own", g, mouseX, mouseY);
                drawRelationChoice(c, relationStart + 49, yy + 7, 45, "ALLY", "ally", g, mouseX, mouseY);
                drawRelationChoice(c, relationStart + 98, yy + 7, 45, "OTHER", "foreign", g, mouseX, mouseY);
                for (int i = 0; i < g.players.size(); i++) {
                    int px = x + 18 + (i % 5) * 92, py = yy + 40 + (i / 5) * 66;
                    c.drawCenteredTextWithShadow(textRenderer, Text.literal(shorten(g.players.get(i), 12)), px + 25, py + 45, 0xFFC9B8E8);
                    drawPlayerModel(c, g.players.get(i), px, py, mouseX, mouseY);
                }
            }
            yy += h;
        }
    }

    private void renderEditor(DrawContext c, int x, int y, int right) {
        List<GuildData.Guild> gs = GuildMarkClient.STORE.data().guilds;
        c.drawTextWithShadow(textRenderer, Text.literal(tr("NEW GUILD / SEARCH", "NOWA GILDIA / SZUKAJ")), x, y - 14, 0xFF9F82C8);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("GUILDS · SELECT NONE = ALL", "GILDIE · NIC NIE WYBRANO = WSZYSTKIE")), x, y + 26, 0xFF9F82C8);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("PLAYER", "GRACZ")), x, y + 130, 0xFF9F82C8);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("GUILD MARK", "ZNAK GILDII")), x, y + 166, 0xFFB8A4CC);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("IMAGE PATH OR URL (PNG / JPG / WEBP)", "ŚCIEŻKA LUB LINK DO GRAFIKI (PNG / JPG / WEBP)")), x, y + 226, 0xFFB8A4CC);
        int gx = x;
        int guildListY = y + 38;
        List<Integer> filtered = filteredGuildIndices();
        editorGuildScroll = clamp(editorGuildScroll, 0, Math.max(0, filtered.size() - 4));
        int visibleGuilds = Math.min(4, Math.max(0, filtered.size() - editorGuildScroll));
        for (int i = 0; i < visibleGuilds; i++) {
            int guildIndex = filtered.get(editorGuildScroll + i);
            GuildData.Guild g = gs.get(guildIndex); boolean selected = guildIndex == selectedGuild;
            CosmicUi.shadow(c, gx, guildListY + i * 23, 170, 19, 6, 0x9B5CFF, selected ? .7f : .24f);
            CosmicUi.roundedGradient(c, gx, guildListY + i * 23, 170, 19, 6,
                selected ? 0xDD9B5CDA : 0xAA362348, selected ? 0xDD4B226F : 0xAA1B1328);
            c.drawTextWithShadow(textRenderer, Text.literal(g.name), gx + 7, guildListY + i * 23 + 5, 0xFFFFFFFF);
        }
        if (filtered.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal(tr("No matching guilds", "Brak pasujących gildii")), gx + 7, guildListY + 6, 0xFF88799B);
        renderEditorGuildScrollbar(c, x + 174, guildListY, filtered.size());
        if (selectedGuild < 0 && !gs.isEmpty()) c.drawTextWithShadow(textRenderer,
            Text.literal(tr("MIXED = some guilds ON, some OFF", "RÓŻNE = część gildii ON, część OFF")), x + 190, y + 116, 0xFFF0C75E);
        if (selectedGuild >= 0 && selectedGuild < gs.size()) {
            GuildData.Guild g = gs.get(selectedGuild); c.drawTextWithShadow(textRenderer, Text.literal(tr("Players in ", "Gracze w ") + g.name + ":"), x + 190, y + 30, 0xFFCFB8F5);
            for (int i = 0; i < g.players.size() && i < 5; i++) c.drawTextWithShadow(textRenderer, Text.literal("• " + g.players.get(i)), x + 190, y + 46 + i * 14, 0xFFE8E0F2);
            renderMarkPreview(c, g, x + 424, y, 82, 82);
            String mark = g.markFile == null || g.markFile.isBlank() ? tr("No image set", "Nie ustawiono grafiki") : g.markFile;
            c.drawTextWithShadow(textRenderer, Text.literal(tr("Mark: ", "Znak: ") + shorten(mark, 22)), x + 382, y + 88, 0xFFCFB7ED);
            String layers = placementLabel(g);
            c.drawTextWithShadow(textRenderer, Text.literal(tr("Visibility: ", "Widoczność: ") + shorten(layers, 20)), x + 382, y + 100, 0xFF9D8BAE);
        }
    }

    private void drawRelationChoice(DrawContext c, int x, int y, int w, String label, String relation, GuildData.Guild guild, int mouseX, int mouseY) {
        boolean active = relation.equals(guild.relation);
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        int top;
        int bottom;
        if (active && "own".equals(relation)) { top = 0xFF42B86B; bottom = 0xFF1D6137; }
        else if (active && "ally".equals(relation)) { top = 0xFFD2AA38; bottom = 0xFF765A18; }
        else if (active) { top = 0xFF786B83; bottom = 0xFF403747; }
        else if (hovered) { top = 0xFF7143A0; bottom = 0xFF39234E; }
        else { top = 0xBB30203F; bottom = 0xBB171020; }
        CosmicUi.roundedRect(c, x, y, w, 18, 6, top);
        CosmicUi.roundedRect(c, x + 1, y + 1, w - 2, 16, 5, bottom);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + 5, active ? 0xFFFFFFFF : 0xFFBAA9CB);
    }

    private void renderSettings(DrawContext c, int x, int y) {
        boolean dedicated = DedicatedServerMode.isActive();
        int panelWidth = Math.max(300, Math.min(520, layoutWidth() - x - 24));
        CosmicUi.editorPanel(c, x - 10, y - 80, panelWidth, 300);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("LANGUAGE", "JĘZYK")), x + 250, y - 70, 0xFF9F82C8);
        c.drawTextWithShadow(textRenderer, Text.literal(dedicated ? tr("Dedicated profile settings for mcextreme.pl", "Ustawienia profilu dedykowanego dla mcextreme.pl") : tr("File: config/guildmark/guilds.json", "Plik: config/guildmark/guilds.json")), x, y, 0xFFC6B2E8);
        c.drawTextWithShadow(textRenderer, Text.literal(dedicated ? tr("Head markers are disabled on this server.", "Kostki na głowie są wyłączone na tym serwerze.") : tr("The export is readable JSON and preserves guild colors.", "Format eksportu jest czytelnym JSON-em i zachowuje kolory gildii.")), x, y + 20, 0xFF9889AA);
        if (!dedicated) {
            c.drawTextWithShadow(textRenderer, Text.literal(tr("HEAD MARKER COLORS", "KOLORY KOSTKI NA GŁOWIE")), x, y + 46, 0xFFB8A4CC);
            c.drawTextWithShadow(textRenderer, Text.literal("MY · " + GuildMarkClient.SETTINGS.ownHeadHue() + "°"), x, y + 60, GuildMarkClient.SETTINGS.ownHeadColor());
            c.drawTextWithShadow(textRenderer, Text.literal("ALLY · " + GuildMarkClient.SETTINGS.allyHeadHue() + "°"), x + 250, y + 60, GuildMarkClient.SETTINGS.allyHeadColor());
            renderHueSlider(c, x, y + 73, 230, GuildMarkClient.SETTINGS.ownHeadHue());
            renderHueSlider(c, x + 250, y + 73, 230, GuildMarkClient.SETTINGS.allyHeadHue());
        }
        int settingsShift = dedicated ? -59 : 0;
        int divisor = GuildMarkClient.SETTINGS.bannerResolutionDivisor();
        c.drawTextWithShadow(textRenderer, Text.literal(tr("BANNER RESOLUTION", "ROZDZIELCZOŚĆ BANNERÓW") + " · " + resolutionPercent(divisor) + " (÷" + divisor + ")"), x, y + 105 + settingsShift, 0xFFB8A4CC);
        renderResolutionSlider(c, x, y + 121 + settingsShift, 480, divisor);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("Lower resolution uses less GPU memory. Source files stay unchanged.", "Niższa rozdzielczość zużywa mniej pamięci GPU. Pliki źródłowe bez zmian.")), x, y + 153 + settingsShift, 0xFF8F829B);
        int renderDistance = GuildMarkClient.SETTINGS.cosmeticRenderDistance();
        int playerLimit = GuildMarkClient.SETTINGS.maxRenderedPlayers();
        c.drawTextWithShadow(textRenderer, Text.literal(tr("RENDER DISTANCE", "ZASIĘG RENDEROWANIA") + " · " + renderDistanceLabel(renderDistance)), x, y + 178 + settingsShift, 0xFFB8A4CC);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("NEAREST PLAYERS", "NAJBLIŻSI GRACZE") + " · " + playerLimitLabel(playerLimit)), x + 250, y + 178 + settingsShift, 0xFFB8A4CC);
        renderDistanceSlider(c, x, y + 194 + settingsShift, 230, renderDistance);
        renderPlayerLimitSlider(c, x + 250, y + 194 + settingsShift, 230, playerLimit);
    }

    private void renderHueSlider(DrawContext c, int x, int y, int w, int hue) {
        CosmicUi.shadow(c, x, y, w, 14, 6, GuildMarkSettings.previewHueColor(hue), .5F);
        CosmicUi.roundedRect(c, x, y, w, 14, 6, 0xFF4E3A61);
        for (int px = 2; px < w - 2; px++) {
            int sampleHue = Math.round((px - 2) * 359.0F / Math.max(1, w - 5));
            c.fill(x + px, y + 2, x + px + 1, y + 12, GuildMarkSettings.previewHueColor(sampleHue));
        }
        int knobX = x + 2 + Math.round(hue * (w - 5) / 359.0F);
        CosmicUi.roundedRect(c, knobX - 2, y, 5, 14, 2, 0xFFFFFFFF);
        CosmicUi.roundedRect(c, knobX - 1, y + 2, 3, 10, 1, GuildMarkSettings.previewHueColor(hue));
    }

    private void renderResolutionSlider(DrawContext c, int x, int y, int w, int divisor) {
        int index = divisorIndex(divisor);
        CosmicUi.roundedRect(c, x, y, w, 14, 6, 0xFF4E3A61);
        CosmicUi.roundedRect(c, x + 2, y + 2, w - 4, 10, 5, 0xFF17121E);
        int innerStart = x + 5, innerWidth = w - 10;
        int knobX = innerStart + Math.round(index * innerWidth / 6.0F);
        if (knobX > innerStart) CosmicUi.roundedGradient(c, innerStart, y + 4, knobX - innerStart, 6, 3, 0xFF9D68D4, 0xFF65408E);
        for (int i = 0; i < 7; i++) {
            int tickX = innerStart + Math.round(i * innerWidth / 6.0F);
            c.fill(tickX, y + 3, tickX + 1, y + 11, 0xFF8E789F);
            String label = "÷" + (1 << i);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(label), tickX, y + 18, i == index ? 0xFFE9DCFF : 0xFF80738B);
        }
        CosmicUi.roundedRect(c, knobX - 3, y, 7, 14, 3, 0xFFFFFFFF);
        CosmicUi.roundedRect(c, knobX - 1, y + 2, 3, 10, 1, 0xFF9B63D0);
    }

    private void renderDistanceSlider(DrawContext c, int x, int y, int w, int blocks) {
        int index = renderDistanceIndex(blocks), steps = RENDER_DISTANCES.length - 1;
        CosmicUi.roundedRect(c, x, y, w, 14, 6, 0xFF4E3A61);
        CosmicUi.roundedRect(c, x + 2, y + 2, w - 4, 10, 5, 0xFF17121E);
        int innerStart = x + 5, innerWidth = w - 10;
        int knobX = innerStart + Math.round(index * innerWidth / (float)steps);
        if (knobX > innerStart) CosmicUi.roundedGradient(c, innerStart, y + 4, knobX - innerStart, 6, 3, 0xFF70B8E8, 0xFF7053B5);
        for (int i = 0; i <= steps; i++) {
            int tickX = innerStart + Math.round(i * innerWidth / (float)steps);
            c.fill(tickX, y + 3, tickX + 1, y + 11, 0xFF8E789F);
            String label = switch (i) { case 0 -> "10"; case 5 -> "64"; case 7 -> "128"; case 9 -> "256"; case 10 -> "∞"; default -> ""; };
            if (!label.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, Text.literal(label), tickX, y + 18, i == index ? 0xFFE9DCFF : 0xFF80738B);
        }
        CosmicUi.roundedRect(c, knobX - 3, y, 7, 14, 3, 0xFFFFFFFF);
        CosmicUi.roundedRect(c, knobX - 1, y + 2, 3, 10, 1, 0xFF6AAFE0);
    }

    private void renderPlayerLimitSlider(DrawContext c, int x, int y, int w, int players) {
        int index = playerLimitIndex(players), steps = PLAYER_LIMITS.length - 1;
        CosmicUi.roundedRect(c, x, y, w, 14, 6, 0xFF4E3A61);
        CosmicUi.roundedRect(c, x + 2, y + 2, w - 4, 10, 5, 0xFF17121E);
        int innerStart = x + 5, innerWidth = w - 10;
        int knobX = innerStart + Math.round(index * innerWidth / (float)steps);
        if (knobX > innerStart) CosmicUi.roundedGradient(c, innerStart, y + 4, knobX - innerStart, 6, 3, 0xFFB274E2, 0xFF7046A0);
        for (int i = 0; i <= steps; i++) {
            int tickX = innerStart + Math.round(i * innerWidth / (float)steps);
            c.fill(tickX, y + 3, tickX + 1, y + 11, 0xFF8E789F);
            String label = i == steps ? "∞" : Integer.toString(PLAYER_LIMITS[i]);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(label), tickX, y + 18, i == index ? 0xFFE9DCFF : 0xFF80738B);
        }
        CosmicUi.roundedRect(c, knobX - 3, y, 7, 14, 3, 0xFFFFFFFF);
        CosmicUi.roundedRect(c, knobX - 1, y + 2, 3, 10, 1, 0xFFA96DDA);
    }

    private List<Integer> filteredGuildIndices() {
        String query = guildInput == null ? "" : guildInput.getText().strip().toLowerCase(Locale.ROOT);
        List<Integer> result = new ArrayList<>();
        List<GuildData.Guild> guilds = GuildMarkClient.STORE.data().guilds;
        for (int i = 0; i < guilds.size(); i++) {
            if (query.isEmpty() || guilds.get(i).name.toLowerCase(Locale.ROOT).contains(query)) result.add(i);
        }
        return result;
    }

    private void renderEditorGuildScrollbar(DrawContext c, int x, int y, int total) {
        if (total <= 4) return;
        int height = 88, max = total - 4;
        int thumbHeight = Math.max(16, height * 4 / total);
        int thumbY = y + (int)Math.round((height - thumbHeight) * (editorGuildScroll / (double)max));
        CosmicUi.roundedRect(c, x, y, 3, height, 2, 0x663A294D);
        CosmicUi.roundedGradient(c, x - 1, thumbY, 5, thumbHeight, 3, 0xFFB77AF2, 0xFF68409A);
    }

    private void renderMarkPreview(DrawContext c, GuildData.Guild guild, int x, int y, int w, int h) {
        CosmicUi.shadow(c, x, y, w, h, 10, 0xA65CFF, .65f);
        CosmicUi.roundedGradient(c, x, y, w, h, 10, 0xFF3B2452, 0xFF110C19);
        GuildMarkTextures.Pair texture = GuildMarkTextures.get(guild.markFile);
        if (texture == null) {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("PREVIEW", "PODGLĄD")), x + w / 2, y + 39, 0xFF927DAA);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(tr("NO MARK", "BRAK ZNAKU")), x + w / 2, y + 57, 0xFF675973);
            return;
        }
        int margin = 8, availableW = w - margin * 2, availableH = h - margin * 2;
        float scale = Math.min(availableW / (float)Math.max(1, texture.width()), availableH / (float)Math.max(1, texture.height()));
        int drawW = Math.max(1, Math.round(texture.width() * scale)), drawH = Math.max(1, Math.round(texture.height() * scale));
        int drawX = x + (w - drawW) / 2, drawY = y + (h - drawH) / 2;
        c.drawTexturedQuad(texture.original(), drawX, drawY, drawX + drawW, drawY + drawH, 0f, 1f, 0f, 1f);
    }

    private void renderAutoImport(DrawContext c, int x, int y, int mouseX, int mouseY) {
        if (DedicatedServerMode.isActive()) {
            int panelWidth = Math.max(300, Math.min(540, layoutWidth() - x - 24));
            CosmicUi.editorPanel(c, x - 10, y - 12, panelWidth, 194);
            c.drawTextWithShadow(textRenderer, Text.literal(tr("DEDICATED CAPE SERVER", "DEDYKOWANY SERWER PELERYN")), x, y, 0xFFDCCCF7);
            String activeApi = GuildMarkClient.SETTINGS.dedicatedApiUrl();
            String shownApi = activeApi.isBlank() ? tr("None", "Brak") : shorten(activeApi, 62);
            c.drawTextWithShadow(textRenderer, Text.literal(tr("ACTIVE API: ", "AKTYWNE API: ") + shownApi), x, y + 78, activeApi.isBlank() ? 0xFF9A8AA8 : 0xFF71E99A);
            CosmicUi.editorPanel(c, x, y + 94, 520, 42);
            c.drawTextWithShadow(textRenderer, Text.literal("GuildMarks · mcextreme.pl"), x + 10, y + 101, 0xFFE2D5F5);
            c.drawTextWithShadow(textRenderer, Text.literal(shorten(discoveredApiUrl, 50)), x + 10, y + 116, 0xFF9786A8);
            String availability = recommendedApiAvailable == null ? tr("CHECKING", "SPRAWDZANIE")
                : recommendedApiAvailable ? tr("AVAILABLE", "DOSTĘPNE") : tr("UNAVAILABLE", "NIEDOSTĘPNE");
            int availabilityColor = recommendedApiAvailable == null ? 0xFFF0C75E : recommendedApiAvailable ? 0xFF71E99A : 0xFFFF7188;
            c.drawTextWithShadow(textRenderer, Text.literal(availability), x + 315, y + 109, availabilityColor);
            c.drawTextWithShadow(textRenderer, Text.literal(tr("Saving immediately refreshes capes and player assignments.", "Zapisanie od razu odświeża peleryny i przypisania graczy.")), x, y + 151, 0xFF8F829B);
            String displayedApi = activeApi.isBlank() ? discoveredApiUrl : activeApi;
            boolean plainHttp = displayedApi.toLowerCase(Locale.ROOT).startsWith("http://");
            c.drawTextWithShadow(textRenderer, Text.literal(plainHttp
                ? tr("HTTP is temporary and unencrypted; use HTTPS when available.", "HTTP jest tymczasowe i nieszyfrowane; przejdź na HTTPS, gdy będzie dostępne.")
                : tr("HTTPS encrypts communication with the cape server.", "HTTPS szyfruje komunikację z serwerem peleryn.")), x, y + 166, plainHttp ? 0xFFFFA06B : 0xFF78DDA2);
            return;
        }
        int panelWidth = Math.max(300, Math.min(540, layoutWidth() - x - 24));
        CosmicUi.editorPanel(c, x - 10, y - 12, panelWidth, 196);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("Import guilds and their marks from a JSON URL", "Importuj gildie i ich znaki z adresu JSON")), x, y, 0xFFDCCCF7);
        if (autoUpdateButton != null && autoUpdateButton.isHovered()) {
            c.drawTextWithShadow(textRenderer, Text.literal(tr("Checks at Minecraft startup and downloads updates", "Sprawdza przy starcie Minecrafta i pobiera aktualizacje")), x + 176, y + 76, 0xFFDCCCF7);
            c.drawTextWithShadow(textRenderer, Text.literal(tr("at most once every 24 hours.", "najwyżej raz na 24 godziny.")), x + 176, y + 89, 0xFFAA96BB);
        } else {
            c.drawTextWithShadow(textRenderer, Text.literal(tr("HTTPS only. Images are downloaded and stored locally.", "Tylko HTTPS. Grafiki są pobierane i zapisywane lokalnie.")), x + 176, y + 83, 0xFF998BA9);
        }
        c.drawTextWithShadow(textRenderer, Text.literal(tr("DEDICATED SERVER API", "API SERWERA DEDYKOWANEGO")), x, y + 110, 0xFFB69AD6);
        c.drawTextWithShadow(textRenderer, Text.literal(tr("Downloads player assignments every 5 minutes; proof is sent once per session.", "Pobiera przypisania co 5 minut; proof wysyła raz na sesję.")), x, y + 158, 0xFF8F829B);
    }

    private void renderAuthor(DrawContext c, int x, int y) {
        c.drawTextWithShadow(textRenderer, Text.literal(tr("CREATED BY", "AUTOR")), x, y, 0xFF9F82C8);
        c.drawTextWithShadow(textRenderer, Text.literal("Slogerski"), x, y + 17, 0xFFE9DFFF);
        c.drawTextWithShadow(textRenderer, Text.literal("Discord: slogers"), x, y + 40, 0xFFC7B6DD);
        c.fill(x, y + 109, x + 520, y + 110, 0x344C3861);
        renderAuthorStarfield(c, x, y + 118, 520, 154);
    }

    private void renderAuthorStarfield(DrawContext c, int x, int y, int w, int h) {
        CosmicUi.shadow(c, x, y, w, h, 9, 0x6844A0, .38F);
        CosmicUi.roundedGradient(c, x, y, w, h, 9, 0xFF17121F, 0xFF08070C);
        CosmicUi.roundedRect(c, x + 1, y + 1, w - 2, h - 2, 8, 0x10101010);
        long now = System.currentTimeMillis();
        int usableWidth = w - 12, usableHeight = h - 12;
        for (int i = 0; i < 42; i++) {
            long seed = 0x9E3779B97F4A7C15L * (i + 11L);
            int starX = x + 6 + Math.floorMod((int)(seed ^ (seed >>> 32)), usableWidth);
            double speed = 0.006D + ((seed >>> 9) & 15L) * 0.00075D;
            double start = Math.floorMod((int)(seed >>> 21), usableHeight);
            int starY = y + 6 + (int)((start + now * speed) % usableHeight);
            double phase = ((seed >>> 37) & 1023L) * 0.017D;
            double pulse = (Math.sin(now * (0.0014D + (i % 5) * 0.00017D) + phase) + 1.0D) * 0.5D;
            int alpha = 55 + (int)(pulse * 120.0D);
            int rgb = switch (i % 4) { case 0 -> 0xEADFFF; case 1 -> 0xB996F2; case 2 -> 0xD6C5FF; default -> 0xFFFFFF; };
            int color = (alpha << 24) | rgb;
            c.fill(starX, Math.max(y + 3, starY - 3), starX + 1, starY, ((Math.max(22, alpha / 3)) << 24) | rgb);
            c.fill(starX, starY, starX + (i % 7 == 0 ? 2 : 1), starY + (i % 7 == 0 ? 2 : 1), color);
            if (pulse > 0.965D && i % 3 == 0 && starX > x + 3 && starX < x + w - 4 && starY > y + 3 && starY < y + h - 4) {
                int glow = (210 << 24) | rgb;
                c.fill(starX - 2, starY, starX + 3, starY + 1, glow);
                c.fill(starX, starY - 2, starX + 1, starY + 3, glow);
            }
        }
    }

    private void addGuild() {
        String name = guildInput.getText().trim(); if (name.isEmpty()) { status = tr("Error: enter a guild name", "Błąd: wpisz nazwę gildii"); return; }
        if (GuildMarkClient.STORE.data().guilds.stream().anyMatch(g -> g.name.equalsIgnoreCase(name))) { status = tr("Error: this guild already exists", "Błąd: taka gildia już istnieje"); return; }
        GuildMarkClient.STORE.data().guilds.add(new GuildData.Guild(name)); selectedGuild = GuildMarkClient.STORE.data().guilds.size() - 1; GuildMarkClient.STORE.save(); guildInput.setText(""); status = tr("Added guild ", "Dodano gildię ") + name;
    }
    private void addPlayer() {
        if (!validSelection()) return; String nick = playerInput.getText().trim();
        if (!nick.matches("[A-Za-z0-9_]{3,16}")) { status = tr("Error: username must be 3–16 characters", "Błąd: nick musi mieć 3–16 znaków"); return; }
        for (GuildData.Guild g : GuildMarkClient.STORE.data().guilds) if (g.players.stream().anyMatch(n -> n.equalsIgnoreCase(nick))) { status = tr("Error: player already belongs to guild ", "Błąd: gracz już należy do gildii ") + g.name; return; }
        GuildMarkClient.STORE.data().guilds.get(selectedGuild).players.add(nick); GuildMarkClient.STORE.save(); playerInput.setText(""); status = tr("Added player ", "Dodano gracza ") + nick;
    }
    private void removePlayer() { if (!validSelection()) return; String nick = playerInput.getText().trim(); boolean ok = GuildMarkClient.STORE.data().guilds.get(selectedGuild).players.removeIf(n -> n.equalsIgnoreCase(nick)); if (ok) GuildMarkClient.STORE.save(); status = ok ? tr("Removed ", "Usunięto ") + nick : tr("Error: enter a player from this guild", "Błąd: wpisz nick gracza z tej gildii"); }
    private void removeGuild() {
        if (!validSelection()) return; GuildData.Guild removed = GuildMarkClient.STORE.data().guilds.remove(selectedGuild);
        try { if (removed.markFile != null && !removed.markFile.isBlank()) Files.deleteIfExists(resolveMarkFile(removed.markFile)); }
        catch (Exception ignored) { }
        GuildMarkTextures.invalidate(removed.markFile); selectedGuild = -1; GuildMarkClient.STORE.save(); status = tr("Removed guild ", "Usunięto gildię ") + removed.name;
    }
    private void pasteMark() {
        if (!validSelection()) return;
        try {
            String text = client == null ? "" : client.keyboard.getClipboard().strip();
            if (!text.isBlank() && (text.regionMatches(true, 0, "https://", 0, 8) || isExistingFilePath(text))) {
                markSourceInput.setText(text);
                loadMarkSource();
                return;
            }

            String filePath = WindowsClipboardImage.readFilePath();
            if (filePath != null && !filePath.isBlank()) {
                markSourceInput.setText(filePath);
                loadMarkSource();
                return;
            }

            BufferedImage buffered = WindowsClipboardImage.readImage();
            if (buffered == null) throw new IllegalArgumentException(tr("clipboard has no image, file, path, or link", "schowek nie zawiera obrazu, pliku, ścieżki ani linku"));
            saveMarkImage(GuildMarkClient.STORE.data().guilds.get(selectedGuild), buffered, "");
            status = tr("Pasted mark: ", "Wklejono znak: ") + buffered.getWidth() + "×" + buffered.getHeight();
        } catch (Exception e) { status = tr("Clipboard error: ", "Błąd schowka: ") + shorten(rootMessage(e), 58); }
    }

    private void loadMarkSource() {
        if (!validSelection()) return;
        String source = markSourceInput == null ? "" : markSourceInput.getText().strip();
        if (source.regionMatches(true, 0, "https://", 0, 8)) downloadMark(source);
        else loadMarkFromPath(source);
    }

    private void loadMarkFromPath(String raw) {
        try {
            Path source = pathFromInput(raw);
            BufferedImage image = readImage(source);
            saveMarkImage(GuildMarkClient.STORE.data().guilds.get(selectedGuild), image, "");
            markSourceInput.setText(source.toString());
            status = tr("Loaded ", "Wczytano ") + source.getFileName() + " (" + image.getWidth() + "×" + image.getHeight() + ")";
        } catch (Exception e) { status = tr("File error: ", "Błąd pliku: ") + shorten(rootMessage(e), 62); }
    }

    private void downloadMark(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException(tr("an HTTPS link is required", "wymagany jest link HTTPS"));
            GuildData.Guild targetGuild = GuildMarkClient.STORE.data().guilds.get(selectedGuild); status = tr("Downloading image…", "Pobieranie grafiki…");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Accept", "image/webp,image/png,image/jpeg,image/*").header("User-Agent", "GuildMark/1.1.1 Minecraft/1.21.8").GET().build();
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> client.execute(() -> {
                    try {
                        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
                        if (response.body().length > 5_000_000) throw new IllegalStateException(tr("image exceeds 5 MB", "grafika przekracza 5 MB"));
                        BufferedImage image = readImage(response.body());
                        if (!GuildMarkClient.STORE.data().guilds.contains(targetGuild)) throw new IllegalStateException(tr("guild was removed", "gildia została usunięta"));
                        saveMarkImage(targetGuild, image, raw);
                        status = tr("Downloaded mark (", "Pobrano znak (") + image.getWidth() + "×" + image.getHeight() + ")";
                    } catch (Exception e) { status = tr("Image error: ", "Błąd grafiki: ") + shorten(rootMessage(e), 58); }
                })).exceptionally(e -> { client.execute(() -> status = tr("Download error: ", "Błąd pobierania: ") + shorten(rootMessage(e), 54)); return null; });
        } catch (Exception e) { status = tr("Image error: ", "Błąd grafiki: ") + shorten(rootMessage(e), 58); }
    }
    private void saveMarkImage(GuildData.Guild guild, BufferedImage image, String sourceUrl) throws Exception {
        validateImageSize(image);
        String file = markFileName(guild.name);
        Path path = resolveMarkFile(file);
        Files.createDirectories(path.getParent()); ImageIO.write(image, "PNG", path.toFile());
        guild.markFile = file; guild.markPath = "GuildMark/Guilds/" + file; guild.markUrl = sourceUrl == null ? "" : sourceUrl;
        guild.showOnChest = true; guild.showOnCape = true; GuildMarkClient.STORE.save(); GuildMarkTextures.invalidate(file);
    }
    private void toggleChest() {
        togglePlacement(tr("Chest", "Klatka"), g -> g.showOnChest, (g, value) -> g.showOnChest = value);
    }
    private void toggleHelmet() {
        togglePlacement(tr("Head", "Głowa"), g -> g.showOnHelmet, (g, value) -> g.showOnHelmet = value);
    }
    private void toggleCape() {
        togglePlacement(tr("Cape", "Peleryna"), g -> g.showOnCape, (g, value) -> g.showOnCape = value);
    }
    private void toggleShield() {
        togglePlacement(tr("Shield", "Tarcza"), g -> g.showOnShield, (g, value) -> g.showOnShield = value);
    }
    private void toggleElytra() {
        togglePlacement("Elytra", g -> g.showOnElytra, (g, value) -> g.showOnElytra = value);
    }
    private void updatePlacementButtonLabels() {
        if (chestToggleButton == null || helmetToggleButton == null || capeToggleButton == null || shieldToggleButton == null || elytraToggleButton == null) return;
        GuildData.Guild g = selectedGuild >= 0 && selectedGuild < GuildMarkClient.STORE.data().guilds.size() ? GuildMarkClient.STORE.data().guilds.get(selectedGuild) : null;
        updatePlacementButton(chestToggleButton, tr("Chest", "Klatka"), stateLabel(g, item -> item.showOnChest));
        updatePlacementButton(helmetToggleButton, tr("Head", "Głowa"), stateLabel(g, item -> item.showOnHelmet));
        updatePlacementButton(capeToggleButton, tr("Cape", "Peleryna"), stateLabel(g, item -> item.showOnCape));
        updatePlacementButton(shieldToggleButton, tr("Shield", "Tarcza"), stateLabel(g, item -> item.showOnShield));
        updatePlacementButton(elytraToggleButton, "Elytra", stateLabel(g, item -> item.showOnElytra));
    }
    private void updatePlacementButton(CosmicButton button, String label, String state) {
        button.setMessage(Text.literal(label + " " + state));
        button.setTextColor(switch (state) {
            case "ON" -> 0xFF69E58B;
            case "OFF" -> 0xFFFF7188;
            case "—" -> 0xFFB4A8BF;
            default -> 0xFFF0C75E;
        });
    }
    private void togglePlacement(String label, Predicate<GuildData.Guild> getter, BiConsumer<GuildData.Guild, Boolean> setter) {
        List<GuildData.Guild> guilds = GuildMarkClient.STORE.data().guilds;
        if (guilds.isEmpty()) { status = tr("Error: add a guild first", "Błąd: najpierw dodaj gildię"); return; }
        boolean single = selectedGuild >= 0 && selectedGuild < guilds.size();
        List<GuildData.Guild> targets = single ? List.of(guilds.get(selectedGuild)) : guilds;
        boolean enable = targets.stream().anyMatch(g -> !getter.test(g));
        targets.forEach(g -> setter.accept(g, enable)); GuildMarkClient.STORE.save();
        status = label + ": " + (enable ? "ON" : "OFF") + (single ? tr(" for ", " dla ") + targets.get(0).name : tr(" for all guilds", " dla wszystkich gildii"));
    }
    private String stateLabel(GuildData.Guild selected, Predicate<GuildData.Guild> getter) {
        if (selected != null) return getter.test(selected) ? "ON" : "OFF";
        List<GuildData.Guild> guilds = GuildMarkClient.STORE.data().guilds;
        if (guilds.isEmpty()) return "—";
        long enabled = guilds.stream().filter(getter).count();
        if (enabled == 0) return "OFF";
        if (enabled == guilds.size()) return "ON";
        return tr("MIXED", "RÓŻNE");
    }
    private void removeMark() {
        if (!validSelection()) return; GuildData.Guild g = GuildMarkClient.STORE.data().guilds.get(selectedGuild);
        try { if (g.markFile != null && !g.markFile.isBlank()) Files.deleteIfExists(resolveMarkFile(g.markFile)); }
        catch (Exception e) { status = tr("Remove error: ", "Błąd usuwania: ") + shorten(rootMessage(e), 54); return; }
        GuildMarkTextures.invalidate(g.markFile); g.markFile = ""; g.markPath = ""; g.markUrl = ""; GuildMarkClient.STORE.save(); status = tr("Removed guild mark", "Usunięto znak gildii");
    }
    private boolean validSelection() { if (selectedGuild < 0 || selectedGuild >= GuildMarkClient.STORE.data().guilds.size()) { status = tr("Error: select a guild from the list", "Błąd: wybierz gildię z listy"); return false; } return true; }

    private void exportClipboard() { MinecraftClient.getInstance().keyboard.setClipboard(GuildMarkClient.STORE.json()); status = tr("Copied JSON to clipboard", "Skopiowano JSON do schowka"); }
    private void importClipboard() { try { GuildMarkClient.STORE.importJson(MinecraftClient.getInstance().keyboard.getClipboard()); status = tr("Imported JSON from clipboard", "Zaimportowano JSON ze schowka"); } catch (Exception e) { status = tr("Import error: ", "Błąd importu: ") + shorten(e.getMessage(), 55); } }
    private void autoImport() {
        String raw = urlInput.getText().trim();
        try {
            String url = GuildAutoImporter.normalizeUrl(raw);
            GuildMarkClient.SETTINGS.setAutoImportUrl(url);
            urlInput.setText(url);
            int requestId = ++autoImportRequest;
            status = tr("Auto Import: downloading JSON…", "Auto Import: pobieranie JSON…");
            GuildAutoImporter.start(url, summary -> {
                if (requestId != autoImportRequest) return;
                GuildMarkClient.SETTINGS.recordAutoUpdateAttempt(System.currentTimeMillis());
                selectedGuild = -1;
                status = tr("Auto Import complete: ", "Auto Import zakończony: ") + summary.guilds()
                    + tr(" guilds, ", " gildii, ") + summary.images() + tr(" images", " grafik");
            }, error -> {
                if (requestId == autoImportRequest)
                    status = tr("Auto Import error: ", "Błąd Auto Import: ") + shorten(rootMessage(error), 52);
            });
        } catch (Exception e) { status = tr("Error: ", "Błąd: ") + e.getMessage(); }
    }

    private void toggleAutoUpdate() {
        boolean enabled = !GuildMarkClient.SETTINGS.autoUpdateEnabled();
        GuildMarkClient.SETTINGS.setAutoUpdateEnabled(enabled);
        updateAutoUpdateButton();
        status = enabled ? tr("Automatic daily updates enabled", "Włączono codzienne automatyczne aktualizacje")
            : tr("Automatic updates disabled", "Wyłączono automatyczne aktualizacje");
    }

    private void updateAutoUpdateButton() {
        if (autoUpdateButton == null) return;
        boolean enabled = GuildMarkClient.SETTINGS.autoUpdateEnabled();
        autoUpdateButton.setMessage(Text.literal(tr("AUTO UPDATE: ", "AUTO AKTUALIZACJA: ") + (enabled ? tr("ON", "WŁ.") : tr("OFF", "WYŁ."))));
        autoUpdateButton.setTextColor(enabled ? 0xFF6CF09A : 0xFFFF6579);
    }

    private void connectDedicatedApi() {
        try {
            String normalized = DedicatedApiClient.normalizeApiUrl(apiInput == null ? "" : apiInput.getText());
            apiInput.setText(normalized);
            status = tr("Connecting to dedicated API…", "Łączenie z dedykowanym API…");
            DedicatedApiClient.configure(normalized,
                message -> status = message,
                error -> status = tr("Dedicated API error: ", "Błąd dedykowanego API: ") + shorten(rootMessage(error), 48));
        } catch (Exception error) {
            status = tr("Dedicated API error: ", "Błąd dedykowanego API: ") + shorten(rootMessage(error), 48);
        }
    }

    private boolean recommendedApiActive() {
        try {
            return DedicatedApiClient.normalizeApiUrl(discoveredApiUrl).equals(GuildMarkClient.SETTINGS.dedicatedApiUrl());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void toggleRecommendedApi() {
        if (recommendedApiActive()) {
            DedicatedApiClient.deactivate();
            status = tr("Dedicated API deactivated", "Dezaktywowano dedykowane API");
            init();
            return;
        }
        if (!Boolean.TRUE.equals(recommendedApiAvailable)) {
            status = tr("This API is currently unavailable", "To API jest obecnie niedostępne");
            return;
        }
        if (apiInput != null) apiInput.setText(discoveredApiUrl);
        connectDedicatedApi();
        init();
    }

    private void updateProfilePickerButton() {
        if (profilePickerButton == null) return;
        String label = capePickerOpen ? tr("CLOSE CAPE LIST", "ZAMKNIJ LISTĘ PELERYN") : tr("CHOOSE CAPE", "WYBIERZ PELERYNĘ");
        profilePickerButton.setMessage(Text.literal(label));
    }

    private void updateGlobalRenderButtons() {
        updateGlobalButton(globalCapeButton, tr("CAPE", "PELERYNA"), GuildMarkClient.SETTINGS.renderCapeEnabled());
        updateGlobalButton(globalChestButton, tr("CHEST", "KLATKA"), GuildMarkClient.SETTINGS.renderChestEnabled());
        updateGlobalButton(globalHelmetButton, tr("HEAD", "GŁOWA"), GuildMarkClient.SETTINGS.renderHelmetEnabled());
        updateGlobalButton(globalShieldButton, tr("SHIELD", "TARCZA"), GuildMarkClient.SETTINGS.renderShieldEnabled());
        updateGlobalButton(globalElytraButton, "ELYTRA", GuildMarkClient.SETTINGS.renderElytraEnabled());
    }

    private static void updateGlobalButton(CosmicButton button, String label, boolean enabled) {
        if (button == null) return;
        button.setMessage(Text.literal(label + "  ·  " + (enabled ? "ON" : "OFF")));
        button.setTextColor(enabled ? 0xFF6CF09A : 0xFFFF6579);
    }

    private List<GuildData.Guild> profileGuildChoices() {
        return DedicatedApiClient.availableCapes().stream().map(DedicatedApiClient.Cape::guild).toList();
    }

    private void selectProfileGuild(GuildData.Guild guild) {
        capePickerOpen = false;
        updateProfilePickerButton();
        String capeId = DedicatedApiClient.capeIdForGuild(guild);
        status = tr("Verifying Minecraft profile…", "Weryfikowanie profilu Minecraft…");
        DedicatedApiClient.selectCape(capeId,
            message -> status = (guild == null ? tr("Profile cosmetic removed", "Usunięto kosmetyk profilu") : tr("Selected cosmetic: ", "Wybrano kosmetyk: ") + guild.name) + " · " + message,
            error -> status = tr("Proof error: ", "Błąd proof: ") + shorten(rootMessage(error), 52));
    }

    public GuildData.Guild profilePreviewGuild() {
        return DedicatedApiClient.guildForCapeId(GuildMarkClient.SETTINGS.dedicatedProfileCapeId());
    }

    public boolean isProfilePreview() { return DedicatedServerMode.isActive() && page == Page.PROFILE; }

    public boolean overridesProfileGuild(String playerName) {
        return isProfilePreview() && client != null && client.player != null && playerName != null
            && client.player.getName().getString().equalsIgnoreCase(playerName);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        mx = layoutMouse(mx); my = layoutMouse(my);
        int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2), top = Math.max(12, (layoutHeight() - UI_HEIGHT) / 2);
        if (page == Page.PROFILE && button == 0) {
            if (capePickerOpen && handleProfileGridClick(mx, my, left, top)) return true;
            if (mx >= left + 208 && mx <= left + 430 && my >= top + 92 && my <= top + 328) { draggingModel = true; return true; }
        }
        if (page == Page.MAP && button == 0 && handleMapRelationClick(mx, my, left, top)) return true;
        if (page == Page.SETTINGS && button == 0) {
            boolean dedicated = DedicatedServerMode.isActive();
            if (!dedicated) {
                int sliderY = top + 221;
                if (mx >= left + 190 && mx <= left + 420 && my >= sliderY && my <= sliderY + 14) {
                    draggingOwnHeadHue = true; updateHeadHue(mx, left + 190, true); return true;
                }
                if (mx >= left + 440 && mx <= left + 670 && my >= sliderY && my <= sliderY + 14) {
                    draggingAllyHeadHue = true; updateHeadHue(mx, left + 440, false); return true;
                }
            }
            int settingsShift = dedicated ? -59 : 0;
            int resolutionY = top + 269 + settingsShift;
            if (mx >= left + 190 && mx <= left + 670 && my >= resolutionY && my <= resolutionY + 14) {
                draggingBannerResolution = true; updateBannerResolution(mx, left + 190); return true;
            }
            int renderDistanceY = top + 342 + settingsShift;
            if (mx >= left + 190 && mx <= left + 420 && my >= renderDistanceY && my <= renderDistanceY + 14) {
                draggingRenderDistance = true; updateRenderDistance(mx, left + 190); return true;
            }
            if (mx >= left + 440 && mx <= left + 670 && my >= renderDistanceY && my <= renderDistanceY + 14) {
                draggingPlayerLimit = true; updatePlayerLimit(mx, left + 440); return true;
            }
        }
        if (page == Page.EDITOR && button == 0 && mx >= left + 361 && mx <= left + 369 && my >= top + 114 && my <= top + 202 && filteredGuildIndices().size() > 4) {
            draggingEditorScrollbar = true; setEditorScrollFromMouse(my, top + 114); return true;
        }
        if (page == Page.EDITOR && mx >= left + 190 && mx <= left + 360 && my >= top + 114 && my < top + 206) {
            int row = (int)(my - top - 114) / 23; List<Integer> filtered = filteredGuildIndices(); int visibleIndex = editorGuildScroll + row;
            if (row < 4 && visibleIndex < filtered.size()) { int guildIndex = filtered.get(visibleIndex); if (selectedGuild == guildIndex) { selectedGuild = -1; status = tr("All guilds mode", "Tryb wszystkich gildii"); } else { selectedGuild = guildIndex; status = tr("Selected ", "Wybrano ") + GuildMarkClient.STORE.data().guilds.get(guildIndex).name; } }
            return true;
        }
        if (!DedicatedServerMode.isActive() && mx >= left + 34 && mx <= left + 150 && my >= top + 50 && my <= top + 180) { draggingModel = true; return true; }
        return super.mouseClicked(mx, my, button);
    }

    private boolean handleProfileGridClick(double mouseX, double mouseY, int left, int top) {
        int pickerX = left + 460, pickerY = top + 120;
        if (mouseX < pickerX + 8 || mouseX >= pickerX + 248 || mouseY < pickerY + 27 || mouseY >= pickerY + 227) return false;
        int col = (int)(mouseX - pickerX - 8) / 80;
        int row = (int)(mouseY - pickerY - 27) / 100;
        if (col < 0 || col > 2 || row < 0 || row > 1) return false;
        int localX = (int)(mouseX - pickerX - 8) % 80, localY = (int)(mouseY - pickerY - 27) % 100;
        if (localX >= 74 || localY >= 92) return false;
        List<GuildData.Guild> choices = profileGuildChoices();
        int index = profileGridScroll * 3 + row * 3 + col;
        if (index >= choices.size()) return false;
        selectProfileGuild(choices.get(index));
        return true;
    }

    private boolean handleMapRelationClick(double mouseX, double mouseY, int left, int top) {
        int x = left + 190, y = top + 58, right = Math.min(layoutWidth() - 12, left + UI_WIDTH) - 14;
        int relationStart = right - 153, yy = y - scroll;
        for (GuildData.Guild guild : GuildMarkClient.STORE.data().guilds) {
            int h = 38 + ((guild.players.size() + 4) / 5) * 66;
            if (mouseY >= yy + 7 && mouseY < yy + 25) {
                if (mouseX >= relationStart && mouseX < relationStart + 45) { setGuildRelation(guild, "own"); return true; }
                if (mouseX >= relationStart + 49 && mouseX < relationStart + 94) { setGuildRelation(guild, "ally"); return true; }
                if (mouseX >= relationStart + 98 && mouseX < relationStart + 143) { setGuildRelation(guild, "foreign"); return true; }
            }
            yy += h;
        }
        return false;
    }

    private void setGuildRelation(GuildData.Guild guild, String relation) {
        if ("own".equals(relation)) {
            for (GuildData.Guild other : GuildMarkClient.STORE.data().guilds) {
                if (other != guild && "own".equals(other.relation)) other.relation = "foreign";
            }
        }
        guild.relation = relation; GuildMarkClient.STORE.save();
        String label = switch (relation) { case "own" -> tr("my guild", "moja"); case "ally" -> tr("ally", "sojusznik"); default -> tr("other", "obca"); };
        status = guild.name + tr(": relation ", ": relacja ") + label;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double scale = responsiveUiScale();
        mx /= scale; my /= scale; dx /= scale; dy /= scale;
        if (draggingBannerResolution && button == 0) {
            int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2);
            updateBannerResolution(mx, left + 190);
            return true;
        }
        if (draggingRenderDistance && button == 0) {
            int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2);
            updateRenderDistance(mx, left + 190);
            return true;
        }
        if (draggingPlayerLimit && button == 0) {
            int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2);
            updatePlayerLimit(mx, left + 440);
            return true;
        }
        if ((draggingOwnHeadHue || draggingAllyHeadHue) && button == 0) {
            int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2);
            updateHeadHue(mx, draggingOwnHeadHue ? left + 190 : left + 440, draggingOwnHeadHue);
            return true;
        }
        if (draggingEditorScrollbar && button == 0) { int top = Math.max(12, (layoutHeight() - UI_HEIGHT) / 2); setEditorScrollFromMouse(my, top + 114); return true; }
        if (draggingModel) {
            modelYaw = (modelYaw + (float)dx * 1.2F) % 360.0F;
            modelPitch = Math.max(-30.0F, Math.min(30.0F, modelPitch - (float)dy * 0.6F));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        mx = layoutMouse(mx); my = layoutMouse(my);
        boolean savedHue = draggingOwnHeadHue || draggingAllyHeadHue;
        boolean savedResolution = draggingBannerResolution;
        boolean savedRenderDistance = draggingRenderDistance;
        boolean savedPlayerLimit = draggingPlayerLimit;
        draggingModel = false; draggingEditorScrollbar = false; draggingOwnHeadHue = false; draggingAllyHeadHue = false; draggingBannerResolution = false; draggingRenderDistance = false; draggingPlayerLimit = false;
        if (savedHue || savedResolution || savedRenderDistance || savedPlayerLimit) {
            GuildMarkClient.SETTINGS.persist();
            if (savedHue) GuildHeadMarker.invalidateTextures();
            if (savedResolution) GuildMarkTextures.invalidateAll();
            status = savedPlayerLimit ? tr("Nearest-player limit saved", "Zapisano limit najbliższych graczy")
                : savedRenderDistance ? tr("Render distance saved", "Zapisano zasięg renderowania")
                : savedResolution ? tr("Banner resolution limit saved", "Zapisano limit rozdzielczości bannerów") : tr("Head marker colors saved", "Zapisano kolory kostki na głowie");
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        mx = layoutMouse(mx); my = layoutMouse(my);
        if (page == Page.PROFILE && capePickerOpen) {
            int rows = (profileGuildChoices().size() + 2) / 3;
            profileGridScroll = clamp(profileGridScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, rows - 2));
            return true;
        }
        if (page == Page.MAP) { scroll = Math.max(0, scroll + (vertical < 0 ? 30 : -30)); return true; }
        if (page == Page.EDITOR) {
            int left = Math.max(12, (layoutWidth() - UI_WIDTH) / 2), top = Math.max(12, (layoutHeight() - UI_HEIGHT) / 2);
            if (mx >= left + 190 && mx <= left + 369 && my >= top + 108 && my <= top + 206) {
                int max = Math.max(0, filteredGuildIndices().size() - 4);
                editorGuildScroll = clamp(editorGuildScroll + (vertical < 0 ? 1 : -1), 0, max); return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private void setEditorScrollFromMouse(double mouseY, int listTop) {
        int total = filteredGuildIndices().size(), max = Math.max(0, total - 4); if (max == 0) { editorGuildScroll = 0; return; }
        int height = 88, thumbHeight = Math.max(16, height * 4 / total), travel = height - thumbHeight;
        double normalized = (mouseY - listTop - thumbHeight / 2.0) / Math.max(1, travel);
        editorGuildScroll = clamp((int)Math.round(normalized * max), 0, max);
    }

    private void updateHeadHue(double mouseX, int sliderX, boolean own) {
        int hue = clamp((int)Math.round((mouseX - sliderX - 2) * 359.0 / 225.0), 0, 359);
        if (own) GuildMarkClient.SETTINGS.setOwnHeadHue(hue);
        else GuildMarkClient.SETTINGS.setAllyHeadHue(hue);
        GuildHeadMarker.invalidateTextures();
    }

    private void updateBannerResolution(double mouseX, int sliderX) {
        int index = clamp((int)Math.round((mouseX - sliderX - 5) * 6.0 / 470.0), 0, 6);
        GuildMarkClient.SETTINGS.setBannerResolutionDivisor(1 << index);
    }
    private void updateRenderDistance(double mouseX, int sliderX) {
        int index = clamp((int)Math.round((mouseX - sliderX - 5) * (RENDER_DISTANCES.length - 1) / 220.0), 0, RENDER_DISTANCES.length - 1);
        GuildMarkClient.SETTINGS.setCosmeticRenderDistance(RENDER_DISTANCES[index]);
        GuildRenderLimiter.invalidate();
    }
    private void updatePlayerLimit(double mouseX, int sliderX) {
        int index = clamp((int)Math.round((mouseX - sliderX - 5) * (PLAYER_LIMITS.length - 1) / 220.0), 0, PLAYER_LIMITS.length - 1);
        GuildMarkClient.SETTINGS.setMaxRenderedPlayers(PLAYER_LIMITS[index]);
        GuildRenderLimiter.invalidate();
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private void drawPlayerModel(DrawContext c, String nick, int x, int y, int mouseX, int mouseY) {
        LivingEntity entity = GuildPlayerPreviewCache.get(nick);
        if (entity == null) return;
        if (entity instanceof GuildPreviewPlayer preview) preview.animateInPlace();
        int x1 = x + 1, y1 = y - 5, x2 = x + 50, y2 = y + 43;
        boolean hovered = mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
        float lookX = hovered ? mouseX : (x1 + x2) / 2.0F;
        float lookY = hovered ? mouseY : (y1 + y2) / 2.0F;
        InventoryScreen.drawEntity(c, x1, y1, x2, y2, 21, 0.0625F, lookX, lookY, entity);
    }
    private void setLanguage(String language) {
        GuildMarkClient.SETTINGS.setLanguage(language);
        status = tr("Language set to English", "Ustawiono język polski");
        init();
    }
    private void copyAuthorDiscord() {
        MinecraftClient.getInstance().keyboard.setClipboard("slogers");
        status = tr("Copied Discord username: slogers", "Skopiowano nazwę Discord: slogers");
    }
    private void openAuthorLink(String url, String label) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
            status = tr("Opened ", "Otwarto ") + label;
        } catch (Exception error) {
            status = tr("Link error: ", "Błąd linku: ") + shorten(rootMessage(error), 54);
        }
    }
    private boolean isErrorStatus(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("error") || lower.contains("błąd");
    }
    private String titleForPage() { return switch(page) { case PROFILE -> tr("PROFILE", "PROFIL"); case RENDER -> "RENDER"; case MAP -> tr("GUILD MAP", "MAPA GILDII"); case SETTINGS -> tr("SETTINGS", "USTAWIENIA"); case EDITOR -> tr("GUILD EDITOR", "EDYTOR GILDII"); case AUTO_IMPORT -> "AUTO IMPORT"; case AUTHOR -> tr("ABOUT THE AUTHOR", "O AUTORZE"); }; }
    private static void panel(DrawContext c, int x1, int y1, int x2, int y2, int fill, int border) {
        int w = x2 - x1, h = y2 - y1, radius = Math.min(12, Math.max(4, Math.min(w, h) / 8));
        CosmicUi.shadow(c, x1, y1, w, h, radius, border, .55f);
        CosmicUi.roundedGradient(c, x1, y1, w, h, radius, border, CosmicUi.lerpColor(border, 0xFF160D20, .68f));
        int innerTop = CosmicUi.lerpColor(fill, 0xFF2D1741, .12f);
        int innerBottom = CosmicUi.lerpColor(fill, 0xFF050309, .28f);
        CosmicUi.roundedGradient(c, x1 + 1, y1 + 1, w - 2, h - 2, Math.max(3, radius - 1), innerTop, innerBottom);
    }
    private static String shorten(String s, int n) { if (s == null) return tr("unknown error", "nieznany błąd"); return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "…"; }
    private static String placementLabel(GuildData.Guild g) {
        java.util.ArrayList<String> enabled = new java.util.ArrayList<>();
        if (g.showOnChest) enabled.add(tr("chest", "klatka"));
        if (g.showOnCape) enabled.add(tr("cape", "peleryna"));
        if (g.showOnShield) enabled.add(tr("shield", "tarcza"));
        if (g.showOnElytra) enabled.add("elytra");
        if (g.showOnHelmet) enabled.add(tr("head", "hełm"));
        return enabled.isEmpty() ? tr("disabled", "wyłączony") : String.join(" + ", enabled);
    }
    private static boolean isExistingFilePath(String value) {
        try { return Files.isRegularFile(pathFromInput(value)); }
        catch (RuntimeException ignored) { return false; }
    }
    private static String fieldText(TextFieldWidget field) { return field == null ? "" : field.getText(); }
    private static int divisorIndex(int divisor) { return switch (divisor) { case 2 -> 1; case 4 -> 2; case 8 -> 3; case 16 -> 4; case 32 -> 5; case 64 -> 6; default -> 0; }; }
    private static String resolutionPercent(int divisor) { return switch (divisor) { case 2 -> "50%"; case 4 -> "25%"; case 8 -> "12.5%"; case 16 -> "6.25%"; case 32 -> "3.13%"; case 64 -> "1.56%"; default -> "100%"; }; }
    private static int renderDistanceIndex(int blocks) {
        for (int i = 0; i < RENDER_DISTANCES.length; i++) if (RENDER_DISTANCES[i] == blocks) return i;
        return 7;
    }
    private static String renderDistanceLabel(int blocks) { return blocks == 0 ? tr("UNLIMITED", "BEZ LIMITU") : blocks + tr(" blocks", " bloków"); }
    private static int playerLimitIndex(int players) {
        for (int i = 0; i < PLAYER_LIMITS.length; i++) if (PLAYER_LIMITS[i] == players) return i;
        return 3;
    }
    private static String playerLimitLabel(int players) { return players == 0 ? tr("UNLIMITED", "BEZ LIMITU") : Integer.toString(players); }
    private static String rootMessage(Throwable error) {
        Throwable current = error; while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    private static String markFileName(String guildName) {
        String safe = guildName.replaceAll("[\\\\/:*?\"<>|]", "_").strip().replaceAll("[. ]+$", "");
        if (safe.isBlank()) safe = "guild";
        return safe + ".png";
    }
    private static Path markRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("GuildMark/Guilds").toAbsolutePath().normalize();
    }
    private static Path resolveMarkFile(String file) {
        if (file == null || file.isBlank()) throw new IllegalArgumentException(tr("empty mark filename", "pusta nazwa pliku znaku"));
        Path root = markRoot();
        Path resolved = root.resolve(file).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) throw new IllegalArgumentException(tr("unsafe mark path", "niebezpieczna ścieżka znaku"));
        return resolved;
    }
    private static void validateImageSize(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() > 4096 || image.getHeight() > 4096 || (long)image.getWidth() * image.getHeight() > 16_000_000L)
            throw new IllegalArgumentException(tr("maximum image size is 4096×4096 px", "dozwolony obraz ma maksymalnie 4096×4096 px"));
    }
    private static Path pathFromInput(String input) {
        String raw = input == null ? "" : input.strip();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
        if (raw.isBlank()) throw new IllegalArgumentException(tr("enter a file path", "wpisz ścieżkę do pliku"));
        return Path.of(raw).toAbsolutePath().normalize();
    }
    private static BufferedImage readImage(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException(tr("file does not exist: ", "plik nie istnieje: ") + path);
        if (Files.size(path) > 5_000_000L) throw new IllegalArgumentException(tr("file exceeds 5 MB", "plik przekracza 5 MB"));
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")))
            throw new IllegalArgumentException(tr("supported formats: PNG, JPG, and WEBP", "obsługiwane formaty: PNG, JPG i WEBP"));
        try (var input = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException(tr("cannot decode image ", "nie można zdekodować obrazu ") + path.getFileName());
            return image;
        }
    }
    private static BufferedImage readImage(byte[] bytes) throws Exception {
        try (var input = new java.io.ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException(tr("response is not a PNG, JPG, or WEBP image", "odpowiedź nie jest obrazem PNG, JPG ani WEBP"));
            return image;
        }
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
