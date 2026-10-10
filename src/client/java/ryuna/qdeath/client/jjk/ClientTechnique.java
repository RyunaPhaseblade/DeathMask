package ryuna.qdeath.client.jjk;

import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.JjkClasses;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.SkillDef;
import ryuna.qdeath.jjk.TechniqueClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Client-side mirror of the server's cursed-technique progress and state. Display only; never trusted. */
public final class ClientTechnique {
    private ClientTechnique() {}

    public static String classId = "";
    public static int level, xp, xpNext, tp;
    public static final Set<String> unlocked = new LinkedHashSet<>();
    public static String selected = "";
    public static JjkNet.State state = new JjkNet.State(0, 0, 0, 0, 0, List.of(), List.of());
    public static long stateTick;
    public static int flashColor, flashTicks, flashMax;
    public static long clientTicks;

    public static void progress(JjkNet.Progress p) {
        classId = p.classId();
        level = p.level();
        xp = p.xp();
        xpNext = p.xpNext();
        tp = p.tp();
        unlocked.clear();
        unlocked.addAll(p.unlocked());
        selected = p.selected();
    }

    public static void state(JjkNet.State s) {
        state = s;
        stateTick = clientTicks;
    }

    public static @Nullable TechniqueClass cls() { return classId.isEmpty() ? null : JjkClasses.get(classId); }

    public static List<SkillDef> actives() {
        TechniqueClass c = cls();
        List<SkillDef> out = new ArrayList<>();
        if (c != null) for (SkillDef s : c.actives()) if (unlocked.contains(s.id())) out.add(s);
        return out;
    }

    /** Remaining cooldown in ticks, counted down locally between state packets. */
    public static int cooldownLeft(String id) {
        for (JjkNet.Cooldown c : state.cooldowns())
            if (c.skill().equals(id)) return (int) Math.max(0, c.left() - (clientTicks - stateTick));
        return 0;
    }

    public static float cooldownFraction(String id) {
        for (JjkNet.Cooldown c : state.cooldowns())
            if (c.skill().equals(id)) return Math.max(0, c.left() - (clientTicks - stateTick)) / (float) Math.max(1, c.total());
        return 0;
    }

    public static JjkNet.@Nullable Indicator indicator(String key) {
        for (JjkNet.Indicator i : state.indicators()) if (i.key().equals(key)) return i;
        return null;
    }
}
