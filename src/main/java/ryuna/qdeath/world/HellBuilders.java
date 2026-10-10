package ryuna.qdeath.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;

import static ryuna.qdeath.world.ArenaBuilders.AIR;
import static ryuna.qdeath.world.ArenaBuilders.facing;

/** Dispatches structure kinds to their builders and declares each kind's footprint. */
public final class HellBuilders {
    private HellBuilders() {}

    static BlockState s(Block b) { return b.defaultBlockState(); }

    public static BoundingBox bounds(String kind, BlockPos o) {
        int[] r = switch (kind) {
            case "ferry_landing" -> new int[]{-31, -19, -31, 31, 27, 61};
            case "feasting_pit" -> new int[]{-29, -8, -29, 29, 26, 59};
            case "drowned_choir" -> new int[]{-25, -20, -25, 25, 27, 41};
            case "ossuary" -> new int[]{-41, -20, -113, 35, 12, 12};
            case "last_vigil" -> new int[]{-28, -31, -28, 28, 41, 73};
            case "dis_watchtower" -> new int[]{-6, -4, -6, 6, 34, 6};
            case "sunken_chapel" -> new int[]{-9, -10, -12, 9, 14, 12};
            case "butchers_hall" -> new int[]{-10, -3, -10, 10, 10, 10};
            case "funeral_village" -> new int[]{-14, -10, -14, 14, 9, 14};
            case "procession_pit" -> new int[]{-11, -12, -11, 11, 8, 11};
            case "limbo_ossuary" -> new int[]{-11, -16, -11, 11, 6, 11};
            default -> new int[]{-8, -4, -8, 8, 10, 8};
        };
        return new BoundingBox(o.getX() + r[0], o.getY() + r[1], o.getZ() + r[2], o.getX() + r[3], o.getY() + r[4], o.getZ() + r[5]);
    }

    public static void build(String kind, Build b) {
        switch (kind) {
            case "ferry_landing" -> ArenaBuilders.ferryLanding(b);
            case "feasting_pit" -> ArenaBuilders.feastingPit(b);
            case "drowned_choir" -> ArenaBuilders.drownedChoir(b);
            case "ossuary" -> OssuaryBuilder.build(b);
            case "last_vigil" -> ArenaBuilders.lastVigil(b);
            case "pilgrim_camp" -> pilgrimCamp(b);
            case "mourner_shrine" -> mournerShrine(b);
            case "butchers_hall" -> butchersHall(b);
            case "sunken_chapel" -> sunkenChapel(b);
            case "dis_watchtower" -> disWatchtower(b);
            case "false_house" -> falseHouse(b);
            case "funeral_village" -> LimboBuilders.funeralVillage(b);
            case "procession_pit" -> LimboBuilders.processionPit(b);
            case "limbo_ossuary" -> LimboBuilders.limboOssuary(b);
            default -> {}
        }
    }

    private static HellLayer layer(Build b) {
        HellLayer l = HellLayer.of(b.level.getLevel().dimension());
        return l == null ? HellLayer.VESTIBULE : l;
    }

    private static BlockState brick(Build b) { return ryuna.qdeath.hell.HellTravel.brick(layer(b)); }

    private static BlockState ground(Build b) {
        return s(switch (layer(b)) {
            case VESTIBULE -> ModBlocks.CINDER_SOIL;
            case GORGE -> ModBlocks.FLESH;
            case MIRE -> ModBlocks.STYGIAN_MUD;
            case DIS -> ModBlocks.DIS_BRICKS;
            case ZENITH -> ModBlocks.ZENITH_STONE;
        });
    }

