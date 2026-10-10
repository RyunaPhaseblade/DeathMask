package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import ryuna.qdeath.Quantumdeath;

import java.util.Optional;
import java.util.UUID;

/**
 * Global awakening state, stored on the Overworld level (persists through restarts; destroying the portal never
 * reverts it).
 *
 * @param awakened           the first obsidian portal has formed (or, in an older world, been crossed)
 * @param day                Overworld day of the awakening, for the book's note
 * @param graceLeft          ticks of Overworld activity left before dangerous encounters
 * @param pendingAdvancement nobody identifiable caused it: the first player through a portal receives the advancement
 * @param awardedTo          who received "What have you done?" (the advancement delivery record)
 */
public record YokaiWorld(boolean awakened, long day, int graceLeft, boolean pendingAdvancement, Optional<UUID> awardedTo) {
    public static final YokaiWorld START = new YokaiWorld(false, 0, 0, false, Optional.empty());

    public static final Codec<YokaiWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("awakened", false).forGetter(YokaiWorld::awakened),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(YokaiWorld::day),
            Codec.INT.optionalFieldOf("grace_left", 0).forGetter(YokaiWorld::graceLeft),
            Codec.BOOL.optionalFieldOf("pending_advancement", false).forGetter(YokaiWorld::pendingAdvancement),
            UUIDUtil.CODEC.optionalFieldOf("awarded_to").forGetter(YokaiWorld::awardedTo)
    ).apply(i, YokaiWorld::new));

    public static final AttachmentType<YokaiWorld> TYPE = AttachmentRegistry.create(Quantumdeath.id("yokai_world"),
            b -> b.persistent(CODEC).initializer(() -> START));

    public static YokaiWorld of(MinecraftServer server) { return server.overworld().getAttachedOrCreate(TYPE); }
    public static void set(MinecraftServer server, YokaiWorld w) { server.overworld().setAttached(TYPE, w); }

    public boolean inGrace() { return awakened && graceLeft > 0; }
    public YokaiWorld withGrace(int g) { return new YokaiWorld(awakened, day, Math.max(0, g), pendingAdvancement, awardedTo); }
    public YokaiWorld awarded(UUID player) { return new YokaiWorld(awakened, day, graceLeft, false, Optional.of(player)); }
}
