package ryuna.qdeath.jjk;

/**
 * The server-side behaviour of an active skill. Input arrives as a press, the ticks it is held, a release, and the
 * secondary key. Everything here runs on the server: costs, cooldowns, damage and movement are decided here only.
 */
public interface Ability {
    default void press(Cast c) {}
    default void hold(Cast c, int ticks) {}
    default void release(Cast c, int ticks) {}
    default void secondary(Cast c) {}
    /** Whether a press while the cooldown runs is still delivered (e.g. a toggle that can always be switched off). */
    default boolean ignoresCooldown(Cast c) { return false; }
}
