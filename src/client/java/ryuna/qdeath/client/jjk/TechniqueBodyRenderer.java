package ryuna.qdeath.client.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ryuna.qdeath.jjk.entity.TechniqueBody;

/** Draws technique bodies as scaled, turning blocks: a basalt-and-magma volcano, a burning meteor, a mass of cursed ice. */
public class TechniqueBodyRenderer extends EntityRenderer<TechniqueBody, TechniqueBodyRenderer.State> {
    public static class State extends EntityRenderState {
        TechniqueBody.Kind kind = TechniqueBody.Kind.VOLCANO;
        float scale = 1, age;
        final BlockModelRenderState a = new BlockModelRenderState(), b = new BlockModelRenderState(), c = new BlockModelRenderState();
    }

    private final BlockModelResolver blocks;

    public TechniqueBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockModelResolver();
        this.shadowRadius = 0.9f;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(TechniqueBody e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.kind = e.kind();
        s.scale = e.scale();
        s.age = e.tickCount + partial;
        BlockState[] states = switch (s.kind) {
            case VOLCANO -> new BlockState[]{Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()};
            case METEOR -> new BlockState[]{Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState()};
            case ICE_MASS -> new BlockState[]{Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(), Blocks.ICE.defaultBlockState()};
        };
        blocks.update(s.a, states[0], TntRenderer.BLOCK_DISPLAY_CONTEXT);
        blocks.update(s.b, states[1], TntRenderer.BLOCK_DISPLAY_CONTEXT);
        blocks.update(s.c, states[2], TntRenderer.BLOCK_DISPLAY_CONTEXT);
    }

    @Override protected AABB getBoundingBoxForCulling(TechniqueBody e, float partial) {
        return super.getBoundingBoxForCulling(e, partial).inflate(e.scale() * 0.8 + 1);
    }

    private static void piece(BlockModelRenderState block, PoseStack pose, SubmitNodeCollector out, int light, int outline,
                              float y, float sx, float sy, float sz, float yaw, float pitch, boolean centered) {
        if (block.isEmpty()) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, pitch);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5f, centered ? -0.5f : 0f, -0.5f);
        block.submit(pose, out, light, OverlayTexture.NO_OVERLAY, outline);
        pose.popPose();
    }

    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        int light = s.lightCoords;
        switch (s.kind) {
            case VOLCANO -> {
                float g = Math.min(1f, s.age / 15f);
                piece(s.a, pose, out, light, s.outlineColor, 0, 2.4f, 0.9f * g, 2.4f, 0, 0, false);
                piece(s.b, pose, out, light, s.outlineColor, 0.9f * g, 1.6f, 0.85f * g, 1.6f, 45, 0, false);
                piece(s.c, pose, out, LightCoordsUtil.FULL_BRIGHT, s.outlineColor, 1.75f * g, 0.9f, 0.4f * g, 0.9f, 0, 0, false);
            }
            case METEOR -> {
                float sc = s.scale;
                piece(s.a, pose, out, LightCoordsUtil.FULL_BRIGHT, s.outlineColor, 0, sc, sc, sc, s.age * 4, s.age * 3, true);
                piece(s.b, pose, out, LightCoordsUtil.FULL_BRIGHT, s.outlineColor, 0, sc * 0.92f, sc * 1.05f, sc * 0.92f, 45 + s.age * 4, 30 + s.age * 3, true);
                piece(s.c, pose, out, LightCoordsUtil.FULL_BRIGHT, s.outlineColor, 0, sc * 0.75f, sc * 0.75f, sc * 0.75f, 20 - s.age * 5, 60, true);
            }
            case ICE_MASS -> {
                float sc = s.scale;
                piece(s.a, pose, out, light, s.outlineColor, 0, sc, sc * 0.8f, sc, s.age * 2, 12, true);
                piece(s.b, pose, out, light, s.outlineColor, 0, sc * 0.7f, sc, sc * 0.7f, 45 + s.age * 2, -10, true);
                piece(s.c, pose, out, light, s.outlineColor, -sc * 0.35f, sc * 0.4f, sc * 0.6f, sc * 0.4f, 30, 0, true);
            }
        }
        super.submit(s, pose, out, camera);
    }
}
