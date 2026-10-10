package ryuna.qdeath.limbo;

/**
 * Every balance number of the Limbo in one place. The refinement document gives them as starting points for playtests;
 * change them here, nowhere else. Times are in ticks (20 = 1 s), distances in blocks, speeds as fractions of the
 * player's normal sprint measured on flat ground (see {@link #chaseModifier}).
 */
public final class LimboTuning {
    private LimboTuning() {}

    // ---------------------------------------------------------------- senses
    /** How often a dead re-reads its senses (staggered per entity); dead far from every player read them less often. */
    public static final int SCAN_INTERVAL = 4, SCAN_INTERVAL_FAR = 16;
    /** Beyond this distance from any player a resting dead thinks slowly (it still walks and still wakes to noise). */
    public static final double FAR_THINKING = 48;
    /** Movement noise radius of a walking / sprinting / crouching living creature. Crouching is quiet, never invisible. */
    public static final double NOISE_WALK = 7, NOISE_SPRINT = 13, NOISE_CROUCH = 2.5;
    /** Below this distance anything alive is felt, whatever the facing (a hand brushing against it). */
    public static final double TOUCH_RANGE = 2.5;
    /** Awareness gained per scan by sight (near / far) and by noise; noise alone can never confirm a target. */
    public static final float AWARE_SIGHT_NEAR = 0.45f, AWARE_SIGHT_FAR = 0.2f, AWARE_NOISE = 0.12f, NOISE_CAP = 0.6f, AWARE_DECAY = 0.06f;
    /** Ticks a newly arrived player stays unnoticed (no ambush on arrival). */
    public static final int ARRIVAL_GRACE = 200;

    // ---------------------------------------------------------------- states
    public static final int RECOGNITION_TICKS = 12;
    /** Pursuit gives up after this long without sight, and searches the last known position instead. */
    public static final int LOSE_SIGHT_TICKS = 80;
    /** Pursuit of an unreachable target (walled in, on a pillar) also turns into a search. */
    public static final int UNREACHABLE_TICKS = 140;
    public static final int DISPERSAL_TICKS = 140;
    /** A dead that called recently will not call again for this long. */
    public static final int CALL_COOLDOWN = 400;
    /**
     * The commotion of a chase: a pursuing dead that can see its quarry draws resting dead within this radius to look at
     * where the quarry is (they get a clue, not the target), at most every COMMOTION_INTERVAL ticks. This is how packs
     * converge into a horde without anyone knowing where the living are.
     */
    public static final double COMMOTION_RADIUS = 14;
    public static final int COMMOTION_INTERVAL = 40;

    // ---------------------------------------------------------------- speed
    /** Normal sprint of a player on flat ground, blocks per tick (vanilla: 5.612 m/s); walking is 0.2159. */
    public static final double PLAYER_SPRINT = 0.2806, PLAYER_WALK = 0.2159;
    /**
     * Correction for path following (corners, node hand-over), measured in game by the speed test of the client gametests:
     * the dead's real chase speed over a straight flat lane divided by its theoretical speed. Raise it if they lag.
     */
    public static final double PATH_EFFICIENCY = 1.02; // measured: every species ran 2.0-2.1% above the formula on the flat lane
    /** Pursuit speeds as fractions of the sprint (the document's targets). The common dead: 0.94-0.97. */
    public static final double CHASE_COMMON = 0.955, CHASE_STALKER = 0.975, CHASE_CRIER = 0.75, CHASE_FOREMAN = 0.85, CHASE_LANKY = 0.9,
            CHASE_BLOATER = 0.8, CHASE_BREAKER = 0.62, CHASE_RUNT = 1.06, CHASE_RUNAWAY = 0.9, CHASE_CRAWLER = 0.82, CHASE_SUNDERED = 0.8;
    /** The Runaway's short burst, as a fraction of the sprint (per-tick displacement while it runs). */
    public static final double RUNAWAY_BURST = 1.2;
    /** Wandering speed of the human dead (fraction of sprint): a slow drift. */
    public static final double WANDER = 0.24;

