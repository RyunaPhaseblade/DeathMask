package ryuna.qdeath.yokai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import ryuna.qdeath.Quantumdeath;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every tunable number of the yokai expansion (docs/yokai-shikigamis-percepcion.md). Damage is in health points before
 * armor, times in ticks (20 per second), distances in blocks. {@code config/quantumdeath-yokai.json} (written on first
 * launch, one entry per field, e.g. {@code "Oni.SLAM_DAMAGE": 8.0}) overrides them on the server.
 */
public final class YokaiBalance {
    private YokaiBalance() {}

    public static final class World {
        /** Grace after the awakening, counted only while someone is in the Overworld: signs and harmless encounters. */
        public static int GRACE_TICKS = 20 * 60 * 10;
    }

    public static final class Perception {
        /** Partial perception: chance that an ordinary hostile starts a pursuit, rolled once per encounter. */
        public static float PARTIAL_PURSUIT_CHANCE = 0.10f;
        /** An encounter ends after this long without detecting the player. */
        public static int ENCOUNTER_TIMEOUT = 20 * 30;
        /** Range at which a creature notices a player (and at which a clear sighting identifies it in the bestiary). */
        public static double DETECT_RANGE = 16, IDENTIFY_RANGE = 24;
        /** Retreat node: speed bonus, duration and cooldown after a yokai hurts you. */
        public static float RETREAT_SPEED = 0.10f;
        public static int RETREAT_TICKS = 80, RETREAT_COOLDOWN = 400;
    }

    public static final class Director {
        public static int INTERVAL = 20 * 90;
        public static float CHANCE = 0.25f;
        public static double MIN_DISTANCE = 24, MAX_DISTANCE = 48, GROUP_RADIUS = 64;
        public static int MAX_WILD = 4;
        public static double BED_SAFE_RADIUS = 16;
        public static int RESPAWN_SAFE_TICKS = 20 * 60;
        /** Half-angle of the "immediate field of view" a spawn must stay out of. */
        public static double VIEW_HALF_ANGLE = 70;
        /** Chance that a grace-period attempt without a creature plays a distant sign instead. */
        public static float SIGN_CHANCE = 0.5f;
        /** Unbonded chest and bell spirits fall asleep (back into their block) after this long without a player within 32 blocks. */
        public static int OBJECT_SLEEP_TICKS = 20 * 120;
        /** Relative weight of each species when several fit a spot, in this order: false creeper, bifauce, damper, swarm eye,
         *  kappa, oni, chest, tanuki, tengu, kodama, nurikabe, ittan-momen, bell. */
        public static int[] WEIGHTS = {10, 10, 10, 8, 12, 8, 6, 10, 8, 10, 8, 10, 5};
    }

    public static final class Skills {
        public static int LEGACY_COST = 1, TIER_1_COST = 1, TIER_2_COST = 2, TIER_3_COST = 3;
    }

    public static final class FalseCreeper {
        public static int FUSE = 60;
        public static float DAMAGE = 6, RADIUS = 3;
        /** Chance per second that an unseen false creeper detonates where it drifts (no pursuit). */
        public static float AMBIENT_DETONATION_CHANCE = 0.004f;
        public static double SPEED = 0.12;
        public static float HEALTH = 20;
    }

    public static final class Bifauce {
        public static int CHARGE_WINDUP = 30, CHARGE_COOLDOWN = 80, CHARGE_TICKS = 16;
        public static float CHARGE_DAMAGE = 5, CHARGE_SPEED = 0.9f, HEALTH = 30;
        /** Level 0: chance per second of mimicking an everyday sound to lure the player aside. */
        public static float MIMIC_CHANCE = 0.03f;
    }

    public static final class Damper {
        public static int WARNING = 40, HIT_INTERVAL = 40, GIVE_UP = 100, TORCH_INTERVAL = 200;
        public static float DAMAGE = 5, HEALTH = 30;
    }

