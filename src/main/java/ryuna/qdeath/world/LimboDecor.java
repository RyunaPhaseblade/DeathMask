package ryuna.qdeath.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import ryuna.qdeath.block.LimboBlocks.Grave;
import ryuna.qdeath.registry.ModBlocks;

/**
 * Funeral remains scattered over the Limbo, one {@code kind} per configured feature (see data/quantumdeath/worldgen/feature):
 * cairns and nameless stones along the roads of the Wastes, graves, broken fences and abandoned goods in the Fields,
 * beached boats and pier posts in the Deltas, dry fountains and empty benches in the Gardens.
 */
public record LimboDecor(String kind) implements Feature {
    public static final MapCodec<LimboDecor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(LimboDecor::kind)).apply(i, LimboDecor::new));
    @Override public MapCodec<? extends Feature> codec() { return CODEC; }

    private static BlockState s(net.minecraft.world.level.block.Block b) { return b.defaultBlockState(); }

    private static boolean ground(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid() && level.getFluidState(pos.below()).isEmpty();
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState s) {
        BlockState cur = level.getBlockState(pos);
        if (cur.isAir() || cur.canBeReplaced()) level.setBlock(pos, s, 2);
    }

    /** Puts {@code s} on the floor of column {@code pos} (searching a few blocks up and down). */
    private static boolean onFloor(WorldGenLevel level, BlockPos pos, BlockState s) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = pos.above(dy);
            if (ground(level, p)) { put(level, p, s); return true; }
        }
        return false;
    }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos origin) {
        if (!ground(level, origin)) return false;
        Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        switch (kind) {
            case "cairn" -> {
                int h = 2 + r.nextInt(3);
                for (int y = 0; y < h; y++) put(level, origin.above(y), s(y == 0 ? Blocks.COBBLED_DEEPSLATE : (r.nextBoolean() ? ModBlocks.ASHEN_STONE : Blocks.COBBLESTONE)));
                if (r.nextInt(3) == 0) put(level, origin.above(h), s(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 1 + r.nextInt(2)));
            }
            case "nameless_stone" -> {
                put(level, origin, s(ModBlocks.CHISELED_ASHEN_BRICKS));
                put(level, origin.above(), s(ModBlocks.ASHEN_BRICKS).trySetValue(SlabBlock.TYPE, SlabType.BOTTOM));
                if (r.nextInt(4) == 0) onFloor(level, origin.relative(face), s(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, r.nextInt(16)));
            }
            case "monument" -> {
                int h = 3 + r.nextInt(4);
                for (int y = 0; y < h; y++) put(level, origin.above(y), s(y == h - 1 ? ModBlocks.CHISELED_ASHEN_BRICKS : ModBlocks.ASHEN_BRICKS));
                for (Direction d : Direction.Plane.HORIZONTAL) if (r.nextInt(3) == 0) put(level, origin.relative(d), s(ModBlocks.ASHEN_STONE));
                if (r.nextBoolean()) put(level, origin.above(h).relative(face), s(ModBlocks.ASHEN_BRICKS)); // a fallen top
            }
            case "grave_plot" -> {
                int n = 1 + r.nextInt(3);
                for (int i = 0; i < n; i++) {
                    BlockPos g = origin.relative(face.getClockWise(), i * 2);
                    onFloor(level, g, s(ModBlocks.LIMBO_GRAVE).setValue(HorizontalDirectionalBlock.FACING, face).setValue(Grave.STAGE, r.nextInt(4) == 0 ? Grave.OPEN : Grave.SEALED));
                }
                for (int i = -1; i <= n * 2; i++) if (r.nextInt(3) != 0) onFloor(level, origin.relative(face.getClockWise(), i).relative(face.getOpposite(), 2), s(Blocks.DARK_OAK_FENCE));
            }
            case "broken_fence" -> {
                int len = 3 + r.nextInt(5);
                for (int i = 0; i < len; i++) if (r.nextInt(4) != 0) onFloor(level, origin.relative(face, i), s(Blocks.DARK_OAK_FENCE));
            }
            case "abandoned_goods" -> {
                BlockState thing = switch (r.nextInt(4)) {
                    case 0 -> s(Blocks.CAULDRON);
                    case 1 -> s(Blocks.DECORATED_POT);
                    case 2 -> s(Blocks.BARREL);
                    default -> s(Blocks.FLOWER_POT);
                };
                put(level, origin, thing);
                onFloor(level, origin.relative(face), Blocks.CARPET.pick(r.nextBoolean() ? DyeColor.WHITE : DyeColor.LIGHT_GRAY).defaultBlockState()); // a shroud on the ground
            }
            case "beached_boat" -> {
                Direction side = face.getClockWise();
                for (int i = -2; i <= 2; i++) {
                    BlockPos keel = origin.relative(face, i);
                    put(level, keel, s(ModBlocks.CHARRED_PLANKS));
                    if (Math.abs(i) < 2) {
                        put(level, keel.relative(side), s(Blocks.DARK_OAK_SLAB).setValue(SlabBlock.TYPE, SlabType.TOP));
                        if (r.nextInt(3) != 0) put(level, keel.relative(side.getOpposite()).above(), s(Blocks.DARK_OAK_SLAB));
                    }
                }
                if (r.nextBoolean()) put(level, origin.relative(face, 3), s(ModBlocks.CHARRED_LOG).trySetValue(RotatedPillarBlock.AXIS, face.getAxis()));
            }
            case "pier_posts" -> {
                int n = 2 + r.nextInt(3);
                for (int i = 0; i < n; i++) {
                    BlockPos p = origin.relative(face, i * 3);
                    int h = 2 + r.nextInt(3);
                    for (int y = 0; y < h; y++) if (!onFloor(level, p.above(y), s(ModBlocks.CHARRED_LOG)) && y == 0) break;
                    if (r.nextInt(3) == 0) put(level, p.above(h), s(Blocks.IRON_CHAIN));
                }
            }
            case "dry_fountain" -> {
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    BlockPos p = origin.offset(x, 0, z);
                    boolean rim = Math.max(Math.abs(x), Math.abs(z)) == 2;
                    if (rim) put(level, p, s(ModBlocks.ASHEN_BRICKS));
                    else if (x == 0 && z == 0) { put(level, p, s(ModBlocks.CHISELED_ASHEN_BRICKS)); put(level, p.above(), s(ModBlocks.CHISELED_ASHEN_BRICKS)); }
                    else if (r.nextInt(4) == 0) put(level, p, s(Blocks.DEAD_BUSH));
                }
            }
            case "bench" -> {
                Direction side = face.getClockWise();
                BlockState stair = s(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, face);
                for (int i = 0; i < 2 + r.nextInt(2); i++) put(level, origin.relative(side, i), stair);
                if (r.nextBoolean()) { put(level, origin.relative(side, -1), s(Blocks.DARK_OAK_FENCE)); put(level, origin.relative(side, -1).above(), s(Blocks.LANTERN)); }
            }
            case "rubble" -> {
                // A rockfall from the vault: a few fallen blocks of the strata, a cracked slab, sometimes a skull under them.
                BlockState[] stones = {s(ModBlocks.ASHEN_STONE), s(Blocks.COBBLED_DEEPSLATE), s(Blocks.TUFF), s(Blocks.BLACKSTONE)};
                for (int i = 0; i < 4 + r.nextInt(6); i++) {
                    BlockPos p = origin.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
                    onFloor(level, p, stones[r.nextInt(stones.length)]);
                    if (r.nextInt(4) == 0) onFloor(level, p.above(), s(Blocks.COBBLED_DEEPSLATE_SLAB));
                }
                if (r.nextInt(5) == 0) onFloor(level, origin.relative(face, 3), s(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, r.nextInt(16)));
            }
            default -> { return false; }
        }
        return true;
    }
}
