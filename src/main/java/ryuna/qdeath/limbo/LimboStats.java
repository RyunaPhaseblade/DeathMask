package ryuna.qdeath.limbo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Counters read by /deathmask limbo stats and the client gametests: who struck whom (to prove that every dead in a horde
 * may attack the same victim) and how many Putrescent corpses burst. Cheap, and reset by the tests.
 */
public final class LimboStats {
    private LimboStats() {}

    /** victim -> dead that have struck it (blow landed or blocked). */
    public static final Map<UUID, Set<UUID>> STRIKERS = new HashMap<>();
    public static int strikes, bursts;

    public static void strike(UUID attacker, UUID victim) {
        strikes++;
        STRIKERS.computeIfAbsent(victim, k -> new HashSet<>()).add(attacker);
    }

    public static void reset() {
        STRIKERS.clear();
        strikes = 0;
        bursts = 0;
    }
}
