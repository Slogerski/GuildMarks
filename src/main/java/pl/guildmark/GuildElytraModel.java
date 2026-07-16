package pl.guildmark;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;

public final class GuildElytraModel extends EntityModel<BipedEntityRenderState> {
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    private GuildElytraModel(ModelPart root) {
        super(root);
        leftWing = root.getChild(EntityModelPartNames.LEFT_WING);
        rightWing = root.getChild(EntityModelPartNames.RIGHT_WING);
    }

    public static GuildElytraModel create() {
        ModelData data = new ModelData();
        data.getRoot().addChild(
            EntityModelPartNames.LEFT_WING,
            ModelPartBuilder.create().uv(-12, 0).cuboid(-11.0F, -1.0F, 3.04F, 12.0F, 22.0F, 0.0F, EnumSet.of(Direction.SOUTH)),
            ModelTransform.of(5.0F, 0.0F, 0.0F, (float)(Math.PI / 12), 0.0F, (float)(-Math.PI / 12))
        );
        data.getRoot().addChild(
            EntityModelPartNames.RIGHT_WING,
            ModelPartBuilder.create().uv(0, 0).cuboid(-1.0F, -1.0F, 3.04F, 12.0F, 22.0F, 0.0F, EnumSet.of(Direction.SOUTH)),
            ModelTransform.of(-5.0F, 0.0F, 0.0F, (float)(Math.PI / 12), 0.0F, (float)(Math.PI / 12))
        );
        return new GuildElytraModel(TexturedModelData.of(data, 24, 22).createModel());
    }

    @Override public void setAngles(BipedEntityRenderState state) {
        super.setAngles(state);
        leftWing.originY = state.isInSneakingPose ? 3.0F : 0.0F;
        leftWing.pitch = state.leftWingPitch;
        leftWing.roll = state.leftWingRoll;
        leftWing.yaw = state.leftWingYaw;
        rightWing.yaw = -leftWing.yaw;
        rightWing.originY = leftWing.originY;
        rightWing.pitch = leftWing.pitch;
        rightWing.roll = -leftWing.roll;
    }
}
