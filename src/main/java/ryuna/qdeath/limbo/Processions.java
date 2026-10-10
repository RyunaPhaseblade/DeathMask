package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.HellEntities;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups of the dead: daily packs and long processions. Common dead are most of every group (75-85%), specialists a
 * tenth to a fifth, heavy threats rare and only in large groups, and the mix follows the ground: open pursuit and
 * alarms on the Wastes, flanking and ambush in the Fields, reach and blocking on the Deltas' bridges, little but
 * dangerous in the Gardens. Never every species at once.
 */
public final class Processions {
    private Processions() {}

    public enum Kind { WASTES, FIELDS, DELTAS, GARDENS }

    public static Kind kindAt(ServerLevel level, BlockPos pos) {
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        return switch (biome) {
            case "weeping_fields" -> Kind.FIELDS;
            case "cinder_deltas" -> Kind.DELTAS;
            case "remembrance_gardens" -> Kind.GARDENS;
            default -> Kind.WASTES;
        };
    }

    private record Pick(EntityType<? extends LimboDead> type, float weight, int minGroup) {}

    private static List<Pick> specialists(Kind kind) {
        return switch (kind) {
            case WASTES -> List.of(new Pick(HellEntities.RUNAWAY, 3, 0), new Pick(HellEntities.CRIER, 2, 0), new Pick(HellEntities.STALKER, 2, 0),
                    new Pick(HellEntities.RUNT, 2, 0), new Pick(HellEntities.LANKY, 1, 0), new Pick(HellEntities.BLOATER, 1.5f, 0),
                    new Pick(HellEntities.FOREMAN, 1, 16), new Pick(HellEntities.MOURNING_HOUND, 0.3f, 0));
            case FIELDS -> List.of(new Pick(HellEntities.STALKER, 3, 0), new Pick(HellEntities.RUNT, 2.5f, 0), new Pick(HellEntities.CRAWLER, 2.5f, 0),
                    new Pick(HellEntities.CRIER, 1, 0), new Pick(HellEntities.BLOATER, 1.5f, 0), new Pick(HellEntities.SUNDERED, 0.4f, 0),
                    new Pick(HellEntities.FOREMAN, 1, 16));
            case DELTAS -> List.of(new Pick(HellEntities.LANKY, 3, 0), new Pick(HellEntities.CRAWLER, 1.5f, 0), new Pick(HellEntities.BLOATER, 2, 0),
                    new Pick(HellEntities.RUNAWAY, 1, 0), new Pick(HellEntities.FOREMAN, 0.8f, 16));
            case GARDENS -> List.of(new Pick(HellEntities.STALKER, 2, 0), new Pick(HellEntities.CRIER, 1.5f, 0), new Pick(HellEntities.FOREMAN, 1, 12),
                    new Pick(HellEntities.LANKY, 1, 0));
        };
    }

    /** Heavy threats: at most one, in groups of {@code minGroup} or more. */
    private static @Nullable EntityType<? extends LimboDead> heavy(Kind kind, int size, RandomSource r) {
        if (size < 20 || r.nextFloat() > 0.45f) return null;
        return switch (kind) {
            case WASTES, FIELDS -> HellEntities.BREAKER;
            case DELTAS -> r.nextFloat() < 0.25f ? HellEntities.WALKING_OSSUARY : HellEntities.BREAKER;
            case GARDENS -> r.nextFloat() < 0.4f ? HellEntities.WAKE_BEARER : HellEntities.BREAKER;
        };
    }

    /**
     * The answers of the expansion to cover and height, by ground: Tickers for walls, the Strangler for perches, the two
     * throwers for both; the parasite where the living animals are. Weights inside a group's specialists.
     */
    private static List<Pick> responses(Kind kind) {
        return switch (kind) {
            case WASTES -> List.of(new Pick(HellEntities.BREACH_TICKER, 2, 0), new Pick(HellEntities.STRANGLER, 1.5f, 0), new Pick(HellEntities.BILE_SPITTER, 1.5f, 0),
                    new Pick(HellEntities.RIB_THROWER, 2, 0));
            case FIELDS -> List.of(new Pick(HellEntities.BREACH_TICKER, 1.5f, 0), new Pick(HellEntities.STRANGLER, 2, 0), new Pick(HellEntities.BILE_SPITTER, 2, 0),
                    new Pick(HellEntities.RIB_THROWER, 1, 0));
            case DELTAS -> List.of(new Pick(HellEntities.BREACH_TICKER, 2.5f, 0), new Pick(HellEntities.STRANGLER, 1, 0), new Pick(HellEntities.BILE_SPITTER, 1, 0),
                    new Pick(HellEntities.RIB_THROWER, 2.5f, 0));
            case GARDENS -> List.of(new Pick(HellEntities.BREACH_TICKER, 1, 0), new Pick(HellEntities.STRANGLER, 1.5f, 0), new Pick(HellEntities.BILE_SPITTER, 1, 0),
                    new Pick(HellEntities.RIB_THROWER, 1.5f, 0));
        };
    }

