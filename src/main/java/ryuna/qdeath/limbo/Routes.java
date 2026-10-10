package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Ways round for a horde that cannot reach its quarry by the ordinary route, shared by everyone chasing the same quarry
 * (what one finds, the others use): reachable accesses beside and around it, at its level or near, and ladders or vines
 * that lead up to it. Path probes are expensive, so the whole server shares a small budget of them per tick; a dead
 * that finds the budget spent just tries again a little later.
 */
public final class Routes {
    private Routes() {}

    /** A climbable column: where to stand to start, the topmost climbable cell, and the side to press against. */
    public record Climb(BlockPos base, BlockPos top, Direction wall) {}

    private record Known<T>(T value, long time) {}

    private static final Map<Integer, Known<List<Vec3>>> ACCESSES = new HashMap<>();
    private static final Map<Integer, Known<@Nullable Climb>> CLIMBS = new HashMap<>();
    private static long budgetTick = -1;
    private static int probesLeft;
    public static int probes, climbsFound;

    static boolean probe(ServerLevel level) {
        long t = level.getGameTime();
        if (t != budgetTick) { budgetTick = t; probesLeft = LimboTuning.BUDGET_PATH_PROBES; }
        if (probesLeft <= 0) return false;
        probesLeft--;
        probes++;
        return true;
    }

    public static long accessKey(Vec3 offset) { return BlockPos.asLong((int) Math.round(offset.x), (int) Math.round(offset.y), (int) Math.round(offset.z)); }

    /**
     * An access to {@code target} that {@code mob} can reach, as an offset from the target: one the horde already found if
     * any, else a few new ones are probed (to the sides and behind, at the target's level or a little above and below).
     * {@code cooled} skips accesses this dead already failed.
     */
    public static @Nullable Vec3 access(ServerLevel level, Mob mob, LivingEntity target, LongPredicate cooled) {
        long now = level.getGameTime();
        Known<List<Vec3>> known = ACCESSES.get(target.getId());
        if (known != null && now - known.time < 60) {
            for (Vec3 o : known.value) if (!cooled.test(accessKey(o))) return o;
        }
        List<Vec3> found = known != null && now - known.time < 60 ? known.value : new ArrayList<>();
        int start = mob.getRandom().nextInt(8);
        for (int r : new int[]{3, 5}) for (int i = 0; i < 8; i++) {
            double a = (start + i) * Math.PI / 4;
            int x = (int) Math.floor(target.getX() + Math.cos(a) * r), z = (int) Math.floor(target.getZ() + Math.sin(a) * r);
            BlockPos g = Processions.ground(level, x, z, target.getBlockY(), 3);
            if (g == null) continue;
            Vec3 offset = Vec3.atBottomCenterOf(g).subtract(target.position());
            if (cooled.test(accessKey(offset)) || found.stream().anyMatch(o -> o.distanceToSqr(offset) < 1)) continue;
            if (!probe(level)) return null;
            Path p = mob.getNavigation().createPath(g, 1);
            if (p != null && p.canReach()) {
                found.add(offset);
                ACCESSES.put(target.getId(), new Known<>(found, now));
                return offset;
            }
        }
        return null;
    }

    /**
     * A ladder or vine column within reach of {@code mob} that rises to (about) the height of {@code target}; shared for a
     * few seconds per target. Only climbable blocks count: no dead is given free climbing of walls.
     */
    public static @Nullable Climb climb(ServerLevel level, Mob mob, LivingEntity target) {
        long now = level.getGameTime();
        Known<@Nullable Climb> known = CLIMBS.get(target.getId());
        if (known != null && now - known.time < 100) return known.value;
        if (!probe(level)) return null;
        Climb best = null;
        double bestD = Double.MAX_VALUE;
        int r = LimboTuning.CLIMB_SEARCH, y0 = mob.getBlockY() - 1, y1 = Math.min(target.getBlockY() + 1, mob.getBlockY() + LimboTuning.PILE_MAX_HEIGHT + 2);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int x = mob.getBlockX() + dx, z = mob.getBlockZ() + dz;
            p.set(x, y0, z);
            if (!level.isLoaded(p)) continue;
            // The lowest climbable cell that a mob can step into from the ground.
            for (int y = y0; y <= y0 + 2; y++) {
                p.set(x, y, z);
                if (!level.getBlockState(p).is(BlockTags.CLIMBABLE)) continue;
                int top = y;
                while (top < y1 + 1 && level.getBlockState(p.set(x, top + 1, z)).is(BlockTags.CLIMBABLE)) top++;
                if (top < target.getBlockY() - 1) break;
                Direction wall = support(level, new BlockPos(x, top, z));
                if (wall == null) break;
                double d = mob.distanceToSqr(x + 0.5, y, z + 0.5);
                if (d < bestD) { bestD = d; best = new Climb(new BlockPos(x, y, z), new BlockPos(x, top, z), wall); }
                break;
            }
        }
        if (best != null) climbsFound++;
        CLIMBS.put(target.getId(), new Known<>(best, now));
        return best;
    }

    /** The solid side a climber presses against to climb this cell (where the ladder or vine hangs), or null. */
    static @Nullable Direction support(ServerLevel level, BlockPos cell) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = cell.relative(d);
            if (level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite())) return d;
        }
        return null;
    }

    public static void clear() { ACCESSES.clear(); CLIMBS.clear(); }

    /** Old entries are dropped now and then (called from the director's tick). */
    public static void prune(long now) {
        ACCESSES.values().removeIf(k -> now - k.time > 200);
        CLIMBS.values().removeIf(k -> now - k.time > 200);
    }
}
