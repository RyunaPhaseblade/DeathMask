package ryuna.qdeath.limbo;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;

import java.util.stream.IntStream;

/**
 * Who put a block where it is. Every block a player places is marked in its chunk (a persistent attachment, so it is
 * per dimension and per chunk and loads with the chunk, never on its own); the mark survives property changes of the
 * same block (a door opening) and is dropped when the block is broken, replaced or moved. Pushed blocks lose their mark:
 * ownership is never inferred from material, shape or nearness to a base. An unmarked block is "unknown", and unknown is
 * protected: only marked blocks may be opened by a Breach Ticker. Operators can mark old builds by hand
 * ({@code /deathmask limbo provenance mark}).
 */
public final class Provenance {
    private Provenance() {}

    /** Mod blocks of progression and function that nothing of the horde ever breaks (gates, altars, anchors, lanterns, graves). */
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK, Quantumdeath.id("breach_protected"));

    /** Packed chunk-local positions of the player-placed blocks of one chunk. Mutable: changes mark the chunk unsaved. */
    public static final class Marks {
        final IntOpenHashSet set;
        Marks(int[] values) { set = new IntOpenHashSet(values); }
        public int size() { return set.size(); }
    }

    private static final Codec<Marks> CODEC = Codec.INT_STREAM.xmap(s -> new Marks(s.toArray()), m -> IntStream.of(m.set.toIntArray()));
    public static final AttachmentType<Marks> TYPE = AttachmentRegistry.create(Quantumdeath.id("provenance"), b -> b.persistent(CODEC));

    public static void init() { /* class load registers the attachment */ }

    /** x and z within the chunk (4 bits each), y in the remaining bits (any build height). */
    static int pack(BlockPos p) { return (p.getY() << 8) | ((p.getZ() & 15) << 4) | (p.getX() & 15); }

    private static @Nullable LevelChunk loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isPlayerPlaced(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loaded(level, pos);
        if (chunk == null) return false;
        Marks m = chunk.getAttached(TYPE);
        return m != null && m.set.contains(pack(pos));
    }

    /** Marks one block; false when its chunk is not loaded (nothing is ever loaded to mark it). */
    public static boolean mark(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loaded(level, pos);
        if (chunk == null) return false;
        Marks m = chunk.getAttached(TYPE);
        if (m == null) {
            m = new Marks(new int[0]);
            m.set.add(pack(pos));
            chunk.setAttached(TYPE, m);
        } else if (m.set.add(pack(pos))) chunk.markUnsaved();
        return true;
    }

    /** A player placed {@code state} at {@code pos}: mark it, and the other parts of doors, tall plants and beds. */
    public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
        mark(level, pos);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            if (level.getBlockState(other).is(state.getBlock())) mark(level, other);
        }
        if (state.getBlock() instanceof AbstractBedBlock) {
            BlockPos other = pos.relative(AbstractBedBlock.getConnectedDirection(state));
            if (level.getBlockState(other).is(state.getBlock())) mark(level, other);
        }
    }

    /** Any block change in a loaded chunk: a different block (broken, replaced, moved away) loses the mark. */
    public static void changed(LevelChunk chunk, BlockPos pos, BlockState old, BlockState now) {
        if (old.getBlock() == now.getBlock()) return;
        Marks m = chunk.getAttached(TYPE);
        if (m != null && m.set.remove(pack(pos))) {
            if (m.set.isEmpty()) chunk.removeAttached(TYPE);
            else chunk.markUnsaved();
        }
    }

    /**
     * Blocks the horde never breaks, whoever placed them: anything holding data (chests, barrels, furnaces, signs...),
     * portals and their frames, unbreakable blocks, votive lanterns and the mod's blocks of progression.
     */
    public static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.is(BlockTags.PORTALS) || s.is(PROTECTED)
                || s.getDestroySpeed(level, pos) < 0;
    }

    /** A block a Ticker may open now: placed by a player, not protected, outside consecrated light, with mobGriefing on. */
    public static boolean breachable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return Siege.allowed(level) && isPlayerPlaced(level, pos) && !protectedBlock(level, pos, s)
                && Consecration.at(level, Vec3.atCenterOf(pos)) == null;
    }

    /**
     * Operator tool for builds made before this version: marks every non-air block in the box whose chunk is loaded.
     * Returns {marked, skipped because unloaded}.
     */
    public static int[] markArea(ServerLevel level, BlockPos from, BlockPos to) {
        int marked = 0, skipped = 0;
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            if (loaded(level, p) == null) { skipped++; continue; }
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            if (mark(level, p)) marked++;
        }
        return new int[]{marked, skipped};
    }
}
