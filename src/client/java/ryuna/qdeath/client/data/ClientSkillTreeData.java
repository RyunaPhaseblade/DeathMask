package ryuna.qdeath.client.data;

import ryuna.qdeath.skills.SkillId;

import java.util.EnumSet;

/** Client-side cache of the server-authoritative skill tree. */
public final class ClientSkillTreeData {
    public static final ClientSkillTreeData INSTANCE = new ClientSkillTreeData();

    private int points;
    private final EnumSet<SkillId> unlocked = EnumSet.noneOf(SkillId.class);

    private ClientSkillTreeData() { }

    public void update(int newPoints, EnumSet<SkillId> newUnlocked) {
        points = newPoints;
        unlocked.clear();
        unlocked.addAll(newUnlocked);
    }

    public int points() { return points; }
    public int unlockedCount() { return unlocked.size(); }
    public int spentPoints() { return unlocked.size(); }
    public int totalSkills() { return SkillId.values().length; }
    public float spentProgress() { return (float) spentPoints() / totalSkills(); }
    public boolean isUnlocked(SkillId skill) { return unlocked.contains(skill); }
    public boolean isAvailable(SkillId skill) {
        return points > 0 && !isUnlocked(skill) && skill.prerequisite().map(unlocked::contains).orElse(true);
    }
}
