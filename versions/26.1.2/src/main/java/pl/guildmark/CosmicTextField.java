package pl.guildmark;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class CosmicTextField extends EditBox {
    public CosmicTextField(Font textRenderer, int x, int y, int width, int height, Component title) {
        super(textRenderer, x, y, width, height, title);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.pose().pushMatrix();
        context.pose().translate(0.0F, Math.max(0, (getHeight() - 9) / 2.0F));
        super.extractWidgetRenderState(context, mouseX, mouseY, deltaTicks);
        context.pose().popMatrix();
    }
}
