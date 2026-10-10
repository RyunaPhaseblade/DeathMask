package ryuna.qdeath.jjk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Short-lived server effects of a technique (blood orbs in flight, a telegraphed impact, an eruption...). Each one
 * belongs to its caster and is cancelled when the caster logs out, dies or changes dimension, and all of them are
 * dropped when the server stops, so nothing outlives its owner or ticks forever. Bounded: at most a few per cast.
 */
public final class Ongoing {
    private Ongoing() {}

    /** Return false when finished. */
    public interface Effect { boolean tick(ServerLevel level, int age); }

    private record Entry(UUID owner, ServerLevel level, Effect effect, int[] age) {}

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final int MAX_PER_OWNER = 24;

    public static void start(UUID owner, ServerLevel level, Effect effect) {
        if (ACTIVE.stream().filter(e -> e.owner.equals(owner)).count() >= MAX_PER_OWNER) return;
        ACTIVE.add(new Entry(owner, level, effect, new int[1]));
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Entry e : new ArrayList<>(ACTIVE)) {
            boolean keep;
            try {
                keep = e.effect.tick(e.level, e.age[0]++);
            } catch (RuntimeException ex) {
                ryuna.qdeath.Quantumdeath.LOGGER.error("Cursed technique effect failed and was dropped", ex);
                keep = false;
            }
            if (!keep) ACTIVE.remove(e);
        }
    }

    public static void cancel(UUID owner) { ACTIVE.removeIf(e -> e.owner.equals(owner)); }
    public static void clear() { ACTIVE.clear(); }
    public static int count() { return ACTIVE.size(); }
}
