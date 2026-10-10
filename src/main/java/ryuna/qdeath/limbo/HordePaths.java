package ryuna.qdeath.limbo;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared routes for hordes. A path toward a target is computed once per 4x2x4-block sector and reused for a moment by
 * every dead of that sector chasing the same target: each takes a copy and joins it at the node nearest to itself.
 * A hundred pursuers then cost a handful of path searches per second instead of a hundred, and nobody is held back
 * to save time: whoever cannot use a shared route (too far from it) computes its own.
 *
 * <p>Routes are kept apart by body: a Crawler, a man and a climbing spider host never share a route one of them could
 * not use (the key holds the body's size and whether it climbs). A route that crosses a section where a block changed
 * after it was found (a door broken, a wall built, a support gone) is not handed out again.
 */
public final class HordePaths {
    private HordePaths() {}

    /** How long a shared route stays fresh, and how far the target may have moved since. */
    static final int FRESH_TICKS = 12;
    static final double TARGET_DRIFT_SQR = 2.5 * 2.5;

    private record Route(List<Node> nodes, BlockPos target, boolean reached, long time, double tx, double ty, double tz) {}

    private static final Long2ObjectOpenHashMap<Route> ROUTES = new Long2ObjectOpenHashMap<>();
    /** Section (packed) -> game time of its last block change, for the levels the dead walk. */
    private static final Long2LongOpenHashMap CHANGED = new Long2LongOpenHashMap();
    private static long lastPrune;
    /** Statistics for the performance survey: searches made, routes shared, routes dropped because the ground changed. */
    public static long searches, shared, invalidated;

    /** Body class of a pursuer: width and height in tenths of a block, plus wall climbing. */
    static int profile(Mob mob) {
        int p = (int) (mob.getBbWidth() * 10) * 64 + (int) (mob.getBbHeight() * 10);
        return mob.getNavigation() instanceof WallClimberNavigation ? p + 100_000 : p;
    }

    private static long key(int targetId, BlockPos p, int profile) {
        long s = BlockPos.asLong(p.getX() >> 2, p.getY() >> 1, p.getZ() >> 2);
        return (s * 31 + targetId) * 1_000_003L + (LimboTuning.expansion ? profile : 0);
    }

    /** A block changed: routes through its section, found before now, are stale. */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        if (!LimboTuning.expansion || ROUTES.isEmpty()) return;
        CHANGED.put(SectionPos.asLong(pos), level.getGameTime());
    }

    private static boolean stale(Route r) {
        if (CHANGED.isEmpty()) return false;
        for (Node n : r.nodes) {
            long t = CHANGED.getOrDefault(SectionPos.asLong(n.x >> 4, n.y >> 4, n.z >> 4), Long.MIN_VALUE);
            if (t >= r.time) return true;
        }
        return false;
    }

    /** Moves {@code mob} toward {@code target} at {@code speed}, reusing a fresh sector route when one exists. */
    public static boolean chase(Mob mob, LivingEntity target, double speed) {
        long now = mob.level().getGameTime();
        if (now - lastPrune > 200) {
            ROUTES.values().removeIf(r -> now - r.time > 40);
            CHANGED.values().removeIf(t -> now - t > 40);
            lastPrune = now;
        }
        long k = key(target.getId(), mob.blockPosition(), profile(mob));
        Route r = ROUTES.get(k);
        if (r != null && now - r.time <= FRESH_TICKS && target.distanceToSqr(r.tx, r.ty, r.tz) < TARGET_DRIFT_SQR) {
            if (LimboTuning.expansion && stale(r)) {
                ROUTES.remove(k);
                invalidated++;
            } else {
                int best = -1;
                double bestD = 2.6 * 2.6;
                for (int i = 0; i < r.nodes.size(); i++) {
                    Node n = r.nodes.get(i);
                    double dx = n.x + 0.5 - mob.getX(), dz = n.z + 0.5 - mob.getZ(), dy = n.y - mob.getY();
                    double d = dx * dx + dz * dz;
                    if (Math.abs(dy) <= 1.1 && d < bestD) { bestD = d; best = i; }
                }
                if (best >= 0) {
                    Path copy = new Path(new ArrayList<>(r.nodes), r.target, r.reached);
                    copy.setNextNodeIndex(Math.min(best + 1, r.nodes.size() - 1));
                    shared++;
                    return mob.getNavigation().moveTo(copy, speed);
                }
            }
        }
        Path path = mob.getNavigation().createPath(target, 0);
        searches++;
        if (path == null) return false;
        List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) nodes.add(path.getNode(i));
        ROUTES.put(k, new Route(nodes, path.getTarget(), path.canReach(), now, target.getX(), target.getY(), target.getZ()));
        return mob.getNavigation().moveTo(path, speed);
    }

    public static void clear() { ROUTES.clear(); CHANGED.clear(); }
}
