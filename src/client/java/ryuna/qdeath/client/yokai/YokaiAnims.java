package ryuna.qdeath.client.yokai;

import net.minecraft.util.Mth;
import ryuna.qdeath.client.render.HellAnims;
import ryuna.qdeath.client.render.HellModel;
import ryuna.qdeath.client.render.HellRenderState;

import static ryuna.qdeath.client.render.HellModel.bump;

/** Procedural animation of the yokai. Every windup has a readable pose; idle motion is slow and a little wrong. */
public final class YokaiAnims {
    private YokaiAnims() {}

    private static float sin(float v) { return Mth.sin(v); }
    private static float cos(float v) { return Mth.cos(v); }
    private static float a(HellRenderState s, int id) { return s.anim == id ? s.animProgress : -1; }
    /** Eases in over a windup and holds: 0 at the start, 1 from 70% on. */
    private static float ease(float p) { return p < 0 ? 0 : Math.min(1f, p / 0.7f); }

    private static void sway(HellModel m, String base, int segments, float t, float speed, float ampX, float ampZ, float phase) {
        for (int i = 0; i < segments; i++) {
            String n = i == 0 ? base : base + "_" + i;
            m.rotX(n, sin(t * speed + phase - i * 0.6f) * ampX);
            m.rotZ(n, cos(t * speed * 0.8f + phase - i * 0.6f) * ampZ);
        }
    }

