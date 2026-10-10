package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import ryuna.qdeath.Quantumdeath;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player yokai data, persisted on the player and kept through death: the discovered bestiary (one bit per
 * witnessed clue, plus "shadow seen" and "identified"), the book note about the awakening, and the operators'
 * demonstration perception override (-1 = none) and demonstration mode; neither ever touches the skill tree.
 */
public record YokaiPlayer(Map<String, Integer> bestiary, boolean noteUnread, boolean noted, int perceptionOverride, boolean demo) {
    public static final int SHADE = 1 << 8, IDENTIFIED = 1 << 9;

    public static final YokaiPlayer START = new YokaiPlayer(Map.of(), false, false, -1, false);

    public static final Codec<YokaiPlayer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bestiary", Map.of()).forGetter(YokaiPlayer::bestiary),
            Codec.BOOL.optionalFieldOf("note_unread", false).forGetter(YokaiPlayer::noteUnread),
            Codec.BOOL.optionalFieldOf("noted", false).forGetter(YokaiPlayer::noted),
            Codec.INT.optionalFieldOf("perception_override", -1).forGetter(YokaiPlayer::perceptionOverride),
            Codec.BOOL.optionalFieldOf("demo", false).forGetter(YokaiPlayer::demo)
    ).apply(i, YokaiPlayer::new));

    public static final AttachmentType<YokaiPlayer> TYPE = AttachmentRegistry.create(Quantumdeath.id("yokai_player"),
            b -> b.persistent(CODEC).copyOnDeath().initializer(() -> START));

    public static YokaiPlayer of(ServerPlayer player) { return player.getAttachedOrCreate(TYPE); }
    public static void set(ServerPlayer player, YokaiPlayer data) { player.setAttached(TYPE, data); }

    public int entry(Species s) { return bestiary.getOrDefault(s.id, 0); }
    public boolean identified(Species s) { return (entry(s) & IDENTIFIED) != 0; }

    public YokaiPlayer withBits(Species s, int bits) {
        Map<String, Integer> m = new HashMap<>(bestiary);
        m.merge(s.id, bits, (a, b) -> a | b);
        return new YokaiPlayer(Map.copyOf(m), noteUnread, noted, perceptionOverride, demo);
    }

    public YokaiPlayer withBestiary(Map<String, Integer> m) { return new YokaiPlayer(Map.copyOf(m), noteUnread, noted, perceptionOverride, demo); }
    public YokaiPlayer withNote(boolean unread) { return new YokaiPlayer(bestiary, unread, true, perceptionOverride, demo); }
    public YokaiPlayer withOverride(int o) { return new YokaiPlayer(bestiary, noteUnread, noted, o, demo); }
    /** Demonstration mode: the nine yokai nodes act as learned while it is on; the skill tree itself is untouched. */
    public YokaiPlayer withDemo(boolean d) { return new YokaiPlayer(bestiary, noteUnread, noted, d ? 2 : -1, d); }
}
