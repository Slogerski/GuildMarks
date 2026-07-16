package pl.guildmark;

import java.util.EnumSet;
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
import net.minecraft.core.Direction;

public final class GuildChestModel extends HumanoidModel<AvatarRenderState> {
    private GuildChestModel(ModelPart root) { super(root); }

    public static GuildChestModel create() {
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = data.getRoot();
        root.clearChild(PartNames.HEAD).clearChild(PartNames.HAT);
        PartDefinition body = root.clearChild(PartNames.BODY);
        root.clearChild(PartNames.LEFT_ARM);
        root.clearChild(PartNames.RIGHT_ARM);
        root.clearChild(PartNames.LEFT_LEG);
        root.clearChild(PartNames.RIGHT_LEG);
        body.addOrReplaceChild(
            "guildmark_chest",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -3.08F, 8.0F, 12.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            PartPose.ZERO
        );
        LayerDefinition model = LayerDefinition.create(data, 8, 12);
        return new GuildChestModel(model.bakeRoot());
    }
}
