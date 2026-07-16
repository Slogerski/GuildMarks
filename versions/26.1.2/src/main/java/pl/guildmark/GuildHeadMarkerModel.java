package pl.guildmark;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class GuildHeadMarkerModel extends HumanoidModel<AvatarRenderState> {
    private GuildHeadMarkerModel(ModelPart root) { super(root); }

    public static GuildHeadMarkerModel create() {
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = data.getRoot();
        PartDefinition head = root.clearChild(PartNames.HEAD);
        head.clearChild(PartNames.HAT);
        root.clearChild(PartNames.BODY);
        root.clearChild(PartNames.LEFT_ARM);
        root.clearChild(PartNames.RIGHT_ARM);
        root.clearChild(PartNames.LEFT_LEG);
        root.clearChild(PartNames.RIGHT_LEG);
        head.addOrReplaceChild(
            "guild_marker_cube",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.ZERO
        );
        return new GuildHeadMarkerModel(LayerDefinition.create(data, 1, 1).bakeRoot());
    }
}