    private static float parasiteWeight(Kind kind) {
        return switch (kind) { case FIELDS -> 1f; case GARDENS -> 0.8f; case WASTES -> 0.6f; case DELTAS -> 0.5f; };
    }

    /** Whether a group may take one more of {@code t}: one Foreman; packs at most 1 Strangler, 1 parasite, 2 Tickers, 2 throwers; processions 2, 2, 3, 4. */
    private static boolean allowed(List<EntityType<? extends LimboDead>> out, EntityType<? extends LimboDead> t, boolean procession) {
        long n = out.stream().filter(x -> x == t).count();
        if (t == HellEntities.FOREMAN) return n == 0;
        if (t == HellEntities.STRANGLER || t == HellEntities.MOURNING_PARASITE) return n < (procession ? 2 : 1);
        if (t == HellEntities.BREACH_TICKER) return n < (procession ? 3 : 2);
        if (t == HellEntities.BILE_SPITTER || t == HellEntities.RIB_THROWER)
            return out.stream().filter(x -> x == HellEntities.BILE_SPITTER || x == HellEntities.RIB_THROWER).count() < (procession ? 4 : 2);
        return true;
    }

    private static @Nullable EntityType<? extends LimboDead> pick(List<Pick> pool, RandomSource r, List<EntityType<? extends LimboDead>> out, boolean procession) {
        List<Pick> open = pool.stream().filter(p -> allowed(out, p.type, procession)).toList();
        float total = 0;
        for (Pick p : open) total += p.weight;
        float x = r.nextFloat() * total;
        for (Pick p : open) { x -= p.weight; if (x <= 0) return p.type; }
        return open.isEmpty() ? null : open.getLast().type;
    }

    /**
     * The expansion's composition: specialists are 30 % of the group (the old ones included, the heavy threat too); every
     * pack of 8-16 brings at least one answer to cover or height (a Ticker, a Strangler or a thrower) and every procession
     * at least two, each in place of a body of the budget, never on top of it. Leader first; {@code size} bodies in all.
     */
    private static List<EntityType<? extends LimboDead>> expandedComposition(Kind kind, int size, RandomSource r) {
        boolean procession = size >= LimboTuning.PROCESSION_MIN;
        List<EntityType<? extends LimboDead>> out = new ArrayList<>();
        EntityType<? extends LimboDead> h = heavy(kind, size, r);
        if (h != null) out.add(h);
        int specials = Math.max(size >= 8 ? 1 : 0, Math.round(size * LimboTuning.EXPANDED_SPECIALISTS));
        int answers = procession ? 2 : size >= LimboTuning.PACK_MIN ? 1 : 0;
        for (int i = 0; i < answers && out.size() < specials; i++) {
            EntityType<? extends LimboDead> t = pick(responses(kind), r, out, procession);
            if (t != null) out.add(t);
        }
        List<Pick> pool = new ArrayList<>(specialists(kind).stream().filter(p -> size >= p.minGroup).toList());
        pool.addAll(responses(kind));
        pool.add(new Pick(HellEntities.MOURNING_PARASITE, parasiteWeight(kind), 0));
        while (out.size() < specials) {
            EntityType<? extends LimboDead> t = pick(pool, r, out, procession);
            if (t == null) break;
            out.add(t);
        }
        while (out.size() < size) out.add(HellEntities.HOLLOW);
        if (out.getFirst() != HellEntities.HOLLOW) {
            int i = out.indexOf(HellEntities.HOLLOW);
            if (i > 0) { out.set(i, out.getFirst()); out.set(0, HellEntities.HOLLOW); }
        }
        return out;
    }

    /** Leader first. {@code size} bodies in all. */
    public static List<EntityType<? extends LimboDead>> composition(Kind kind, int size, RandomSource r) {
        if (LimboTuning.expansion) return expandedComposition(kind, size, r);
        List<EntityType<? extends LimboDead>> out = new ArrayList<>();
        EntityType<? extends LimboDead> h = heavy(kind, size, r);
        if (h != null) out.add(h);
        int specials = Math.max(size >= 8 ? 1 : 0, Math.round(size * (0.1f + r.nextFloat() * 0.1f)));
        List<Pick> pool = specialists(kind).stream().filter(p -> size >= p.minGroup).toList();
        float total = 0;
        for (Pick p : pool) total += p.weight;
        boolean foreman = false;
        for (int i = 0; i < specials && !pool.isEmpty(); i++) {
            float x = r.nextFloat() * total;
            for (Pick p : pool) {
                x -= p.weight;
                if (x <= 0) {
                    if (p.type == HellEntities.FOREMAN && foreman) break; // one Foreman per group
                    foreman |= p.type == HellEntities.FOREMAN;
                    out.add(p.type);
                    break;
                }
            }
        }
        while (out.size() < size) out.add(HellEntities.HOLLOW);
        // A common dead leads (the heavy or a specialist walks among the others, not at the head).
        if (out.getFirst() != HellEntities.HOLLOW) {
            int i = out.indexOf(HellEntities.HOLLOW);
            if (i > 0) { out.set(i, out.getFirst()); out.set(0, HellEntities.HOLLOW); }
        }
        return out;
    }

