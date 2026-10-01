package ryuna.qdeath.intro;

/** Persistent player state for the one-time introductory book delivery. */
public interface PlayerIntroHolder {
    boolean quantumdeath$hasReceivedNotes();
    void quantumdeath$setReceivedNotes(boolean received);
}
