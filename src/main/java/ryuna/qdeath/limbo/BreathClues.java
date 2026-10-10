package ryuna.qdeath.limbo;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Breath left behind by the living, which Mourning Hounds follow, and Breath Lures that imitate it.
 * Trails are short-lived and only hold where someone actually walked: nothing here updates across the map.
 */
public final class BreathClues {
    private BreathClues() {}

    public record Point(ResourceKey<Level> dim, Vec3 pos, long time) {}

    private static final Map<UUID, ArrayDeque<Point>> TRAILS = new HashMap<>();
    private static final List<Point> LURES = new ArrayList<>();

    static void breathe(LivingEntity e) {
        ArrayDeque<Point> trail = TRAILS.computeIfAbsent(e.getUUID(), k -> new ArrayDeque<>());
        trail.addLast(new Point(e.level().dimension(), e.position(), e.level().getGameTime()));
        while (trail.size() > LimboTuning.TRAIL_POINTS) trail.removeFirst();
    }

    /**
     * The freshest breath of {@code who} newer than {@code after} and within {@code reach} of {@code from}, or null when the trail
     * has gone cold (too old, broken by a long gap, or in another dimension).
     */
    public static @Nullable Point follow(ServerLevel level, UUID who, Vec3 from, long after, double reach) {
        ArrayDeque<Point> trail = TRAILS.get(who);
        if (trail == null) return null;
        long now = level.getGameTime();
        Point best = null;
        for (Point p : trail) {
            if (p.dim != level.dimension() || p.time <= after || now - p.time > LimboTuning.TRAIL_LIFE) continue;
            if (p.pos.distanceToSqr(from) <= reach * reach && (best == null || p.time > best.time)) best = p;
        }
        return best;
    }

    public static void addLure(ServerLevel level, Vec3 pos) {
        LURES.add(new Point(level.dimension(), pos, level.getGameTime() + LimboTuning.LURE_LIFE));
    }

    /** Nearest live lure within {@code radius}; a lure's "time" is when it fades. */
    public static @Nullable Vec3 nearestLure(ServerLevel level, Vec3 from, double radius) {
        long now = level.getGameTime();
        LURES.removeIf(p -> p.time < now);
        Vec3 best = null;
        double bestD = radius * radius;
        for (Point p : LURES) {
            if (p.dim != level.dimension()) continue;
            double d = p.pos.distanceToSqr(from);
            if (d <= bestD) { bestD = d; best = p.pos; }
        }
        return best;
    }

    static void forget(UUID who) { TRAILS.remove(who); }

    static void clear() { TRAILS.clear(); LURES.clear(); }
}