    /**
     * Navigation modifier that makes a mob of base speed {@code attribute} move at {@code fraction} of the player's sprint.
     * On normal ground a mob's steady speed is 0.98 s^2 / (1 - 0.546) blocks per tick, with s = attribute * modifier.
     */
    public static double chaseModifier(double fraction, double attribute) {
        double v = fraction * PLAYER_SPRINT / PATH_EFFICIENCY;
        return Math.sqrt(v * (1 - 0.546) / 0.98) / attribute;
    }

    // ---------------------------------------------------------------- encounters (director)
    /**
     * Daily packs (8-16) come every PACK_INTERVAL..+PACK_JITTER ticks per player group, processions (24-48) every
     * PROCESSION_INTERVAL..+jitter. They appear PACK_MIN..PACK_MAX blocks away, out of view, with a walkable route.
     */
    public static final int PACK_INTERVAL = 500, PACK_JITTER = 400, PACK_MIN = 8, PACK_MAX = 16;
    public static final int PROCESSION_INTERVAL = 3600, PROCESSION_JITTER = 2400, PROCESSION_MIN = 24, PROCESSION_MAX = 48;
    public static final int SPAWN_MIN_DIST = 40, SPAWN_MAX_DIST = 60;
    /**
     * Technical bound of the director, not a cap on attackers: it adds no new group while this many dead already exist
     * within DIRECTOR_RADIUS of the player. Hordes formed by the world (packs converging, graves, structures) are never
     * trimmed, and every dead already present keeps hunting.
     */
    public static final int DIRECTOR_SOFT_CAP = 120;
    public static final double DIRECTOR_RADIUS = 96;
    /** The dead do not despawn closer than this to a player, nor while they hunt. */
    public static final double DESPAWN_DISTANCE = 96;
    /** Share of specialists in a pack (the rest are common dead); heavy threats only in larger groups. */
    public static final float PACK_SPECIALISTS = 0.15f;
    /** Share of packs that arrive investigating a noise near the player (the rest wander in their own way). */
    public static final float PACK_INVESTIGATES = 0.6f;
    /** Sealed graves wake when something alive comes this close; they scrape for this long first. */
    public static final int GRAVE_WAKE_RADIUS = 5, GRAVE_WARNING_TICKS = 40;

    // ---------------------------------------------------------------- the Putrescent's corpse
    public static final int CORPSE_FUSE_MIN = 70, CORPSE_FUSE_MAX = 90, CORPSE_RESIDUE = 80;
    public static final double CORPSE_RADIUS = 3.75;
    public static final float CORPSE_DAMAGE = 14f, CORPSE_DAMAGE_DEAD = 18f;

    // ---------------------------------------------------------------- trail and lures
    /** A living creature leaves a breath point every this many ticks; points last TRAIL_LIFE. */
    public static final int TRAIL_STEP = 20, TRAIL_LIFE = 600, TRAIL_POINTS = 32;
    public static final int LURE_LIFE = 600;
    public static final double LURE_PULL_HOUND = 24, LURE_PULL_DEAD = 18;

    // ---------------------------------------------------------------- siege
    public static final int SIEGE_PREPARE_OSSUARY = 70, SIEGE_PREPARE_BEARER = 90, SIEGE_PREPARE_BREAKER = 60;

    // ---------------------------------------------------------------- votive consecration
    /** One unit of votive fuel lasts this long; funeral wax gives WAX_UNITS, consecrated wax CONSECRATED_UNITS (max MAX_FUEL). */
    public static final int FUEL_UNIT_TICKS = 900, WAX_UNITS = 4, CONSECRATED_UNITS = 8, MAX_FUEL = 8;

