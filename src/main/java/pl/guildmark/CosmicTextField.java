package pl.guildmark;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class CosmicTextField extends TextFieldWidget {
    public CosmicTextField(TextRenderer textRenderer, int x, int y, int width, int height, Text title) {
        super(textRenderer, x, y, width, height, title);
    }

    @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0F, Math.max(0, (getHeight() - 9) / 2.0F));
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
        context.getMatrices().popMatrix();
    }
}
