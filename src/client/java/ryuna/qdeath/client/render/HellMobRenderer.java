package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.entity.hell.HollowEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.hell.HellMonster;
import ryuna.qdeath.entity.hell.HereticEntity;
import ryuna.qdeath.entity.hell.PenitentEntity;
import ryuna.qdeath.entity.hell.WrathbornEntity;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.entity.vigil.VigilWakeEntity;

/** Renders every Hell creature from its generated model layer, animation and texture. */
public class HellMobRenderer<T extends Mob> extends MobRenderer<T, HellRenderState, HellModel> {
    private final Identifier texture;
    private final String textureName;
    private final float scale;
    private final boolean translucent;

    public HellMobRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, HellModel.Anim anim, String texture, float scale, float shadow, boolean translucent) {
        super(ctx, new HellModel(ctx.bakeLayer(layer), anim, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout), shadow * scale);
        this.texture = Quantumdeath.id("textures/entity/" + texture + ".png");
        this.textureName = texture;
        this.scale = scale;
        this.translucent = translucent;
        addLayer(new GlowLayer(this));
    }

    public HellMobRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, HellModel.Anim anim, String texture, float scale, float shadow) {
        this(ctx, layer, anim, texture, scale, shadow, false);
    }

    @Override public HellRenderState createRenderState() { return new HellRenderState(); }

    @Override
    public void extractRenderState(T entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.walking = state.walkAnimationSpeed > 0.05f;
        state.alpha = 1f;
        state.flag = false;
        state.flag2 = false;
        state.finalForm = entity instanceof VigilWakeEntity;
        if (entity instanceof HellMonster hm) {
            state.anim = hm.animId();
            state.animProgress = hm.animProgress(partial);
            state.affix = hm.affix().ordinal();
        }
        if (entity instanceof HellBoss boss) state.phase = boss.phase();
        state.mind = entity instanceof LimboDead d ? d.mind().ordinal() : 0;
        state.special = entity instanceof ryuna.qdeath.entity.limbo.MourningParasiteEntity p ? p.phase()
                : entity instanceof ryuna.qdeath.entity.limbo.BreachTickerEntity t ? t.mode() : 0;
        state.variant = entity instanceof HollowEntity h ? h.variant() : 0;
        if (entity instanceof WrathbornEntity w) state.value = w.rage();
        if (entity instanceof PenitentEntity p) state.flag = p.isWatched();
        if (entity instanceof HereticEntity h) state.flag = h.isInquisitor();
        if (entity instanceof VigilWakeEntity v) {
            state.form = 5;
            state.flag = v.maskBroken();
            state.flag2 = v.veiled();
            state.alpha = v.veiled() ? 0.55f + 0.15f * Mth.sin(state.ageInTicks * 0.2f) : 1f;
        }
        if (entity instanceof VigilEntity v) {
            state.form = v.form();
            state.flag2 = v.mode() == VigilEntity.Mode.PURSUE;
            state.flag = false;
            int age = v.tickCount;
            float in = Math.min(1f, (age + partial) / 40f);
            float out = v.clientFadeStart < 0 ? 1f : Math.max(0f, 1f - (age - v.clientFadeStart + partial) / VigilEntity.FADE_TICKS);
            state.alpha = Math.min(in, out);
        }
    }

    private static boolean isVigil(HellRenderState state) {
        return state.entityType == ryuna.qdeath.registry.HellEntities.VIGIL || state.entityType == ryuna.qdeath.registry.HellEntities.VIGIL_WAKE;
    }

    @Override
    public Identifier getTextureLocation(HellRenderState state) {
        if (isVigil(state)) return Quantumdeath.id("textures/entity/vigil_" + Mth.clamp(state.form, 1, 5) + ".png");
        if (state.variant > 0) return Quantumdeath.id("textures/entity/" + textureName + "_" + state.variant + ".png");
        return texture;
    }

    /** The emissive "_glow" texture (eyes, embers, soul-fire) for this creature, or null if it has none. */
    private @Nullable Identifier glowTexture(HellRenderState state) {
        String name = isVigil(state) ? "vigil_" + Mth.clamp(state.form, 1, 5) : state.variant > 0 ? textureName + "_" + state.variant : textureName;
        return HellModelLayers.GLOWING.contains(name) ? Quantumdeath.id("textures/entity/" + name + "_glow.png") : null;
    }

    /** Draws the glow texture full-bright on top of the model, like spider and enderman eyes; fades with the creature. */
    private static final class GlowLayer extends RenderLayer<HellRenderState, HellModel> {
        private final HellMobRenderer<?> renderer;

        GlowLayer(HellMobRenderer<?> renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HellRenderState state, float yRot, float xRot) {
            Identifier glow = renderer.glowTexture(state);
            if (glow == null || state.isInvisible) return;
            float a = Mth.clamp(state.alpha, 0f, 1f);
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(glow), lightCoords, OverlayTexture.NO_OVERLAY,
                    ARGB.colorFromFloat(a, a, a, a), null, state.outlineColor);
        }
    }

    @Override
    protected void scale(HellRenderState state, PoseStack poseStack) {
        // The dead of the Limbo climb out of graves and breaches: the whole body rises out of the ground.
        if (state.anim == LimboDead.ANIM_EMERGE && state.animProgress >= 0 && state.entityType != null && LimboAnims.LIMBO.contains(state.entityType))
            poseStack.translate(0, (1 - state.animProgress) * 1.6 * scale, 0);
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected int getModelTint(HellRenderState state) {
        if (state.alpha >= 1f) return -1;
        int a = (int) (Mth.clamp(state.alpha, 0f, 1f) * 255);
        return ARGB.color(a, 255, 255, 255);
    }

    @Override
    protected @Nullable RenderType getRenderType(HellRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (translucent && bodyVisible) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getShadowRadius(HellRenderState state) {
        return super.getShadowRadius(state) * state.alpha;
    }
}
