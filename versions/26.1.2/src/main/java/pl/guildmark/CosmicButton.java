package pl.guildmark;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class CosmicButton extends AbstractButton {
    public enum Style { PRIMARY, NAVIGATION, DANGER, QUIET, EDITOR, EDITOR_DANGER }
    private final Runnable action;
    private final Style style;
    private final boolean selected;
    private float hoverProgress;
    private int textColorOverride = -1;

    public CosmicButton(int x, int y, int width, int height, Component message, Style style, boolean selected, Runnable action) {
        super(x, y, width, height, message);
        this.style = style;
        this.selected = selected;
        this.action = action;
    }

    public static CosmicButton of(int x, int y, int width, int height, String text, Runnable action) {
        return new CosmicButton(x, y, width, height, Component.literal(text), Style.PRIMARY, false, action);
    }

    @Override public void onPress(InputWithModifiers input) { action.run(); }
    public void setTextColor(int color) { textColorOverride = color; }
    @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }

    @Override protected void extractContents(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
        float target = isHovered() || isFocused() ? 1f : 0f;
        hoverProgress = Mth.lerp(Math.min(1f, Math.max(.08f, delta * .22f)), hoverProgress, target);
        if (selected) hoverProgress = Math.max(hoverProgress, .62f);
        if (style == Style.EDITOR || style == Style.EDITOR_DANGER) {
            renderEditorButton(c);
            return;
        }
        int[] colors = palette();
        int top = CosmicUi.lerpColor(colors[0], colors[2], hoverProgress);
        int bottom = CosmicUi.lerpColor(colors[1], colors[3], hoverProgress);
        int radius = Math.min(8, getHeight() / 2);
        CosmicUi.shadow(c, getX(), getY(), getWidth(), getHeight(), radius, colors[4], .48f + hoverProgress * .75f);
        CosmicUi.roundedGradient(c, getX(), getY(), getWidth(), getHeight(), radius, top, bottom);
        CosmicUi.roundedGradient(c, getX() + 1, getY() + 1, getWidth() - 2, Math.max(1, getHeight() / 2), Math.max(2, radius - 1),
            (0x38 << 24) | 0xFFFFFF, 0x00FFFFFF);
        if (selected || hoverProgress > .02f) {
            int a = (int)(90 + 100 * hoverProgress);
            CosmicUi.roundedRect(c, getX(), getY(), getWidth(), getHeight(), radius, (a << 24) | (colors[5] & 0xFFFFFF));
            CosmicUi.roundedRect(c, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, Math.max(2, radius - 1), 0x00101010);
        }
        int textColor = active ? CosmicUi.lerpColor(0xFFD7CBE9, 0xFFFFFFFF, hoverProgress) : 0xFF70677C;
        extractScrollingStringOverContents(c.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE), getMessage().copy().withColor(textColor & 0xFFFFFF), 7);
    }

    private void renderEditorButton(GuiGraphicsExtractor c) {
        boolean danger = style == Style.EDITOR_DANGER;
        int borderBase = danger ? 0xFF70364E : 0xFF51366D;
        int borderHover = danger ? 0xFFE05983 : 0xFFAA72DF;
        int fillTop = danger ? 0xFF2C1721 : 0xFF20182A;
        int fillBottom = danger ? 0xFF1A1016 : 0xFF110E18;
        int border = CosmicUi.lerpColor(borderBase, borderHover, hoverProgress);
        int radius = Math.min(6, getHeight() / 2);
        CosmicUi.roundedRect(c, getX(), getY(), getWidth(), getHeight(), radius, border);
        CosmicUi.roundedGradient(c, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, Math.max(2, radius - 1),
            CosmicUi.lerpColor(fillTop, danger ? 0xFF552338 : 0xFF372346, hoverProgress),
            CosmicUi.lerpColor(fillBottom, danger ? 0xFF321521 : 0xFF20152A, hoverProgress));
        if (hoverProgress > .05f) CosmicUi.roundedRect(c, getX() + 4, getY() + getHeight() - 2, getWidth() - 8, 1, 0, borderHover);
        int textColor = textColorOverride != -1 ? textColorOverride : active ? CosmicUi.lerpColor(0xFFD7CDE0, 0xFFFFFFFF, hoverProgress) : 0xFF736A7A;
        extractScrollingStringOverContents(c.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE), getMessage().copy().withColor(textColor & 0xFFFFFF), 5);
    }

    private int[] palette() {
        return switch (style) {
            case DANGER -> new int[]{0xFF421D35, 0xFF24121F, 0xFFB83C73, 0xFF69214C, 0xFFE43E83, 0xFFFF72AD};
            case QUIET -> new int[]{0xFF21192C, 0xFF100D18, 0xFF4B3865, 0xFF281B3A, 0xFF8F5EC8, 0xFFB879FF};
            case NAVIGATION -> new int[]{0xFF2B1B40, 0xFF151020, 0xFF8450C4, 0xFF43245F, 0xFFA15CF2, 0xFFD49AFF};
            case EDITOR -> new int[]{0xFF20182A, 0xFF110E18, 0xFF372346, 0xFF20152A, 0xFF51366D, 0xFFAA72DF};
            case EDITOR_DANGER -> new int[]{0xFF2C1721, 0xFF1A1016, 0xFF552338, 0xFF321521, 0xFF70364E, 0xFFE05983};
            default -> new int[]{0xFF553078, 0xFF28183E, 0xFFB15FF0, 0xFF6130A0, 0xFFC66DFF, 0xFFE4A8FF};
        };
    }
}
