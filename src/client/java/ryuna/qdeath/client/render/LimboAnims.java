package ryuna.qdeath.client.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.HellEntities;

import java.util.Set;

import static ryuna.qdeath.client.render.HellModel.bump;

/**
 * Animation of the dead of the Limbo. Every state and every announced attack has a pose that reads at a distance,
 * so nothing has to be learnt by being hit: the Hollow lifts its head and opens its jaw when it recognizes life, raises
 * both arms before it strikes; the Crier's ribcage swells through its inhale; the Sundered opens its halves; the
 * Ossuary leans its mass to one side; the Hound lowers itself and chatters; the Bearer gathers its remains first.
 */
public final class LimboAnims {
    private LimboAnims() {}

    public static final Set<EntityType<?>> LIMBO = Set.of(HellEntities.HOLLOW, HellEntities.RUNAWAY, HellEntities.CRIER, HellEntities.CRAWLER,
            HellEntities.SUNDERED, HellEntities.WALKING_OSSUARY, HellEntities.MOURNING_HOUND, HellEntities.WAKE_BEARER, HellEntities.MOURNING_PARASITE,
            HellEntities.BREACH_TICKER);

    // Mind ordinals (ryuna.qdeath.limbo.Mind)
    private static final int SUSPICION = 1, PURSUIT = 4, SEARCH = 5;

    private static float sin(float v) { return Mth.sin(v); }
    private static float cos(float v) { return Mth.cos(v); }
    private static float a(HellRenderState s, int id) { return s.anim == id ? s.animProgress : -1; }

    /** 0 -> 1 over the windup part of an attack animation, then quickly back to 0. */
    private static float windup(float p, float split) {
        if (p < 0) return 0;
        return p < split ? p / split : Math.max(0, 1 - (p - split) / (1 - split) * 1.5f);
    }

    /** Head behaviour shared by all: a tilt while suspicious, a sweep while searching, a lift on recognition. */
    private static void head(HellModel m, HellRenderState s, String head, float look) {
        m.look(head, s, look);
        float t = s.ageInTicks;
        if (s.mind == SUSPICION) { m.rotZ(head, 0.28f); m.rotY(head, sin(t * 0.05f) * 0.15f); }
        if (s.mind == SEARCH) m.rotY(head, sin(t * 0.12f) * 0.7f);
        m.rotX(head, -0.55f * bump(a(s, LimboDead.ANIM_RECOGNIZE)));
        float st = a(s, LimboDead.ANIM_STAGGER);
        if (st >= 0) m.rotZ(head, sin(t * 1.7f) * 0.25f * (1 - st));
    }

    // ------------------------------------------------------------------ the expansion
    /**
     * Mourning Parasite: legs skitter in two tripods, the sac breathes. Before the leap it crouches; clinging, its legs
     * close round the host's head and the tendrils reach down into it, writhing, while the sac pulses fast; before its
     * host strikes, the sac clenches.
     */
    public static final HellModel.Anim PARASITE = (m, s) -> {
        float t = s.ageInTicks, wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        for (int i = 0; i < 3; i++) {
            float sw = sin(wp * 1.4f + (i % 2 == 0 ? 0 : Mth.PI)) * 0.55f * ws;
            m.rotY("right_leg" + i, sw);
            m.rotY("left_leg" + i, sw);
            m.rotZ("right_leg" + i, -Math.max(0, cos(wp * 1.4f + i)) * 0.25f * ws);
            m.rotZ("left_leg" + i, Math.max(0, cos(wp * 1.4f + i)) * 0.25f * ws);
        }
        boolean clinging = s.special == 3 || s.special == 4;
        float br = 1f + (clinging ? 0.14f * sin(t * 0.55f) : 0.07f * sin(t * 0.2f));
        m.scale("sac", br, br, br);
        float crouch = bump(a(s, LimboDead.ANIM_SPECIAL));
        m.moveY("body", 1.5f * crouch);
        for (int i = 0; i < 3; i++) { m.rotZ("right_leg" + i, -0.5f * crouch); m.rotZ("left_leg" + i, 0.5f * crouch); }
        float sink = s.special == 4 ? 1f : Math.max(0, a(s, LimboDead.ANIM_CALL));
        if (clinging) {
            for (int i = 0; i < 3; i++) {
                float grip = 0.55f + 0.08f * sin(t * 0.7f + i);
                m.rotZ("right_leg" + i, grip);
                m.rotZ("left_leg" + i, -grip);
                m.rotZ("right_leg" + i + "_1", 0.6f);
                m.rotZ("left_leg" + i + "_1", -0.6f);
            }
        }
        for (String side : new String[]{"right", "left"}) for (int i = 0; i < 2; i++) {
            String n = side + "_tendril" + i;
            float w = sin(t * 0.35f + i * 1.7f + (side.equals("left") ? 1 : 0));
            m.rotX(n, -1.15f * sink + 0.1f * w);
            m.rotX(n + "_1", -0.55f * sink + 0.25f * w * (0.4f + sink));
            m.rotX(n + "_2", -0.45f * sink + 0.3f * w);
        }
        float clench = bump(a(s, LimboDead.ANIM_ATTACK));
        m.scale("sac", br - 0.25f * clench, br + 0.2f * clench, br - 0.25f * clench);
        m.rotX("mouth", -0.4f * clench);
        float st = a(s, LimboDead.ANIM_STAGGER);
        if (st >= 0) for (int i = 0; i < 3; i++) m.rotY("right_leg" + i, sin(t * 2.1f + i) * 0.6f * (1 - st));
    };

