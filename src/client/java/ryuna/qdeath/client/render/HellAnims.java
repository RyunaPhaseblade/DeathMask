package ryuna.qdeath.client.render;

import net.minecraft.util.Mth;
import ryuna.qdeath.entity.boss.HeresiarchEntity;
import ryuna.qdeath.entity.boss.HellBoss;

import static ryuna.qdeath.client.render.HellModel.bump;

/** Procedural animation for each creature. Pose is reset every frame; these add on top of the rest pose. */
public final class HellAnims {
    private HellAnims() {}

    /** Render scale of the Heresiarch (see HellRenderers); the censer orbit needs it to match world positions. */
    public static final float HERESIARCH_SCALE = 2.3f;

    private static float sin(float v) { return Mth.sin(v); }
    private static float cos(float v) { return Mth.cos(v); }
    private static float a(HellRenderState s, int id) { return s.anim == id ? s.animProgress : -1; }

    /** Lazy whip: every segment of a chain (base, base_1, base_2...) follows a slightly delayed sine. */
    private static void sway(HellModel m, String base, int segments, float t, float speed, float ampX, float ampZ, float phase) {
        for (int i = 0; i < segments; i++) {
            String n = i == 0 ? base : base + "_" + i;
            m.rotX(n, sin(t * speed + phase - i * 0.6f) * ampX);
            m.rotZ(n, cos(t * speed * 0.8f + phase - i * 0.6f) * ampZ);
        }
    }

    public static final HellModel.Anim CINDER_KNIGHT = (m, s) -> {
        m.look("head", s, 1f);
        m.walkPair("right_leg", "left_leg", s, 1.1f);
        m.walkPair("left_arm", "right_arm", s, 0.5f);
        m.rotX("sword", 1.15f);
        m.rotX("cape", 0.05f + sin(s.ageInTicks * 0.08f) * 0.04f + 0.5f * s.walkAnimationSpeed);
        float swing = a(s, 1);
        if (swing >= 0) m.rotX("right_arm", -2.4f + swing * 3.2f);
        float lunge = a(s, 2);
        if (lunge >= 0) {
            m.rotX("right_arm", lunge < 0.5f ? -2.6f * lunge * 2 : -1.4f);
            m.rotX("body", 0.35f * bump(lunge));
        }
    };

    public static final HellModel.Anim GLUTTON = (m, s) -> {
        m.look("head", s, 0.8f);
        m.walkPair("right_leg", "left_leg", s, 0.9f);
        m.walkPair("left_arm", "right_arm", s, 0.7f);
        float t = s.ageInTicks;
        m.moveY("belly", sin(t * 0.1f) * 0.6f);
        m.scale("belly", 1f + sin(t * 0.1f) * 0.025f, 1f, 1f + sin(t * 0.1f) * 0.025f);
        float open = 0.12f + sin(t * 0.08f) * 0.08f + 0.9f * bump(a(s, 1)) + 1.1f * bump(a(s, 2));
        m.rotX("maw_jaw", open);
        m.rotX("maw_top", -open * 0.4f);
        m.rotX("tongue", sin(t * 0.13f) * 0.12f - open * 0.3f);
        m.rotZ("drool", sin(t * 0.09f) * 0.1f);
    };

    public static final HellModel.Anim GLUTTLING = (m, s) -> {
        m.quad(s, 1.3f);
        float t = s.ageInTicks;
        m.rotX("jaw", Math.abs(sin(t * 0.6f)) * 0.5f + bump(a(s, 1)) * 0.6f);
        m.moveY("body", Math.abs(sin(s.walkAnimationPos * 0.6662f)) * -1.2f * s.walkAnimationSpeed);
    };

    public static final HellModel.Anim SULLEN = (m, s) -> {
        m.look("head", s, 0.7f);
        float t = s.ageInTicks;
        m.rotX("right_arm", -0.35f + sin(t * 0.05f) * 0.18f);
        m.rotX("left_arm", -0.35f + sin(t * 0.05f + 1.7f) * 0.18f);
        m.rotZ("tail", sin(t * 0.07f) * 0.12f);
        m.rotX("tail", 0.15f + sin(t * 0.05f) * 0.05f);
        m.rotZ("tail_1", sin(t * 0.07f - 0.8f) * 0.18f);
        m.rotX("tail_1", 0.1f + sin(t * 0.05f - 0.8f) * 0.08f);
        float grab = bump(a(s, 1));
        m.rotX("right_arm", -1.3f * grab);
        m.rotX("left_arm", -1.3f * grab);
    };

