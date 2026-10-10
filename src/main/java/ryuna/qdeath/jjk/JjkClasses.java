package ryuna.qdeath.jjk;

import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.choso.Choso;
import ryuna.qdeath.jjk.jogo.Jogo;
import ryuna.qdeath.jjk.naoya.Naoya;
import ryuna.qdeath.jjk.todo.Todo;
import ryuna.qdeath.jjk.uraume.Uraume;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every playable class, in selection-screen order. Add a future character with one more {@link #add} line. */
public final class JjkClasses {
    private JjkClasses() {}

    private static final Map<String, TechniqueClass> CLASSES = new LinkedHashMap<>();

    static {
        add(Choso.define());
        add(Uraume.define());
        add(Jogo.define());
        add(Naoya.define());
        add(Todo.define());
    }

    private static void add(TechniqueClass c) { CLASSES.put(c.id, c); }

    public static Collection<TechniqueClass> all() { return CLASSES.values(); }
    public static @Nullable TechniqueClass get(String id) { return CLASSES.get(id); }

    public static @Nullable SkillDef skill(String id) {
        int colon = id.indexOf(':');
        TechniqueClass c = colon > 0 ? CLASSES.get(id.substring(0, colon)) : null;
        return c == null ? null : c.get(id);
    }

    public enum Status { UNLOCKED, AVAILABLE, NO_TP, NO_PREREQ, NO_LEVEL, LOCKED }

    /** The unlock rule, shared by the server (authoritative) and the tree screen (display): level + prerequisites + TP. */
    public static Status status(SkillDef s, int level, int tp, Collection<String> unlocked) {
        if (unlocked.contains(s.id())) return Status.UNLOCKED;
        boolean prereq = unlocked.containsAll(s.requires());
        boolean lvl = level >= s.level();
        if (!prereq && !lvl) return Status.LOCKED;
        if (!prereq) return Status.NO_PREREQ;
        if (!lvl) return Status.NO_LEVEL;
        return tp >= s.cost() ? Status.AVAILABLE : Status.NO_TP;
    }

    public static void init() { /* class load defines everything */ }
}