    public static final HellModel.Anim FAKE_CREEPER = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.8f);
        m.rotZ("body", sin(t * 0.07f) * 0.05f);
        m.rotX("body", sin(t * 0.05f) * 0.04f);
        for (int i = 0; i < 4; i++) sway(m, "wisp" + i, 3, t, 0.12f, 0.25f, 0.15f, i * 1.3f);
        float fuse = s.value;
        if (fuse > 0) {
            // The hiss: it swells and shivers.
            float k = 1 + fuse * 0.18f + sin(t * 2.5f) * 0.05f * fuse;
            m.scale("head", k, k, k);
            m.scale("body", k, 1 + fuse * 0.08f, k);
            m.rotZ("head", sin(t * 3.1f) * 0.1f * fuse);
            m.rotX("head", sin(t * 2.3f) * 0.06f * fuse);
        }
    };

    /**
     * The big eye looks around while its nerves hang and sway; its swarm of small eyes circles it like a Blaze's rods
     * (an upper ring of 4 and a lower one of 3 turning the other way), every small eye staring where the big one stares.
     * The small eye it last shot grows back.
     */
    public static final HellModel.Anim SWARM_EYE = (m, s) -> {
        float t = s.ageInTicks;
        boolean alarmed = s.value >= 2;
        m.look("eye", s, 1f);
        m.moveY("eye", sin(t * 0.1f) * 0.5f);
        sway(m, "optic", 3, t, 0.09f, 0.15f, 0.12f, 0f);
        for (int i = 0; i < 4; i++) sway(m, "nerve" + i, 3, t, alarmed ? 0.4f : 0.12f, alarmed ? 0.4f : 0.22f, 0.18f, i * 1.6f);
        float spin = t * (alarmed ? 0.06f : 0.03f);
        for (int i = 0; i < 7; i++) {
            boolean upper = i < 4;
            float a = upper ? spin + i * Mth.HALF_PI : -spin * 1.3f + (i - 4) * Mth.TWO_PI / 3;
            float r = upper ? 10 : 6;
            String orb = "orb" + i;
            m.move(orb, sin(a) * r, (upper ? 14 : 24) + sin(t * 0.13f + i) * 1.2f, cos(a) * r);
            m.look(orb, s, 1f);
            sway(m, orb + "_a", 2, t, 0.16f, 0.3f, 0.2f, i * 0.9f);
            sway(m, orb + "_b", 2, t, 0.16f, 0.3f, 0.2f, i * 0.9f + 2f);
        }
        if (s instanceof YokaiRenderer.State y) m.scale("orb0", y.regrow, y.regrow, y.regrow);
        if (s.value >= 1) {
            float k = 1 + 0.03f * sin(t * (alarmed ? 1.6f : 0.4f));
            m.scale("eye", k, k, k);
        }
    };

    /** The shot: the small eye wobbles in flight while its three torn nerves whip behind it. */
    public static final HellModel.Anim SWARM_EYE_SHOT = (m, s) -> {
        m.rotZ("eye", sin(s.ageInTicks * 0.5f) * 0.25f);
        for (int i = 0; i < 3; i++) sway(m, "tail" + i, 3, s.ageInTicks, 0.9f, 0.35f, 0.3f, i * 2.1f);
    };

    public static final HellModel.Anim KAPPA = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.8f);
        m.walkPair("right_leg", "left_leg", s, 0.9f);
        m.walkPair("left_arm", "right_arm", s, 0.6f);
        m.rotX("body", sin(t * 0.05f) * 0.03f);
        m.rotZ("head", sin(t * 0.04f) * 0.06f);
        float w = a(s, 1);
        if (w >= 0) {
            m.rotX("right_arm", -1.3f * bump(w));
            m.rotX("left_arm", -1.3f * bump(w));
            m.moveY("body", sin(t * 1.5f) * 0.6f);
            m.moveY("head", sin(t * 1.5f) * 0.6f);
        }
        float b = a(s, 2);
        if (b >= 0) {
            m.rotX("head", 0.5f * bump(b));
            m.moveY("head", 1.2f * bump(b));
        }
        float g = a(s, 3);
        if (g >= 0) {
            m.rotX("right_arm", -1.5f * bump(g));
            m.rotX("left_arm", -1.5f * bump(g));
        }
    };

    public static final HellModel.Anim ONI = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.7f);
        m.walkPair("right_leg", "left_leg", s, 0.8f);
        m.walkPair("left_arm", "right_arm", s, 0.4f);
        m.rotX("body", sin(t * 0.06f) * 0.03f + 0.12f * s.walkAnimationSpeed);
        m.rotZ("left_arm", -0.1f - sin(t * 0.05f) * 0.03f);
        float w = a(s, 1);
        if (w >= 0) {
            // The club goes up and stays up: two seconds to step out of the way.
            float e = ease(w);
            m.rotX("right_arm", -2.7f * e);
            m.rotZ("right_arm", 0.25f * e);
            m.rotX("body", -0.15f * e);
            m.rotX("head", -0.2f * e);
        }
        float sl = a(s, 2);
        if (sl >= 0) {
            float p = Math.min(1f, sl * 3f);
            m.rotX("right_arm", -2.7f + 3.3f * p);
            m.rotX("body", 0.25f * bump(sl));
        }
        float st = a(s, 3);
        if (st >= 0) {
            m.rotX("right_leg", -1.0f * bump(st));
            m.rotX("right_arm", -0.6f * bump(st));
        }
    };

    public static final HellModel.Anim CHEST = (m, s) -> {
        float t = s.ageInTicks;
        m.quad(s, 1.2f);
        float hop = -Math.abs(sin(s.walkAnimationPos * 0.6f)) * 2.2f * s.walkAnimationSpeed;
        for (String n : new String[]{"base", "lid", "tongue"}) m.moveY(n, hop);
        float lid = s.flag ? 1.0f : 0f;
        float r = a(s, 1);
        if (r >= 0) lid += 0.28f * Math.abs(sin(t * 1.3f)) * bump(r);
        float w = a(s, 2);
        if (w >= 0) lid += 0.75f * ease(w) + sin(t * 2.4f) * 0.06f;
        float b = a(s, 3);
        if (b >= 0) lid += 0.9f * (1 - b);
        m.rotX("lid", -lid);
        m.show("tongue", lid > 0.15f);
        m.rotX("tongue", -lid * 0.35f + sin(t * 0.25f) * 0.08f);
    };

    public static final HellModel.Anim TANUKI = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.8f);
        m.walkPair("right_leg", "left_leg", s, 1.0f);
        m.moveY("body", sin(t * 0.1f) * 0.2f);
        m.rotY("tail", sin(t * 0.15f) * 0.3f);
        m.rotX("tail_1", sin(t * 0.12f) * 0.12f);
        float d = a(s, 1);
        if (d >= 0) {
            float k = bump(d);
            m.rotX("right_arm", 0.4f * Math.abs(sin(t * 1.2f)) * k);
            m.rotX("left_arm", 0.4f * Math.abs(cos(t * 1.2f)) * k);
            m.scale("body", 1 + 0.04f * Math.abs(sin(t * 1.2f)) * k, 1, 1 + 0.04f * Math.abs(sin(t * 1.2f)) * k);
        }
        float tr = a(s, 2);
        if (tr >= 0) {
            m.rotY("leaf", tr * 6.28f);
            m.rotZ("head", 0.3f * bump(tr));
        }
    };

    public static final HellModel.Anim TENGU = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.8f);
        m.walkPair("right_leg", "left_leg", s, 1.0f);
        m.walkPair("left_arm", "right_arm", s, 0.5f);
        m.rotY("right_wing", sin(t * 0.05f) * 0.08f);
        m.rotY("left_wing", -sin(t * 0.05f) * 0.08f);
        float f = a(s, 3);
        if (f >= 0) {
            float beat = sin(f * Mth.PI * 4) * 0.7f;
            m.rotY("right_wing", beat);
            m.rotY("left_wing", -beat);
        }
        float w = a(s, 1);
        if (w >= 0) {
            float e = ease(w);
            m.rotX("right_arm", -2.5f * e);
            m.rotZ("right_arm", -0.25f * e);
            m.rotY("right_wing", -0.4f * e);
            m.rotY("left_wing", 0.4f * e);
        }
        float g = a(s, 2);
        if (g >= 0) {
            m.rotX("right_arm", -2.5f + 3.0f * Math.min(1f, g * 2.5f));
            m.rotY("right_wing", 0.5f * bump(g));
            m.rotY("left_wing", -0.5f * bump(g));
        }
    };

    public static final HellModel.Anim KODAMA = (m, s) -> {
        float t = s.ageInTicks;
        m.look("head", s, 0.6f);
        m.walkPair("right_leg", "left_leg", s, 1.2f);
        m.walkPair("left_arm", "right_arm", s, 0.8f);
        m.rotZ("head", sin(t * 0.04f) * 0.08f);
        float r = a(s, 1);
        // Karakara: the head rattles from side to side.
        if (r >= 0) m.rotZ("head", sin(t * 2.2f) * 0.38f * bump(r));
        float p = a(s, 2);
        if (p >= 0) m.rotX("right_arm", -1.7f * bump(p));
    };

    public static final HellModel.Anim NURIKABE = (m, s) -> {
        float t = s.ageInTicks;
        m.rotZ("body", sin(t * 0.02f) * 0.01f);
        m.walkPair("right_foot", "left_foot", s, 0.6f);
        float p = a(s, 1);
        if (p >= 0) m.rotZ("body", 0.08f * bump(p));
    };

    public static final HellModel.Anim ITTAN_MOMEN = (m, s) -> {
        float t = s.ageInTicks;
        if (s.flag) {
            // Pinned by snow: flat, twitching.
            for (int i = 2; i <= 8; i++) m.rotX("cloth" + i, sin(t * 0.9f + i) * 0.03f);
            m.rotX("head", 0.9f);
            return;
        }
        float w = a(s, 1), wr = a(s, 2);
        float amp = w >= 0 ? 0.42f : 0.25f, speed = w >= 0 ? 0.5f : s.walking ? 0.35f : 0.18f;
        for (int i = 2; i <= 8; i++) m.rotX("cloth" + i, sin(t * speed - i * 0.7f) * amp);
        m.rotX("cloth1", sin(t * speed) * amp * 0.5f);
        m.rotX("head", sin(t * 0.1f) * 0.1f - (w >= 0 ? 0.6f * ease(w) : 0));
        if (wr >= 0) for (int i = 2; i <= 8; i++) m.rotX("cloth" + i, 0.55f * bump(wr));
    };

    public static final HellModel.Anim BELL = (m, s) -> {
        float t = s.ageInTicks;
        m.walkPair("right_foot", "left_foot", s, 0.8f);
        m.moveY("bell", -Math.abs(sin(s.walkAnimationPos * 0.66f)) * 1.5f * s.walkAnimationSpeed);
        m.moveY("tongue", -Math.abs(sin(s.walkAnimationPos * 0.66f)) * 1.5f * s.walkAnimationSpeed);
        m.rotX("tongue", sin(t * 0.1f) * 0.1f);
        float r = a(s, 1), w = a(s, 2);
        float k = r >= 0 ? 0.12f * bump(r) : w >= 0 ? 0.3f * bump(w) : 0;
        m.rotZ("bell", sin(t * 1.6f) * k);
        m.rotZ("tongue", sin(t * 1.6f + 1) * k * 3);
    };

    /** The original Damper and Doublemouth animations, plus their Overworld warnings (pose in {@code anim}). */
    public static final HellModel.Anim DAMPER = (m, s) -> {
        HellAnims.DAMPER.apply(m, s);
        float t = s.ageInTicks;
        if (s.anim == 1) {
            // The creak: it stiffens, head cocked, long fingers lifting.
            m.rotZ("head", 0.35f + sin(t * 1.7f) * 0.05f);
            m.rotX("right_arm", -0.9f + sin(t * 2.1f) * 0.08f);
            m.rotX("left_arm", -0.9f - sin(t * 2.3f) * 0.08f);
        } else if (s.anim == 2) {
            m.rotX("right_arm", -1.6f);
        }
    };

    public static final HellModel.Anim BIFAUCE = (m, s) -> {
        HellAnims.BIFAUCE.apply(m, s);
        float t = s.ageInTicks;
        if (s.anim == 1) {
            // Both mouths gape for a second and a half before the charge.
            m.rotY("head_left", -0.95f + sin(t * 2f) * 0.05f);
            m.rotY("head_right", 0.95f - sin(t * 2f) * 0.05f);
            m.rotX("tongue", 0.3f);
        } else if (s.anim == 2) {
            m.rotX("body", 0.12f);
            m.rotY("head_left", -0.5f);
            m.rotY("head_right", 0.5f);
        }
    };
}
