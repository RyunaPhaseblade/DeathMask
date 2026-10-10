package ryuna.qdeath.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Mixing of the horde's voices. Close voices stay distinct but limited: at most {@link #MAX_NEAR} common vocalizations
 * within {@link #RADIUS} blocks in any {@link #WINDOW}-tick window; the rest of a crowd is carried by the horde murmur
 * the director plays from its centre. Specialists' signals never go through this budget, so a Crier's inhale or a
 * Breaker's grunt is always heard over the crowd.
 */
public final class LimboVoices {
    private LimboVoices() {}

    static final int MAX_NEAR = 4, WINDOW = 30, SIZE = 64;
    static final double RADIUS = 18;
    private static final double[] X = new double[SIZE], Y = new double[SIZE], Z = new double[SIZE];
    private static final long[] T = new long[SIZE];
    private static int head;

    /** True (and recorded) when a common voice may play at {@code at} now. */
    public static boolean allow(ServerLevel level, Vec3 at) {
        long now = level.getGameTime();
        int near = 0;
        for (int i = 0; i < SIZE; i++) {
            if (now - T[i] > WINDOW) continue;
            double dx = X[i] - at.x, dy = Y[i] - at.y, dz = Z[i] - at.z;
            if (dx * dx + dy * dy + dz * dz < RADIUS * RADIUS && ++near >= MAX_NEAR) return false;
        }
        X[head] = at.x;
        Y[head] = at.y;
        Z[head] = at.z;
        T[head] = now;
        head = (head + 1) % SIZE;
        return true;
    }

    public static void clear() { java.util.Arrays.fill(T, Long.MIN_VALUE / 2); }

    static { clear(); }
}
