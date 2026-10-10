package ryuna.qdeath.jjk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import ryuna.qdeath.Quantumdeath;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every tunable number of the cursed-technique classes. Durations are in ticks (20 per second), distances in blocks,
 * damage in half-hearts. The values are the defaults; {@code config/quantumdeath-jjk.json} (written on first launch,
 * one entry per field, e.g. {@code "Choso.SLICING_DAMAGE": 6.0}) overrides them on the server. Gameplay always uses the
 * server's values; the client only shows them in tooltips.
 */
public final class JjkBalance {
    private JjkBalance() {}

    public static final class Progression {
        public static int MAX_LEVEL = 30;
        /** XP needed to go from level L to L+1: BASE + LINEAR*L + QUAD*L^2. */
        public static int XP_BASE = 60, XP_LINEAR = 25, XP_QUAD = 3;
        public static int TP_PER_LEVEL = 2;
        /** Extra TP when reaching a tier level (5, 10, 15, 20). */
        public static int TP_MILESTONE_BONUS = 1;
        /** Class level that unlocks tiers I..V (Mastery). */
        public static int TIER_2_LEVEL = 5, TIER_3_LEVEL = 10, TIER_4_LEVEL = 15, TIER_5_LEVEL = 20;
        public static int TP_COST_TIER_1 = 1, TP_COST_TIER_2 = 2, TP_COST_TIER_3 = 3, TP_COST_TIER_4 = 4, TP_COST_MAXIMUM = 5, TP_COST_MASTERY = 6;
        /** XP per point of damage dealt by a cursed technique / by an ordinary melee hit, against valid opponents. */
        public static float XP_PER_TECHNIQUE_DAMAGE = 1.0f, XP_PER_MELEE_DAMAGE = 0.4f;
        /** XP for a kill, as a fraction of the victim's max health. */
        public static float XP_KILL_FRACTION = 0.5f;
        /** Anti-farming: at most this much class XP per minute from any source. */
        public static int XP_CAP_PER_MINUTE = 600;
        /** Skill-expression XP (chained Projection, combat swaps, immobilizing). */
        public static int XP_SKILL_EVENT = 4;
    }

    public static final class Energy {
        public static float BASE_MAX = 100, MAX_PER_LEVEL = 4;
        /** Cursed energy regenerated per tick, and the multiplier while in combat (hurt or hitting in the last 5 s). */
        public static float REGEN = 0.30f, REGEN_PER_LEVEL = 0.01f, COMBAT_REGEN_MULT = 0.6f;
        /** Fraction of energy/blood restored on respawn. */
        public static float RESPAWN_FILL = 0.5f;
        public static int COMBAT_WINDOW = 100;
    }

    public static final class Choso {
        public static float BLOOD_MAX = 100;
        /** Blood regenerated per tick while fed (food >= 6); hunger exhaustion paid per blood point. */
        public static float BLOOD_REGEN = 0.025f, BLOOD_REGEN_EXHAUSTION = 0.04f;
        /** Health lost per blood point spent beyond what you have (a cast that would kill you is refused). */
        public static float BLOOD_DEFICIT_HEALTH = 0.25f;
        public static float ANEMIA_THRESHOLD = 0.35f, SEVERE_THRESHOLD = 0.15f;
        public static float BLOOD_CONTROL_COST_MULT = 0.75f, BLOOD_CONTROL_REGEN_MULT = 1.4f;
        public static float MASTERY_REFUND_ON_HIT = 0.25f;

