package ryuna.qdeath.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Hell vegetation and decoration features, configured from JSON (data/quantumdeath/worldgen/feature). */
public final class HellFeatures {
    private HellFeatures() {}

    private static boolean ground(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid();
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState s) {
        BlockState cur = level.getBlockState(pos);
        if (cur.isAir() || cur.canBeReplaced()) level.setBlock(pos, s, 2);
    }

    /** A dead, crooked tree: a trunk and a few bent branches. */
    public record DeadTree(BlockState log, IntProvider height) implements Feature {
        public static final MapCodec<DeadTree> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockState.CODEC.fieldOf("log").forGetter(DeadTree::log),
                net.minecraft.util.valueproviders.IntProviders.CODEC.fieldOf("height").forGetter(DeadTree::height)).apply(i, DeadTree::new));
        @Override public MapCodec<? extends Feature> codec() { return CODEC; }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos origin) {
            if (!ground(level, origin)) return false;
            int h = height.sample(r);
            BlockPos.MutableBlockPos p = origin.mutable();
            for (int y = 0; y < h; y++) {
                put(level, p, log.trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                if (y > 1 && r.nextInt(5) == 0) p.move(Direction.Plane.HORIZONTAL.getRandomDirection(r));
                p.move(Direction.UP);
            }
            int branches = 2 + r.nextInt(3);
            for (int i = 0; i < branches; i++) {
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                BlockPos.MutableBlockPos q = origin.above(h / 2 + r.nextInt(Math.max(1, h / 2))).mutable();
                int len = 2 + r.nextInt(3);
                for (int j = 0; j < len; j++) {
                    q.move(d);
                    if (r.nextInt(3) == 0) q.move(Direction.UP);
                    put(level, q, log.trySetValue(RotatedPillarBlock.AXIS, d.getAxis()));
                }
            }
            return true;
        }
    }

    /** A tapering spike rising from the floor or hanging from the ceiling (fangs, spires, icicles, static). */
    public record Spire(BlockState block, IntProvider height, int radius, boolean hanging) implements Feature {
        public static final MapCodec<Spire> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Spire::block),
                net.minecraft.util.valueproviders.IntProviders.CODEC.fieldOf("height").forGetter(Spire::height),
                Codec.INT.optionalFieldOf("radius", 1).forGetter(Spire::radius),
                Codec.BOOL.optionalFieldOf("hanging", false).forGetter(Spire::hanging)).apply(i, Spire::new));
        @Override public MapCodec<? extends Feature> codec() { return CODEC; }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos origin) {
            Direction dir = hanging ? Direction.DOWN : Direction.UP;
            BlockPos support = origin.relative(dir.getOpposite());
            if (!level.getBlockState(origin).isAir() || !level.getBlockState(support).isSolid()) return false;
            int h = height.sample(r);
            for (int y = 0; y < h; y++) {
                double rad = radius * (1.0 - (double) y / h) + 0.3;
                int ir = (int) Math.ceil(rad);
                for (int x = -ir; x <= ir; x++) for (int z = -ir; z <= ir; z++) {
                    if (x * x + z * z > rad * rad) continue;
                    put(level, origin.relative(dir, y).offset(x, 0, z), block);
                }
            }
            return true;
        }
    }

    /** Chains, sinew or roots hanging from a ceiling. */
    public record Hanging(BlockState block, IntProvider length, java.util.Optional<BlockState> tip) implements Feature {
        public static final MapCodec<Hanging> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Hanging::block),
                net.minecraft.util.valueproviders.IntProviders.CODEC.fieldOf("length").forGetter(Hanging::length),
                BlockState.CODEC.optionalFieldOf("tip").forGetter(Hanging::tip)).apply(i, Hanging::new));
        @Override public MapCodec<? extends Feature> codec() { return CODEC; }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos origin) {
            if (!level.getBlockState(origin).isAir() || !level.getBlockState(origin.above()).isSolid()) return false;
            int n = length.sample(r);
            BlockPos.MutableBlockPos p = origin.mutable();
            int placed = 0;
            for (int i = 0; i < n && level.getBlockState(p).isAir(); i++, placed++) { level.setBlock(p, block, 2); p.move(Direction.DOWN); }
            // The last block gets the tip state (hanging moss ends in a frayed tip).
            if (placed > 0 && tip.isPresent()) level.setBlock(p.move(Direction.UP), tip.get(), 2);
            return true;
        }
    }

    /** A kneeling figure: the petrified souls of the Mire, the frozen penitents of the Zenith. */
    public record Statue(BlockState body, BlockState head) implements Feature {
        public static final MapCodec<Statue> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockState.CODEC.fieldOf("body").forGetter(Statue::body),
                BlockState.CODEC.fieldOf("head").forGetter(Statue::head)).apply(i, Statue::new));
        @Override public MapCodec<? extends Feature> codec() { return CODEC; }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos origin) {
            if (!ground(level, origin)) return false;
            boolean kneeling = r.nextBoolean();
            level.setBlock(origin, body, 2);
            if (!kneeling) level.setBlock(origin.above(), body, 2);
            level.setBlock(origin.above(kneeling ? 1 : 2), head, 2);
            Direction arms = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            if (r.nextBoolean()) put(level, origin.relative(arms).above(kneeling ? 0 : 1), body);
            return true;
        }
    }
}
