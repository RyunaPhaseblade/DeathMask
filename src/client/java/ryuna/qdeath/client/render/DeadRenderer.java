package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.DeadModelLayers;
import ryuna.qdeath.entity.limbo.BloaterEntity;
import ryuna.qdeath.entity.limbo.LimboDead;

/**
 * The human dead of the Limbo: player-proportioned bodies from {@link ryuna.qdeath.client.model.DeadModelLayers}, one
 * skin per creature ({@code textures/entity/limbo/<id>_<skin>.png}, kept in its save data), and the motion of
 * {@link DeadAnims}. The common dead switch between broad and slim arms with their skin.
 */
public class DeadRenderer<T extends LimboDead> extends MobRenderer<T, HellRenderState, HellModel> {
    private final HellModel broad;
    private final @Nullable HellModel slim;
    private final String id;
    private final float scale;

    public DeadRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, @Nullable ModelLayerLocation slimLayer, HellModel.Anim anim,
                        String id, float scale, float shadow) {
        super(ctx, new HellModel(ctx.bakeLayer(layer), anim, RenderTypes::entityCutout), shadow * scale);
        this.broad = this.model;
        this.slim = slimLayer == null ? null : new HellModel(ctx.bakeLayer(slimLayer), anim, RenderTypes::entityCutout);
        this.id = id;
        this.scale = scale;
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(T entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.walking = state.walkAnimationSpeed > 0.05f;
        state.anim = entity.animId();
        state.animProgress = entity.animProgress(partial);
        state.affix = entity.affix().ordinal();
        state.mind = entity.mind().ordinal();
        state.variant = entity.skin();
        state.slim = slim != null && state.variant < DeadModelLayers.HOLLOW_SLIM.length && DeadModelLayers.HOLLOW_SLIM[state.variant];
        state.seed = (Mth.murmurHash3Mixer(entity.getUUID().hashCode()) & 0xffff) / 65536f;
        state.swell = entity instanceof BloaterEntity b ? b.swelling(partial) : 0f;
        state.corpse = false;
        state.alpha = 1f;
        state.mound = entity.moundRole();
        state.special = entity instanceof ryuna.qdeath.entity.limbo.StranglerEntity s ? s.tongue() : 0;
    }

    @Override
    public void submit(HellRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.slim && slim != null ? slim : broad;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(HellRenderState state) {
        return Quantumdeath.id("textures/entity/limbo/" + id + "_" + state.variant + ".png");
    }

    @Override
    protected void scale(HellRenderState state, PoseStack poseStack) {
        // The dead climb out of graves and breaches: the whole body rises out of the ground.
        if (state.anim == LimboDead.ANIM_EMERGE && state.animProgress >= 0) poseStack.translate(0, (1 - state.animProgress) * 1.8 * scale, 0);
        poseStack.scale(scale, scale, scale);
    }

}
