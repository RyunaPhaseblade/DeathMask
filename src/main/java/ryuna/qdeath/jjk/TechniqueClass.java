package ryuna.qdeath.jjk;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A character archetype: its stable id, colour, optional class resource (Choso's Blood) and skill tree.
 * New characters are added by defining one more of these in {@link JjkClasses}; the screen, HUD, progression,
 * commands and persistence are all generic.
 */
public final class TechniqueClass {
    public final String id;
    public final int color;
    public final Supplier<Item> icon;
    /** Lang key of the class resource shown on the HUD, or null when the class has none. */
    public final @Nullable String resourceKey;
    public final int resourceColor;
    public final ClassHooks hooks;
    private final List<SkillDef> skills = new ArrayList<>();
    private final Map<String, SkillDef> byId = new LinkedHashMap<>();

    public TechniqueClass(String id, int color, Supplier<Item> icon, @Nullable String resourceKey, int resourceColor, ClassHooks hooks) {
        this.id = id;
        this.hooks = hooks;
        this.color = color;
        this.icon = icon;
        this.resourceKey = resourceKey;
        this.resourceColor = resourceColor;
    }

    public SkillDef add(SkillDef.Builder b) {
        SkillDef s = b.build();
        skills.add(s);
        byId.put(s.id(), s);
        return s;
    }

    public SkillDef.Builder skill(String name) { return new SkillDef.Builder(id, name); }

    public List<SkillDef> skills() { return Collections.unmodifiableList(skills); }
    public @Nullable SkillDef get(String fullId) { return byId.get(fullId); }
    public List<SkillDef> starters() { return skills.stream().filter(s -> s.tier() == 0).toList(); }
    public List<SkillDef> actives() { return skills.stream().filter(SkillDef::active).toList(); }

    public String nameKey() { return "jjk.quantumdeath.class." + id; }
    public String techniqueKey() { return nameKey() + ".technique"; }
    public String descKey() { return nameKey() + ".desc"; }
    public String styleKey() { return nameKey() + ".style"; }
}
