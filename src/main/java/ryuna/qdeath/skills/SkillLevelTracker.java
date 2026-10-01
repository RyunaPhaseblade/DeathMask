package ryuna.qdeath.skills;

/** Server-only state used to award a point whenever the observed XP level rises. */
public interface SkillLevelTracker {
    boolean quantumdeath$updateLevelTracking(int currentLevel);
}
