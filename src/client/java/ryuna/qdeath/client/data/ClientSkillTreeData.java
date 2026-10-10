package ryuna.qdeath.client.data;

import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillProgress;

import java.util.EnumSet;
import java.util.List;

/** Client-side cache of the server-authoritative general skill tree (points, unlocked nodes, the server's costs). */
public final class ClientSkillTreeData {
    public static final ClientSkillTreeData INSTANCE = new ClientSkillTreeData();

    private int points;
    private final EnumSet<SkillId> unlocked = EnumSet.noneOf(SkillId.class);
    private int[] costs = new int[0];

    private ClientSkillTreeData() { }

    public void update(int newPoints, List<String> ids, int[] newCosts) {
        points = newPoints;
        unlocked.clear();
        for (String id : ids) SkillId.byId(id).ifPresent(unlocked::add);
        costs = newCosts;
    }

    public int points() { return points; }
    public int cost(SkillId skill) { return skill.ordinal() < costs.length ? costs[skill.ordinal()] : skill.cost(); }
    public int spentPoints() { return unlocked.stream().mapToInt(this::cost).sum(); }
    public int totalCost() { int t = 0; for (SkillId s : SkillId.values()) t += cost(s); return t; }
    public boolean isUnlocked(SkillId skill) { return unlocked.contains(skill); }
    public EnumSet<SkillId> unlocked() { return unlocked; }

    public SkillProgress.Status status(SkillId skill) {
        if (unlocked.contains(skill)) return SkillProgress.Status.UNLOCKED;
        if (!unlocked.containsAll(skill.prerequisites())) return SkillProgress.Status.NO_PREREQ;
        return points >= cost(skill) ? SkillProgress.Status.AVAILABLE : SkillProgress.Status.NO_POINTS;
    }

    public boolean isAvailable(SkillId skill) { return status(skill) == SkillProgress.Status.AVAILABLE; }
}
