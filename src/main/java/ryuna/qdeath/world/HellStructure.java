package ryuna.qdeath.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import ryuna.qdeath.registry.HellWorldgen;

import java.util.Optional;

/**
 * Every Hell structure is this one type; its {@code kind} picks the builder in {@link HellBuilders}.
 * {@code placement} decides the Y: "fixed" (offset from min Y; arenas carve their own space), "open" (the roomiest level floor in the band, for the Limbo),
 * "floor" (lowest open cave floor in [min_y, max_y]) or "surface" (highest open floor in the range).
 */
public class HellStructure extends Structure {
    public static final MapCodec<HellStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),
            Codec.STRING.optionalFieldOf("placement", "fixed").forGetter(s -> s.placement),
            Codec.INT.optionalFieldOf("min_y", 40).forGetter(s -> s.minY),
            Codec.INT.optionalFieldOf("max_y", 90).forGetter(s -> s.maxY)
    ).apply(i, HellStructure::new));

    private final String kind;
    private final String placement;
    private final int minY;
    private final int maxY;

    public HellStructure(StructureSettings settings, String kind, String placement, int minY, int maxY) {
        super(settings);
        this.kind = kind;
        this.placement = placement;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        int y = minY;
        if (placement.equals("open")) {
            Integer found = null;
            for (int[] o : new int[][]{{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {8, 8}, {-8, -8}}) {
                found = open(ctx, x + o[0], z + o[1]);
                if (found != null) { x += o[0]; z += o[1]; break; }
            }
            if (found == null) return Optional.empty();
            y = found;
        } else if (!placement.equals("fixed")) {
            Integer found = null;
            // Try the chunk centre, then a few nearby columns, for an open floor in the allowed band.
            for (int[] o : new int[][]{{0, 0}, {5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
                found = floor(ctx, x + o[0], z + o[1], placement.equals("surface"));
                if (found != null) { x += o[0]; z += o[1]; break; }
            }
            if (found == null) return Optional.empty();
            y = found;
        }
        BlockPos origin = new BlockPos(x, y, z);
        BoundingBox box = HellBuilders.bounds(kind, origin);
        long seed = ctx.random().nextLong();
        return Optional.of(new GenerationStub(origin, b -> b.addPiece(new HellPiece(kind, origin, seed, box))));
    }

    private Integer floor(GenerationContext ctx, int x, int z, boolean surface) {
        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        for (int yy = surface ? maxY : minY; surface ? yy >= minY : yy <= maxY; yy += surface ? -1 : 1) {
            BlockState below = column.getBlock(yy - 1);
            if (below.isSolid() && column.getBlock(yy).isAir() && column.getBlock(yy + 1).isAir() && column.getBlock(yy + 2).isAir()) return yy;
        }
        return null;
    }

    /**
     * "open": the floor of column (x, z) in [min_y, max_y] with the most headroom (at least 12 blocks: a chamber, not a
     * crevice), provided the ground 10 blocks around it is within 5 blocks of that height (level enough to stand on and
     * leave in every direction). Used by the Limbo, whose terrain has floors at many heights.
     */
    private Integer open(GenerationContext ctx, int x, int z) {
        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        Integer best = null;
        int bestRoom = 11;
        for (int yy = minY; yy <= maxY; yy++) {
            if (!column.getBlock(yy - 1).isSolid() || !column.getBlock(yy).isAir()) continue;
            int room = 0;
            while (room < 64 && column.getBlock(yy + room).isAir()) room++;
            if (room > bestRoom && level(ctx, x, z, yy)) { best = yy; bestRoom = room; }
            yy += room;
        }
        return best;
    }

    private boolean level(GenerationContext ctx, int x, int z, int y) {
        for (int[] o : new int[][]{{10, 0}, {-10, 0}, {0, 10}, {0, -10}}) {
            NoiseColumn c = ctx.chunkGenerator().getBaseColumn(x + o[0], z + o[1], ctx.heightAccessor(), ctx.randomState());
            boolean ok = false;
            for (int dy = -5; dy <= 5 && !ok; dy++) ok = c.getBlock(y + dy - 1).isSolid() && c.getBlock(y + dy).isAir() && c.getBlock(y + dy + 1).isAir();
            if (!ok) return false;
        }
        return true;
    }

    @Override public StructureType<?> type() { return HellWorldgen.HELL_STRUCTURE; }
}
