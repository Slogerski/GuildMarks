package pl.guildmark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;

public final class GuildShieldModel {
    private final ModelPart mark;
    private GuildShieldModel(ModelPart mark) { this.mark = mark; }

    public static GuildShieldModel create() {
        MeshDefinition data = new MeshDefinition();
        data.getRoot().addOrReplaceChild(
            "mark",
            CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, -10.5F, -2.03F, 11.0F, 21.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            PartPose.ZERO
        );
        return new GuildShieldModel(LayerDefinition.create(data, 11, 21).bakeRoot().getChild("mark"));
    }

    public void render(PoseStack matrices, VertexConsumer vertices, int light) {
        mark.render(matrices, vertices, light, OverlayTexture.NO_OVERLAY);
    }

    public void submit(PoseStack matrices, SubmitNodeCollector collector, Identifier texture, int light) {
        collector.submitModelPart(mark, matrices, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, null);
    }
}