    public static final HellModel.Anim WRATHBORN = (m, s) -> {
        m.look("head", s, 0.5f);
        m.walkPair("right_leg", "left_leg", s, 1.0f);
        m.walkPair("left_arm", "right_arm", s, 0.8f);
        float shake = s.value * 0.012f;
        m.rotZ("body", sin(s.ageInTicks * 2.1f) * shake);
        m.rotX("right_arm", -2.0f * bump(a(s, 1)));
        float roar = bump(a(s, 2));
        m.rotX("head", -0.7f * roar);
        m.rotX("jaw", 0.08f + 0.6f * roar + sin(s.ageInTicks * 0.1f) * 0.04f);
        sway(m, "tail", 4, s.ageInTicks, 0.09f, 0.12f, 0.18f, 0f);
        sway(m, "right_chain", 3, s.ageInTicks, 0.12f, 0.15f, 0.1f, 0f);
        sway(m, "left_chain", 3, s.ageInTicks, 0.12f, 0.15f, 0.1f, 2f);
        m.rotZ("right_arm", 0.9f * roar);
        m.rotZ("left_arm", -0.9f * roar);
    };

    public static final HellModel.Anim HERETIC = (m, s) -> {
        m.look("head", s, 0.8f);
        float bob = sin(s.ageInTicks * 0.08f) * 0.6f;
        for (String n : new String[]{"body", "skirt", "head", "right_arm", "left_arm"}) m.moveY(n, bob);
        m.rotX("skirt", 0.08f + sin(s.ageInTicks * 0.06f) * 0.05f);
        float cast = bump(a(s, 1));
        m.rotX("right_arm", -2.7f * cast);
        m.rotX("left_arm", -2.7f * cast);
        m.rotZ("right_arm", 0.3f * cast);
        m.rotZ("left_arm", -0.3f * cast);
        m.rotX("right_arm", -1.8f * bump(a(s, 2)));
        float t = s.ageInTicks;
        m.scale("flames", 1f, 1f + sin(t * 0.7f) * 0.15f, 1f);
        for (int i = 0; i < 2; i++) {
            float o = t * 0.06f + i * Mth.PI;
            m.move("orb" + i, cos(o) * 8f, 2f + sin(t * 0.11f + i) * 2f, sin(o) * 8f);
        }
    };

    public static final HellModel.Anim IRON_FURY = (m, s) -> {
        m.look("head", s, 0.7f);
        float flap = sin(s.ageInTicks * 0.55f) * 0.65f;
        float dive = s.anim == 1 ? 1f : 0f;
        m.rotZ("right_wing", -flap * (1 - dive) + 1.1f * dive - 0.15f);
        m.rotZ("left_wing", flap * (1 - dive) - 1.1f * dive + 0.15f);
        float lag = sin(s.ageInTicks * 0.55f - 0.9f) * 0.45f * (1 - dive);
        m.rotZ("right_wing_tip", -lag + 0.5f * dive);
        m.rotZ("left_wing_tip", lag - 0.5f * dive);
        m.rotX("tail", sin(s.ageInTicks * 0.2f) * 0.1f - 0.3f * dive);
        m.rotX("body", 0.9f * dive);
        m.rotX("right_leg", 0.4f + sin(s.ageInTicks * 0.2f) * 0.1f);
        m.rotX("left_leg", 0.4f + sin(s.ageInTicks * 0.2f + 1f) * 0.1f);
        float shriek = bump(a(s, 2));
        m.rotX("head", -0.8f * shriek);
    };

    public static final HellModel.Anim PENITENT = (m, s) -> {
        if (s.flag) return; // watched: an icon of ice
        m.walkPair("right_leg", "left_leg", s, 1.2f);
        m.rotX("head", sin(s.ageInTicks * 1.7f) * 0.05f);
        m.rotX("right_arm", -0.4f * bump(a(s, 1)));
        m.rotX("left_arm", -0.4f * bump(a(s, 1)));
    };