    /** A pilgrim's camp. Some are still lit; some were found by VIGIL first. */
    static void pilgrimCamp(Build b) {
        boolean ravaged = b.rand.nextBoolean();
        b.carve(7, 4, 7, 2, 0, ground(b));
        b.disc(0, 0, -1 + 1, 6, AIR);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (x * x + z * z <= 36) b.foundation(x, -1, z, 6, ground(b));
        BlockState canvas = Blocks.WOOL.pick(DyeColor.LIGHT_GRAY).defaultBlockState(), pole = s(ModBlocks.CHARRED_LOG);
        int[][] frame = {{-2, 0}, {-1, 1}, {0, 2}, {1, 1}, {2, 0}};
        for (int z = -2; z <= 2; z++) for (int[] f : frame) if (!ravaged || b.noise(f[0], f[1], z) > 0.35) b.set(f[0], f[1], z, canvas);
        b.set(0, 0, -3, pole); b.set(0, 1, -3, pole); b.set(0, 2, -3, pole);
        b.set(0, 0, 0, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
        b.set(3, 0, 3, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, !ravaged));
        b.chest(-1, 0, 1, Direction.SOUTH, "chests/pilgrim_camp");
        if (!ravaged) ArenaBuilders.votive(b, 4, 0, -3, pole);
        else {
            b.set(2, 0, 2, facing(ModBlocks.PILGRIM_REMAINS, Direction.NORTH));
            for (int i = 0; i < 5; i++) b.set((int) Math.round(Math.cos(i * 1.256) * 3) + 2, 0, (int) Math.round(Math.sin(i * 1.256) * 3) + 2,
                    s(Blocks.CANDLE).setValue(CandleBlock.CANDLES, 1 + b.rand.nextInt(3)));
            for (int i = 1; i < 6; i++) b.set(-2 - i, 0, 4 + (i & 1), s(ModBlocks.ASH_PRINT));
        }
        b.set(-4, 0, -2, s(ModBlocks.CHARRED_PLANKS));
        b.sign(-4, 0, -1, Direction.SOUTH, "sign.quantumdeath.camp." + b.rand.nextInt(4) + ".0", "sign.quantumdeath.camp.x.1");
        if (layer(b) == HellLayer.VESTIBULE) {
            // Limbo survivors learnt to turn processions aside: a half-ring of funeral barricades and a warning post.
            for (int i = -3; i <= 3; i++) if (!ravaged || b.noise(i, 2, -6) > 0.4) b.set(i, 0, -5 - (Math.abs(i) > 1 ? 0 : 1), s(ModBlocks.FUNERAL_BARRICADE));
            b.set(5, 0, -1, s(ModBlocks.CHARRED_LOG));
            b.sign(5, 0, 0, Direction.SOUTH, "sign.quantumdeath.camp.limbo.0", "sign.quantumdeath.camp.limbo.1", "sign.quantumdeath.camp.limbo.2", "sign.quantumdeath.camp.limbo.3");
        }
    }

    /** A roadside shrine with a votive lantern: VIGIL cannot enter its light. Learn where they are. */
    static void mournerShrine(Build b) {
        BlockState brick = brick(b);
        b.fill(-4, 1, -4, 4, 8, 4, AIR);
        b.fill(-3, 0, -3, 3, 0, 3, brick);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.foundation(x, -1, z, 8, brick);
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) b.pillar(c[0], c[1], 1, 4, brick);
        b.fill(-3, 5, -3, 3, 5, 3, brick);
        b.fill(-2, 6, -2, 2, 6, 2, brick);
        boolean limbo = layer(b) == HellLayer.VESTIBULE;
        boolean burning = limbo && b.rand.nextInt(3) == 0;
        BlockState lantern = s(ModBlocks.VOTIVE_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        if (burning) lantern = lantern.setValue(ryuna.qdeath.block.HellBlockTypes.VotiveLantern.FUEL, 2 + b.rand.nextInt(5));
        b.set(0, 4, 0, lantern);
        if (burning) b.tick(0, 4, 0, ModBlocks.VOTIVE_LANTERN, 20);
        if (limbo) b.sign(3, 1, 4, Direction.SOUTH, "sign.quantumdeath.shrine.0", "sign.quantumdeath.shrine.1", "sign.quantumdeath.shrine.2", "sign.quantumdeath.shrine.3");
        b.set(0, 1, -2, s(Blocks.LECTERN));
        b.lectern(0, 1, -2, Direction.SOUTH, Build.book("on_the_mourner", "On the Mourner", 3));
        // Candles burn while the consecration lasts; at an exhausted shrine they have melted out.
        for (int[] c : new int[][]{{-2, 2}, {2, 2}, {-2, -1}, {2, -1}}) b.set(c[0], 1, c[1], s(Blocks.CANDLE).setValue(CandleBlock.LIT, !limbo || burning).setValue(CandleBlock.CANDLES, 1 + b.rand.nextInt(4)));
        if (b.rand.nextInt(3) == 0 || limbo) b.chest(0, 1, 2, Direction.NORTH, limbo ? "chests/mourner_shrine" : "chests/pilgrim_camp");
    }

