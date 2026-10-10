package ryuna.qdeath.jjk.naoya;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The 24-frame plan of one Projection: a straight horizontal line that steps up small ledges and follows the ground
 * down, decided entirely from the world before the move starts. The server uses it to move Naoya; the client uses the
 * very same function to preview the frames (and where the plan would break) before he commits.
 */
public final class ProjectionPath {
    private ProjectionPath() {}

    /** {@code blockedAt}: index of the first frame that collides with terrain, or -1 if the whole plan is clear. */
    public record Plan(Vec3[] frames, int blockedAt, Vec3 dir) {
        public boolean clear() { return blockedAt < 0; }
        public Vec3 last() { return frames[blockedAt < 0 ? frames.length - 1 : Math.max(0, blockedAt - 1)]; }
    }

    public static Vec3 horizontal(Vec3 look) {
        Vec3 h = new Vec3(look.x, 0, look.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    public static Plan plan(Level level, Entity e, Vec3 start, Vec3 dir, double distance, int frames, double stepUp) {
        Vec3 h = horizontal(dir);
        EntityDimensions dims = e.getDimensions(Pose.STANDING);
        Vec3[] out = new Vec3[frames + 1];
        out[0] = start;
        double step = distance / frames;
        Vec3 cur = start;
        // Frames spent without ground below: a plan can cross a gap, but over open air it sinks ever faster,
        // so chaining projections is never a way to fly. A plan that starts in the air is already falling.
        int air = free(level, e, dims, start.add(0, -0.2, 0)) ? AIR_START : 0;
        for (int i = 1; i <= frames; i++) {
            Vec3 placed = fit(level, e, dims, cur.add(h.scale(step)), stepUp);
            if (placed == null) {
                for (int k = i; k <= frames; k++) out[k] = cur;
                return new Plan(out, i, h);
            }
            Vec3 settled = settle(level, e, dims, placed, Math.max(1.0, step));
            if (settled == placed && free(level, e, dims, placed.add(0, -0.2, 0))) {
                air++;
                Vec3 sunk = placed.add(0, -Math.min(1.5, SINK * air), 0);
                if (!free(level, e, dims, sunk)) {
                    for (int k = i; k <= frames; k++) out[k] = cur;
                    return new Plan(out, i, h);
                }
                settled = sunk;
            } else air = 0;
            cur = settled;
            out[i] = cur;
        }
        return new Plan(out, -1, h);
    }

    /** Blocks of sink per airborne frame, growing each frame (about 6 blocks over a full plan in open air). */
    private static final double SINK = 0.02;
    private static final int AIR_START = 8;

    private static boolean free(Level level, Entity e, EntityDimensions dims, Vec3 p) {
        AABB box = dims.makeBoundingBox(p).deflate(1e-3);
        return !level.isOutsideBuildHeight(net.minecraft.core.BlockPos.containing(p)) && level.noCollision(e, box);
    }

    private static Vec3 fit(Level level, Entity e, EntityDimensions dims, Vec3 p, double stepUp) {
        for (double dy = 0; dy <= stepUp + 1e-6; dy += 0.5) {
            Vec3 q = p.add(0, dy, 0);
            if (free(level, e, dims, q)) return q;
        }
        return null;
    }

    /** Drops onto the ground below (at most {@code maxDrop} per frame); over a gap the line just carries on. */
    private static Vec3 settle(Level level, Entity e, EntityDimensions dims, Vec3 p, double maxDrop) {
        double d = 0;
        while (d < maxDrop + 2 && free(level, e, dims, p.add(0, -(d + 0.125), 0))) d += 0.125;
        if (d >= maxDrop + 2) return p;
        return p.add(0, -Math.min(d, maxDrop), 0);
    }
}
