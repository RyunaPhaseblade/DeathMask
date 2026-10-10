package ryuna.qdeath.client.yokai;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.client.render.HellModel;
import ryuna.qdeath.client.render.HellRenderState;
import ryuna.qdeath.entity.SwarmArmProjectile;

/** The Swarm Eye's shot as a 3D model: one of its small eyes flying pupil first, its torn nerves whipping behind. */
public class SwarmEyeShotRenderer extends EntityRenderer<SwarmArmProjectile, HellRenderState> {
    private static final Identifier TEXTURE = Quantumdeath.id("textures/entity/swarm_eye_shot.png");
    private static final Identifier GLOW = Quantumdeath.id("textures/entity/swarm_eye_shot_glow.png");
    private final HellModel model;

    public SwarmEyeShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        model = new HellModel(ctx.bakeLayer(HellModelLayers.SWARM_EYE_SHOT), YokaiAnims.SWARM_EYE_SHOT, RenderTypes::entityCutout);
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(SwarmArmProjectile shot, HellRenderState state, float partial) {
        super.extractRenderState(shot, state, partial);
        state.yRot = shot.getYRot(partial);
        state.xRot = shot.getXRot(partial);
    }

    @Override
    public void submit(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0, 0.175, 0);
        // Projectile yaw runs opposite to a mob's; the model's front (-Z) ends up along the flight, tilted with it.
        pose.rotateDegrees(Axis.YP, 180 + state.yRot);
        pose.rotateDegrees(Axis.XP, state.xRot);
        pose.scale(-1, -1, 1);
        collector.submitModel(model, state, pose, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        collector.order(1).submitModel(model, state, pose, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
