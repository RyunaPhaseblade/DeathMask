package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;

/**
 * Limited siege. Only the Walking Ossuary and the Wake Bearer may break defences, only the ones listed in the
 * {@code quantumdeath:siege_breakable} block tag (wooden doors, fences, fence gates, funeral barricades), only with
 * {@code mobGriefing} on, and only after a long, visible and interruptible preparation. Stone, chests, portals
 * and votive lanterns are never in reach.
 */
public final class Siege {
    private Siege() {}

    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, Quantumdeath.id("siege_breakable"));
    /**
     * Weak obstacles any humanoid dead able to strike may break when one blocks its way: glass, panes and leaves, natural
     * or placed ({@code quantumdeath:weak_barrier}). Kept apart from the heavy siege above.
     */
    public static final TagKey<Block> WEAK = TagKey.create(Registries.BLOCK, Quantumdeath.id("weak_barrier"));

    public static boolean allowed(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    /** An authorized defence between {@code mob} and {@code goal}, at most two blocks ahead, at foot or head height. */
    public static @Nullable BlockPos barrierToward(ServerLevel level, Mob mob, Vec3 goal) {
        Vec3 dir = new Vec3(goal.x - mob.getX(), 0, goal.z - mob.getZ());
        if (dir.lengthSqr() < 1.0e-4) return null;
        dir = dir.normalize();
        for (double step = 0.6; step <= 2.2; step += 0.4) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = BlockPos.containing(mob.getX() + dir.x * step, mob.getY() + 0.2 + dy, mob.getZ() + dir.z * step);
                if (level.getBlockState(p).is(BREAKABLE)) return p;
            }
        }
        return null;
    }

    /** A weak obstacle right in front of {@code mob} toward {@code goal}, at foot or head height (or just above, for a climber). */
    /** A weak block in what encloses a goal, and the open ground outside it to stand on while breaking it. */
    public record Access(BlockPos block, BlockPos stand) {}

    /**
     * A weak block in what hides {@code goal} (a window in a sealed shelter), looked for from the inside out in eight
     * directions at two heights; the one whose outer side is nearest to {@code mob}. Null when there is none.
     */
    public static @Nullable Access weakAround(ServerLevel level, Mob mob, Vec3 goal) {
        Access best = null;
        double bestD = Double.MAX_VALUE;
        for (double h : new double[]{0.6, 1.6}) {
            Vec3 from = goal.add(0, h, 0);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4;
                BlockHitResult hit = level.clip(new ClipContext(from, from.add(Math.cos(ang) * 8, 0, Math.sin(ang) * 8), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
                if (hit.getType() != HitResult.Type.BLOCK) continue;
                BlockPos p = hit.getBlockPos();
                Direction out = hit.getDirection().getOpposite();
                var s = level.getBlockState(p);
                if (!out.getAxis().isHorizontal() || !s.is(WEAK) || Provenance.protectedBlock(level, p, s)) continue;
                BlockPos stand = p.relative(out);
                if (!Processions.standable(level, stand)) stand = stand.below();
                if (!Processions.standable(level, stand)) continue;
                double d = mob.distanceToSqr(Vec3.atBottomCenterOf(stand));
                if (d < bestD) { bestD = d; best = new Access(p.immutable(), stand.immutable()); }
            }
        }
        return best;
    }

    public static @Nullable BlockPos weakToward(ServerLevel level, Mob mob, Vec3 goal) {
        Vec3 dir = new Vec3(goal.x - mob.getX(), 0, goal.z - mob.getZ());
        if (dir.lengthSqr() < 1.0e-4) return null;
        dir = dir.normalize();
        for (double step = 0.5; step <= 1.7; step += 0.4) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = BlockPos.containing(mob.getX() + dir.x * step, mob.getY() + 0.2 + dy, mob.getZ() + dir.z * step);
                var s = level.getBlockState(p);
                if (s.is(WEAK) && !Provenance.protectedBlock(level, p, s)) return p;
                if (!s.getCollisionShape(level, p).isEmpty() && dy == 0) break;
            }
        }
        return null;
    }
}
