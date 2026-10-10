package ryuna.qdeath.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.mojang.serialization.Codec;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * General points and unlocked nodes of one player. Points come from vanilla level-ups and are never technique points.
 * Saved by stable id; older saves (an int array of the nine original ordinals) are migrated through
 * {@link SkillId#LEGACY_ORDER}. Ids this version does not know are kept untouched.
 */
public final class SkillProgress {
    public enum Status { UNLOCKED, AVAILABLE, NO_PREREQ, NO_POINTS }

    private int points;
    private final Set<SkillId> unlocked = EnumSet.noneOf(SkillId.class);
    private final Set<String> unknown = new LinkedHashSet<>();

    public int points() { return points; }
    public Set<SkillId> unlocked() { return Set.copyOf(unlocked); }
    public boolean isUnlocked(SkillId skill) { return unlocked.contains(skill); }

    public static Status status(SkillId skill, int points, Set<SkillId> unlocked) {
        if (unlocked.contains(skill)) return Status.UNLOCKED;
        if (!unlocked.containsAll(skill.prerequisites())) return Status.NO_PREREQ;
        return points >= skill.cost() ? Status.AVAILABLE : Status.NO_POINTS;
    }

    public Status status(SkillId skill) { return status(skill, points, unlocked); }
    public boolean canUnlock(SkillId skill) { return status(skill) == Status.AVAILABLE; }

    public boolean unlock(SkillId skill) {
        if (!canUnlock(skill)) return false;
        points -= skill.cost();
        unlocked.add(skill);
        return true;
    }

    /** Administrative unlock (commands): no cost, prerequisites included. */
    public void grant(SkillId skill) {
        for (SkillId p : skill.prerequisites()) grant(p);
        unlocked.add(skill);
    }

    public int spent() { return unlocked.stream().mapToInt(SkillId::cost).sum(); }
    public void addPoints(int amount) { if (amount > 0) points += amount; }
    public void reset() { points = 0; unlocked.clear(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("points", points);
        ListTag skills = new ListTag();
        unlocked.forEach(skill -> skills.add(StringTag.valueOf(skill.id)));
        unknown.forEach(id -> skills.add(StringTag.valueOf(id)));
        tag.put("ids", skills);
        return tag;
    }

    public void load(CompoundTag tag) {
        points = Math.max(0, tag.getIntOr("points", 0));
        unlocked.clear();
        unknown.clear();
        for (var value : tag.getListOrEmpty("ids")) if (value instanceof StringTag s) add(s.value());
    }

    private void add(String id) {
        SkillId.byId(id).ifPresentOrElse(unlocked::add, () -> unknown.add(id));
    }

    public void writeTo(ValueOutput output) {
        output.putInt("points", points);
        var ids = output.list("ids", Codec.STRING);
        unlocked.forEach(s -> ids.add(s.id));
        unknown.forEach(ids::add);
        // Still written for older versions of the mod: only the nine nodes they know, by their original ordinal.
        output.putIntArray("unlocked", unlocked.stream().filter(SkillId::legacy).mapToInt(SkillId.LEGACY_ORDER::indexOf).toArray());
    }

    public void readFrom(ValueInput input) {
        points = Math.max(0, input.getIntOr("points", 0));
        unlocked.clear();
        unknown.clear();
        var ids = input.list("ids", Codec.STRING);
        if (ids.isPresent()) {
            ids.get().forEach(this::add);
            return;
        }
        // Migration from the ordinal format: a fixed table of the nine original nodes, never the current enum order.
        for (int ordinal : input.getIntArray("unlocked").orElseGet(() -> new int[0]))
            if (ordinal >= 0 && ordinal < SkillId.LEGACY_ORDER.size()) unlocked.add(SkillId.LEGACY_ORDER.get(ordinal));
    }
}
