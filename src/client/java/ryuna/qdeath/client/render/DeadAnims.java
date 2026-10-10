package ryuna.qdeath.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import ryuna.qdeath.entity.limbo.LimboDead;

import static ryuna.qdeath.client.render.HellModel.bump;

/**
 * Motion of the human dead. Not the vanilla zombie's arms held out: they walk bent, heads lolling at an angle of their
 * own, one leg dragging; once they have found life they run leaning forward, arms thrown out and pumping unevenly.
 * Every attack is announced by the torso and the arms (raised and drawn back) before it lands. Each specialist
 * keeps that base and adds its own posture and signals.
 */
public final class DeadAnims {
    private DeadAnims() {}

    private static final int SUSPICION = 1, RECOGNITION = 2, PURSUIT = 4, SEARCH = 5;

    private static float sin(float v) { return Mth.sin(v); }
    private static float cos(float v) { return Mth.cos(v); }
    private static float a(HellRenderState s, int id) { return s.anim == id ? s.animProgress : -1; }

    /** 0 -> 1 through the windup (until {@code split}), then quickly back to 0 for the blow. */
    static float windup(float p, float split) {
        if (p < 0) return 0;
        return p < split ? Mth.sin(p / split * Mth.HALF_PI) : Math.max(0, 1 - (p - split) / (1 - split) * 1.4f);
    }

    /** The blow after the windup: 0 until {@code split}, a fast swing to 1 and back. */
    static float strike(float p, float split) {
        if (p < split) return 0;
        return bump(Math.min(1f, (p - split) / (1 - split)));
    }

    /**
     * Bends the torso forward by {@code angle} around the hips (not the neck, as the vanilla pivot would): the neck,
     * head and shoulders move forward and down with it so the body stays joined to the legs.
     */
    static void lean(HellModel m, float angle) { m.lean += angle; }

    /** Applies the accumulated lean once, after every other pose of the frame. */
    static void applyLean(HellModel m) {
        float angle = m.lean;
        ModelPart body = m.p("body"), leg = m.p("right_leg");
        if (body == null || leg == null || angle == 0) return;
        float hipY = leg.y, len = hipY - body.y, z0 = body.z, neckY = body.y;
        float ny = hipY - len * cos(angle), nz = z0 - len * sin(angle);
        float dy = ny - body.y, dz = nz - body.z;
        body.xRot += angle;
        body.y = ny;
        body.z = nz;
        ModelPart head = m.p("head");
        if (head != null) { head.y += dy; head.z += dz; }
        for (String arm : new String[]{"right_arm", "left_arm"}) {
            ModelPart p = m.p(arm);
            if (p == null) continue;
            float d = p.y - neckY; // shoulder below the neck along the torso
            p.y = ny + d * cos(angle);
            p.z = nz + d * sin(angle);
        }
    }

    /** Wraps a human pose: the lean is summed over the frame and applied at the end. */
    static HellModel.Anim human(HellModel.Anim pose) {
        return (m, s) -> { m.lean = 0; pose.apply(m, s); mound(m, s); applyLean(m); };
    }

    private static void set(HellModel m, String name, float x, float y, float z) {
        ModelPart p = m.p(name);
        if (p != null) { p.xRot = x; p.yRot = y; p.zRot = z; }
    }

    /**
     * A body of a pile (ryuna.qdeath.limbo.Horde): pressing at the foot with the hands raised, braced under the ones
     * climbing it, or clambering up hand over hand. Replaces the limbs' own pose.
     */
    static void mound(HellModel m, HellRenderState s) {
        if (s.mound == 0) return;
        float t = s.ageInTicks + s.seed * 50, c = sin(t * 0.55f);
        switch (s.mound) {
            case 1 -> {
                set(m, "right_arm", -2.75f + sin(t * 0.3f) * 0.25f, 0, 0.3f);
                set(m, "left_arm", -2.6f + cos(t * 0.33f) * 0.25f, 0, -0.3f);
                m.rotX("head", -0.45f);
            }
            case 2 -> {
                m.lean = 0.35f;
                set(m, "right_arm", -2.95f, 0, 0.15f);
                set(m, "left_arm", -2.85f + sin(t * 0.4f) * 0.08f, 0, -0.15f);
                set(m, "right_leg", -0.45f, 0, 0.18f);
                set(m, "left_leg", -0.3f, 0, -0.18f);
                m.rotX("head", 0.35f);
            }
            default -> {
                m.lean = 0.3f;
                set(m, "right_arm", -2.6f - 0.5f * c, 0, 0.2f);
                set(m, "left_arm", -2.6f + 0.5f * c, 0, -0.2f);
                set(m, "right_leg", -0.5f + 0.45f * c, 0, 0.1f);
                set(m, "left_leg", -0.5f - 0.45f * c, 0, -0.1f);
                m.rotX("head", -0.45f);
            }
        }
    }

