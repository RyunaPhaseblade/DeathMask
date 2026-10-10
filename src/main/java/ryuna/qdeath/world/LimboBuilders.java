package ryuna.qdeath.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import ryuna.qdeath.block.LimboBlocks.Grave;
import ryuna.qdeath.block.LimboBlocks.UnstableBone;
import net.minecraft.world.entity.EntityType;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;

import static ryuna.qdeath.world.ArenaBuilders.AIR;
import static ryuna.qdeath.world.HellBuilders.s;

/**
 * The structures the Limbo adds: the funeral village, the procession pit and the Limbo ossuary. Each holds a finite
 * contingent (dead placed once, sealed graves) and shows signs of what is inside before anyone walks in.
 */
final class LimboBuilders {
    private LimboBuilders() {}

    private static final BlockState PLANKS = s(ModBlocks.CHARRED_PLANKS), LOG = s(ModBlocks.CHARRED_LOG), BRICKS = s(ModBlocks.ASHEN_BRICKS),
            CHISELED = s(ModBlocks.CHISELED_ASHEN_BRICKS), SOIL = s(ModBlocks.CINDER_SOIL), ASH = s(ModBlocks.ASH);

    /** Levels a square of ground: open air above y=0, a floor of {@code floor} at y=-1 down to solid ground. */
    private static void level(Build b, int r, int height, BlockState floor) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            b.fill(x, 0, z, x, height, z, AIR);
            b.set(x, -1, z, floor);
            b.foundation(x, -2, z, 8, SOIL);
        }
    }

    /** A dead of the structure's finite contingent, lying still until something alive disturbs it. */
    private static <T extends LimboDead> void rest(Build b, int x, int y, int z, EntityType<T> type) {
        b.mob(x, y, z, type, m -> m.setResting(true));
    }

    private static BlockState grave(Direction facing, boolean sealed) {
        return s(ModBlocks.LIMBO_GRAVE).setValue(HorizontalDirectionalBlock.FACING, facing).setValue(Grave.STAGE, sealed ? Grave.SEALED : Grave.OPEN);
    }

    private static void door(Build b, int x, int z, Direction facing) {
        b.set(x, 0, z, s(Blocks.DARK_OAK_DOOR).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(x, 1, z, s(Blocks.DARK_OAK_DOOR).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** An empty house: charred walls, a wooden door (the dead can batter only that), windows without glass, a slab roof. */
    private static void house(Build b, int x0, int z0, int w, int d, boolean doorSouth) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        b.room(x0, -1, z0, x1, 4, z1, PLANKS);
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) b.pillar(c[0], c[1], 0, 3, LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        b.fill(x0, 4, z0, x1, 4, z1, s(Blocks.DARK_OAK_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (b.noise(x, 4, z) < 0.12) b.set(x, 4, z, AIR); // a roof falling in
        int dx = (x0 + x1) / 2;
        door(b, dx, doorSouth ? z1 : z0, doorSouth ? Direction.SOUTH : Direction.NORTH);
        b.set(x0, 1, (z0 + z1) / 2, AIR);
        b.set(x1, 1, (z0 + z1) / 2, AIR);
        b.set(x0 + 1, 0, z0 + 1, s(Blocks.DARK_OAK_FENCE));
        b.set(x0 + 1, 1, z0 + 1, s(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 1 + b.rand.nextInt(3)));
        b.barrel(x1 - 1, 0, doorSouth ? z0 + 1 : z1 - 1, "chests/funeral_village");
        if (b.rand.nextBoolean()) b.set(x1 - 1, 0, (z0 + z1) / 2, s(Blocks.CAULDRON));
        b.set(dx, 0, doorSouth ? z0 + 1 : z1 - 1, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState());
    }

    /**
     * Funeral village: empty houses around connected yards, light defences (fences, gates, funeral barricades), a
     * churchyard of sealed graves and an unlit votive lantern in the square. The Sundered wait inside the houses;
     * Hollows stand in the yards. Supplies, and room to practise retreat and flanking.
     */
    static void funeralVillage(Build b) {
        level(b, 13, 7, SOIL);
        // Paths connecting the yards, worn into the ash.
        for (int i = -12; i <= 12; i++) for (int w = -1; w <= 1; w++) {
            if (b.noise(i, 0, w) < 0.8) b.set(i, -1, w, b.noise(i, 1, w) < 0.5 ? BRICKS : s(Blocks.GRAVEL));
            if (b.noise(w, 0, i) < 0.8) b.set(w, -1, i, b.noise(w, 1, i) < 0.5 ? BRICKS : s(Blocks.GRAVEL));
        }
        house(b, -11, -11, 7, 6, true);
        house(b, 4, -11, 7, 6, true);
        house(b, -11, 5, 6, 6, false);
        house(b, 5, 6, 6, 5, false);
        // Yard fences with gaps, gates, and two alleys closed with funeral barricades.
        for (int i = -12; i <= 12; i++) {
            for (int[] e : new int[][]{{i, -13}, {i, 13}, {-13, i}, {13, i}}) if (Math.abs(i) > 1 && b.noise(e[0], 2, e[1]) < 0.75) b.set(e[0], 0, e[1], s(Blocks.DARK_OAK_FENCE));
        }
        b.set(0, 0, -13, s(Blocks.DARK_OAK_FENCE_GATE).setValue(FenceGateBlock.FACING, Direction.NORTH));
        b.set(0, 0, 13, s(Blocks.DARK_OAK_FENCE_GATE).setValue(FenceGateBlock.FACING, Direction.SOUTH));
        b.fill(-13, 0, -1, -13, 1, 1, s(ModBlocks.FUNERAL_BARRICADE));
        b.fill(13, 0, -1, 13, 1, 1, s(ModBlocks.FUNERAL_BARRICADE));
        // The square: a votive lantern with no flame of its own yet, and a sign of those who barricaded themselves here.
        b.set(2, 0, 2, LOG);
        b.set(2, 1, 2, s(ModBlocks.VOTIVE_LANTERN));
        b.sign(2, 0, 3, Direction.SOUTH, "sign.quantumdeath.village.0", "sign.quantumdeath.village.1", "sign.quantumdeath.village.2", "sign.quantumdeath.village.3");
        // Churchyard: sealed graves (each holds one of the dead) behind a low wall.
        for (int i = 0; i < 4; i++) b.set(-9 + i * 2, 0, -3, grave(Direction.SOUTH, b.rand.nextInt(4) != 0));
        b.fill(-10, 0, -4, -3, 0, -4, s(Blocks.COBBLED_DEEPSLATE_WALL));
        b.chest(9, 0, 3, Direction.WEST, "chests/funeral_village");
        // The finite contingent placed once with the village: they rest until something alive disturbs them. Stalkers and
        // Runts wait inside the houses (watch the back and the doors), the rest stand in the yards and alleys.
        b.mob(-8, 0, -8, HellEntities.STALKER, m -> m.setResting(true));
        b.mob(7, 0, -8, HellEntities.RUNT, m -> m.setResting(true));
        EntityType<? extends LimboDead> inside = b.rand.nextInt(4) == 0 ? HellEntities.SUNDERED : HellEntities.RUNT;
        rest(b, -8, 0, 8, inside);
        if (b.rand.nextBoolean()) b.mob(7, 0, 8, HellEntities.STALKER, m -> m.setResting(true));
        int[][] yard = {{-3, 6}, {0, 7}, {3, 6}, {-6, 2}, {6, 2}, {-2, -6}, {2, -6}, {-10, 0}, {10, 0}, {0, 10}, {0, -10}, {-5, -2}, {5, -2}, {-1, 3}};
        for (int[] y : yard) b.mob(y[0], 0, y[1], HellEntities.HOLLOW, m -> m.setResting(true));
        if (b.rand.nextInt(3) != 0) b.mob(6, 0, -3, HellEntities.CRIER, m -> m.setResting(true));
        if (b.rand.nextBoolean()) b.mob(-6, 0, 4, HellEntities.BLOATER, m -> m.setResting(true));
    }

    /**
     * Procession pit: a long open grave where a procession lies down to wait. Lit candles, prints in the ash and a
     * bell on its post show that it is not empty. Its dead (laid out once) and its sealed graves are all it holds.
     */
    static void processionPit(Build b) {
        level(b, 10, 6, ASH);
        BlockState wall = s(Blocks.COBBLED_DEEPSLATE);
        b.fill(-4, -5, -7, 4, -1, 7, AIR);
        b.fill(-4, -5, -7, 4, -5, 7, SOIL);
        for (int x = -5; x <= 5; x++) for (int z = -8; z <= 8; z++) if (Math.abs(x) == 5 || Math.abs(z) == 8) b.fill(x, -5, z, x, -1, z, wall);
        // A ramp down at the south end.
        for (int i = 0; i < 4; i++) b.fill(-1, -1 - i, 8 - i, 1, -1 - i, 8 - i, s(Blocks.COBBLED_DEEPSLATE_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        for (int i = 0; i < 4; i++) b.fill(-1, -i, 8 - i, 1, 1, 8 - i, AIR);
        // Rows of sealed graves along both walls, and a graveyard on the rim: the reserve of this pit.
        for (int z = -6; z <= 4; z += 2) {
            b.set(-3, -4, z, grave(Direction.EAST, b.rand.nextInt(5) != 0));
            b.set(3, -4, z, grave(Direction.WEST, b.rand.nextInt(5) != 0));
        }
        for (int x = -8; x <= 8; x += 2) {
            if (Math.abs(x) <= 6 && b.rand.nextInt(3) == 0) continue;
            b.set(x, 0, -10, grave(Direction.SOUTH, b.rand.nextInt(4) != 0));
            if (Math.abs(x) > 2) b.set(x, 0, 10, grave(Direction.NORTH, b.rand.nextInt(4) != 0));
        }
        // Signs of activity on the rim.
        for (int i = 0; i < 6; i++) {
            int x = b.rand.nextInt(13) - 6, z = b.rand.nextBoolean() ? -9 - b.rand.nextInt(2) : 9 + b.rand.nextInt(2);
            b.set(x, 0, z, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + b.rand.nextInt(4)));
        }
        for (int i = 0; i < 8; i++) b.set(b.rand.nextInt(15) - 7, 0, 10 + b.rand.nextInt(2), s(ModBlocks.ASH_PRINT));
        b.set(6, 0, 9, LOG);
        b.set(6, 1, 9, LOG);
        b.set(6, 2, 9, s(Blocks.BELL));
        b.chest(0, -4, -6, Direction.SOUTH, "chests/procession_pit");
        // The procession lying in wait: two dozen bodies laid out in the pit (a Crier among them is the danger: its scream
        // wakes the graves). Together with the graves this is a reserve of about fifty, finite, visible from the rim.
        int rows = 22 + b.rand.nextInt(6);
        for (int i = 0; i < rows; i++) {
            int x = -1 + (i % 3), z = -6 + (i / 3) * 2 % 12;
            EntityType<? extends LimboDead> type = i == 7 && b.rand.nextBoolean() ? HellEntities.BLOATER : i == 13 && b.rand.nextInt(3) == 0 ? HellEntities.LANKY : HellEntities.HOLLOW;
            rest(b, x, -4, z + (i / 18), type);
        }
        if (b.rand.nextInt(3) != 0) b.mob(0, -4, 3, HellEntities.CRIER, m -> m.setResting(true));
    }

    /**
     * The Limbo ossuary: a half-buried hall of bones whose names have worn away. Skulls in the niches, bones piled
     * against the walls, a floor of unstable bone over a shallow pit of deep ash. Crawlers sleep in the niches and,
     * sometimes, a Walking Ossuary stands among its own. No puzzle, no sarcophagus: that is the Ossuary of Dis.
     */
    static void limboOssuary(Build b) {
        level(b, 10, 4, SOIL);
        BlockState bone = s(Blocks.BONE_BLOCK);
        b.room(-8, -6, -8, 8, 3, 8, BRICKS);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            b.foundation(x, -7, z, 6, BRICKS);
            if (b.noise(x, 3, z) < 0.25) b.set(x, 3, z, AIR); // the roof has fallen in places
        }
        // Entrance stair from the north.
        for (int i = 0; i < 5; i++) {
            b.fill(-1, -1 - i, -9 + i, 1, -1 - i, -9 + i, s(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
            b.fill(-1, -i, -9 + i, 1, 2, -9 + i, AIR);
        }
        b.fill(-1, -5, -8, 1, -3, -8, AIR);
        // Niches with skulls along the walls, bone heaps below them.
        for (int i = -6; i <= 6; i += 3) {
            for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH}) {
                int x = d == Direction.EAST ? -8 : d == Direction.WEST ? 8 : i, z = d == Direction.SOUTH ? 8 : i;
                if (d != Direction.SOUTH && Math.abs(i) > 6) continue;
                int nx = x + (d == Direction.EAST ? 1 : d == Direction.WEST ? -1 : 0), nz = z + (d == Direction.SOUTH ? -1 : 0);
                b.set(x, -3, z, s(Blocks.SKELETON_WALL_SKULL).setValue(WallSkullBlock.FACING, d == Direction.SOUTH ? Direction.NORTH : d));
                b.set(x, -2, z, CHISELED);
                if (b.rand.nextBoolean()) b.set(nx, -5, nz, bone);
            }
        }
        // A floor of unstable bone over a shallow pit of deep ash (a fall that hurts little).
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            b.set(x, -6, z, AIR);
            b.set(x, -7, z, AIR);
            b.set(x, -8, z, s(ModBlocks.DEEP_ASH));
            b.set(x, -6, z, s(ModBlocks.UNSTABLE_BONE).setValue(UnstableBone.CRACKS, b.noise(x, -6, z) < 0.3 ? 1 : 0));
        }
        for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) b.pillar(c[0], c[1], -5, 2, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        b.lectern(0, -5, 6, Direction.NORTH, Build.book("worn_names", "Worn Names", 3));
        b.set(-2, -5, 6, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
        b.set(2, -5, 6, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, false));
        b.chest(6, -5, 6, Direction.NORTH, "chests/limbo_ossuary");
        b.chest(-6, -5, 6, Direction.NORTH, "chests/limbo_ossuary");
        for (int i = 0; i < 3 + b.rand.nextInt(2); i++) b.mob(-6 + i * 4, -5, 2 - i, HellEntities.CRAWLER, m -> m.setResting(true));
        b.mob(5, -5, 4, HellEntities.LANKY, m -> m.setResting(true));
        for (int i = 0; i < 4; i++) b.mob(-4 + i * 3, -5, -3, HellEntities.HOLLOW, m -> m.setResting(true));
        if (b.rand.nextInt(5) < 2) b.mob(0, -5, -5, HellEntities.WALKING_OSSUARY, m -> m.setResting(true));
        b.set(0, -5, 0, s(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, b.rand.nextInt(16)));
    }
}