    // ---------------------------------------------------------------- the plague of the Hollows (Overworld)
    public static final int PLAGUE_CHECK_INTERVAL = 2400;
    /** Chance per check and per eligible player that a new focus opens (minor / after the first seal broke). */
    public static final float PLAGUE_MINOR_CHANCE = 0.12f, PLAGUE_MAJOR_CHANCE = 0.2f;
    public static final int PLAGUE_MAX_ACTIVE = 2, PLAGUE_MIN_DIST = 48, PLAGUE_MAX_DIST = 96;
    public static final int PLAGUE_WARNING_TICKS = 1200, PLAGUE_WAVE_INTERVAL = 700;
    public static final int MINOR_WAVES = 3, MINOR_RESERVE = 9, MAJOR_WAVES = 5, MAJOR_RESERVE = 20;
    public static final int PURGE_TICKS = 100;
    /** Plague dead never wander further than this from their breach. */
    public static final double PLAGUE_LEASH = 72;

    // ================================================================ the expansion: parasites, siege and climbing hordes
    // (docs/ampliacion-limbo-especificacion.md). Damage in health points (2 = one heart).
    /**
     * Master switch of the expansion's behaviours (stimuli, lights, weak blocks, stuck handling, the horde's instincts, profiled routes).
     * Only the performance comparison of the client gametests turns it off, to measure the same hordes without them.
     */
    public static boolean expansion = true;

    // ---------------------------------------------------------------- stimuli (heard by the dead of the Limbo and of the plague)
    public static final double STIMULUS_MINING = 16, STIMULUS_BREAK = 18, STIMULUS_PLACE = 10, STIMULUS_CONTAINER = 12, STIMULUS_EAT = 8, LIGHT_RANGE = 16;
    /** Mining and eating publish at most once per this many ticks per player; clues expire after STIMULUS_LIFE. */
    public static final int STIMULUS_REPEAT = 10, STIMULUS_LIFE = 160;
    /** A dead that inspected a light without finding life ignores it this long; dead re-look for lights this often. */
    public static final int LIGHT_IGNORE = 600, LIGHT_SCAN = 40;
    /** Shared budgets per server tick: wall checks of heard stimuli, sight checks of lights, extra path probes. */
    public static final int BUDGET_STIMULUS_RAYS = 96, BUDGET_LIGHT_RAYS = 24, BUDGET_PATH_PROBES = 6;

    // ---------------------------------------------------------------- weak blocks (glass, panes, leaves)
    public static final int WEAK_PREPARE = 30, WEAK_RECOVERY = 20;

    // ---------------------------------------------------------------- navigation
    /** Stuck: less than STUCK_PROGRESS blocks in STUCK_TICKS; the failed access cools for ACCESS_COOLDOWN. */
    public static final int STUCK_TICKS = 40, ACCESS_COOLDOWN = 100;
    public static final double STUCK_PROGRESS = 0.5;
    /** How far a dead looks for a ladder or vine leading up to an elevated quarry. */
    public static final int CLIMB_SEARCH = 10;

    // ---------------------------------------------------------------- the horde's instincts (Horde, after Enhanced Hordes)
    /**
     * Climbing: a hunting dead whose feet come within STACK_REACH of another's body is pushed up at up to STACK_LIFT
     * blocks a tick, and glides down for STACK_GLIDE ticks once above it (the values of Enhanced Hordes).
     */
    public static final double STACK_REACH = 0.225, STACK_LIFT = 0.16;
    public static final int STACK_GLIDE = 5;
    /** Piles: the highest wall worth piling against; how far a dead joins its horde's pile; how long an unused pile is kept. */
    public static final int PILE_MAX_HEIGHT = 12, PILE_LIFE = 200;
    public static final double PILE_JOIN = 24;
    /** How far from the top of the pile the quarry may stand and still count as up there (the edge hides it from below). */
    public static final double PILE_REACH = 5;
    /** Fire: a burning dead sets those it touches alight for FIRE_SPREAD seconds and runs with Speed (this amplifier) while it burns. */
    public static final int FIRE_SPREAD = 5, BURN_SPEED = 2;
    /**
     * Digging up the dead: a hunting dead on soft ground, at least DIG_MIN_DISTANCE from its quarry, starts with DIG_CHANCE
     * a tick (as Enhanced Hordes), claws for DIG_TICKS and a common dead comes out; a hit stops it and it waits
     * DIG_COOLDOWN. No digging where DIG_LOCAL_CAP dead already stand within 32 blocks.
     */
    public static final float DIG_CHANCE = 8.0e-4f;
    public static final int DIG_TICKS = 200, DIG_COOLDOWN = 400, DIG_LOCAL_CAP = 48;
    public static final double DIG_MIN_DISTANCE = 6;
    /** Off in the test suites that measure pursuits (a random dig would stop a runner for ten seconds). */
    public static boolean digging = true;

