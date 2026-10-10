package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;

/**
 * Natural provenance of the objects tsukumogami may inhabit. A chest or bell is "natural" only if its block entity
 * was created by world generation: the mark is put on the block entity itself when the chunk is first generated, so
 * breaking and placing, moving or rebuilding an object always loses it, and objects of older chunks never have it.
 * Chests also need their loot untouched (nobody has opened or filled them) and to be single, plain chests.
 */
public final class Provenance {
    private Provenance() {}

    public static final AttachmentType<Boolean> NATURAL = AttachmentRegistry.create(Quantumdeath.id("natural_object"),
            b -> b.persistent(Codec.BOOL));

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (generated && level.dimension() == Level.OVERWORLD) markGenerated(chunk);
        });
    }

    /** Marks the chests and bells world generation just placed in a new chunk. */
    public static void markGenerated(LevelChunk chunk) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity chest && chest.getLootTable() != null && be.getBlockState().is(Blocks.CHEST)
                    || be instanceof BellBlockEntity) {
                // Fabric marks the block entity changed itself, deferred until the chunk is FULL: calling setChanged()
                // here, while the chunk is still loading, waits for that same chunk and freezes the server.
                be.setAttached(NATURAL, true);
            }
        }
    }

    public static boolean natural(BlockEntity be) { return Boolean.TRUE.equals(be.getAttached(NATURAL)); }

    /** A chest a tsukumogami may awaken in: natural, single, dry, plain chest whose loot was never rolled. */
    public static boolean eligibleChest(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(Blocks.CHEST) || s.getValue(ChestBlock.TYPE) != ChestType.SINGLE || s.getValue(ChestBlock.WATERLOGGED)) return false;
        // A loot table still set means it was never unpacked: opening, a hopper or any deposit unpacks it first.
        // (Container.isEmpty() would unpack it, so it is never called here.)
        return level.getBlockEntity(pos) instanceof ChestBlockEntity chest && natural(chest) && chest.getLootTable() != null;
    }

    public static boolean eligibleBell(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BELL) && level.getBlockEntity(pos) instanceof BellBlockEntity bell && natural(bell);
    }

    /** Natural candidates in already-loaded chunks within [min, max] blocks of {@code center}. Never loads a chunk. */
    public static List<BlockPos> candidates(ServerLevel level, BlockPos center, double min, double max, boolean chests) {
        List<BlockPos> out = new ArrayList<>();
        int r = (int) Math.ceil(max) >> 4;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (!natural(be)) continue;
                double d = Math.sqrt(be.getBlockPos().distSqr(center));
                if (d < min || d > max) continue;
                BlockPos p = be.getBlockPos();
                if (chests ? eligibleChest(level, p) : eligibleBell(level, p)) out.add(p);
            }
        }
        return out;
    }

    /** Restores a natural mark on a block entity put back by its own spirit (the same object returning home). */
    public static void remark(ServerLevel level, BlockPos pos) {
        @Nullable BlockEntity be = level.getBlockEntity(pos);
        if (be != null) be.setAttached(NATURAL, true);
    }
}
