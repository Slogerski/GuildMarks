package pl.guildmark;

import org.joml.Quaternionf;

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

public final class GuildCapeModel extends HumanoidModel<AvatarRenderState> {
    private final ModelPart cape;

    private GuildCapeModel(ModelPart root) {
        super(root);
        this.cape = this.body.getChild("cape");
    }

    public static GuildCapeModel create() {
        MeshDefinition data = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = data.getRoot();
        root.clearChild(PartNames.HEAD);
        root.getChild(PartNames.HEAD).clearChild(PartNames.HAT);
        PartDefinition body = root.clearChild(PartNames.BODY);
        root.clearChild(PartNames.LEFT_ARM);
        root.clearChild(PartNames.RIGHT_ARM);
        root.clearChild(PartNames.LEFT_LEG);
        root.clearChild(PartNames.RIGHT_LEG);
        body.addOrReplaceChild(
            "cape",
            CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.01F, 10.0F, 16.0F, 0.0F, EnumSet.of(Direction.NORTH)),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, (float)Math.PI, 0.0F)
        );
        LayerDefinition model = LayerDefinition.create(data, 10, 16);
        return new GuildCapeModel(model.bakeRoot());
    }

    @Override public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        cape.rotateBy(new Quaternionf()
            .rotateY((float)-Math.PI)
            .rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * (float)(Math.PI / 180.0))
            .rotateZ(state.capeLean2 / 2.0F * (float)(Math.PI / 180.0))
            .rotateY((180.0F - state.capeLean2 / 2.0F) * (float)(Math.PI / 180.0)));
    }
}