        public static float DRAW_HEALTH = 6, DRAW_BLOOD = 35; public static int DRAW_COOLDOWN = 120;
        public static float SLICING_DAMAGE = 6, SLICING_SPEED = 2.6f, SLICING_BLOOD = 10, SLICING_CONVERGED_MULT = 1.6f;
        public static int SLICING_PIERCE = 1, SLICING_COOLDOWN = 14;
        public static int CONVERGENCE_CHARGE_TICKS = 30, CONVERGENCE_HOLD_TICKS = 120, CONVERGENCE_MASTERY_HOLD_TICKS = 200;
        public static float CONVERGENCE_BLOOD_PER_TICK = 0.6f, CONVERGENCE_BLOOD_CONTROL_SPEED = 1.25f, CONVERGENCE_MASTERY_SPEED = 1.9f;
        public static float PIERCING_DAMAGE = 10, PIERCING_DAMAGE_PER_CHARGE = 10, PIERCING_RANGE = 48, PIERCING_BLOOD = 22, PIERCING_MIN_CHARGE = 0.5f;
        public static int PIERCING_PIERCE = 3, PIERCING_COOLDOWN = 60;
        public static float SUPERNOVA_BLOOD = 25, SUPERNOVA_ORB_DAMAGE = 6, SUPERNOVA_ORB_RADIUS = 2.2f, SUPERNOVA_RANGE = 7, SUPERNOVA_MIN_CHARGE = 0.3f;
        public static int SUPERNOVA_ORBS = 6, SUPERNOVA_ORBS_PER_CHARGE = 4, SUPERNOVA_MASTERY_ORBS = 3, SUPERNOVA_COOLDOWN = 120;
        public static float FRS_ACTIVATE_BLOOD = 5, FRS_BLOOD_PER_SECOND = 1.0f, FRS_SPEED = 0.20f, FRS_ATTACK_SPEED = 0.15f, FRS_DAMAGE = 2, FRS_JUMP = 0.08f;
        public static float STACK_ACTIVATE_BLOOD = 10, STACK_BLOOD_PER_SECOND = 3.0f, STACK_SPEED = 0.40f, STACK_ATTACK_SPEED = 0.35f, STACK_DAMAGE = 5,
                STACK_JUMP = 0.15f, STACK_KNOCKBACK_RESIST = 0.4f;
        public static int STACK_CRASH_TICKS = 100, FRS_TOGGLE_COOLDOWN = 20;
        public static float MASTERY_FRS_COST_MULT = 0.6f;
    }

    public static final class Uraume {
        public static float FORMATION_DAMAGE = 4, FORMATION_FREEZE = 25, FORMATION_RANGE = 14, FORMATION_ENERGY = 10;
        public static int FORMATION_COOLDOWN = 24;
        public static float SHARD_DAMAGE = 5, SHARD_FREEZE = 18, SHARD_SPEED = 2.2f, SHARD_ENERGY = 6;
        public static int SHARD_COOLDOWN = 10;
        public static float TOUCH_DAMAGE = 3, TOUCH_FREEZE = 45, TOUCH_RANGE = 4.5f, TOUCH_ENERGY = 12;
        public static int TOUCH_COOLDOWN = 40;
        public static float ICEFALL_DAMAGE = 9, ICEFALL_FREEZE = 45, ICEFALL_RADIUS = 3.5f, ICEFALL_RANGE = 28, ICEFALL_ENERGY = 25;
        public static float ENHANCED_DAMAGE = 13, ENHANCED_FREEZE = 70, ENHANCED_RADIUS = 5.5f;
        public static int ICEFALL_TELEGRAPH = 12, ICEFALL_COOLDOWN = 160;
        public static int WALL_WIDTH = 7, WALL_HEIGHT = 4, WALL_DURATION = 200, WALL_COOLDOWN = 200;
        public static float WALL_RANGE = 14, WALL_ENERGY = 20, WALL_SHATTER_DAMAGE = 4, WALL_SHATTER_FREEZE = 20;
        public static float CALM_RANGE = 11, CALM_HALF_ANGLE = 35, CALM_FREEZE_PER_PULSE = 16, CALM_DAMAGE_PER_PULSE = 1, CALM_ENERGY = 40;
        public static int CALM_DURATION = 40, CALM_PULSE = 4, CALM_COOLDOWN = 400;
        public static float MASTERY_FREEZE_MULT = 1.3f, MASTERY_DURATION_MULT = 1.6f, MASTERY_CALM_RANGE = 4, MASTERY_CALM_ANGLE = 10;
    }

    public static final class Freeze {
        /** Buildup thresholds for Freeze I, II, III and Immobilized. */
        public static float STAGE_1 = 25, STAGE_2 = 50, STAGE_3 = 75, IMMOBILIZE = 100;
        /** Buildup lost per tick once no ice has touched the target for DECAY_DELAY ticks. */
        public static float DECAY = 0.6f; public static int DECAY_DELAY = 40;
        public static int IMMOBILIZE_TICKS = 50, MASTERY_EXTRA_TICKS = 20;
        /** After an immobilization: buildup drops to this and gains are halved for RESIST_TICKS (no PvP stun-lock). */
        public static float AFTER_IMMOBILIZE = 40; public static int RESIST_TICKS = 120;
        /** Extra damage taken per stage (I, II, III, immobilized). */
        public static float VULN_1 = 0.05f, VULN_2 = 0.10f, VULN_3 = 0.20f, VULN_IMMOBILIZED = 0.30f;
    }