    /** The Butcher's Hall of the Gorge: meat on chains, teeth in the floor, a Glutton spawner. */
    static void butchersHall(Build b) {
        BlockState wall = s(ModBlocks.GRISTLE_BRICKS);
        b.room(-8, -1, -5, 8, 7, 5, wall);
        for (int x = -8; x <= 8; x++) for (int z = -5; z <= 5; z++) b.foundation(x, -2, z, 8, s(ModBlocks.GRISTLE));
        b.fill(-1, 0, 5, 1, 2, 5, AIR);
        for (int x = -6; x <= 6; x += 3) for (int z : new int[]{-3, 2}) {
            b.pillar(x, z, 4, 6, s(Blocks.IRON_CHAIN));
            b.set(x, 3, z, s(ModBlocks.FLESH));
        }
        b.set(-4, 0, 0, s(ModBlocks.MAW_TRAP).setValue(ryuna.qdeath.block.HellBlockTypes.MawTrap.BITING, false));
        b.set(4, 0, -1, s(ModBlocks.MAW_TRAP));
        b.set(0, -1, 2, s(ModBlocks.MAW_TRAP));
        b.fill(-2, 0, -3, 2, 0, -3, s(ModBlocks.GRISTLE_BRICKS));
        b.set(0, 1, -3, s(Blocks.SKELETON_SKULL));
        b.chest(-6, 0, -4, Direction.SOUTH, "chests/butchers_hall");
        b.barrel(6, 0, -4, "chests/butchers_hall");
        b.spawner(0, 0, -1, HellEntities.GLUTTON);
    }

