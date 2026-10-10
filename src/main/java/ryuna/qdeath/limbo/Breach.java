package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Breach Ticker's siege: a directed, selective opening instead of an explosion. From the face the Ticker clings to,
 * a corridor TICKER_CORRIDOR blocks long and 3 x 3 across runs along the direction fixed when it attached (Ticker ->
 * barrier -> pursued point: horizontal, vertical or diagonal). Each of the nine lanes advances through air and opens
 * blocks a player placed that pass the protections; the first solid block that is not authorized ends that lane, so
 * natural rock shields whatever lies behind it. At most TICKER_MAX_BLOCKS go, with their normal drops, no fire, and
 * nothing at all happens to entities. Authorization is checked again at the moment of the burst.
 */
public final class Breach {
    private Breach() {}

    /** A barrier found toward a goal: the block, and the face of it the Ticker can reach. */
    public record Barrier(BlockPos pos, Direction face) {}

    public static int breaches, opened;

    /**
     * The first player-placed, authorized block standing between the Ticker and {@code goal} (what keeps it from the place
     * it hunts), looked for along two lines (low and high), else around the goal; null when nothing there may be opened.
     */
    public static @Nullable Barrier toward(ServerLevel level, Entity ticker, Vec3 goal) {
        for (double h : new double[]{0.4, 1.4}) {
            Vec3 from = ticker.position().add(0, h, 0);
            Vec3 to = goal.add(0, Math.min(h + 0.6, 1.6), 0);
            if (from.distanceToSqr(to) > 28 * 28) continue;
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ticker));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos p = hit.getBlockPos();
            Direction face = hit.getDirection();
            if (!Provenance.breachable(level, p)) continue;
            if (face.getAxis().isHorizontal()) p = lowestFace(level, p, face);
            return new Barrier(p.immutable(), face);
        }
        return around(level, ticker, goal);
    }

    /**
     * The straight line meets something the Ticker may not open (an unknown wall, rock): what hides the goal is looked at
     * from the inside out in eight directions, and the nearest outer face of a player-placed block that it can stand
     * before is taken (the corridor then stops at the first layer it may not open, as always).
     */
    private static @Nullable Barrier around(ServerLevel level, Entity ticker, Vec3 goal) {
        Vec3 from = goal.add(0, 0.6, 0);
        Barrier best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            Vec3 to = from.add(Math.cos(ang) * 8, 0, Math.sin(ang) * 8);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ticker));
            if (hit.getType() != HitResult.Type.BLOCK) continue; // open on that side
            Direction out = hit.getDirection().getOpposite();
            if (!out.getAxis().isHorizontal()) continue;
            BlockPos p = hit.getBlockPos();
            for (int k = 0; k < 4 && !level.getBlockState(p.relative(out)).getCollisionShape(level, p.relative(out)).isEmpty(); k++) p = p.relative(out);
            if (!Provenance.breachable(level, p)) continue;
            p = lowestFace(level, p, out);
            if (!Processions.standable(level, p.relative(out))) continue;
            double d = ticker.distanceToSqr(Vec3.atBottomCenterOf(p.relative(out)));
            if (d < bestD) { bestD = d; best = new Barrier(p.immutable(), out); }
        }
        return best;
    }

    /** A face high on a wall cannot be reached: the same face lower down, where the ground meets the wall, can. */
    private static BlockPos lowestFace(ServerLevel level, BlockPos p, Direction face) {
        BlockPos low = p;
        while (low.getY() > p.getY() - 6 && !Processions.standable(level, low.relative(face)) && Provenance.breachable(level, low.below())) low = low.below();
        return Processions.standable(level, low.relative(face)) ? low : p;
    }

    /** The blocks the burst opens, nearest first, revalidated now. */
    public static List<BlockPos> corridor(ServerLevel level, Vec3 start, Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 u = Math.abs(d.y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 v = d.cross(u).normalize();
        Set<BlockPos> picked = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            BlockPos last = null;
            for (double t = 0; t <= LimboTuning.TICKER_CORRIDOR; t += 0.4) {
                Vec3 p = start.add(d.scale(t)).add(u.scale(i)).add(v.scale(j));
                BlockPos bp = BlockPos.containing(p);
                if (bp.equals(last)) continue;
                last = bp;
                if (picked.contains(bp)) continue;
                BlockState s = level.getBlockState(bp);
                if (s.isAir() || (s.getCollisionShape(level, bp).isEmpty() && !Provenance.isPlayerPlaced(level, bp))) continue;
                if (Provenance.breachable(level, bp)) { picked.add(bp); out.add(bp); continue; }
                if (!s.getCollisionShape(level, bp).isEmpty()) break; // an unauthorized solid block stops this lane
            }
        }
        out.sort(Comparator.comparingDouble(b -> Vec3.atCenterOf(b).distanceToSqr(start)));
        return out.size() > LimboTuning.TICKER_MAX_BLOCKS ? out.subList(0, LimboTuning.TICKER_MAX_BLOCKS) : out;
    }

    /** Opens the corridor: every block revalidated, broken with its drops. Returns how many went. */
    public static int open(ServerLevel level, Entity ticker, Vec3 start, Vec3 dir) {
        int n = 0;
        breaches++;
        for (BlockPos p : corridor(level, start, dir)) {
            if (!Provenance.breachable(level, p)) continue;
            if (level.destroyBlock(p, true, ticker)) n++;
        }
        opened += n;
        return n;
    }
}
