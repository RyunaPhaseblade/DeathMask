package ryuna.qdeath.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.SwarmArmProjectile;

/** A compact cuboid using the Swarm Eye texture, shaped like one detached Blaze arm. */
public class SwarmArmRenderer extends EntityRenderer<SwarmArmProjectile, SwarmArmRenderer.ArmRenderState> {
    private static final Identifier TEXTURE = Quantumdeath.id("textures/entity/swarm-eye.png");
    private final Model<EntityRenderState> armModel;

    public SwarmArmRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Exact Blaze-arm geometry and UV section: a single complete 2x8x2 rod.
        CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 16)
                .addBox(-1.0f, -4.0f, -1.0f, 2.0f, 8.0f, 2.0f);
        root.addOrReplaceChild("arm", arm, PartPose.ZERO);
        ModelPart part = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        armModel = new Model<>(part, RenderTypes::entityCutout) {};
    }

    @Override public ArmRenderState createRenderState() { return new ArmRenderState(); }
    @Override public void extractRenderState(SwarmArmProjectile projectile, ArmRenderState state, float partialTick) {
        super.extractRenderState(projectile, state, partialTick);
        state.yRot = projectile.getYRot();
        state.xRot = projectile.getXRot();
    }

    @Override public void submit(ArmRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        poseStack.scale(1.0f, 1.0f, 1.0f);
        collector.submitModel(armModel, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
        poseStack.popPose();
    }

    public static class ArmRenderState extends EntityRenderState {
        float yRot;
        float xRot;
    }
}
