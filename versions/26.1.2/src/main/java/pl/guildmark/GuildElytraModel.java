package pl.guildmark;

import java.util.EnumSet;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Direction;

public final class GuildElytraModel extends EntityModel<HumanoidRenderState> {
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    private GuildElytraModel(ModelPart root) {
        super(root);
        leftWing = root.getChild(PartNames.LEFT_WING);
        rightWing = root.getChild(PartNames.RIGHT_WING);
    }

    public static GuildElytraModel create() {
        MeshDefinition data = new MeshDefinition();
        data.getRoot().addOrReplaceChild(
            PartNames.LEFT_WING,
            CubeListBuilder.create().texOffs(-12, 0).addBox(-11.0F, -1.0F, 3.04F, 12.0F, 22.0F, 0.0F, EnumSet.of(Direction.SOUTH)),
            PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, (float)(Math.PI / 12), 0.0F, (float)(-Math.PI / 12))
        );
        data.getRoot().addOrReplaceChild(
            PartNames.RIGHT_WING,
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, 3.04F, 12.0F, 22.0F, 0.0F, EnumSet.of(Direction.SOUTH)),
            PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, (float)(Math.PI / 12), 0.0F, (float)(Math.PI / 12))
        );
        return new GuildElytraModel(LayerDefinition.create(data, 24, 22).bakeRoot());
    }

    @Override public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state);
        leftWing.y = state.isCrouching ? 3.0F : 0.0F;
        leftWing.xRot = state.elytraRotX;
        leftWing.zRot = state.elytraRotZ;
        leftWing.yRot = state.elytraRotY;
        rightWing.yRot = -leftWing.yRot;
        rightWing.y = leftWing.y;
        rightWing.xRot = leftWing.xRot;
        rightWing.zRot = -leftWing.zRot;
    }
}
