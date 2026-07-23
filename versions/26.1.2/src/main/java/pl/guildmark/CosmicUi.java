package pl.guildmark;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class CosmicUi {
    private CosmicUi() {}

    public static void roundedRect(GuiGraphicsExtractor c, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r <= 1) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h > r * 2) c.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(row, h, r);
            c.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            int bottomRow = h - 1 - row;
            c.fill(x + inset, y + bottomRow, x + w - inset, y + bottomRow + 1, color);
        }
    }

    public static void roundedGradient(GuiGraphicsExtractor c, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r <= 1) {
            c.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        float denominator = h - 1.0F;
        if (h > r * 2) {
            c.fillGradient(x, y + r, x + w, y + h - r,
                lerpColor(top, bottom, r / denominator),
                lerpColor(top, bottom, (h - 1 - r) / denominator));
        }
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(row, h, r);
            c.fill(x + inset, y + row, x + w - inset, y + row + 1, lerpColor(top, bottom, row / denominator));
            int bottomRow = h - 1 - row;
            c.fill(x + inset, y + bottomRow, x + w - inset, y + bottomRow + 1, lerpColor(top, bottom, bottomRow / denominator));
        }
    }

    public static void shadow(GuiGraphicsExtractor c, int x, int y, int w, int h, int radius, int rgb, float strength) {
        if (h <= 20) {
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 3, 16);
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 2, 25);
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 1, 38);
        } else {
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 5, 18);
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 3, 28);
            shadowLayer(c, x, y, w, h, radius, rgb, strength, 2, 42);
        }
    }

    private static void shadowLayer(GuiGraphicsExtractor c, int x, int y, int w, int h, int radius, int rgb, float strength, int spread, int alpha) {
        int a = Mth.clamp((int)(alpha * strength), 0, 255);
        roundedRect(c, x - spread, y + 2 - spread, w + spread * 2, h + spread * 2, radius + spread, (a << 24) | (rgb & 0xFFFFFF));
    }

    public static void glowBorder(GuiGraphicsExtractor c, int x, int y, int w, int h, int radius, int color, float glow) {
        int alpha = Mth.clamp((int)(38 + 70 * glow), 0, 255);
        shadow(c, x, y - 1, w, h, radius, color, .45f + glow * .55f);
        roundedRect(c, x, y, w, h, radius, (alpha << 24) | (color & 0xFFFFFF));
        roundedRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), 0xFF120D1D);
    }

    public static void textField(GuiGraphicsExtractor c, int x, int y, int w, int h, boolean focused) {
        float glow = focused ? 1f : .15f;
        shadow(c, x, y, w, h, 7, 0x8C55E8, focused ? .8f : .35f);
        roundedGradient(c, x, y, w, h, 7, focused ? 0xFF5B3390 : 0xFF302044, focused ? 0xFF9C59EC : 0xFF54316F);
        roundedGradient(c, x + 1, y + 1, w - 2, h - 2, 6, 0xF0161122, 0xF00C0912);
        if (focused) roundedRect(c, x + 3, y + h - 2, w - 6, 1, 0, 0xFFD896FF);
    }

    public static void editorTextField(GuiGraphicsExtractor c, int x, int y, int w, int h, boolean focused) {
        int border = focused ? 0xFFB474E8 : 0xFF52366A;
        roundedRect(c, x, y, w, h, 6, border);
        roundedGradient(c, x + 1, y + 1, w - 2, h - 2, 5, focused ? 0xFF21152C : 0xFF17121E, 0xFF0E0B13);
        if (focused) roundedRect(c, x + 5, y + h - 2, w - 10, 1, 0, 0xFFD69AFF);
    }

    public static void editorPanel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        roundedRect(c, x, y, w, h, 8, 0xFF4A3260);
        roundedGradient(c, x + 1, y + 1, w - 2, h - 2, 7, 0xEE18121F, 0xEE0D0A12);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return ((int)(aa + (ba-aa)*t) << 24) | ((int)(ar + (br-ar)*t) << 16) | ((int)(ag + (bg-ag)*t) << 8) | (int)(ab + (bb-ab)*t);
    }

    private static int cornerInset(int row, int height, int radius) {
        if (radius <= 1) return 0;
        int edge = Math.min(row, height - 1 - row);
        if (edge >= radius) return 0;
        double dy = radius - edge - .5;
        return Math.max(0, radius - (int)Math.sqrt(Math.max(0, radius * radius - dy * dy)));
    }
}
