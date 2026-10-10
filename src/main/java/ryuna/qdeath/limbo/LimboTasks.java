package ryuna.qdeath.limbo;

import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local tasks of a horde, reserved with an expiry so two dead never do the same job: a host a parasite is going for,
 * a barrier a Ticker is opening, a wall segment a mound is forming against. A reservation that is not renewed lapses.
 */
public final class LimboTasks {
    private LimboTasks() {}

    private record Hold(UUID owner, long until) {}

    private static final Map<String, Hold> HOLDS = new HashMap<>();
    private static long lastPrune;

    private static String key(ServerLevel level, String task) { return level.dimension().identifier() + "|" + task; }

    /** True when nobody else holds {@code task} (it is free, lapsed, or already ours). */
    public static boolean free(ServerLevel level, String task, UUID owner) {
        Hold h = HOLDS.get(key(level, task));
        return h == null || h.owner.equals(owner) || h.until < level.getGameTime();
    }

    /** Takes or renews {@code task} for {@code ticks}; false when someone else holds it. */
    public static boolean reserve(ServerLevel level, String task, UUID owner, int ticks) {
        long now = level.getGameTime();
        if (now - lastPrune > 200) { HOLDS.values().removeIf(h -> h.until < now); lastPrune = now; }
        if (!free(level, task, owner)) return false;
        HOLDS.put(key(level, task), new Hold(owner, now + ticks));
        return true;
    }

    public static void release(ServerLevel level, String task, UUID owner) {
        HOLDS.computeIfPresent(key(level, task), (k, h) -> h.owner.equals(owner) ? null : h);
    }

    public static void clear() { HOLDS.clear(); }
}
