package ryuna.qdeath.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ryuna.qdeath.block.PuzzleBlocks;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;

import static ryuna.qdeath.world.ArenaBuilders.AIR;
import static ryuna.qdeath.world.ArenaBuilders.facing;
import static ryuna.qdeath.world.HellBuilders.s;

/**
 * THE OSSUARY OF DIS. A mausoleum on the surface; below it, rooms in a line to the north:
 * Hall of Tombs, the trapped Bone Walk (west), the Library and its hidden Reliquary of the Mask (east),
 * the Choir of Bells (puzzle: ring the sins in the order the library names), the Crypt of the Inquisitor
 * (miniboss), the Reliquary (vaults that reward every player once per key), and the Sanctum of the Heresiarch.
 */
final class OssuaryBuilder {
    private OssuaryBuilder() {}

    static final int FLOOR = -13;

    static void build(Build b) {
        BlockState bricks = s(ModBlocks.DIS_BRICKS), cracked = s(ModBlocks.CRACKED_DIS_BRICKS), iron = s(ModBlocks.DIS_IRON_BLOCK);
        BlockState black = s(Blocks.POLISHED_BLACKSTONE_BRICKS), chisel = s(Blocks.CHISELED_POLISHED_BLACKSTONE);

        // ---- Mausoleum on the surface
        b.fill(-7, -1, -3, 7, -1, 11, bricks);
        b.room(-6, 0, -2, 6, 9, 10, bricks);
        b.fill(-6, 9, -2, 6, 9, 10, iron);
        b.fill(-1, 1, 10, 1, 4, 10, AIR);
        b.set(-2, 5, 10, chisel); b.set(2, 5, 10, chisel); b.set(0, 5, 10, s(Blocks.WITHER_SKELETON_SKULL));
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-1, 9}) b.set(x, 1, z, ArenaBuilders.litCampfire(Blocks.CAMPFIRE));
        b.lectern(4, 1, 6, Direction.WEST, Build.book("confession_of_the_first", "Confession of the First Heretic", 4));
        b.sign(0, 2, 11, Direction.SOUTH, "sign.quantumdeath.ossuary.0", "sign.quantumdeath.ossuary.1", "sign.quantumdeath.ossuary.2", "sign.quantumdeath.ossuary.3");
        // Stairs down to the ossuary floor.
        for (int i = 0; i <= 14; i++) {
            int y = -i, z = 6 - i;
            b.fill(-2, y - 1, z, 2, y + 4, z, bricks);
            b.fill(-1, y, z, 1, y + 3, z, AIR);
            b.fill(-1, y - 1, z, 1, y - 1, z, black);
        }

        // ---- Foundations: where the burning city's terraces fall away, the halls stand on brick piers instead of floating.
        int[][] footprint = {{-10, -30, 10, -8}, {10, -26, 26, -12}, {26, -23, 34, -15}, {-30, -21, -10, -17}, {-40, -24, -30, -14},
                {-9, -48, 9, -30}, {-8, -64, 8, -48}, {-6, -76, 6, -64}, {-17, -112, 17, -76}};
        for (int[] f : footprint) for (int x = f[0]; x <= f[2]; x++) for (int z = f[1]; z <= f[3]; z++)
            b.foundation(x, FLOOR - 3, z, 40, bricks);

        // ---- Shells first, openings after, so shared walls stay intact.
        b.room(-10, FLOOR - 1, -30, 10, FLOOR + 7, -8, bricks);    // R1 Hall of Tombs
        b.room(10, FLOOR - 1, -26, 26, FLOOR + 6, -12, bricks);    // R2 Library
        b.room(26, FLOOR - 1, -23, 34, FLOOR + 4, -15, black);     // R2b hidden Reliquary of the Mask
        b.room(-30, FLOOR - 1, -21, -10, FLOOR + 4, -17, bricks);  // Bone Walk
        b.room(-40, FLOOR - 1, -24, -30, FLOOR + 5, -14, bricks);  // Bone Walk storeroom
        b.room(-9, FLOOR - 1, -48, 9, FLOOR + 7, -30, bricks);     // R3 Choir of Bells
        b.room(-8, FLOOR - 1, -64, 8, FLOOR + 6, -48, bricks);     // R4 Crypt
        b.room(-6, FLOOR - 1, -76, 6, FLOOR + 5, -64, black);      // R5 Reliquary
        b.room(-17, FLOOR - 2, -112, 17, FLOOR + 14, -76, bricks); // R6 Sanctum (floor one lower)

        // Openings.
        b.fill(-1, FLOOR, -8, 1, FLOOR + 3, -8, AIR);
        b.fill(10, FLOOR, -20, 10, FLOOR + 2, -18, AIR);
        b.fill(-10, FLOOR, -20, -10, FLOOR + 2, -18, AIR);
        b.fill(-30, FLOOR, -20, -30, FLOOR + 2, -18, AIR);
        b.fill(-1, FLOOR, -30, 1, FLOOR + 3, -30, AIR);
        b.fill(-1, FLOOR, -64, 1, FLOOR + 2, -64, AIR);
        b.fill(-1, FLOOR - 1, -76, 1, FLOOR + 3, -76, AIR);
        // The hidden wall: it looks like Dis brick, but a crack betrays it.
        b.fill(26, FLOOR, -20, 26, FLOOR + 1, -18, s(ModBlocks.HOLLOW_WALL));
        // The Sealed Door between the choir and the crypt.
        b.fill(-1, FLOOR, -48, 1, FLOOR + 3, -48, s(ModBlocks.SEALED_DOOR));

        hallOfTombs(b);
        library(b);
        boneWalk(b);
        choir(b);
        crypt(b);
        reliquary(b);
        sanctum(b);
        // A few cracked bricks everywhere: the city is old.
        for (int i = 0; i < 120; i++) {
            int x = b.rand.nextInt(60) - 35, z = -b.rand.nextInt(110), y = FLOOR + b.rand.nextInt(6);
            if (b.get(x, y, z).is(ModBlocks.DIS_BRICKS)) b.set(x, y, z, cracked);
        }
    }

    private static void hallOfTombs(Build b) {
        for (int z = -27; z <= -12; z += 5) {
            b.set(-6, FLOOR, z, facing(ModBlocks.BURNING_TOMB, Direction.EAST));
            b.set(6, FLOOR, z, facing(ModBlocks.BURNING_TOMB, Direction.WEST));
        }
        b.fill(-1, FLOOR - 1, -29, 1, FLOOR - 1, -9, s(Blocks.POLISHED_BLACKSTONE));
        b.spawner(8, FLOOR, -28, HellEntities.HERETIC);
        b.spawner(-8, FLOOR, -10, HellEntities.HERETIC);
        b.chest(-8, FLOOR, -28, Direction.EAST, "chests/ossuary_common");
        for (int z = -26; z <= -10; z += 8) for (int x : new int[]{-9, 9}) b.set(x, FLOOR + 4, z, ArenaBuilders.hangingLantern(Blocks.SOUL_LANTERN));
        b.mob(3, FLOOR, -20, HellEntities.HERETIC, m -> {});
    }

    private static void library(Build b) {
        for (int x = 11; x <= 25; x++) for (int y = FLOOR; y <= FLOOR + 3; y++) {
            b.set(x, y, -25, s(Blocks.BOOKSHELF));
            b.set(x, y, -13, s(Blocks.BOOKSHELF));
        }
        for (int z = -24; z <= -14; z++) for (int y = FLOOR; y <= FLOOR + 3; y++) if (z < -20 || z > -18) b.set(25, y, z, s(Blocks.BOOKSHELF));
        b.fill(14, FLOOR, -21, 14, FLOOR + 2, -17, s(Blocks.BOOKSHELF));
        b.fill(20, FLOOR, -21, 20, FLOOR + 2, -17, s(Blocks.BOOKSHELF));
        // The riddle of the choir.
        b.lectern(17, FLOOR, -19, Direction.WEST, Build.book("order_of_the_fallen", "The Order of the Fallen", 3));
        b.chest(23, FLOOR, -15, Direction.WEST, "chests/ossuary_library");
        b.set(17, FLOOR + 4, -19, ArenaBuilders.hangingLantern(Blocks.LANTERN));
        b.mob(12, FLOOR, -15, HellEntities.HERETIC, m -> {});
        // Hidden Reliquary of the Mask: a painting of VIGIL in black and white, and what the last pilgrim hid.
        BlockState white = ArenaBuilders.concrete(DyeColor.WHITE), black = ArenaBuilders.concrete(DyeColor.BLACK);
        b.fill(33, FLOOR, -22, 33, FLOOR + 3, -16, black);
        int[][] mask = {{-1, 3}, {0, 3}, {1, 3}, {-2, 2}, {2, 2}, {-2, 1}, {2, 1}, {-1, 1}, {1, 1}, {-2, 0}, {2, 0}, {0, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int[] m : mask) b.set(33, FLOOR + m[1], -19 + m[0], white);
        b.set(33, FLOOR + 1, -20, black); b.set(33, FLOOR + 1, -18, black);
        b.chest(28, FLOOR, -22, Direction.SOUTH, "chests/ossuary_secret");
        b.set(31, FLOOR, -16, facing(ModBlocks.PILGRIM_REMAINS, Direction.WEST));
        ArenaBuilders.votive(b, 28, FLOOR, -16, s(ModBlocks.DIS_BRICKS));
    }

    private static void boneWalk(Build b) {
        // A pit of lava under a floor of crumbling bone, and fire traps in the walls.
        b.fill(-25, FLOOR - 4, -20, -15, FLOOR - 2, -18, AIR);
        b.fill(-25, FLOOR - 4, -20, -15, FLOOR - 4, -18, s(Blocks.LAVA));
        b.fill(-25, FLOOR - 1, -20, -15, FLOOR - 1, -18, s(ModBlocks.CRUMBLING_BONE));
        for (int x = -27; x <= -13; x += 7) {
            b.set(x, FLOOR, -18, s(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE));
            BlockPos dp = b.at(x, FLOOR, -17);
            b.set(x, FLOOR, -17, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.NORTH));
            if (b.box.isInside(dp) && b.level.getBlockEntity(dp) instanceof DispenserBlockEntity d) d.insertItem(new ItemStack(Items.FIRE_CHARGE, 6));
        }
        b.chest(-38, FLOOR, -22, Direction.EAST, "chests/ossuary_common");
        b.barrel(-38, FLOOR, -16, "chests/ossuary_common");
        b.set(-34, FLOOR, -19, facing(ModBlocks.PILGRIM_REMAINS, Direction.NORTH));
        b.set(-35, FLOOR, -19, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 2));
    }

    private static void choir(Build b) {
        // Four bells, one for each sin; which bell sits where differs in every Ossuary.
        List<Integer> notes = new ArrayList<>(List.of(0, 1, 2, 3));
        for (int i = 3; i > 0; i--) { int j = b.rand.nextInt(i + 1); int t = notes.get(i); notes.set(i, notes.get(j)); notes.set(j, t); }
        int[][] spots = {{-5, -35}, {5, -35}, {-5, -43}, {5, -43}};
        for (int i = 0; i < 4; i++) {
            int x = spots[i][0], z = spots[i][1], note = notes.get(i);
            b.set(x, FLOOR, z, s(Blocks.CHISELED_POLISHED_BLACKSTONE));
            b.set(x, FLOOR + 1, z, ModBlocks.CHOIR_BELL.defaultBlockState().setValue(PuzzleBlocks.ChoirBell.NOTE, note));
            Direction face = x < 0 ? Direction.EAST : Direction.WEST;
            b.sign(x + face.getStepX(), FLOOR, z, face, "puzzle.quantumdeath.sin_bell", "puzzle.quantumdeath.sin." + note);
        }
        b.set(0, FLOOR + 6, -39, ArenaBuilders.hangingLantern(Blocks.SOUL_LANTERN));
        b.set(-1, FLOOR, -47, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 4));
        b.set(1, FLOOR, -47, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 4));
    }

    private static void crypt(Build b) {
        b.set(0, FLOOR, -56, facing(ModBlocks.SEALED_SARCOPHAGUS, Direction.SOUTH));
        for (int x : new int[]{-3, 3}) for (int z : new int[]{-59, -53}) b.set(x, FLOOR, z, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
        for (int z = -62; z <= -50; z += 4) { b.set(-7, FLOOR, z, facing(ModBlocks.BURNING_TOMB, Direction.EAST)); b.set(7, FLOOR, z, facing(ModBlocks.BURNING_TOMB, Direction.WEST)); }
        b.chest(-6, FLOOR, -50, Direction.EAST, "chests/ossuary_common");
        b.spawner(6, FLOOR, -62, HellEntities.HERETIC);
    }

    private static void reliquary(Build b) {
        // Each player may open each vault once per Ossuary Key: friends never compete for the reward.
        ItemStack key = new ItemStack(HellItems.OSSUARY_KEY);
        b.vault(-3, FLOOR, -73, Direction.EAST, "chests/ossuary_vault", key);
        b.vault(3, FLOOR, -73, Direction.WEST, "chests/ossuary_vault", key);
        b.vault(-3, FLOOR, -68, Direction.EAST, "chests/ossuary_vault", key);
        b.vault(3, FLOOR, -68, Direction.WEST, "chests/ossuary_vault", key);
        b.set(0, FLOOR + 4, -70, ArenaBuilders.hangingLantern(Blocks.SOUL_LANTERN));
        b.sign(-5, FLOOR + 1, -70, Direction.EAST, "sign.quantumdeath.reliquary.0", "sign.quantumdeath.reliquary.1", "sign.quantumdeath.reliquary.2", "sign.quantumdeath.reliquary.3");
    }

    private static void sanctum(Build b) {
        int f = FLOOR - 1;
        b.fill(-16, f - 1, -111, 16, f - 1, -77, s(ModBlocks.DIS_BRICKS));
        // Centre at z = -94, altar 12 south of it.
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 11), z = -94 + (int) Math.round(Math.sin(a) * 11);
            b.set(x, f, z, facing(ModBlocks.BURNING_TOMB, Direction.Plane.HORIZONTAL.getRandomDirection(b.rand)));
        }
        for (int[] p : new int[][]{{-7, -101}, {7, -101}, {-7, -87}, {7, -87}}) b.fill(p[0], f, p[1], p[0] + 1, f + 13, p[1] + 1, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.ring(0, -94, f - 1, 3.5, 4.5, s(ModBlocks.MOLTEN_DIS_IRON));
        b.set(0, f, -82, s(ModBlocks.CONFESSION_LECTERN));
        ArenaBuilders.gate(b, 0, f, -110, s(ModBlocks.DIS_BRICKS), s(Blocks.WITHER_SKELETON_SKULL));
        ArenaBuilders.votive(b, 4, f, -78, s(ModBlocks.DIS_BRICKS));
        b.chest(-4, f, -78, Direction.EAST, "chests/ossuary_common");
        for (int x = -15; x <= 15; x += 6) b.pillar(x, -111, f + 6, f + 12, s(Blocks.IRON_CHAIN));
    }
}