    /** A chapel sinking into the Styx, its congregation turned to stone in the pews. */
    static void sunkenChapel(Build b) {
        BlockState wall = s(ModBlocks.STYGIAN_BRICKS), soul = s(ModBlocks.PETRIFIED_SOUL);
        b.room(-5, -4, -9, 5, 6, 9, wall);
        b.fill(-4, -3, -8, 4, -1, 8, s(Blocks.WATER));
        for (int x = -5; x <= 5; x++) for (int z = -9; z <= 9; z++) if (b.noise(x, 6, z) < 0.35) b.set(x, 6, z, AIR);
        b.fill(-1, -3, 9, 1, 1, 9, AIR);
        for (int z = -4; z <= 5; z += 3) for (int x : new int[]{-3, 2}) {
            b.fill(x, -2, z, x + 1, -2, z, s(Blocks.DEEPSLATE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
            if (b.rand.nextBoolean()) { b.set(x, -1, z + 1, soul); b.set(x, 0, z + 1, s(Blocks.SKELETON_SKULL)); }
        }
        b.fill(-2, -3, -8, 2, -2, -7, wall);
        b.chest(0, -1, -8, Direction.SOUTH, "chests/sunken_chapel");
        b.set(-2, -1, -7, s(Blocks.SOUL_LANTERN));
        b.set(2, -1, -7, s(Blocks.SOUL_LANTERN));
        b.mob(0, -2, 2, HellEntities.SULLEN, m -> {});
        b.mob(-3, -2, -2, HellEntities.SULLEN, m -> {});
    }

    /** A watchtower of the iron city; furies nest at its top. */
    static void disWatchtower(Build b) {
        BlockState wall = s(ModBlocks.DIS_BRICKS), iron = s(ModBlocks.DIS_IRON_BLOCK);
        b.room(-3, -1, -3, 3, 26, 3, wall);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.foundation(x, -2, z, 12, wall);
        b.fill(0, 0, 3, 0, 1, 3, AIR);
        for (int y = 0; y <= 25; y++) b.set(0, y, -2, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH));
        for (int y = 8; y <= 24; y += 8) { b.fill(-2, y, -1, 2, y, 2, wall); }
        b.fill(-4, 26, -4, 4, 26, 4, iron);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if ((Math.abs(x) == 4 || Math.abs(z) == 4) && ((x + z) & 1) == 0) b.set(x, 27, z, wall);
        b.set(0, 26, -2, AIR); b.set(0, 25, -2, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH));
        b.set(-3, 27, -3, ArenaBuilders.litCampfire(Blocks.CAMPFIRE));
        b.set(3, 27, 3, ArenaBuilders.litCampfire(Blocks.CAMPFIRE));
        b.chest(2, 27, -2, Direction.WEST, "chests/dis_watchtower");
        b.mob(1, 29, 1, HellEntities.IRON_FURY, m -> {});
        b.mob(-1, 30, -1, HellEntities.IRON_FURY, m -> {});
    }

    /** In the Zenith, a perfectly ordinary house. Grass, a bed, a flower pot. Somebody made it to remember. */
    static void falseHouse(Build b) {
        BlockState planks = s(Blocks.OAK_PLANKS), log = s(Blocks.OAK_LOG);
        b.fill(-6, 1, -6, 6, 9, 6, AIR);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) { b.set(x, 0, z, s(Blocks.GRASS_BLOCK)); b.foundation(x, -1, z, 6, s(Blocks.DIRT)); }
        b.room(-3, 0, -3, 3, 5, 3, planks);
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) b.pillar(c[0], c[1], 1, 4, log);
        b.fill(-3, 0, -3, 3, 0, 3, planks);
        b.set(-3, 2, 0, s(Blocks.GLASS_PANE)); b.set(3, 2, 0, s(Blocks.GLASS_PANE)); b.set(0, 2, -3, s(Blocks.GLASS_PANE));
        b.set(0, 1, 3, s(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, 2, 3, s(Blocks.OAK_DOOR).setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Block bed = Blocks.BED.pick(DyeColor.RED);
        b.set(-2, 1, -2, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        b.set(-2, 1, -1, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        b.set(2, 1, -2, s(Blocks.CRAFTING_TABLE));
        b.set(2, 1, -1, s(Blocks.FURNACE));
        b.set(2, 1, 1, s(Blocks.POTTED_POPPY));
        b.chest(-2, 1, 1, Direction.EAST, "chests/false_house");
        b.set(0, 4, 0, ArenaBuilders.hangingLantern(Blocks.LANTERN));
        b.lectern(1, 1, 0, Direction.WEST, Build.book("home", "Home", 3));
        b.set(2, 1, 4, s(Blocks.OAK_FENCE)); b.set(2, 2, 4, s(Blocks.LANTERN));
        for (int i = 0; i < 6; i++) b.set(b.rand.nextInt(9) - 4, 1, 4 + b.rand.nextInt(2), s(i % 2 == 0 ? Blocks.POPPY : Blocks.DANDELION));
        b.set(-1, 1, 4, planks);
        b.sign(-1, 1, 5, Direction.SOUTH, "sign.quantumdeath.home.0", "sign.quantumdeath.home.1", "sign.quantumdeath.home.2", "sign.quantumdeath.home.3");
    }
}