    /** Head: its own look, an individual tilt, an occasional twitch, and the shared state cues. */
    static void head(HellModel m, HellRenderState s, float look) {
        m.look("head", s, look);
        float t = s.ageInTicks;
        m.rotZ("head", (s.seed - 0.5f) * 0.55f);
        if (sin(t * 0.11f + s.seed * 40) > 0.985f) m.rotZ("head", 0.3f * sin(t * 2.3f)); // a broken twitch now and then
        if (s.mind == SUSPICION) { m.rotZ("head", 0.25f); m.rotY("head", sin(t * 0.05f) * 0.18f); }
        if (s.mind == SEARCH) m.rotY("head", sin(t * 0.12f + s.seed * 6) * 0.65f);
        m.rotX("head", -0.5f * bump(a(s, LimboDead.ANIM_RECOGNIZE)));
        float st = a(s, LimboDead.ANIM_STAGGER);
        if (st >= 0) m.rotZ("head", sin(t * 1.6f) * 0.3f * (1 - st));
    }

    /** Legs: a stiff shamble with one dragging leg; a long running stride while chasing. */
    static void legs(HellModel m, HellRenderState s, float run, float amp) {
        float wp = s.walkAnimationPos, ws = Math.min(1.2f, s.walkAnimationSpeed);
        float rate = 0.6662f;
        float swing = cos(wp * rate) * ws;
        float limp = 0.55f + 0.35f * s.seed; // the bad leg moves less
        boolean badRight = s.seed > 0.5f;
        float big = amp * (0.75f + 0.55f * run);
        m.rotX("right_leg", swing * big * (badRight ? limp : 1));
        m.rotX("left_leg", -swing * big * (badRight ? 1 : limp));
        m.rotZ(badRight ? "right_leg" : "left_leg", (badRight ? 1 : -1) * 0.06f * (1 - run));
        // Knees (where a body has shins): bend on the back swing.
        m.rotX("right_shin", Math.max(0, -swing) * 0.9f * ws);
        m.rotX("left_shin", Math.max(0, swing) * 0.9f * ws);
    }

    /** Arms: hanging and swaying at rest, thrown forward and pumping unevenly in pursuit. */
    static void arms(HellModel m, HellRenderState s, float run) {
        float t = s.ageInTicks, wp = s.walkAnimationPos, ws = Math.min(1.2f, s.walkAnimationSpeed);
        float sway = sin(t * 0.07f + s.seed * 9) * 0.08f;
        float rest = -0.12f + sway, counter = cos(wp * 0.6662f) * 0.3f * ws;
        float reach = -0.75f - 0.25f * s.seed, pump = cos(wp * 0.6662f + 0.4f) * 0.75f * ws;
        m.rotX("right_arm", Mth.lerp(run, rest - counter, reach + pump));
        m.rotX("left_arm", Mth.lerp(run, rest + counter * 0.6f - 0.05f, reach - pump * 0.9f - 0.15f));
        m.rotZ("right_arm", Mth.lerp(run, 0.08f + 0.06f * s.seed, 0.25f) + sway * 0.5f);
        m.rotZ("left_arm", -Mth.lerp(run, 0.05f + (1 - s.seed) * 0.12f, 0.2f) - sway * 0.5f);
        m.rotX("right_forearm", -0.25f - 0.35f * run);
        m.rotX("left_forearm", -0.2f - 0.4f * run);
    }

    static float run(HellRenderState s) {
        return s.mind == PURSUIT ? Mth.clamp(s.walkAnimationSpeed * 1.4f, 0, 1) : 0f;
    }

