package ryuna.qdeath.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;
import java.util.Set;

public final class SkillProgress {
    private int points;
    private final Set<SkillId> unlocked = EnumSet.noneOf(SkillId.class);

    public int points() { return points; }
    public Set<SkillId> unlocked() { return Set.copyOf(unlocked); }
    public boolean isUnlocked(SkillId skill) { return unlocked.contains(skill); }
    public boolean canUnlock(SkillId skill) { return points > 0 && !isUnlocked(skill) && skill.prerequisite().map(unlocked::contains).orElse(true); }
    public boolean unlock(SkillId skill) {
        if (!canUnlock(skill)) return false;
        points--;
        unlocked.add(skill);
        return true;
    }
    public void addPoints(int amount) { if (amount > 0) points += amount; }
    public void reset() { points = 0; unlocked.clear(); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("points", points);
        ListTag skills = new ListTag();
        unlocked.forEach(skill -> skills.add(StringTag.valueOf(skill.name())));
        tag.put("unlocked", skills);
        return tag;
    }
    public void load(CompoundTag tag) {
        points = Math.max(0, tag.getIntOr("points", 0));
        unlocked.clear();
        for (var value : tag.getListOrEmpty("unlocked")) {
            if (value instanceof StringTag string) {
                try { unlocked.add(SkillId.valueOf(string.value())); } catch (IllegalArgumentException ignored) { }
            }
        }
    }
    /** Persistence format used by Minecraft 26.2 player data. */
    public void writeTo(ValueOutput output) {
        output.putInt("points", points);
        output.putIntArray("unlocked", unlocked.stream().mapToInt(Enum::ordinal).toArray());
    }
    public void readFrom(ValueInput input) {
        points = Math.max(0, input.getIntOr("points", 0));
        unlocked.clear();
        for (int ordinal : input.getIntArray("unlocked").orElseGet(() -> new int[0])) {
            if (ordinal >= 0 && ordinal < SkillId.values().length) unlocked.add(SkillId.values()[ordinal]);
        }
    }
}
