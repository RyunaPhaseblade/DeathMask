package ryuna.qdeath.client.yokai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiNet;

import java.util.List;
import java.util.Map;

/** What the server told this client about its own player: perception, the book's yokai pages, companions, tracking. */
public final class ClientYokai {
    private ClientYokai() {}

    public static int perception;
    public static boolean override;
    public static boolean awakened, noteUnread;
    public static long day;
    public static int nodes;
    public static Map<String, Integer> bestiary = Map.of();
    public static List<YokaiNet.Status> companions = List.of();
    public static YokaiNet.Tracking tracking = YokaiNet.Tracking.NONE;
    /** Client tick at which {@link #tracking} arrived (its age keeps counting between updates). */
    public static long trackingAt;
    public static long clientTicks;

    public static void self(YokaiNet.Self p) {
        perception = p.perception();
        override = p.override();
        awakened = p.awakened();
        noteUnread = p.noteUnread();
        day = p.day();
        nodes = p.nodes();
        bestiary = p.bestiary();
    }

    public static boolean node(SkillId s) {
        int bit = s.ordinal() - SkillId.PERCEPTION_I.ordinal();
        return bit >= 0 && (nodes >> bit & 1) != 0;
    }

    public static int entry(String species) { return bestiary.getOrDefault(species, 0); }

    /** What the local player perceives of {@code e}: 0 nothing, 1 an omen, 2 the creature. */
    public static int view(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isSpectator()) return 2;
        if (YokaiRenderer.PREVIEW.contains(e)) return 2;
        if (e instanceof YokaiCreature y && y.species().legacy && HellLayer.of(e.level()) != null) return 2;
        return perception;
    }

    public static void reset() {
        perception = 0;
        override = false;
        awakened = noteUnread = false;
        nodes = 0;
        bestiary = Map.of();
        companions = List.of();
        tracking = YokaiNet.Tracking.NONE;
    }
}