    public static final HellModel.Anim ECHO = (m, s) -> {
        m.look("head", s, 1f);
        m.walkPair("right_leg", "left_leg", s, 1.4f);
        m.walkPair("left_arm", "right_arm", s, 1.4f);
        m.rotZ("head", sin(s.ageInTicks * 0.31f) * 0.08f);
        m.rotX("right_arm", -1.7f * bump(a(s, 1)));
        for (int i = 0; i < 6; i++) {
            m.moveY("frag" + i, sin(s.ageInTicks * 0.1f + i * 1.3f) * 1.5f);
            m.rotY("frag" + i, s.ageInTicks * 0.05f * (i % 2 == 0 ? 1 : -1));
        }
    };

    public static final HellModel.Anim TOLL_KEEPER = (m, s) -> {
        m.look("head", s, 0.8f);
        float t = s.ageInTicks;
        m.rotX("skirt", sin(t * 0.06f) * 0.05f);
        m.rotZ("skirt", sin(t * 0.045f) * 0.04f);
        m.rotX("left_arm", -0.5f);
        m.rotX("right_arm", -0.25f);
        m.rotZ("lantern", sin(t * 0.09f) * 0.2f);
        m.rotX("lantern", 0.5f);
        float sweep = a(s, 1);
        if (sweep >= 0) {
            float raise = sweep < 0.6f ? sweep / 0.6f : 1f - (sweep - 0.6f) / 0.4f;
            m.rotX("right_arm", -2.2f * raise + (sweep > 0.6f ? 1.2f * (sweep - 0.6f) / 0.4f : 0));
            m.rotY("body", 0.6f * bump(sweep));
        }
        m.rotX("left_arm", -1.8f * bump(a(s, 2)));
        float wake = a(s, 3);
        if (wake >= 0) { m.rotX("right_arm", wake < 0.4f ? -2.6f : -0.2f); m.rotX("left_arm", wake < 0.4f ? -2.6f : -0.2f); }
        float call = bump(a(s, 4));
        m.rotX("left_arm", -2.8f * call);
        m.rotZ("lantern", sin(t * 1.4f) * 0.5f * call);
        float rise = bump(a(s, HellBoss.ANIM_ENTRANCE));
        m.rotZ("right_arm", 0.8f * rise);
        m.rotZ("left_arm", -0.8f * rise);
        m.rotX("tatter0", sin(t * 0.07f) * 0.12f);
        m.rotX("tatter1", sin(t * 0.07f + 1.5f) * 0.12f);
    };

    public static final HellModel.Anim MATRIARCH = (m, s) -> {
        m.look("head", s, 0.5f);
        m.quad(s, 0.6f);
        m.walkPair("leg5", "leg4", s, 0.6f);
        m.walkPair("left_arm", "right_arm", s, 0.6f);
        float t = s.ageInTicks;
        m.rotX("torso", sin(t * 0.05f) * 0.06f);
        m.rotX("upper_right_arm", sin(t * 0.06f) * 0.15f);
        m.rotX("upper_left_arm", sin(t * 0.06f + 2f) * 0.15f);
        m.rotX("tongue", sin(t * 0.1f) * 0.1f);
        float breath = sin(t * 0.05f) * 0.03f;
        m.scale("belly", 1f + breath, 1f - breath * 0.5f, 1f + breath);
        float devour = a(s, 1);
        float open = 0.1f + sin(t * 0.07f) * 0.06f;
        if (devour >= 0) open = devour < 0.85f ? 1.2f : 1.2f * (1f - (devour - 0.85f) / 0.15f);
        open += 0.7f * bump(a(s, 2)) + 0.9f * bump(a(s, 4));
        m.rotX("maw_jaw", open);
        m.rotX("maw_top", -open * 0.35f);
        m.rotY("right_mouth", -0.4f * open);
        m.rotY("left_mouth", 0.4f * open);
        float quake = a(s, 3);
        if (quake >= 0) m.moveY("belly", quake < 0.6f ? -5f * quake / 0.6f : -5f * (1f - (quake - 0.6f) / 0.4f));
        m.rotZ("belly", sin(t * 3f) * 0.03f * bump(a(s, HellBoss.ANIM_ENTRANCE)));
    };

