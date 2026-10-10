package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.block.LimboBlocks.Grave;
import ryuna.qdeath.entity.hell.HollowEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pacing of the Limbo around each player (players close together share it, so a group draws no more than one):
 * frequent packs of 8-16, processions of 24-48 now and then, graves that stir when the living come close, the
 * murmur of a horde, and a quiet arrival. Groups appear out of view, at a distance, on ground with a walkable route
 * to the player, never inside consecrated light. The Gardens and the light of a votive lantern let a player breathe.
 *
 * <p>Population is bounded only by the director's own soft cap for <em>new</em> groups ({@link LimboTuning#DIRECTOR_SOFT_CAP});
 * nothing here limits how many dead may hunt one player: packs that meet, the Crier's call and the commotion of a
 * chase merge freely into larger hordes.
 */
public final class LimboDirector {
    private LimboDirector() {}

    /** /deathmask limbo debug: shows every dead's state above its head. */
    public static boolean debug;
    /** /deathmask limbo processions off: stops new groups (for filming quiet shots and for tests). */
    public static boolean processions = true;
    private static final Map<UUID, Long> NEXT_PACK = new HashMap<>(), NEXT_PROCESSION = new HashMap<>(), ARRIVED = new HashMap<>();
    private static final Map<UUID, BlockPos> ARRIVAL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_MURMUR = new HashMap<>();
    /** Statistics for the encounter survey: groups and bodies the director has placed. */
    public static int packsSpawned, processionsSpawned, bodiesSpawned;
    /** Why placements failed (no out-of-view ground / no walkable route / too close to the arrival), for the survey. */
    public static int failSpot, failPath, failArrival, deferredCap;

    public static void tick(MinecraftServer server) {
        LimboSenses.tick(server);
        Stimuli.tick(server);
        if (server.getTickCount() % 200 == 0) Routes.prune(server.overworld().getGameTime());
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel limbo = server.getLevel(HellLayer.VESTIBULE.level);
        if (limbo == null) return;
        long now = limbo.getGameTime();
        for (ServerPlayer p : limbo.players()) {
            if (p.isSpectator()) continue;
            if (LimboSenses.isLife(p)) stirGraves(limbo, p.blockPosition(), LimboTuning.GRAVE_WAKE_RADIUS);
            murmur(limbo, p, now);
            if (!processions) continue;
            if (now < ARRIVED.getOrDefault(p.getUUID(), 0L) + 600) continue; // half a minute of quiet after arriving
            if (Consecration.at(limbo, p.position()) != null || nearLantern(limbo, p.blockPosition())) continue;
            Processions.Kind kind = Processions.kindAt(limbo, p.blockPosition());
            float pace = kind == Processions.Kind.GARDENS ? 0.45f : 1f; // the Gardens let you breathe
            RandomSource r = limbo.getRandom();
            long pack = NEXT_PACK.computeIfAbsent(p.getUUID(), k -> now + LimboTuning.PACK_INTERVAL);
            long proc = NEXT_PROCESSION.computeIfAbsent(p.getUUID(), k -> now + LimboTuning.PROCESSION_INTERVAL);
            boolean doProc = now >= proc, doPack = now >= pack;
            if (!doProc && !doPack) continue;
            int near = deadAround(limbo, p.position(), LimboTuning.DIRECTOR_RADIUS);
            if (near >= LimboTuning.DIRECTOR_SOFT_CAP) {
                deferredCap++;
                share(limbo, p, NEXT_PACK, now + 200);
                continue;
            }
            int size = doProc ? LimboTuning.PROCESSION_MIN + r.nextInt(LimboTuning.PROCESSION_MAX - LimboTuning.PROCESSION_MIN + 1)
                    : LimboTuning.PACK_MIN + r.nextInt(LimboTuning.PACK_MAX - LimboTuning.PACK_MIN + 1);
            if (kind == Processions.Kind.GARDENS) size = Math.max(6, size * 2 / 3);
            size = Math.min(size, LimboTuning.DIRECTOR_SOFT_CAP - near);
            List<LimboDead> group = place(limbo, p, Processions.composition(kind, size, r), doProc);
            if (group == null) { share(limbo, p, doProc ? NEXT_PROCESSION : NEXT_PACK, now + 100); continue; }
            bodiesSpawned += group.size();
            if (doProc) {
                processionsSpawned++;
                share(limbo, p, NEXT_PROCESSION, now + (long) ((LimboTuning.PROCESSION_INTERVAL + r.nextInt(LimboTuning.PROCESSION_JITTER)) / pace));
                share(limbo, p, NEXT_PACK, Math.max(pack, now + LimboTuning.PACK_INTERVAL / 2));
                limbo.playSound(null, group.getFirst().blockPosition(), ModSounds.LIMBO_BELL_FAR, SoundSource.AMBIENT, 2.5f, 0.75f);
            } else {
                packsSpawned++;
                share(limbo, p, NEXT_PACK, now + (long) ((LimboTuning.PACK_INTERVAL + r.nextInt(LimboTuning.PACK_JITTER)) / pace));
            }
        }
    }