    /** The old fixed groups used by commands and the first contact: a small procession of the biome. */
    public static List<EntityType<? extends LimboDead>> composition(Kind kind, RandomSource r) {
        return composition(kind, 8 + r.nextInt(5), r);
    }

    /** Spawns a group at {@code at}; returns its members (leader first). Members are ordinary, despawnable dead. */
    public static List<LimboDead> spawn(ServerLevel level, BlockPos at, Kind kind) {
        return spawn(level, at, composition(kind, level.getRandom()), level.getRandom().nextFloat() * 360f);
    }

    public static List<LimboDead> spawn(ServerLevel level, BlockPos at, List<EntityType<? extends LimboDead>> types, float yaw) {
        List<LimboDead> members = new ArrayList<>();
        LimboDead leader = null;
        RandomSource r = level.getRandom();
        int spread = 2 + (int) Math.sqrt(types.size());
        for (EntityType<? extends LimboDead> type : types) {
            LimboDead d = type.create(level, EntitySpawnReason.EVENT);
            if (d == null) continue;
            BlockPos spot = members.isEmpty() ? at : ground(level, at.getX() + r.nextInt(spread * 2 + 1) - spread, at.getZ() + r.nextInt(spread * 2 + 1) - spread, at.getY(), 5);
            if (spot == null) spot = at;
            d.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw + r.nextFloat() * 40 - 20, 0);
            d.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            if (leader == null) leader = d;
            else d.follow(leader, members.size() - 1);
            level.addFreshEntity(d);
            members.add(d);
        }
        return members;
    }

    /**
     * A spot for a group around {@code player}, between {@code min} and {@code max} blocks away, on firm ground, outside
     * consecrated light and never close to a player. {@code inView} picks the direction the player is facing (the first
     * sight of the Limbo); otherwise the spot must be out of the player's view (behind, to the side, or hidden by rock).
     */
    public static @Nullable BlockPos findSpot(ServerLevel level, ServerPlayer player, int min, int max, boolean inView) {
        Vec3 look = player.getViewVector(1f).multiply(1, 0, 1).normalize();
        RandomSource r = level.getRandom();
        for (int i = 0; i < 20; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            double dot = dir.dot(look);
            if (inView && dot < 0.7) continue;
            double dist = min + r.nextDouble() * (max - min);
            BlockPos g = ground(level, (int) (player.getX() + dir.x * dist), (int) (player.getZ() + dir.z * dist), player.getBlockY(), 24);
            if (g == null || Consecration.at(level, Vec3.atBottomCenterOf(g)) != null) continue;
            if (level.getNearestPlayer(g.getX(), g.getY(), g.getZ(), min * 0.75, false) != null) continue;
            if (!inView && dot > 0.25 && seen(level, player, g)) continue;
            return g;
        }
        return null;
    }

    /** True when the player has a clear line to the spot (so a group appearing there would pop into view). */
    static boolean seen(ServerLevel level, ServerPlayer player, BlockPos g) {
        Vec3 to = Vec3.atBottomCenterOf(g).add(0, 1.5, 0);
        return level.clip(new ClipContext(player.getEyePosition(), to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** Standable floor in column (x, z) within {@code span} blocks of {@code aroundY}, nearest first. */
    public static @Nullable BlockPos ground(ServerLevel level, int x, int z, int aroundY, int span) {
        if (!level.isLoaded(new BlockPos(x, aroundY, z))) return null;
        for (int d = 0; d <= span; d++) {
            for (int s : new int[]{1, -1}) {
                BlockPos p = new BlockPos(x, aroundY + d * s, z);
                if (standable(level, p)) return p;
                if (d == 0) break;
            }
        }
        return null;
    }

    /** Reinforcements never appear on top of anyone: true when no player stands within {@code r} of {@code p}. */
    public static boolean clearOfPlayers(ServerLevel level, BlockPos p, double r) {
        return level.getNearestPlayer(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, r, false) == null;
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return below.isFaceSturdy(level, p.below(), Direction.UP) && below.getFluidState().isEmpty()
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p).getFluidState().isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
    }
}
