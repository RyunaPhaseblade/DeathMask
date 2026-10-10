package ryuna.qdeath.limbo;

/**
 * Detection states shared by every dead of the Limbo, in the order of the redesign document:
 * rest, suspicion, recognition, call, pursuit, search, dispersal. Each has a visible (and audible) signal.
 */
public enum Mind {
    /** Wanders, waits or walks in a procession; no confirmed target. */
    REST,
    /** Stops, turns and listens toward a clue. */
    SUSPICION,
    /** Faces a living creature it has confirmed. */
    RECOGNITION,
    /** Gesture or voice that sends nearby dead to the last known position (the Crier's long inhale). */
    CALL,
    /** Follows a located target with its species' own tools. */
    PURSUIT,
    /** Checks the last known position and the clues it has (the Hound follows breath). */
    SEARCH,
    /** Gives up, drifts away and settles back to rest. */
    DISPERSAL;

    public boolean alert() { return this == RECOGNITION || this == CALL || this == PURSUIT; }
}
