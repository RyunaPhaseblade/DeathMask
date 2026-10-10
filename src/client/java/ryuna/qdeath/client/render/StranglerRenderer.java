package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.DeadModelLayers;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.entity.limbo.StranglerEntity;

/**
 * The Strangler, and its tongue: a chain of fleshy lengths from the dislocated jaw to wherever the tongue is, writhing
 * while it flies, drawn taut and quivering while it drags someone. Both ends come from the synced tongue (the victim's
 * chest while it holds one), so the victim sees exactly what the server pulls with.
 */
public class StranglerRenderer extends DeadRenderer<StranglerEntity> {
    private static final Identifier TONGUE = Quantumdeath.id("textures/entity/strangler_tongue.png");
    private final HellModel tongue;

    public StranglerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, DeadModelLayers.STRANGLER, null, DeadAnims.STRANGLER, "strangler", 1f, 0.45f);
        tongue = new HellModel(ctx.bakeLayer(HellModelLayers.STRANGLER_TONGUE), (m, s) -> {}, RenderTypes::entityCutout);
    }

    @Override
    public void extractRenderState(StranglerEntity entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        Vec3 end = entity.tongueEnd(partial);
        byte t = entity.tongue();
        if (end == null || t == StranglerEntity.NONE || t == StranglerEntity.PREPARE) { state.tongueFrom = state.tongueTo = null; return; }
        Vec3 base = entity.getPosition(partial);
        state.tongueFrom = entity.tongueStart(partial).subtract(base);
        state.tongueTo = end.subtract(base);
    }

    @Override
    public void submit(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        Vec3 a = state.tongueFrom, b = state.tongueTo;
        if (a == null || b == null) return;
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        int n = Mth.clamp((int) (len / 0.45), 3, 32);
        boolean taut = state.special == StranglerEntity.HOLDING;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        if (side.lengthSqr() < 1.0e-6) side = new Vec3(1, 0, 0);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double u = i / (double) n;
            // Writhing in flight, a quivering sag while it pulls; the ends stay pinned to the jaw and the catch.
            double env = Math.sin(u * Math.PI);
            double wave = taut ? 0.05 * Math.sin(state.ageInTicks * 1.7 + u * 9) : 0.18 * Math.sin(state.ageInTicks * 0.9 + u * 7);
            Vec3 p = a.add(d.scale(u)).add(side.scale(wave * env)).add(0, -(taut ? 0.12 : 0.25) * env * Math.min(1, len / 6), 0);
            segment(state, pose, collector, prev, p);
            prev = p;
        }
    }

    private void segment(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0e-3) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(d.x, d.z)));
        pose.rotateDegrees(Axis.XP, (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y / len, -1, 1))));
        pose.scale(1f, 1f, (float) len * 1.08f);
        collector.submitModel(tongue, state, pose, TONGUE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