    public static final class Jogo {
        public static float FLAME_ENERGY_PER_TICK = 0.9f, FLAME_RANGE = 6, FLAME_DAMAGE = 1.5f, FLAME_HALF_ANGLE = 28;
        public static int FLAME_PULSE = 6, FLAME_COOLDOWN = 20;
        public static float BLAST_DAMAGE = 7, BLAST_SPLASH = 3, BLAST_SPEED = 1.9f, BLAST_ENERGY = 8;
        public static int BLAST_COOLDOWN = 12;
        public static float BURST_DAMAGE = 12, BURST_RADIUS = 4.5f, BURST_RANGE = 12, BURST_ENERGY = 20;
        public static int BURST_COOLDOWN = 60;
        public static float INSECT_DAMAGE = 4, INSECT_RADIUS = 2, INSECT_SEEK_RANGE = 20, INSECT_ENERGY = 25;
        public static int INSECTS = 4, MASTERY_INSECTS = 6, INSECT_LIFE = 200, INSECT_COOLDOWN = 200, MAX_INSECTS = 12;
        public static float VOLCANO_RANGE = 16, VOLCANO_ENERGY = 30, VOLCANO_HEALTH = 30, VOLCANO_ATTACK_RANGE = 12, VOLCANO_CONTROL_RANGE = 18,
                BOMB_DAMAGE = 5, BOMB_RADIUS = 2;
        public static int VOLCANO_LIFE = 400, MASTERY_VOLCANO_LIFE = 600, VOLCANO_FIRE_INTERVAL = 30, VOLCANO_COOLDOWN = 100,
                MAX_VOLCANOES = 1, CONTROL_MAX_VOLCANOES = 3, SALVO_COOLDOWN = 80, MASTERY_VOLCANO_INSECT_INTERVAL = 120;
        public static float ERUPTION_DAMAGE = 14, ERUPTION_RADIUS = 5, ERUPTION_RANGE = 20, ERUPTION_ENERGY = 45, ERUPTION_VOLCANO_WAVE_DAMAGE = 6;
        public static int ERUPTION_TELEGRAPH = 20, ERUPTION_COOLDOWN = 240;
        public static float METEOR_MIN_ENERGY_FRACTION = 0.9f, METEOR_RANGE = 64, METEOR_RADIUS = 13, METEOR_DAMAGE_CENTER = 45, METEOR_DAMAGE_EDGE = 15,
                METEOR_SCALE = 9, METEOR_BLOCK_POWER = 6;
        public static int METEOR_FALL_TICKS = 70, METEOR_COOLDOWN = 6000;
        /** Whether Maximum: Meteor carves a crater. Only ever with the mobGriefing gamerule on as well. */
        public static boolean METEOR_BREAKS_BLOCKS = true;
        public static float MASTERY_BURST_COST_MULT = 0.7f, MASTERY_REACT_RANGE = 6;
    }

    public static final class Naoya {
        /** Blocks moved per tick at x1 momentum; a projection is 24 frames played over PROJECTION_TICKS ticks. */
        public static float BASE_SPEED = 0.55f, SPEED_INCREASE_MULT = 1.35f, MAX_SPEED = 2.0f;
        public static int PROJECTION_TICKS = 12, FRAMES = 24;
        public static float PROJECTION_ENERGY = 6, CHAIN_ENERGY = 4, CHAIN_REFUND = 2, MASTERY_COST_MULT = 0.75f;
        /** Ticks after a projection during which the next press chains; presses in the last BUFFER frames are buffered. */
        public static int CHAIN_WINDOW = 8, MASTERY_CHAIN_WINDOW = 12, BUFFER_FRAMES = 6, MASTERY_BUFFER_FRAMES = 10;
        public static float MAX_TURN = 50, MASTERY_TURN_BONUS = 25, MACH_TURN = 40;
        /** Momentum multipliers by chain count (Speed Stacking); without Mach the multiplier stops at STACK_CAP. */
        public static float[] MOMENTUM = {1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f};
        public static int STACK_CAP = 4, MACH_CAP = 6, MACH_FROM = 4;
        /** The 24 FPS penalty: frozen for one second. */
        public static int VIOLATION_FREEZE = 20;
        public static float MOMENTUM_ATTACK_PER_STACK = 0.30f, MOMENTUM_ATTACK_PER_SPEED = 0.25f, MOMENTUM_ATTACK_MIN_SPEED = 0.25f;
        public static float MACH_BOOM_DAMAGE = 4, MACH_BOOM_RADIUS = 2.2f;
        public static float FRAME_TOUCH_ENERGY = 8, FRAME_MARK_BONUS = 0.15f, FRAME_SHATTER_BONUS = 0.5f, FRAME_TURN = 30, FRAME_SPEED_CHANGE = 0.15f;
        public static int FRAME_WINDOW = 24, EXTENDED_FRAME_WINDOW = 36, FRAME_TOUCH_COOLDOWN = 60, FRAME_FREEZE_TICKS = 20, EXTENDED_FREEZE_TICKS = 35,
                FRAME_IMMUNITY = 60, FRAME_DIMINISH_WINDOW = 200;
    }

