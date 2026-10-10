package ryuna.qdeath.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import ryuna.qdeath.registry.HellWorldgen;

/**
 * One piece per structure. The builder is re-run deterministically (same seed) for every chunk the
 * structure touches and only writes the blocks inside that chunk, so large arenas need no piece graph.
 */
public class HellPiece extends StructurePiece {
    private final String kind;
    private final BlockPos origin;
    private final long seed;

    public HellPiece(String kind, BlockPos origin, long seed, BoundingBox box) {
        super(HellWorldgen.HELL_PIECE, 0, box);
        this.kind = kind;
        this.origin = origin;
        this.seed = seed;
    }

    public HellPiece(CompoundTag tag) {
        super(HellWorldgen.HELL_PIECE, tag);
        this.kind = tag.getStringOr("Kind", "pilgrim_camp");
        this.origin = BlockPos.of(tag.getLongOr("Origin", 0L));
        this.seed = tag.getLongOr("Seed", 0L);
    }

    public BlockPos origin() { return origin; }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind);
        tag.putLong("Origin", origin.asLong());
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        HellBuilders.build(kind, new Build(level, chunkBB, origin, seed));
    }
}
