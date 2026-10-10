package ryuna.qdeath.jjk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import ryuna.qdeath.Quantumdeath;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's persistent cursed-technique state, saved with the player and kept through death, logout, restarts and
 * dimension changes. Progress is kept per class (by stable class id), so changing class never destroys the other.
 * Skills are stored by stable string id; ids no longer defined are kept untouched (a later version may bring them
 * back) and simply ignored. Cooldowns are absolute server game times, so relogging never resets them.
 */
public final class TechniqueData {
    public String classId = "";
    public final Map<String, ClassProgress> classes = new HashMap<>();
    public final Map<String, Long> cooldowns = new HashMap<>();
    public float energy;
    /** The class resource (Choso's Blood). */
    public float resource;
    public String selected = "";

    public static final class ClassProgress {
        public int level = 1, xp, tp;
        public final Set<String> unlocked = new LinkedHashSet<>();

        public ClassProgress() {}

        ClassProgress(int level, int xp, int tp, List<String> unlocked) {
            this.level = Math.max(1, level);
            this.xp = Math.max(0, xp);
            this.tp = Math.max(0, tp);
            this.unlocked.addAll(unlocked);
        }

        static final Codec<ClassProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 1).forGetter(p -> p.level),
                Codec.INT.optionalFieldOf("xp", 0).forGetter(p -> p.xp),
                Codec.INT.optionalFieldOf("tp", 0).forGetter(p -> p.tp),
                Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(p -> List.copyOf(p.unlocked))
        ).apply(i, ClassProgress::new));
    }

    public TechniqueData() {}

    private TechniqueData(String classId, Map<String, ClassProgress> classes, Map<String, Long> cooldowns, float energy, float resource, String selected) {
        this.classId = classId;
        this.classes.putAll(classes);
        this.cooldowns.putAll(cooldowns);
        this.energy = Math.max(0, energy);
        this.resource = Math.max(0, resource);
        this.selected = selected;
    }

    public static final Codec<TechniqueData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("class", "").forGetter(d -> d.classId),
            Codec.unboundedMap(Codec.STRING, ClassProgress.CODEC).optionalFieldOf("classes", Map.of()).forGetter(d -> d.classes),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
            Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(d -> d.energy),
            Codec.FLOAT.optionalFieldOf("resource", 0f).forGetter(d -> d.resource),
            Codec.STRING.optionalFieldOf("selected", "").forGetter(d -> d.selected)
    ).apply(i, TechniqueData::new));

    public static final AttachmentType<TechniqueData> TYPE = AttachmentRegistry.create(Quantumdeath.id("cursed_technique"),
            b -> b.persistent(CODEC).copyOnDeath().initializer(TechniqueData::new));

    public boolean hasClass() { return !classId.isEmpty() && JjkClasses.get(classId) != null; }

    /** Progress of the selected class (created on demand). Only call when {@link #hasClass()}. */
    public ClassProgress progress() { return classes.computeIfAbsent(classId, k -> new ClassProgress()); }

    public boolean unlocked(String skillId) {
        ClassProgress p = classes.get(classId);
        return p != null && p.unlocked.contains(skillId);
    }
}
