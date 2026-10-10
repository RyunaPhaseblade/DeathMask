package ryuna.qdeath.jjk;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One node of a class's skill tree. {@code id} is the stable save key ({@code <class>:<skill>}); never rename it.
 * Tier 0 skills are the class's starting techniques (free, granted on selection). Actives carry an {@link Ability};
 * passives are checked with {@link Cast#has}. {@code col}/{@code row} place the node in the tree screen.
 * Cooldown / energy / resource suppliers read {@link JjkBalance} and are only used for display.
 */
public record SkillDef(String id, String classId, int tier, int cost, int level, List<String> requires,
                       @Nullable Ability ability, int col, int row, Supplier<Item> icon,
                       IntSupplier cooldown, DoubleSupplier energy, DoubleSupplier resource, boolean hold) {

    public boolean active() { return ability != null; }
    public String key() { return "jjk.quantumdeath.skill." + id.replace(':', '.'); }
    public String descKey() { return key() + ".desc"; }

    /** Fluent definition used by the class files. */
    public static final class Builder {
        private final String id, classId;
        private int tier = 1, cost = -1, level = -1, col, row;
        private List<String> requires = List.of();
        private Ability ability;
        private Supplier<Item> icon;
        private IntSupplier cooldown = () -> 0;
        private DoubleSupplier energy = () -> 0, resource = () -> 0;
        private boolean hold;

        public Builder(String classId, String name) { this.classId = classId; this.id = classId + ":" + name; }
        public Builder tier(int t) { tier = t; return this; }
        public Builder cost(int c) { cost = c; return this; }
        public Builder level(int l) { level = l; return this; }
        public Builder at(int c, int r) { col = c; row = r; return this; }
        public Builder requires(String... names) { requires = java.util.Arrays.stream(names).map(n -> classId + ":" + n).toList(); return this; }
        public Builder ability(Ability a) { ability = a; return this; }
        public Builder icon(Supplier<Item> i) { icon = i; return this; }
        public Builder cooldown(IntSupplier c) { cooldown = c; return this; }
        public Builder energy(DoubleSupplier e) { energy = e; return this; }
        public Builder resource(DoubleSupplier r) { resource = r; return this; }
        /** Shown as "hold to charge/channel" in the tooltip. */
        public Builder hold() { hold = true; return this; }

        public SkillDef build() {
            int c = cost >= 0 ? cost : JjkBalance.tierCost(tier);
            int l = level >= 0 ? level : JjkBalance.tierLevel(tier);
            return new SkillDef(id, classId, tier, c, l, requires, ability, col, row, icon, cooldown, energy, resource, hold);
        }
    }
}
