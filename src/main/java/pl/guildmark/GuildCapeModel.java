package pl.guildmark;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;

import java.util.EnumSet;

public final class GuildCapeModel extends BipedEntityModel<PlayerEntityRenderState> {
    private final ModelPart cape;

    private GuildCapeModel(ModelPart root) {
        super(root);
        this.cape = this.body.getChild("cape");
    }

    public static GuildCapeModel create() {
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
        ModelPartData root = data.getRoot();
        root.addChild(EntityModelPartNames.HEAD);
        root.getChild(EntityModelPartNames.HEAD).addChild(EntityModelPartNames.HAT);
        ModelPartData body = root.addChild(EntityModelPartNames.BODY);
        root.addChild(EntityModelPartNames.LEFT_ARM);
        root.addChild(EntityModelPartNames.RIGHT_ARM);
        root.addChild(EntityModelPartNames.LEFT_LEG);
        root.addChild(EntityModelPartNames.RIGHT_LEG);
        body.addChild(
            "cape",
            ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, 0.0F, -1.01F, 10.0F, 16.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            ModelTransform.of(0.0F, 0.0F, 2.0F, 0.0F, (float)Math.PI, 0.0F)
        );
        TexturedModelData model = TexturedModelData.of(data, 10, 16);
        return new GuildCapeModel(model.createModel());
    }

    @Override public void setAngles(PlayerEntityRenderState state) {
        super.setAngles(state);
        cape.rotate(new Quaternionf()
            .rotateY((float)-Math.PI)
            .rotateX((6.0F + state.field_53537 / 2.0F + state.field_53536) * (float)(Math.PI / 180.0))
            .rotateZ(state.field_53538 / 2.0F * (float)(Math.PI / 180.0))
            .rotateY((180.0F - state.field_53538 / 2.0F) * (float)(Math.PI / 180.0)));
    }
}