    /**
     * Breach Ticker: a low scuttle on four limbs, gnawing jaw, the sac breathing on its back. Crouched before it leaps.
     * Clinging (to a victim or a wall) it turns its belly to what it holds, legs spread, and the sac swells and pulses
     * faster and harder through the count: the warning.
     */
    public static final HellModel.Anim TICKER = (m, s) -> {
        float t = s.ageInTicks, wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        float sw = cos(wp * 0.9f) * 0.8f * ws;
        m.rotX("right_arm", sw);
        m.rotX("left_hind", sw);
        m.rotX("left_arm", -sw);
        m.rotX("right_hind", -sw);
        m.look("head", s, 0.4f);
        m.rotX("jaw", 0.15f + Math.abs(sin(t * 0.6f)) * 0.25f);
        m.rotY("tail", sin(t * 0.12f) * 0.4f);
        m.rotY("tail_1", sin(t * 0.12f + 0.8f) * 0.4f);
        float sac = 1f + 0.04f * sin(t * 0.15f);
        float p = a(s, LimboDead.ANIM_CALL);
        if (p >= 0) {
            // The count: swelling, and pulses that quicken and deepen toward the burst.
            float f = 0.35f + 1.9f * p;
            float pulse = Math.max(0, sin(t * f));
            sac = 1f + 0.3f * p + (0.08f + 0.28f * p) * pulse * pulse;
            m.rotX("jaw", 0.5f * p);
        }
        m.scale("sac", sac, sac * (1f + 0.1f * (sac - 1f)), sac);
        boolean clinging = s.special == 3 || s.special == 4;
        if (clinging) {
            m.rotX("body", -1.35f);
            m.rotX("right_arm", -1.3f);
            m.rotX("left_arm", -1.3f);
            m.rotX("right_hind", 1.1f);
            m.rotX("left_hind", 1.1f);
            m.rotZ("right_arm", 0.5f);
            m.rotZ("left_arm", -0.5f);
        }
        float crouch = bump(a(s, LimboDead.ANIM_SPECIAL));
        m.moveY("body", 1.6f * crouch);
        m.rotX("right_hind", 0.6f * crouch);
        m.rotX("left_hind", 0.6f * crouch);
        float st = a(s, LimboDead.ANIM_STAGGER);
        if (st >= 0) { m.rotZ("body", sin(t * 1.9f) * 0.3f * (1 - st)); m.scale("sac", 0.85f, 0.85f, 0.85f); }
    };