    /** Sets a timer for the player and everyone within 96 blocks of them. */
    private static void share(ServerLevel level, ServerPlayer p, Map<UUID, Long> timer, long at) {
        for (ServerPlayer o : level.players()) if (o.distanceToSqr(p) < 96 * 96) timer.put(o.getUUID(), at);
    }

    private static boolean nearLantern(ServerLevel level, BlockPos at) {
        return ryuna.qdeath.hell.HellTravel.findBlock(level, at, java.util.Set.of(ModBlocks.VOTIVE_LANTERN), 10) != null;
    }

    /**
     * Places a group out of the player's view with a walkable route to them: the leader is spawned first and asked for a
     * path; a spot without one (a ledge, a sealed pocket) is abandoned and another tried.
     */
    private static @Nullable List<LimboDead> place(ServerLevel level, ServerPlayer p, List<EntityType<? extends LimboDead>> types, boolean procession) {
        BlockPos arrival = ARRIVAL.get(p.getUUID());
        for (int attempt = 0; attempt < 5; attempt++) {
            int min = procession ? LimboTuning.SPAWN_MIN_DIST + 8 : LimboTuning.SPAWN_MIN_DIST;
            BlockPos at = Processions.findSpot(level, p, min, LimboTuning.SPAWN_MAX_DIST, false);
            if (at == null) { failSpot++; continue; }
            if (arrival != null && at.distSqr(arrival) < 32 * 32) { failArrival++; continue; }
            HollowEntity probe = HellEntities.HOLLOW.create(level, EntitySpawnReason.EVENT);
            if (probe == null) return null;
            probe.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            probe.setOnGround(true); // ground navigation only plans from the ground
            probe.getNavigation().setMaxVisitedNodesMultiplier(4f);
            Path path = probe.getNavigation().createPath(p, 2);
            probe.discard();
            // A route that reaches the player, or ends within a few blocks of them (the same chamber), proves the spot is connected.
            if (path == null || (!path.canReach() && (path.getEndNode() == null || path.getEndNode().asBlockPos().distSqr(p.blockPosition()) > 6 * 6))) {
                failPath++;
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - at.getZ(), p.getX() - at.getX())) - 90f;
            List<LimboDead> group = Processions.spawn(level, at, types, yaw);
            RandomSource r = level.getRandom();
            if (procession) {
                // A procession walks across the player's region rather than straight at them.
                Vec3 cross = p.position().add(r.nextInt(41) - 20, 0, r.nextInt(41) - 20);
                group.getFirst().setHeading((float) Math.atan2(cross.z - at.getZ(), cross.x - at.getX()));
            } else if (r.nextFloat() < LimboTuning.PACK_INVESTIGATES) {
                // Most packs come because they heard something: they search where the noise was (a clue, not the target).
                Vec3 noise = p.position().add(r.nextInt(13) - 6, 0, r.nextInt(13) - 6);
                for (LimboDead d : group) d.hearAlert(noise);
            }
            return group;
        }
        return null;
    }

    /** The murmur of a horde: one voice for the crowd, from its centre, when many are hunting near a player. */
    private static void murmur(ServerLevel level, ServerPlayer p, long now) {
        if (now < NEXT_MURMUR.getOrDefault(p.getUUID(), 0L)) return;
        List<LimboDead> hunting = level.getEntitiesOfClass(LimboDead.class, p.getBoundingBox().inflate(32), d -> d.mind().alert());
        if (hunting.size() < 8) return;
        double x = 0, y = 0, z = 0;
        for (LimboDead d : hunting) { x += d.getX(); y += d.getY(); z += d.getZ(); }
        int n = hunting.size();
        level.playSound(null, x / n, y / n + 1, z / n, ModSounds.DEAD_HORDE, SoundSource.HOSTILE, Math.min(3f, 0.8f + n / 15f), 0.9f + level.getRandom().nextFloat() * 0.15f);
        NEXT_MURMUR.put(p.getUUID(), now + 50 + level.getRandom().nextInt(30));
    }

    public static int deadAround(ServerLevel level, Vec3 c, double r) {
        return level.getEntitiesOfClass(LimboDead.class, new AABB(c, c).inflate(r)).size();
    }

    /** Wakes every sealed grave within {@code radius}; each one first scrapes and shifts, then lets its occupant out. */
    public static int stirGraves(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        int dy = Math.min(radius, 4);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -dy, -radius), center.offset(radius, dy, radius))) {
            BlockState s = level.getBlockState(p);
            if (s.is(ModBlocks.LIMBO_GRAVE) && s.getValue(Grave.STAGE) == Grave.SEALED && p.distSqr(center) <= radius * radius) {
                Grave.stir(level, p.immutable(), s);
                n++;
            }
        }
        return n;
    }

    /**
     * Arrival in the Limbo: a short grace in which no dead can recognize the newcomer, and half a minute without new
     * groups, so nobody steps out of a portal into an ambush. On the very first arrival, the introduction: a lone dead
     * nearby that will stop, turn and breathe, and a procession in the distance. It never repeats.
     */
    public static void arrive(ServerPlayer player, boolean first) {
        LimboSenses.grantGrace(player, LimboTuning.ARRIVAL_GRACE);
        long now = player.level().getGameTime();
        ARRIVED.put(player.getUUID(), now);
        ARRIVAL.put(player.getUUID(), player.blockPosition());
        NEXT_PACK.put(player.getUUID(), now + 600 + LimboTuning.PACK_INTERVAL / 2);
        NEXT_PROCESSION.put(player.getUUID(), now + LimboTuning.PROCESSION_INTERVAL);
        if (first) firstContact(player);
    }

    public static void firstContact(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos far = Processions.findSpot(level, player, 40, 56, true);
        if (far != null) Processions.spawn(level, far, Processions.composition(Processions.Kind.WASTES, 10, level.getRandom()), level.getRandom().nextFloat() * 360);
        BlockPos near = Processions.findSpot(level, player, 12, 16, true);
        if (near == null) return;
        HollowEntity h = HellEntities.HOLLOW.create(level, EntitySpawnReason.EVENT);
        if (h == null) return;
        Vec3 side = player.position().subtract(Vec3.atBottomCenterOf(near));
        // It stands sideways to the player: it has to stop and turn before it notices anyone.
        float yaw = (float) Math.toDegrees(Math.atan2(side.z, side.x));
        h.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, yaw, 0);
        h.setYHeadRot(yaw);
        h.setYBodyRot(yaw);
        h.finalizeSpawn(level, level.getCurrentDifficultyAt(near), EntitySpawnReason.EVENT, null);
        h.setResting(true);
        level.addFreshEntity(h);
        level.playSound(null, near, ModSounds.LIMBO_BELL_FAR, SoundSource.AMBIENT, 2.0f, 0.8f);
    }

    /** Forces the next pack (and, with {@code procession}, a procession) for this player now: tests and filming. */
    public static void hurry(ServerPlayer p, boolean procession) {
        long now = p.level().getGameTime();
        ARRIVED.put(p.getUUID(), now - 10_000);
        NEXT_PACK.put(p.getUUID(), now);
        if (procession) NEXT_PROCESSION.put(p.getUUID(), now);
    }

    public static void forget(UUID player) {
        NEXT_PACK.remove(player);
        NEXT_PROCESSION.remove(player);
        ARRIVED.remove(player);
        ARRIVAL.remove(player);
        NEXT_MURMUR.remove(player);
        Stimuli.forget(player);
        Grips.forget(player);
    }

    public static void clear() {
        NEXT_PACK.clear();
        NEXT_PROCESSION.clear();
        ARRIVED.clear();
        ARRIVAL.clear();
        NEXT_MURMUR.clear();
        HordePaths.clear();
        LimboVoices.clear();
        Stimuli.clear();
        LightLures.clear();
        LimboTasks.clear();
        Possession.clear();
        Horde.clear();
        Routes.clear();
        Grips.clear();
    }

    /** Every dead within {@code r} of {@code c}, for commands. */
    public static List<LimboDead> deadNear(ServerLevel level, Vec3 c, double r) {
        return level.getEntitiesOfClass(LimboDead.class, new AABB(c, c).inflate(r));
    }
}