    public static final HellModel.Anim STYXMAW = (m, s) -> {
        float t = s.ageInTicks;
        m.rotZ("neck0", sin(t * 0.05f) * 0.08f);
        m.rotX("neck1", sin(t * 0.07f) * 0.12f - 0.1f);
        m.rotX("neck2", sin(t * 0.07f + 0.8f) * 0.12f - 0.15f);
        m.rotY("head", s.yRot * HellModel.DEG * 0.5f);
        m.rotX("head", 0.25f + s.xRot * HellModel.DEG * 0.5f);
        m.rotX("jaw", 0.12f + sin(t * 0.09f) * 0.06f);
        m.rotZ("right_fin", sin(t * 0.2f) * 0.3f);
        m.rotZ("left_fin", -sin(t * 0.2f) * 0.3f);
        m.rotY("frill_right", sin(t * 0.15f) * 0.15f);
        m.rotY("frill_left", -sin(t * 0.15f) * 0.15f);
        float lunge = bump(a(s, 1));
        m.rotX("neck1", -0.7f * lunge);
        m.rotX("neck2", -0.5f * lunge);
        m.rotX("jaw", 0.9f * lunge);
        float tar = bump(a(s, 2));
        m.rotX("jaw", 0.8f * tar);
        m.rotY("head", sin(t * 0.8f) * 0.25f * tar);
        float slam = a(s, 3);
        if (slam >= 0) { float back = slam < 0.55f ? slam / 0.55f : 1f - (slam - 0.55f) / 0.45f * 2f; m.rotX("neck1", 0.5f * back); m.rotX("neck2", 0.4f * back); }
        m.rotX("neck1", 0.6f * bump(a(s, 4)));
    };

    public static final HellModel.Anim HERESIARCH = (m, s) -> {
        m.look("head", s, 0.8f);
        float t = s.ageInTicks;
        float bob = sin(t * 0.07f) * 0.8f;
        for (String n : new String[]{"body", "skirt", "head", "right_arm", "left_arm"}) m.moveY(n, bob);
        m.rotX("skirt", sin(t * 0.05f) * 0.06f);
        m.rotZ("right_arm", 0.45f);
        m.rotZ("left_arm", -0.45f);
        m.rotX("right_arm", -0.3f);
        m.rotX("left_arm", -0.3f);
        float orbit = a(s, HeresiarchEntity.CENSERS);
        float scale = HERESIARCH_SCALE;
        float theta = (180f - s.bodyRot) * HellModel.DEG;
        for (int i = 0; i < 3; i++) {
            float angle, radius, y;
            if (orbit >= 0) {
                // Same orbit the server uses for hits (HeresiarchEntity.censerPos): world angle, radius in blocks, 1 block below the feet.
                int tick = (int) (orbit * 80);
                angle = tick * 0.15f + i * Mth.TWO_PI / 3;
                radius = 3f + tick * 0.08f;
                y = 16f * (1.501f + 1f / scale);
            } else {
                angle = t * 0.05f + i * Mth.TWO_PI / 3;
                radius = 1.1f;
                y = 14f + bob;
            }
            // Invert the renderer transform (yaw by 180-bodyRot, flip X/Y, scale) so the censer sits at that world offset.
            float k = 16f * radius / scale;
            m.move("censer" + i, -k * Mth.cos(angle + theta), y, k * Mth.sin(angle + theta));
        }
        m.rotZ("halo_fire", t * 0.03f);
        m.rotX("right_arm2", sin(t * 0.07f) * 0.2f);
        m.rotX("left_arm2", sin(t * 0.07f + 2f) * 0.2f);
        m.rotZ("right_arm2_1", sin(t * 0.09f) * 0.15f);
        m.rotZ("left_arm2_1", -sin(t * 0.09f + 1f) * 0.15f);
        float cast = bump(a(s, HeresiarchEntity.PYRE)) + bump(a(s, HeresiarchEntity.CHOIR)) + bump(a(s, HeresiarchEntity.IRON_RAIN));
        m.rotX("right_arm", -2.4f * Math.min(1, cast));
        m.rotX("left_arm", -2.4f * Math.min(1, cast));
        m.rotX("right_arm2", -1.6f * Math.min(1, cast));
        m.rotX("left_arm2", -1.6f * Math.min(1, cast));
        m.rotX("right_arm", -1.8f * bump(a(s, HeresiarchEntity.CHAIN)));
    };

