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

import java.util.EnumSet;

public final class GuildChestModel extends BipedEntityModel<PlayerEntityRenderState> {
    private GuildChestModel(ModelPart root) { super(root); }

    public static GuildChestModel create() {
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
        ModelPartData root = data.getRoot();
        root.addChild(EntityModelPartNames.HEAD).addChild(EntityModelPartNames.HAT);
        ModelPartData body = root.addChild(EntityModelPartNames.BODY);
        root.addChild(EntityModelPartNames.LEFT_ARM);
        root.addChild(EntityModelPartNames.RIGHT_ARM);
        root.addChild(EntityModelPartNames.LEFT_LEG);
        root.addChild(EntityModelPartNames.RIGHT_LEG);
        body.addChild(
            "guildmark_chest",
            ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, 0.0F, -3.08F, 8.0F, 12.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            ModelTransform.NONE
        );
        TexturedModelData model = TexturedModelData.of(data, 8, 12);
        return new GuildChestModel(model.createModel());
    }
}
