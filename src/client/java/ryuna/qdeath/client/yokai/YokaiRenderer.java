package ryuna.qdeath.client.yokai;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.client.render.HellModel;
import ryuna.qdeath.client.render.HellMobRenderer;
import ryuna.qdeath.client.render.HellRenderState;
import ryuna.qdeath.entity.BifauceEntity;
import ryuna.qdeath.entity.DamperEntity;
import ryuna.qdeath.entity.FakeCreeperEntity;
import ryuna.qdeath.entity.SwarmEyeEntity;
import ryuna.qdeath.entity.yokai.AbstractYokai;
import ryuna.qdeath.entity.yokai.ChestTsukumogami;
import ryuna.qdeath.entity.yokai.IttanMomenEntity;
import ryuna.qdeath.skills.SkillId;

import java.util.Collections;
import java.util.Set;

/**
 * Draws a yokai according to what the local player perceives of it: nothing at all without perception, only its
 * shadow (plus the dark motes spawned by {@link YokaiClient}) with partial perception, the full model, name and the
 * optional Reading Intent mark with complete perception.
 */
public class YokaiRenderer<T extends Mob> extends HellMobRenderer<T> {
    /** Entities drawn for the book's preview: always fully visible. */
    public static final Set<Entity> PREVIEW = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static class State extends HellRenderState {
        public int view = 2;
        public boolean intent;
        /** Swarm Eye: 0..1 regrowth of the small eye it last shot. */
        public float regrow = 1f;
    }

    public YokaiRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, HellModel.Anim anim, String texture, float scale, float shadow, boolean translucent) {
        super(ctx, layer, anim, texture, scale, shadow, translucent);
    }

    @Override public HellRenderState createRenderState() { return new State(); }

    @Override public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z, float partial) {
        return ClientYokai.view(entity) >= 1 && super.shouldRender(entity, frustum, x, y, z, partial);
    }

    @Override
    public void extractRenderState(T entity, HellRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        State st = (State) state;
        st.view = ClientYokai.view(entity);
        boolean preparing = false;
        if (entity instanceof AbstractYokai y) {
            st.anim = y.animId();
            st.animProgress = y.animProgress(partial);
            preparing = y.preparing();
        }
        if (entity instanceof ChestTsukumogami c) st.flag = c.openClient();
        if (entity instanceof IttanMomenEntity i) st.flag = i.stunnedClient();
        if (entity instanceof FakeCreeperEntity f) {
            st.value = f.fuseProgress(partial);
            preparing = st.value >= 0;
        }
        if (entity instanceof BifauceEntity b) {
            st.anim = b.pose();
            preparing = st.anim == 1;
        }
        if (entity instanceof DamperEntity d) {
            st.anim = d.pose();
            preparing = st.anim == 1;
        }
        if (entity instanceof SwarmEyeEntity e) {
            st.value = e.gaze();
            st.regrow = Mth.clamp((e.tickCount - e.shotTick + partial) / 40f, 0f, 1f);
        }
        st.intent = preparing && st.view >= 2 && ClientYokai.node(SkillId.PERCEPTION_III);
        if (st.view < 2) st.nameTag = null;
        else if (st.intent) {
            // Reading Intent: a discreet red mark over a visible attack preparation.
            st.nameTag = Component.literal("!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            st.nameTagAttachment = new Vec3(0, entity.getBbHeight() + 0.4, 0);
        }
    }

    @Override
    public void submit(HellRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        // Partial perception: no recognisable model, no name - only the shadow on the ground.
        if (((State) state).view < 2) return;
        super.submit(state, pose, collector, camera);
    }

    @Override
    protected float getShadowRadius(HellRenderState state) {
        float r = super.getShadowRadius(state);
        return ((State) state).view == 1 ? r * 1.3f : r;
    }
}