    /** VIGIL, all forms. form: 1..5; flag = mask broken (final), flag2 = pursuing/veiled. */
    public static final HellModel.Anim VIGIL = (m, s) -> {
        int form = s.form;
        // The stalker's mask splits from form IV on; the final VIGIL keeps it until phase 2 breaks it.
        boolean masked = s.finalForm ? !s.flag : form < 4;
        m.show("mask", masked);
        m.show("eyes", !masked);
        m.show("right_arm2", form >= 2);
        m.show("left_arm2", form >= 2);
        m.show("halo", form >= 5);
        m.show("candles", form >= 3);
        m.show("spine", form >= 3);
        float stretch = form >= 3 ? 1.25f : 1f;
        m.stretch("right_arm", stretch);
        m.stretch("left_arm", stretch);
        m.stretch("right_arm2", stretch);
        m.stretch("left_arm2", stretch);
        float t = s.ageInTicks;
        m.look("head", s, 0.9f);
        m.rotZ("head", sin(t * 0.021f) * 0.18f);      // the slow, wrong tilt
        m.walkPair("right_leg", "left_leg", s, 0.7f);
        m.rotX("right_arm2", sin(t * 0.07f) * 0.2f);
        m.rotX("left_arm2", sin(t * 0.07f + 2f) * 0.2f);
        if (s.flag2) {  // reaching while it hunts
            m.rotX("right_arm", -1.1f + sin(t * 0.15f) * 0.1f);
            m.rotX("left_arm", -1.1f + sin(t * 0.15f + 1f) * 0.1f);
        }
        m.rotY("halo", t * 0.02f);
        for (int i = 0; i < 3; i++) m.rotX("tatter" + i, sin(t * 0.06f + i * 1.7f) * 0.15f);
        // Final-boss attacks.
        float toll = bump(a(s, 1));
        m.rotX("right_arm", -2.6f * toll); m.rotX("left_arm", -2.6f * toll);
        float hands = bump(a(s, 2));
        m.rotX("right_arm", 0.6f * hands); m.rotX("left_arm", 0.6f * hands); m.rotX("body", 0.4f * hands);
        float blink = a(s, 3);
        if (blink >= 0) m.rotZ("body", sin(t * 9f) * 0.08f);
        float echoes = bump(a(s, 4));
        m.rotZ("right_arm", 1.2f * echoes); m.rotZ("left_arm", -1.2f * echoes);
        float gaze = bump(a(s, 5));
        m.rotX("head", 0.3f * gaze);
        m.rotX("right_arm", -1.6f * bump(a(s, 6)));
        float rise = bump(a(s, HellBoss.ANIM_ENTRANCE));
        m.rotX("head", 0.5f * rise);
    };

    public static final HellModel.Anim DAMPER = (m, s) -> {
        m.look("head", s, 0.8f);
        m.walkPair("right_leg", "left_leg", s, 0.9f);
        m.walkPair("left_arm", "right_arm", s, 0.35f);
        m.rotZ("right_arm", 0.05f + sin(s.ageInTicks * 0.04f) * 0.04f);
        m.rotZ("left_arm", -0.05f - sin(s.ageInTicks * 0.04f) * 0.04f);
        m.rotX("cloak", sin(s.ageInTicks * 0.05f) * 0.05f + 0.35f * s.walkAnimationSpeed);
    };

    public static final HellModel.Anim BIFAUCE = (m, s) -> {
        m.quad(s, 1.1f);
        float open = Math.abs(sin(s.ageInTicks * 0.1f)) * 0.18f + 0.3f * s.walkAnimationSpeed;
        m.rotY("head_left", -0.1f - open);
        m.rotY("head_right", 0.1f + open);
        m.rotY("head_left", s.yRot * HellModel.DEG * 0.5f);
        m.rotY("head_right", s.yRot * HellModel.DEG * 0.5f);
        sway(m, "right_tentacle", 6, s.ageInTicks, 0.08f, 0.08f, 0.06f, 0f);
        sway(m, "left_tentacle", 6, s.ageInTicks, 0.08f, 0.08f, 0.06f, 1.7f);
    };
}