    public static final HellModel.Anim HOLLOW = (m, s) -> {
        head(m, s, "head", 0.5f);
        m.walkPair("right_leg", "left_leg", s, 0.8f);
        float t = s.ageInTicks;
        // Hands groping at the air, higher when it has found something.
        float grope = s.mind == PURSUIT || s.mind == SEARCH ? 0.95f : 0.45f;
        m.rotX("right_arm", -grope + sin(t * 0.07f) * 0.14f);
        m.rotX("left_arm", -grope * 0.85f + sin(t * 0.07f + 1.3f) * 0.14f);
        m.rotZ("right_arm", 0.1f + sin(t * 0.05f) * 0.05f);
        m.rotZ("left_arm", -0.1f - sin(t * 0.05f + 0.7f) * 0.05f);
        m.rotX("right_forearm", -0.25f - sin(t * 0.09f) * 0.15f);
        m.rotX("left_forearm", -0.25f - sin(t * 0.09f + 2f) * 0.15f);
        // Laboured breathing: the jaw works, the chest heaves.
        float rec = bump(a(s, LimboDead.ANIM_RECOGNIZE));
        m.rotX("jaw", 0.12f + Math.abs(sin(t * 0.11f)) * 0.12f + 0.45f * rec);
        m.rotX("body", sin(t * 0.11f) * 0.02f);
        // The announced attack: both arms rise high and the body leans in.
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.7f);
        m.rotX("right_arm", -1.9f * up);
        m.rotX("left_arm", -1.9f * up);
        m.rotX("body", 0.3f * up);
        // The call: arms spread, head thrown back, jaw open.
        float call = bump(a(s, LimboDead.ANIM_CALL));
        m.rotZ("right_arm", 0.9f * call);
        m.rotZ("left_arm", -0.9f * call);
        m.rotX("head", -0.6f * call);
        m.rotX("jaw", 0.6f * call);
        m.rotX("tatter0", sin(t * 0.1f) * 0.15f + 0.4f * s.walkAnimationSpeed);
        m.rotX("tatter1", -sin(t * 0.1f + 1f) * 0.15f - 0.4f * s.walkAnimationSpeed);
    };

    public static final HellModel.Anim RUNAWAY = (m, s) -> {
        head(m, s, "head", 0.4f);
        float t = s.ageInTicks;
        m.walkPair("right_leg", "left_leg", s, 1.2f);
        float swing = cos(s.walkAnimationPos * 0.6662f) * s.walkAnimationSpeed;
        m.rotX("right_arm", 0.25f * swing);
        m.rotX("left_arm", -0.25f * swing);
        m.rotX("torso", sin(s.walkAnimationPos * 1.33f) * 0.06f * s.walkAnimationSpeed);
        m.rotX("jaw", 0.15f + Math.abs(sin(t * 0.25f)) * 0.15f);
        // Before the run: it pants and compresses, knees bent, torso folding lower.
        float prep = bump(a(s, LimboDead.ANIM_SPECIAL));
        m.rotX("torso", 0.3f * prep);
        m.rotX("right_leg", -0.55f * prep);
        m.rotX("left_leg", -0.55f * prep);
        m.rotX("right_shin", 0.6f * prep);
        m.rotX("left_shin", 0.6f * prep);
        m.rotX("jaw", Math.abs(sin(t * 1.4f)) * 0.5f * prep);
        // After it: heaving for breath, head hanging.
        float rec = a(s, LimboDead.ANIM_STAGGER) >= 0 ? 1 : 0;
        m.rotX("torso", (0.18f + sin(t * 0.9f) * 0.08f) * rec);
        m.rotX("head", 0.4f * rec);
        m.rotX("jaw", (0.3f + Math.abs(sin(t * 0.9f)) * 0.3f) * rec);
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.62f);
        m.rotX("right_arm", -1.2f * up);
        m.rotX("left_arm", -1.2f * up);
    };

    public static final HellModel.Anim CRIER = (m, s) -> {
        head(m, s, "head", 0.6f);
        m.walkPair("right_leg", "left_leg", s, 0.7f);
        m.walkPair("left_arm", "right_arm", s, 0.3f);
        float t = s.ageInTicks;
        float breathe = 1f + sin(t * 0.08f) * 0.03f;
        m.scale("chest", breathe, 1f, breathe);
        // The long inhale: the ribcage swells, the head tips back; at the end the opening parts and it screams.
        float p = a(s, LimboDead.ANIM_CALL);
        if (p >= 0) {
            float inh = Math.min(1f, p / 0.85f);
            m.scale("chest", 1f + 0.45f * inh, 1f + 0.15f * inh, 1f + 0.55f * inh);
            m.rotX("head", -0.5f * inh);
            m.rotX("torso", -0.12f * inh);
            float scream = p > 0.85f ? 1f : 0f;
            m.rotY("right_lip", -1.1f * scream);
            m.rotY("left_lip", 1.1f * scream);
            m.rotX("head", 0.75f * scream);
            m.rotZ("right_arm", 0.7f * scream);
            m.rotZ("left_arm", -0.7f * scream);
        } else {
            float lip = 0.15f + Math.abs(sin(t * 0.07f)) * 0.1f;
            m.rotY("right_lip", -lip);
            m.rotY("left_lip", lip);
        }
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.67f);
        m.rotX("right_arm", -1.6f * up);
        m.rotZ("right_arm", 0.3f * up);
    };

    public static final HellModel.Anim CRAWLER = (m, s) -> {
        head(m, s, "head", 0.5f);
        float t = s.ageInTicks;
        float w = s.walkAnimationPos * 0.9f, ws = s.walkAnimationSpeed;
        m.rotX("right_arm", sin(w) * 0.5f * ws);
        m.rotX("left_arm", -sin(w) * 0.5f * ws);
        m.rotX("right_leg", -sin(w) * 0.4f * ws);
        m.rotX("left_leg", sin(w) * 0.4f * ws);
        m.rotZ("torso", sin(w) * 0.06f * ws);
        m.rotX("jaw", 0.2f + Math.abs(sin(t * 0.13f)) * 0.15f);
        m.rotZ("head", sin(t * 0.37f) * 0.05f); // a twitch
        // The pounce is announced: limbs drawn in, body pressed low.
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.7f);
        m.moveY("torso", 2.5f * up);
        m.moveY("head", 2.5f * up);
        m.rotZ("right_arm", 0.4f * up);
        m.rotZ("left_arm", -0.4f * up);
        m.rotX("jaw", 0.6f * up);
    };

    public static final HellModel.Anim SUNDERED = (m, s) -> {
        float t = s.ageInTicks;
        m.walkPair("right_leg", "left_leg", s, 0.8f);
        // The halves part a little as it turns and walks: the deformation is visible even at rest.
        float drift = 0.04f + 0.08f * s.walkAnimationSpeed;
        m.rotZ("right_half", -drift - sin(t * 0.06f) * 0.03f);
        m.rotZ("left_half", drift + sin(t * 0.06f + 1f) * 0.03f);
        m.rotY("right_half", s.yRot * HellModel.DEG * 0.3f);
        m.rotY("left_half", s.yRot * HellModel.DEG * 0.3f);
        m.rotX("right_arm", -0.3f + sin(t * 0.07f) * 0.1f);
        m.rotX("left_arm", -0.3f + sin(t * 0.07f + 2f) * 0.1f);
        // Opening: both halves swing apart before they close on what is in front.
        float open = windup(a(s, LimboDead.ANIM_ATTACK), 0.75f);
        m.rotZ("right_half", -0.65f * open);
        m.rotZ("left_half", 0.65f * open);
        m.rotX("right_arm", -1.3f * open);
        m.rotX("left_arm", -1.3f * open);
        // Recomposition: the halves do not quite line up while it pulls itself together.
        float rec = a(s, LimboDead.ANIM_STAGGER);
        if (rec >= 0) {
            m.moveY("right_half", sin(t * 0.8f) * 1.2f * (1 - rec));
            m.rotZ("left_half", sin(t * 1.1f) * 0.12f * (1 - rec));
        }
        float r = bump(a(s, LimboDead.ANIM_RECOGNIZE));
        m.rotX("right_half", -0.15f * r);
        m.rotX("left_half", -0.15f * r);
    };

    public static final HellModel.Anim OSSUARY = (m, s) -> {
        float t = s.ageInTicks;
        float w = s.walkAnimationPos * 0.5f, ws = s.walkAnimationSpeed;
        for (int i = 0; i < 5; i++) m.rotX("leg" + i, sin(w + i * 1.3f) * 0.35f * ws);
        m.rotZ("mass", sin(w) * 0.06f * ws);
        m.rotX("mass", sin(w * 2) * 0.03f * ws);
        for (int i = 0; i < 5; i++) m.rotZ("rib" + i, sin(t * 0.05f + i) * 0.06f);
        // Before a blow it shifts its weight to one side and lifts its club of bone.
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.77f);
        m.rotZ("mass", 0.3f * up);
        m.rotZ("club", -1.3f * up);
        float sg = a(s, LimboDead.ANIM_SIEGE);
        if (sg >= 0) { m.rotZ("club", -1.2f * sin(sg * Mth.PI)); m.rotZ("mass", 0.2f * sin(sg * Mth.PI)); }
        // Before it sheds one of its dead, the remains inside rattle.
        float shed = a(s, LimboDead.ANIM_SPECIAL);
        if (shed >= 0) {
            m.move("occupant", sin(t * 2.3f) * 0.8f, -5 + sin(t * 3.1f) * 0.6f, -6);
            m.rotX("cage", sin(t * 1.9f) * 0.12f);
            for (int i = 0; i < 5; i++) m.rotX("rib" + i, sin(t * 2f + i) * 0.25f);
        }
    };

    public static final HellModel.Anim HOUND = (m, s) -> {
        float t = s.ageInTicks;
        m.quad(s, 0.6f);
        m.rotX("head", sin(t * 0.15f) * 0.05f);
        // Nose to the ground while resting or searching, head up when it has contact.
        boolean sniff = s.mind != PURSUIT;
        m.rotX("neck", sniff ? 0.35f + Math.abs(sin(t * 0.5f)) * 0.08f : -0.2f);
        if (s.mind == SEARCH) m.rotY("neck", sin(t * 0.2f) * 0.5f);
        m.rotX("rag", sin(t * 0.1f) * 0.12f + 0.3f * s.walkAnimationSpeed);
        m.moveY("body", Math.abs(sin(s.walkAnimationPos * 0.66f)) * -0.8f * s.walkAnimationSpeed);
        // It lowers itself and chatters its teeth before it lunges.
        float up = windup(a(s, LimboDead.ANIM_ATTACK), 0.67f);
        m.moveY("body", 2.5f * up);
        m.rotX("neck", -0.3f * up);
        m.rotX("jaw", Math.abs(sin(t * 2.6f)) * 0.5f * up);
        float r = bump(a(s, LimboDead.ANIM_RECOGNIZE));
        m.rotX("neck", -0.5f * r);
        m.rotX("jaw", Math.abs(sin(t * 2.6f)) * 0.4f * r);
    };

    public static final HellModel.Anim BEARER = (m, s) -> {
        head(m, s, "head", 0.3f);
        float t = s.ageInTicks;
        m.rotX("skirt", sin(s.walkAnimationPos * 0.6662f) * 0.06f * s.walkAnimationSpeed);
        m.rotZ("skirt", cos(s.walkAnimationPos * 0.6662f) * 0.04f * s.walkAnimationSpeed);
        m.moveY("remains", sin(t * 0.06f) * 0.3f);
        // It gathers the remains against its chest before a wide blow.
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float gather = Math.min(1f, p / 0.81f);
            m.moveY("remains", -1.5f * gather);
            m.rotX("right_arm", -0.3f * gather);
            m.rotX("left_arm", -0.3f * gather);
            if (p > 0.81f) {
                float out = 1 - (p - 0.81f) / 0.19f;
                m.rotZ("right_arm", 1.5f * out);
                m.rotZ("left_arm", -1.5f * out);
            }
        }
        // Stillness, then the gesture that points the procession forward.
        float sig = a(s, LimboDead.ANIM_SPECIAL);
        if (sig > 0.5f) {
            float k = bump((sig - 0.5f) * 2f);
            m.rotX("right_arm", -1.7f * k);
            m.rotY("right_forearm", 0.9f * k);
            m.rotX("right_forearm", 1.0f * k);
        }
        float sg = a(s, LimboDead.ANIM_SIEGE);
        if (sg >= 0) { m.rotX("right_arm", -2.2f * sin(sg * Mth.PI)); m.rotX("left_arm", -2.2f * sin(sg * Mth.PI)); }
    };
}
