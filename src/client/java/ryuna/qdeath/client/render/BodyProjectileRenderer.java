package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.projectile.BodyProjectile;

/**
 * The thrown pieces of the dead as 3D models: the bolus wobbles as it flies, the rib flies point first and tumbles end
 * over end, so either reads at a glance and from the side.
 */
public class BodyProjectileRenderer<T extends BodyProjectile> extends EntityRenderer<T, HellRenderState> {
    private final HellModel model;
    private final Identifier texture;
    private final boolean tumble;

    public BodyProjectileRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, String texture, boolean tumble) {
        super(ctx);
        this.model = new HellModel(ctx.bakeLayer(layer), (m, s) -> {}, RenderTypes::entityCutout);
        this.texture = Quantumdeath.id("textures/entity/" + texture + ".png");
        this.tumble = tumble;
        this.shadowRadius = 0.15f;
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(T shot, HellRenderState state, float partial) {
        super.extractRenderState(shot, state, partial);
        state.yRot = shot.getYRot(partial);
        state.xRot = shot.getXRot(partial);
    }

    @Override
    public void submit(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0, 0.15, 0);
        pose.rotateDegrees(Axis.YP, 180 + state.yRot);
        pose.rotateDegrees(Axis.XP, state.xRot);
        if (tumble) pose.rotateDegrees(Axis.XP, state.ageInTicks * 38f);
        else {
            float w = 1f + 0.12f * (float) Math.sin(state.ageInTicks * 1.3f);
            pose.scale(w, 2f - w, w);
        }
        pose.scale(-1, -1, 1);
        collector.submitModel(model, state, pose, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