    // ---------------------------------------------------------------- new creatures: speeds (fractions of the sprint)
    public static final double CHASE_PARASITE = 0.90, CHASE_TICKER = 1.00, CHASE_STRANGLER = 0.75, CHASE_SPITTER = 0.80, CHASE_RIBS = 0.85;

    // ---------------------------------------------------------------- the Mourning Parasite
    public static final double PARASITE_SEEK = 10, PARASITE_LEAP_MIN = 2, PARASITE_LEAP_MAX = 5;
    public static final int PARASITE_PREPARE = 12, PARASITE_FIXATION = 30, PARASITE_STUN = 40, PARASITE_COOLDOWN = 160;
    /** Possessed hosts: +20 % real speed, +25 % damage; hosts without an attack strike for HOST_DAMAGE every HOST_INTERVAL. */
    public static final double HOST_SPEED = 0.20, HOST_DAMAGE_BONUS = 0.25;
    public static final float HOST_DAMAGE = 4f;
    public static final int HOST_INTERVAL = 30, HOST_WINDUP = 8;
    /** A host never pursues faster than this fraction of the sprint before its bonus (villagers would otherwise outrun anyone). */
    public static final double HOST_PACE = 0.75;
    /** Ticks a saved bond waits for its other half to load before it is declared broken. */
    public static final int LINK_GRACE = 100;

    // ---------------------------------------------------------------- the Breach Ticker
    public static final int TICKER_SIEGE_PREPARE = 60, TICKER_LEAP_PREPARE = 10, TICKER_COUNTDOWN = 40, TICKER_STUN = 40, TICKER_REATTACH = 100;
    public static final double TICKER_LEAP_RANGE = 3, TICKER_CORRIDOR = 8, TICKER_BURST_RADIUS = 2.5;
    public static final int TICKER_MAX_BLOCKS = 72;
    public static final float TICKER_BURST_DAMAGE = 10f;
    /** A freshly loaded Ticker waits this long before it may attach to anything (loading never detonates). */
    public static final int TICKER_LOAD_GRACE = 40;

    // ---------------------------------------------------------------- the Strangler
    public static final double TONGUE_RANGE = 14, TONGUE_SPEED = 1.1, TONGUE_PULL = 0.125;
    public static final int TONGUE_PREPARE = 20, TONGUE_HOLD = 80, TONGUE_COOLDOWN = 160, TONGUE_IMMUNITY = 120;
    public static final float TONGUE_DAMAGE = 1f;
    /** The gripped victim's own legs barely help: their movement speed is cut by this share while held. */
    public static final double TONGUE_DRAG = 0.7;

    // ---------------------------------------------------------------- the shooters
    public static final double SHOOTER_MIN = 6, SHOOTER_MAX = 12;
    public static final double BILE_RANGE = 18, RIB_RANGE = 24;
    public static final int BILE_PREPARE = 20, BILE_COOLDOWN = 80, RIB_PREPARE = 18, RIB_COOLDOWN = 70;
    public static final float BILE_DAMAGE = 5f, RIB_DAMAGE = 6f;
    public static final int BILE_SLOW = 40, RESIDUE_LIFE = 60;
    public static final double RESIDUE_RADIUS = 1.5, BILE_GRAVITY = 0.05, RIB_GRAVITY = 0.02, RIB_SPEED = 1.6;
    /** Rib throwers lead a moving target by at most this many ticks of its observed motion. */
    public static final int RIB_LEAD = 10;

    // ---------------------------------------------------------------- composition
    public static final float EXPANDED_SPECIALISTS = 0.30f;
}
