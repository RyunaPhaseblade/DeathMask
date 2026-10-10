package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.DeadModelLayers;
import ryuna.qdeath.entity.limbo.BloaterCorpse;

/**
 * The Putrescent's corpse: the same body lying on its back, the belly swelling and the limbs jerking harder as the fuse
 * burns down; after the burst, a flattened, darkened residue that fades.
 */
public class BloaterCorpseRenderer extends EntityRenderer<BloaterCorpse, HellRenderState> {
    private final HellModel model;

    public BloaterCorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new HellModel(ctx.bakeLayer(DeadModelLayers.BLOATER), DeadAnims.BLOATER_CORPSE, RenderTypes::entityCutout);
        this.shadowRadius = 0.6f;
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(BloaterCorpse entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.yRot = entity.getYRot();
        state.variant = entity.skin();
        state.swell = entity.burst() ? 1f : entity.progress(partial);
        state.corpse = entity.burst();
        state.ageInTicks = entity.tickCount + partial;
    }

    @Override
    public void submit(HellRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180f - state.yRot);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        // Lying on its back: rotate the body about the hips onto the ground.
        poseStack.translate(0, 1.38, 0);
        poseStack.rotateDegrees(Axis.XP, -90f);
        poseStack.translate(0, -0.75, 0);
        if (state.corpse) poseStack.scale(1.25f, 1.0f, 0.3f); // burst open: a flattened heap
        int light = state.corpse ? Math.max(0, state.lightCoords - 0x40) : state.lightCoords;
        collector.submitModel(model, state, poseStack, Quantumdeath.id("textures/entity/limbo/bloater_" + state.variant + ".png"), light,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
