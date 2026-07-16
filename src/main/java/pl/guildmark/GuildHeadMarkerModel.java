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

public final class GuildHeadMarkerModel extends BipedEntityModel<PlayerEntityRenderState> {
    private GuildHeadMarkerModel(ModelPart root) { super(root); }

    public static GuildHeadMarkerModel create() {
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
        ModelPartData root = data.getRoot();
        ModelPartData head = root.addChild(EntityModelPartNames.HEAD);
        head.addChild(EntityModelPartNames.HAT);
        root.addChild(EntityModelPartNames.BODY);
        root.addChild(EntityModelPartNames.LEFT_ARM);
        root.addChild(EntityModelPartNames.RIGHT_ARM);
        root.addChild(EntityModelPartNames.LEFT_LEG);
        root.addChild(EntityModelPartNames.RIGHT_LEG);
        head.addChild(
            "guild_marker_cube",
            ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            ModelTransform.NONE
        );
        return new GuildHeadMarkerModel(TexturedModelData.of(data, 1, 1).createModel());
    }
}
