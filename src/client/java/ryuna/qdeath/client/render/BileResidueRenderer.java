package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.entity.limbo.BileResidue;

/** The puddle of bile on the ground: full-sized when it lands, shrinking and fading over its three seconds. */
public class BileResidueRenderer extends EntityRenderer<BileResidue, HellRenderState> {
    private static final Identifier TEXTURE = Quantumdeath.id("textures/entity/bile_residue.png");
    private final HellModel model;

    public BileResidueRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        model = new HellModel(ctx.bakeLayer(HellModelLayers.BILE_RESIDUE), (m, s) -> {}, RenderTypes::entityTranslucent);
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(BileResidue entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.yRot = entity.getYRot();
        state.swell = entity.age(partial);
    }

    @Override
    public void submit(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180f - state.yRot);
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        float k = 1f - 0.35f * state.swell;
        pose.scale(k, 1f, k);
        float a = state.swell < 0.7f ? 1f : 1f - (state.swell - 0.7f) / 0.3f;
        collector.submitModel(model, state, pose, RenderTypes.entityTranslucent(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
                ARGB.colorFromFloat(a, 1f, 1f, 1f), null, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