    public static final class SwarmEye {
        public static int OBSERVE = 60, ALERT_COOLDOWN = 400, SHOT_INTERVAL = 50;
        public static float SHOT_DAMAGE = 3, RANGE = 20, ALERT_RADIUS = 16, HEALTH = 20;
        /** Pact: hold its gaze this long within this range, then give the offering within the window. */
        public static int PACT_GAZE = 100, PACT_WINDOW = 600;
        public static float PACT_RANGE = 12;
        /** Tracking: initial range, update period, search time after losing sight. */
        public static double TRACK_START_RANGE = 32;
        public static int TRACK_UPDATE = 20, TRACK_SEARCH = 600;
        public static double TRACK_FOLLOW_DISTANCE = 10;
    }

    public static final class Kappa {
        public static int WARNING = 60, BITE_INTERVAL = 40;
        public static float BITE_DAMAGE = 3, HEALTH = 24;
        public static double PROVOKE_RADIUS = 6, HOLD_RADIUS = 8;
        public static int APPEASE_PICKLES = 3;
        /** Pact: three visits with an offering, at least this far apart. */
        public static int PACT_VISITS = 3, PACT_SPACING = 20 * 60;
    }

    public static final class Oni {
        public static int WINDUP = 40, COOLDOWN = 80;
        public static float SLAM_DAMAGE = 8, SLAM_RADIUS = 2.5f, HEALTH = 60;
        public static double TERRITORY = 16;
    }

    public static final class Chest {
        public static int WARNING = 20;
        public static float BITE_DAMAGE = 3, HEALTH = 20;
        public static int REPAIR_PLANKS = 4;
    }

    public static final class Tanuki {
        public static int DISTRACT_TICKS = 60, DISTRACT_COOLDOWN = 300, DECOY_LIFETIME = 20 * 45, PACT_DECOYS = 3;
        public static float HEALTH = 16;
    }

    public static final class Tengu {
        public static int WINDUP = 30, COOLDOWN = 80, ROUTE_TIME = 20 * 60, GUST_COOLDOWN = 160;
        public static float FAN_DAMAGE = 4, FAN_KNOCKBACK = 1.6f, HEALTH = 32;
    }

    public static final class Kodama {
        public static int SHELTER_TICKS = 20 * 60 * 5;
        public static float DETECTION_FACTOR = 0.75f, HEALTH = 8;
        public static double SHELTER_RADIUS = 8;
    }

    public static final class Nurikabe {
        public static int STEP_ASIDE = 200, BLOCK_DURATION = 20 * 60;
        public static float HEALTH = 80;
    }

    public static final class IttanMomen {
        public static int WINDUP = 40, COOLDOWN = 100, WRAP_SLOW_TICKS = 40, STUN_TICKS = 100;
        public static float WRAP_DAMAGE = 3, HEALTH = 18;
        /** Mount: blocks per second (descending at least, horizontal at most). */
        public static float MIN_DESCENT = 0.5f, MAX_HORIZONTAL = 6.0f, GROUND_SPEED = 2.5f;
    }

    public static final class Bell {
        public static int WARN_COOLDOWN = 200, ATTEND_WINDOW = 200, PACT_WARNINGS = 3;
        public static float WARN_RADIUS = 24, ATTEND_RADIUS = 4, HEALTH = 20;
    }

    public static final class Companion {
        /** Storing is refused this long after the companion's last attack made or received. */
        public static int COMBAT_TICKS = 200;
        public static double STORE_RANGE = 6, FOLLOW_TELEPORT = 24;
        public static float LOW_HEALTH = 0.25f;
        public static int STATUS_INTERVAL = 20;
    }

    private static final Class<?>[] SECTIONS = {World.class, Perception.class, Director.class, Skills.class, FalseCreeper.class, Bifauce.class, Damper.class,
            SwarmEye.class, Kappa.class, Oni.class, Chest.class, Tanuki.class, Tengu.class, Kodama.class, Nurikabe.class, IttanMomen.class, Bell.class, Companion.class};

    /** Reads (or creates) the config file. Unknown or malformed entries are ignored, missing ones are added. */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("quantumdeath-yokai.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject file = null;
        try {
            if (Files.exists(path)) file = gson.fromJson(Files.readString(path), JsonObject.class);
        } catch (Exception e) {
            Quantumdeath.LOGGER.warn("Ignoring unreadable {}: {}", path, e.getMessage());
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
        } catch (Exception e) {
            Quantumdeath.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
        }
    }
}
