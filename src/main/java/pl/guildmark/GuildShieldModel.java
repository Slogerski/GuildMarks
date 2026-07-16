package pl.guildmark;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;

public final class GuildShieldModel {
    private final ModelPart mark;
    private GuildShieldModel(ModelPart mark) { this.mark = mark; }

    public static GuildShieldModel create() {
        ModelData data = new ModelData();
        data.getRoot().addChild(
            "mark",
            ModelPartBuilder.create().uv(0, 0).cuboid(-5.5F, -10.5F, -2.03F, 11.0F, 21.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            ModelTransform.NONE
        );
        return new GuildShieldModel(TexturedModelData.of(data, 11, 21).createModel().getChild("mark"));
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
        mark.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
    }
}
