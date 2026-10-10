package ryuna.qdeath.limbo.plague;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import ryuna.qdeath.Quantumdeath;

import java.util.List;

/**
 * Persistent state of the Plague of the Hollows, stored on the Overworld level.
 *
 * @param portalOpened a portal to the Limbo has been crossed: minor seepages may begin
 * @param sealBroken   a Toll-Keeper has fallen: the first seal is broken and major incidents may begin
 * @param foci         every focus ever opened (contained ones are kept so a purged anchor never comes back)
 */
public record PlagueState(boolean portalOpened, boolean sealBroken, int nextId, List<Focus> foci) {
    public static final int WARNING = 0, ACTIVE = 1, EXHAUSTED = 2, CONTAINED = 3;

    /**
     * One local incident. Identity, anchor, waves spent and reserve left are saved, so unloading and reloading neither
     * restarts it nor duplicates its dead. {@code ticks} only advances while someone is near.
     */
    public record Focus(int id, BlockPos anchor, boolean major, int stage, int ticks, int wavesLeft, int reserve) {
        public static final Codec<Focus> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Focus::id),
                BlockPos.CODEC.fieldOf("anchor").forGetter(Focus::anchor),
                Codec.BOOL.fieldOf("major").forGetter(Focus::major),
                Codec.INT.fieldOf("stage").forGetter(Focus::stage),
                Codec.INT.fieldOf("ticks").forGetter(Focus::ticks),
                Codec.INT.fieldOf("waves_left").forGetter(Focus::wavesLeft),
                Codec.INT.fieldOf("reserve").forGetter(Focus::reserve)
        ).apply(i, Focus::new));

        public Focus at(int stage, int ticks) { return new Focus(id, anchor, major, stage, ticks, wavesLeft, reserve); }
        public Focus spent(int size) {
            int waves = wavesLeft - 1, left = reserve - size;
            return new Focus(id, anchor, major, waves <= 0 || left <= 0 ? EXHAUSTED : ACTIVE, 0, waves, left);
        }
        public boolean open() { return stage < CONTAINED; }
    }

    public static final PlagueState START = new PlagueState(false, false, 1, List.of());

    public static final Codec<PlagueState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("portal_opened", false).forGetter(PlagueState::portalOpened),
            Codec.BOOL.optionalFieldOf("seal_broken", false).forGetter(PlagueState::sealBroken),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(PlagueState::nextId),
            Focus.CODEC.listOf().optionalFieldOf("foci", List.of()).forGetter(PlagueState::foci)
    ).apply(i, PlagueState::new));

    public static final AttachmentType<PlagueState> TYPE = AttachmentRegistry.create(Quantumdeath.id("plague"),
            b -> b.persistent(CODEC).initializer(() -> START));

    public PlagueState withFoci(List<Focus> f) { return new PlagueState(portalOpened, sealBroken, nextId, List.copyOf(f)); }
    public PlagueState withPortalOpened() { return new PlagueState(true, sealBroken, nextId, foci); }
    public PlagueState withSealBroken(boolean b) { return new PlagueState(portalOpened, b, nextId, foci); }
    public long active() { return foci.stream().filter(Focus::open).count(); }
}