    /** The announced blow shared by the common dead: arms rise and draw back with the torso, then come down together. */
    static void blow(HellModel m, HellRenderState s, float split) {
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p < 0) return;
        float up = windup(p, split), hit = strike(p, split);
        m.rotX("right_arm", -2.3f * up - 0.8f * hit);
        m.rotX("left_arm", -2.0f * up - 0.9f * hit);
        m.rotZ("right_arm", 0.2f * up);
        m.rotZ("left_arm", -0.2f * up);
        lean(m, -0.22f * up + 0.4f * hit);
        if (s.seed > 0.6f) m.rotX("head", 0.5f * hit); // biters lunge with the head
    }

    static void common(HellModel m, HellRenderState s, float hunch) {
        float run = run(s);
        lean(m, hunch + 0.28f * run);
        head(m, s, 0.6f);
        m.rotX("head", -0.25f * run);
        legs(m, s, run, 0.8f);
        arms(m, s, run);
        float rec = bump(a(s, LimboDead.ANIM_RECOGNIZE));
        m.rotX("right_arm", -0.6f * rec);
        m.rotX("left_arm", -0.45f * rec);
        float call = bump(a(s, LimboDead.ANIM_CALL));
        m.rotZ("right_arm", 0.9f * call);
        m.rotZ("left_arm", -0.9f * call);
        m.rotX("head", -0.55f * call);
        float st = a(s, LimboDead.ANIM_STAGGER);
        if (st >= 0) {
            float k = 1 - st;
            lean(m, -0.3f * k);
            m.rotX("right_arm", -0.7f * k * (0.5f + 0.5f * sin(s.ageInTicks * 1.3f)));
            m.rotX("left_arm", -0.6f * k * (0.5f + 0.5f * cos(s.ageInTicks * 1.1f)));
        }
    }

    // ------------------------------------------------------------------ the common dead (Errante)
    public static final HellModel.Anim HOLLOW = human((m, s) -> {
        common(m, s, 0.2f + 0.1f * s.seed);
        blow(m, s, 0.68f);
    });

    // ------------------------------------------------------------------ Stalker: low prowl, raised shoulder before the slash
    public static final HellModel.Anim STALKER = human((m, s) -> {
        float run = run(s);
        lean(m, 0.45f + 0.2f * run);
        head(m, s, 0.7f);
        m.rotX("head", -0.35f);
        legs(m, s, run, 0.9f);
        float wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        m.rotX("right_arm", -0.55f + cos(wp * 0.6662f) * 0.5f * ws);
        m.rotX("left_arm", -0.5f - cos(wp * 0.6662f) * 0.5f * ws);
        m.rotZ("right_arm", 0.12f);
        m.rotZ("left_arm", -0.12f);
        m.rotX("jaw", 0.15f + Math.abs(sin(s.ageInTicks * 0.2f)) * 0.2f);
        // The signal: the hunched shoulder lifts and the claws scrape before the slash.
        float sig = bump(a(s, LimboDead.ANIM_SPECIAL));
        m.rotZ("hump", -0.35f * sig);
        m.rotX("right_arm", -0.6f * sig);
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float up = windup(p, 0.6f), hit = strike(p, 0.6f);
            m.rotX("right_arm", -2.5f * up + 1.2f * hit);
            m.rotZ("right_arm", -0.5f * up);
            m.rotZ("hump", -0.4f * up);
            m.rotX("jaw", 0.5f * up);
            lean(m, 0.25f * hit);
        }
    });

    // ------------------------------------------------------------------ Crier: throat swells through the inhale, then the scream
    public static final HellModel.Anim CRIER = human((m, s) -> {
        common(m, s, 0.12f);
        blow(m, s, 0.65f);
        float t = s.ageInTicks;
        float breathe = 1f + sin(t * 0.09f) * 0.06f;
        m.scale("throat", breathe, breathe, breathe);
        float p = a(s, LimboDead.ANIM_CALL);
        if (p >= 0) {
            float inh = Math.min(1f, p / 0.85f);
            float sz = 1f + 0.8f * inh;
            m.scale("throat", sz, 1f + 0.5f * inh, sz);
            m.rotX("head", -0.55f * inh);
            lean(m, -0.15f * inh);
            if (p > 0.85f) {
                m.rotX("head", 0.9f);
                m.rotZ("right_arm", 0.8f);
                m.rotZ("left_arm", -0.8f);
                lean(m, 0.3f);
            }
        }
    });

    // ------------------------------------------------------------------ Foreman: upright, the arm that points the way
    public static final HellModel.Anim FOREMAN = human((m, s) -> {
        common(m, s, 0.12f);
        float wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        m.rotX("coat_tail", 0.25f + Math.abs(cos(wp * 0.6662f)) * 0.35f * ws);
        float sig = a(s, LimboDead.ANIM_SPECIAL);
        if (sig >= 0) {
            float k = bump(Math.min(1f, sig * 1.6f));
            float hold = sig > 0.35f ? 1f : sig / 0.35f;
            m.rotX("right_arm", -1.55f * hold);
            m.rotY("right_arm", 0.1f * hold);
            m.rotZ("left_arm", -1.2f * k);
            m.rotX("left_arm", -0.4f * k);
            m.rotX("head", -0.2f * hold);
        }
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float up = windup(p, 0.7f), hit = strike(p, 0.7f);
            m.rotX("right_arm", -2.7f * up - 0.5f * hit);
            m.rotZ("right_arm", 0.25f * up);
            lean(m, -0.2f * up + 0.35f * hit);
        }
    });

    // ------------------------------------------------------------------ Lanky: long strides; a long arm raised high before it comes down
    public static final HellModel.Anim LANKY = human((m, s) -> {
        float run = run(s);
        lean(m, 0.12f + 0.15f * run);
        head(m, s, 0.5f);
        m.rotX("head", 0.2f);
        legs(m, s, run, 0.65f);
        arms(m, s, run * 0.6f);
        float t = s.ageInTicks;
        m.rotZ("right_arm", 0.05f + sin(t * 0.05f) * 0.04f);
        m.rotZ("left_arm", -0.05f - sin(t * 0.05f + 1) * 0.04f);
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float up = windup(p, 0.72f), hit = strike(p, 0.72f);
            m.rotX("right_arm", -3.0f * up + 1.6f * hit);
            m.rotX("right_forearm", -0.6f * up + 0.4f * hit);
            m.rotZ("right_arm", -0.15f * up);
            lean(m, -0.12f * up + 0.35f * hit);
        }
        float sw = a(s, LimboDead.ANIM_SPECIAL); // the sweep: arm out to the side, then across the front
        if (sw >= 0) {
            float up = windup(sw, 0.65f), hit = strike(sw, 0.65f);
            m.rotZ("left_arm", -1.4f * up);
            m.rotX("left_arm", -1.4f * hit - 0.3f * up);
            m.rotY("left_arm", 1.2f * hit);
            m.rotY("body", -0.3f * up + 0.5f * hit);
        }
    });

    // ------------------------------------------------------------------ Bloater: a waddle, the belly heaving, spasms
    public static final HellModel.Anim BLOATER = human((m, s) -> {
        common(m, s, -0.05f);
        blow(m, s, 0.7f);
        float t = s.ageInTicks, wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        m.rotZ("body", sin(wp * 0.6662f) * 0.08f * ws);
        m.rotZ("right_arm", 0.25f);
        m.rotZ("left_arm", -0.25f);
        float heave = 1f + 0.05f * sin(t * 0.12f) + 0.25f * s.swell;
        m.scale("belly", heave, heave, 1f + (heave - 1) * 1.6f);
        if (sin(t * 0.21f + s.seed * 30) > 0.93f) { m.rotZ("head", 0.4f * sin(t * 3f)); m.rotX("body", 0.08f * sin(t * 4f)); }
        float sp = a(s, LimboDead.ANIM_SPECIAL);
        if (sp >= 0) {
            float k = bump(sp);
            m.scale("belly", heave + 0.3f * k, heave + 0.3f * k, heave + 0.5f * k);
            m.rotX("head", 0.4f * k * sin(t * 2.5f));
        }
    });

    /** The Putrescent's corpse: lying on its back, belly swelling as the fuse burns, spasms growing more violent. */
    public static final HellModel.Anim BLOATER_CORPSE = (m, s) -> {
        float t = s.ageInTicks, f = s.swell;
        float sw = 1.1f + 0.7f * f + 0.06f * sin(t * (0.4f + f));
        m.scale("belly", sw, sw, sw * 1.4f);
        float spasm = f > 0.15f ? sin(t * (0.8f + 2.2f * f)) * (0.1f + 0.35f * f) : 0;
        m.rotX("right_arm", -0.3f + spasm);
        m.rotX("left_arm", -0.2f - spasm);
        m.rotZ("right_arm", 1.2f);
        m.rotZ("left_arm", -1.2f);
        m.rotX("right_leg", 0.15f - spasm * 0.5f);
        m.rotX("left_leg", -0.1f + spasm * 0.5f);
        m.rotZ("head", 0.5f + spasm);
        for (String tumor : new String[]{"tumor0", "tumor1", "tumor2"}) {
            float k = 1f + 0.5f * f;
            m.scale(tumor, k, k, k);
        }
    };

    // ------------------------------------------------------------------ Breaker: heavy, arms low; both fists raised overhead, then the slam
    public static final HellModel.Anim BRUTE = human((m, s) -> {
        float run = run(s);
        lean(m, 0.22f + 0.1f * run);
        head(m, s, 0.35f);
        legs(m, s, run * 0.5f, 0.55f);
        float t = s.ageInTicks, wp = s.walkAnimationPos, ws = Math.min(1f, s.walkAnimationSpeed);
        m.rotZ("body", sin(wp * 0.6662f) * 0.07f * ws);
        m.rotX("right_arm", -0.3f + cos(wp * 0.6662f) * 0.35f * ws);
        m.rotX("left_arm", -0.25f - cos(wp * 0.6662f) * 0.35f * ws);
        m.rotZ("right_arm", 0.12f);
        m.rotZ("left_arm", -0.12f);
        m.rotZ("hump", sin(t * 0.06f) * 0.03f);
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float up = windup(p, 0.75f), hit = strike(p, 0.75f);
            m.rotX("right_arm", -2.9f * up + 0.6f * hit);
            m.rotX("left_arm", -2.9f * up + 0.6f * hit);
            m.rotZ("right_arm", -0.45f * up);
            m.rotZ("left_arm", 0.45f * up);
            lean(m, -0.3f * up + 0.6f * hit);
            m.rotX("head", -0.3f * up);
        }
        float sg = a(s, LimboDead.ANIM_SIEGE);
        if (sg >= 0) { float k = sin(sg * Mth.PI); m.rotX("right_arm", -2.4f * k); m.rotX("left_arm", -2.4f * k); lean(m, 0.3f * k); }
        float st = a(s, LimboDead.ANIM_STAGGER); // the recovery after a blow: hunched, heaving
        if (st >= 0) { lean(m, 0.25f * (1 - st)); m.rotX("head", 0.3f * (1 - st)); }
    });

    // ------------------------------------------------------------------ Runt: crouched; a pause, then the leap
    public static final HellModel.Anim RUNT = human((m, s) -> {
        common(m, s, 0.5f);
        blow(m, s, 0.6f);
        float sp = a(s, LimboDead.ANIM_SPECIAL);
        if (sp >= 0) {
            float k = bump(sp);
            lean(m, 0.4f * k);
            m.rotX("right_arm", 0.7f * k);
            m.rotX("left_arm", 0.7f * k);
            m.rotX("head", -0.4f * k);
        }
    });

    // ------------------------------------------------------------------ Runaway: digitigrade legs; compression before the run, heaving after
    public static final HellModel.Anim RUNAWAY = human((m, s) -> {
        float run = run(s);
        lean(m, 0.25f + 0.3f * run);
        head(m, s, 0.5f);
        legs(m, s, run, 1.05f);
        float wp = s.walkAnimationPos, ws = Math.min(1.2f, s.walkAnimationSpeed), t = s.ageInTicks;
        m.rotX("right_arm", 0.35f * run + cos(wp * 0.6662f) * 0.9f * ws);
        m.rotX("left_arm", 0.35f * run - cos(wp * 0.6662f) * 0.9f * ws);
        float prep = bump(a(s, LimboDead.ANIM_SPECIAL));
        lean(m, 0.35f * prep);
        m.rotX("right_leg", -0.5f * prep);
        m.rotX("left_leg", -0.5f * prep);
        m.rotX("right_shin", 0.5f * prep);
        m.rotX("left_shin", 0.5f * prep);
        m.rotX("right_arm", 0.8f * prep);
        m.rotX("left_arm", 0.8f * prep);
        float rec = a(s, LimboDead.ANIM_STAGGER) >= 0 ? 1 : 0;
        lean(m, (0.3f + sin(t * 0.9f) * 0.1f) * rec);
        m.rotX("head", 0.35f * rec);
        m.rotX("right_arm", 0.3f * rec);
        m.rotX("left_arm", 0.3f * rec);
        blow(m, s, 0.6f);
    });

    // ------------------------------------------------------------------ Strangler: the jaw hangs loose; the throat bulges and the jaw drops wide before the tongue
    public static final HellModel.Anim STRANGLER = human((m, s) -> {
        common(m, s, 0.18f);
        blow(m, s, 0.66f);
        float t = s.ageInTicks;
        m.rotX("jaw", sin(t * 0.13f + s.seed * 9) * 0.08f);
        float br = 1f + sin(t * 0.1f) * 0.08f;
        m.scale("throat", br, br, br);
        m.rotX("tongue", sin(t * 0.09f) * 0.18f);
        m.rotZ("tongue", sin(t * 0.07f + 1) * 0.12f);
        float p = a(s, LimboDead.ANIM_SPECIAL);
        if (p >= 0) {
            float k = Math.min(1f, p * 2.5f);
            float sz = 1f + 0.7f * k + 0.1f * sin(t * 1.3f);
            m.scale("throat", sz, 1f + 0.4f * k, sz);
            m.rotX("jaw", 0.6f * k);
            m.rotX("head", -0.3f * k);
            lean(m, -0.12f * k);
            m.rotX("right_arm", -0.4f * k);
            m.rotX("left_arm", -0.3f * k);
        }
        float hold = a(s, LimboDead.ANIM_CALL);
        if (hold >= 0 || s.special == 3) {
            // Hauling in: leaning back on braced legs, the jaw wide, the throat working.
            lean(m, -0.28f);
            m.rotX("jaw", 0.65f);
            m.rotX("right_arm", -0.7f + sin(t * 0.8f) * 0.15f);
            m.rotX("left_arm", -0.6f - sin(t * 0.8f) * 0.15f);
            m.rotX("right_leg", -0.35f);
            m.rotX("left_leg", 0.3f);
            m.scale("throat", 1.3f + 0.15f * sin(t * 0.9f), 1.2f, 1.3f);
        }
        // The tongue that hangs from the jaw is the one the renderer throws: hidden while it is out.
        m.show("tongue", s.special < 2);
    });

    // ------------------------------------------------------------------ Bile Spitter: the chest doors fly open, the cavity heaves, then the spit
    public static final HellModel.Anim SPITTER = human((m, s) -> {
        common(m, s, 0.0f);
        blow(m, s, 0.68f);
        float t = s.ageInTicks;
        float gut = 1f + 0.05f * sin(t * 0.11f);
        m.scale("gut", gut, gut, gut);
        float open = 0.08f + 0.05f * sin(t * 0.07f);
        float p = a(s, LimboDead.ANIM_SPECIAL);
        if (p >= 0) {
            float o = Math.min(1f, p * 1.6f), rel = p > 0.75f ? (p - 0.75f) / 0.25f : 0;
            open += 1.25f * o * (1 - 0.4f * rel);
            lean(m, -0.25f * o + 0.5f * rel);
            m.rotX("head", -0.45f * o + 0.7f * rel);
            m.rotZ("right_arm", 0.6f * o);
            m.rotZ("left_arm", -0.6f * o);
            m.rotX("right_arm", -0.4f * o);
            m.rotX("left_arm", -0.4f * o);
            float heave = 1f + 0.35f * o * Math.abs(sin(t * 1.6f));
            m.scale("cavity", heave, heave, 1f + 1.2f * o);
            m.scale("gut", gut + 0.2f * o, gut + 0.1f * o, gut + 0.25f * o);
        }
        m.rotY("right_door", open);
        m.rotY("left_door", -open);
    });

    // ------------------------------------------------------------------ Rib Thrower: it reaches into its chest, cracks a rib free, draws back, throws
    public static final HellModel.Anim RIBS = human((m, s) -> {
        common(m, s, 0.22f);
        blow(m, s, 0.66f);
        float p = a(s, LimboDead.ANIM_SPECIAL);
        m.show("held_rib", p >= 0.45f);
        if (p >= 0) {
            if (p < 0.45f) {
                float k = p / 0.45f;
                m.rotX("right_arm", -1.25f * k);
                m.rotY("right_arm", 0.85f * k);
                m.rotZ("right_arm", -0.25f * k);
                lean(m, 0.18f * k);
                m.rotX("head", 0.35f * k);
                m.rotX("ribcage", 0.12f * k * sin(s.ageInTicks * 2.2f));
            } else {
                float k = Math.min(1f, (p - 0.45f) / 0.3f), thr = p > 0.75f ? Math.min(1f, (p - 0.75f) / 0.15f) : 0;
                m.rotX("right_arm", Mth.lerp(k, -1.25f, -2.9f) + 3.3f * thr);
                m.rotZ("right_arm", 0.2f * k);
                lean(m, -0.2f * k + 0.5f * thr);
                m.rotY("body", -0.3f * k + 0.55f * thr);
                m.rotX("head", -0.2f * k);
            }
        }
    });

    // ------------------------------------------------------------------ Crawler: prone, pulling itself on its arms, legs dragging
    public static final HellModel.Anim CRAWLER = (m, s) -> {
        float hipY = 21.5f, hipZ = 5f, ang = 1.42f;
        ModelPart body = m.p("body");
        if (body != null) {
            body.xRot = ang;
            body.y = hipY - 12 * cos(ang);
            body.z = hipZ - 12 * sin(ang);
        }
        float ny = hipY - 12 * cos(ang), nz = hipZ - 12 * sin(ang);
        m.move("head", 0, ny, nz);
        m.look("head", s, 0.5f);
        m.rotX("head", -1.0f);
        m.rotZ("head", (s.seed - 0.5f) * 0.5f);
        float wp = s.walkAnimationPos * 0.9f, ws = Math.min(1f, s.walkAnimationSpeed);
        float sy = ny + 2 * cos(ang), sz = nz + 2 * sin(ang);
        m.move("right_arm", -5, sy, sz);
        m.move("left_arm", 5, sy, sz);
        m.rotX("right_arm", -2.5f + sin(wp) * 0.55f * ws);
        m.rotX("left_arm", -2.5f - sin(wp) * 0.55f * ws);
        m.rotZ("right_arm", 0.2f);
        m.rotZ("left_arm", -0.2f);
        m.move("right_leg", -1.9f, hipY, hipZ);
        m.move("left_leg", 1.9f, hipY, hipZ);
        m.rotX("right_leg", 1.5f + sin(wp * 0.5f) * 0.05f);
        m.rotX("left_leg", 1.45f - sin(wp * 0.5f) * 0.05f);
        m.rotZ("right_leg", 0.18f);
        m.rotZ("left_leg", -0.3f);
        // The pounce is announced: arms drawn under the body, head low, then it throws itself forward.
        float p = a(s, LimboDead.ANIM_ATTACK);
        if (p >= 0) {
            float up = windup(p, 0.7f);
            m.rotX("right_arm", 1.2f * up);
            m.rotX("left_arm", 1.2f * up);
            m.rotX("head", 0.4f * up);
        }
        float sp = bump(a(s, LimboDead.ANIM_SPECIAL)); // the scrape
        m.rotX("right_arm", 0.5f * sp * sin(s.ageInTicks * 1.5f));
    };

    // ------------------------------------------------------------------ Sundered: the halves part a little as it walks, wide open before the sweep
    public static final HellModel.Anim SUNDERED = (m, s) -> {
        float t = s.ageInTicks, run = run(s);
        legs(m, s, run, 0.8f);
        float drift = 0.04f + 0.08f * s.walkAnimationSpeed;
        m.rotZ("right_half", -drift - sin(t * 0.06f) * 0.03f);
        m.rotZ("left_half", drift + sin(t * 0.06f + 1f) * 0.03f);
        m.rotX("right_half", 0.12f);
        m.rotX("left_half", 0.12f);
        m.rotY("right_half", s.yRot * HellModel.DEG * 0.3f);
        m.rotY("left_half", s.yRot * HellModel.DEG * 0.3f);
        arms(m, s, run * 0.7f);
        float open = windup(a(s, LimboDead.ANIM_ATTACK), 0.75f);
        m.rotZ("right_half", -0.65f * open);
        m.rotZ("left_half", 0.65f * open);
        m.rotX("right_arm", -1.3f * open);
        m.rotX("left_arm", -1.3f * open);
        float rec = a(s, LimboDead.ANIM_STAGGER);
        if (rec >= 0) {
            m.moveY("right_half", sin(t * 0.8f) * 1.2f * (1 - rec));
            m.rotZ("left_half", sin(t * 1.1f) * 0.12f * (1 - rec));
        }
        float r = bump(a(s, LimboDead.ANIM_RECOGNIZE));
        m.rotX("right_half", -0.15f * r);
        m.rotX("left_half", -0.15f * r);
    };
}