    public static final class Todo {
        public static float RANGE = 24, MASTERY_RANGE = 36, AIM_CONE = 4, MASTERY_AIM_CONE = 8, SWAP_ENERGY = 5, FAKE_CLAP_ENERGY = 2;
        public static int SWAP_DELAY = 4, RAPID_SWAP_DELAY = 2, MASTERY_SWAP_DELAY = 1;
        public static int SWAP_COOLDOWN = 30, RAPID_SWAP_COOLDOWN = 10, CLAP_COOLDOWN = 6;
        public static int DOUBLE_WINDOW = 30, RAPID_DOUBLE_WINDOW = 45, MASTERY_EXTRA_FREE_SWAPS = 1;
        /** Hard server limit: never more than RATE_LIMIT swaps in RATE_WINDOW ticks. */
        public static int RATE_LIMIT = 5, RATE_WINDOW = 40;
        public static int MARK_TICKS = 120, OFF_BALANCE_TICKS = 12, BOOGIE_STRIKE_TICKS = 20;
        public static float BOOGIE_STRIKE_BONUS = 0.2f;
    }

    // ------------------------------------------------------------------------------------------------------- config

    private static final Class<?>[] SECTIONS = {Progression.class, Energy.class, Choso.class, Uraume.class, Freeze.class, Jogo.class, Naoya.class, Todo.class};

    /** Reads (or creates) the config file. Unknown or malformed entries are ignored, missing ones are added. */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("quantumdeath-jjk.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject file = new JsonObject();
        try {
            if (Files.exists(path)) file = gson.fromJson(Files.readString(path), JsonObject.class);
        } catch (Exception e) {
            Quantumdeath.LOGGER.warn("Ignoring unreadable {}: {}", path, e.getMessage());
            file = new JsonObject();
        }
        if (file == null) file = new JsonObject();
        JsonObject out = new JsonObject();
        for (Class<?> section : SECTIONS) {
            for (Field f : section.getFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                String key = section.getSimpleName() + "." + f.getName();
                try {
                    JsonElement v = file.get(key);
                    if (v != null) f.set(null, gson.fromJson(v, f.getType()));
                    out.add(key, gson.toJsonTree(f.get(null)));
                } catch (Exception e) {
                    Quantumdeath.LOGGER.warn("Bad value for {} in {}, keeping default", key, path);
                }
            }
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(out));
        } catch (IOException e) {
            Quantumdeath.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
        }
    }

    public static int tierLevel(int tier) {
        return switch (tier) {
            case 2 -> Progression.TIER_2_LEVEL;
            case 3 -> Progression.TIER_3_LEVEL;
            case 4 -> Progression.TIER_4_LEVEL;
            case 5 -> Progression.TIER_5_LEVEL;
            default -> 1;
        };
    }

    public static int tierCost(int tier) {
        return switch (tier) {
            case 0 -> 0;
            case 2 -> Progression.TP_COST_TIER_2;
            case 3 -> Progression.TP_COST_TIER_3;
            case 4 -> Progression.TP_COST_TIER_4;
            case 5 -> Progression.TP_COST_MASTERY;
            default -> Progression.TP_COST_TIER_1;
        };
    }

    public static int xpToNext(int level) {
        return Progression.XP_BASE + Progression.XP_LINEAR * level + Progression.XP_QUAD * level * level;
    }

    public static float maxEnergy(int level) { return Energy.BASE_MAX + Energy.MAX_PER_LEVEL * level; }
}
