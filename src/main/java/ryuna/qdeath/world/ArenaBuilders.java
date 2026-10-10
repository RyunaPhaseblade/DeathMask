package ryuna.qdeath.world;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import ryuna.qdeath.block.PuzzleBlocks;
import ryuna.qdeath.registry.ModBlocks;

import static ryuna.qdeath.world.HellBuilders.s;

/**
 * The boss arenas. In every arena the altar stands 12 blocks south of the centre (see BossSummons),
 * the descent gate stands on the north side, and a votive lantern near the entrance gives shelter from VIGIL.
 */
final class ArenaBuilders {
    private ArenaBuilders() {}

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Sealed gateway: a stone frame around a 3x5 descent gate. */
    static void gate(Build b, int x, int y, int z, BlockState frame, BlockState accent) {
        b.fill(x - 3, y, z, x + 3, y + 6, z, frame);
        b.fill(x - 1, y, z, x + 1, y + 4, z, ModBlocks.DESCENT_GATE.defaultBlockState());
        b.set(x - 3, y + 7, z, accent);
        b.set(x + 3, y + 7, z, accent);
        b.set(x, y + 7, z, accent);
        b.fill(x - 2, y - 1, z - 1, x + 2, y - 1, z + 1, frame);
    }

    static void votive(Build b, int x, int y, int z, BlockState post) {
        b.set(x, y, z, post);
        b.set(x, y + 1, z, ModBlocks.VOTIVE_LANTERN.defaultBlockState());
    }

    /**
     * Natural floor height (relative to the origin) of column (0, z) near relative height {@code near}, read from the noise
     * itself so every chunk the road crosses computes the same road; null when the column is rock there.
     */
    static Integer groundAt(Build b, int z, int near) {
        var source = b.level.getLevel().getChunkSource();
        net.minecraft.world.level.NoiseColumn col = source.getGenerator().getBaseColumn(b.origin.getX(), b.origin.getZ() + z, b.level, source.randomState());
        for (int d = 0; d <= 6; d++) {
            for (int sgn : new int[]{1, -1}) {
                int yy = b.origin.getY() + near + d * sgn;
                if (col.getBlock(yy - 1).isSolid() && col.getBlock(yy).isAir() && col.getBlock(yy + 1).isAir()) return yy - b.origin.getY();
                if (d == 0) break;
            }
        }
        return null;
    }

    static BlockState facing(Block block, Direction d) { return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d); }

    // ------------------------------------------------------------------ I. The Toll Gate (Ferry Landing)
    static void ferryLanding(Build b) {
        BlockState bricks = s(ModBlocks.ASHEN_BRICKS), chiseled = s(ModBlocks.CHISELED_ASHEN_BRICKS), basalt = s(Blocks.POLISHED_BASALT);
        b.carve(30, 18, 30, 8, 0, Blocks.LAVA.defaultBlockState());
        // The processional road south out of the crater. The landing stands on an open floor of the Limbo, so the road
        // follows the real ground; wherever rock closes over it, it becomes a tunnel climbing at a walkable slope.
        int y = 0;
        int[] road = new int[61];
        for (int z = 24; z <= 60; z++) {
            Integer ground = z <= 30 ? null : groundAt(b, z, y);
            if (ground != null && Math.abs(ground - y) <= 1) y = ground;
            else if (ground != null) y += Integer.signum(ground - y);
            else y = Math.min(y + (z % 2), 17);
            b.fill(-2, y + 1, z, 2, y + 4, z, AIR);
            b.fill(-2, y, z, 2, y, z, bricks);
            if (z % 6 == 0) { b.set(-3, y + 3, z, s(Blocks.SOUL_LANTERN)); b.set(3, y + 3, z, s(Blocks.SOUL_LANTERN)); }
            road[z] = y;
        }
        // The landing: a round dock on the burning lake, a causeway to the south, a gate platform to the north.
        b.disc(0, 0, 0, 10, bricks);
        b.ring(0, 0, 0, 9.5, 11, basalt);
        b.fill(-2, 0, 10, 2, 0, 26, bricks);
        b.fill(-5, 0, -16, 5, 0, -9, bricks);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 8), pz = (int) Math.round(Math.sin(a) * 8);
            b.foundation(px, -1, pz, 20, s(ModBlocks.CHARRED_LOG));
            b.set(px, 1, pz, s(ModBlocks.CHARRED_LOG));
            b.set(px, 2, pz, s(Blocks.SOUL_LANTERN));
        }
        for (int z = 12; z <= 26; z += 7) for (int x : new int[]{-2, 2}) b.foundation(x, -1, z, 20, basalt);
        for (int z = 14; z <= 26; z += 4) for (int x : new int[]{-3, 3}) { b.set(x, 0, z, basalt); b.set(x, 1, z, s(Blocks.BLACKSTONE_WALL)); b.set(x, 2, z, s(Blocks.SOUL_LANTERN)); }
        // The Gate of the Vestibule.
        gate(b, 0, 1, -13, bricks, chiseled);
        b.set(-4, 1, -12, s(Blocks.SOUL_CAMPFIRE));
        b.set(4, 1, -12, s(Blocks.SOUL_CAMPFIRE));
        // Altar and its inscription.
        b.set(0, 1, 12, s(ModBlocks.TOLL_BRAZIER));
        b.set(-3, 0, 12, chiseled); b.set(-3, 1, 12, chiseled);
        b.sign(-2, 1, 12, Direction.EAST, "sign.quantumdeath.toll.0", "sign.quantumdeath.toll.1", "sign.quantumdeath.toll.2", "sign.quantumdeath.toll.3");
        b.chest(2, 1, 13, Direction.WEST, "chests/ferry_landing");
        b.lectern(4, 1, -6, Direction.SOUTH, Build.book("ferrymans_ledger", "The Ferryman's Ledger", 4));
        votive(b, 0, 1, 24, s(ModBlocks.CHARRED_LOG));
        b.set(-5, 1, 4, facing(ModBlocks.PILGRIM_REMAINS, Direction.EAST));
        b.set(-6, 1, 4, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
        // The Ferryman's Landing in the Limbo: cairns mark the route up the tunnel, an obol dish waits by the altar,
        // a funeral barricade closes a side ledge, and (sometimes) a Cinder Knight still guards the causeway.
        for (int z = 30; z <= 58; z += 7) b.set(z % 2 == 0 ? -3 : 3, road[z] + 1, z, s(Blocks.COBBLED_DEEPSLATE_WALL));
        b.set(-2, 1, 14, s(Blocks.DECORATED_POT));
        b.set(-2, 1, 16, s(ModBlocks.CHARRED_LOG));
        b.sign(-2, 1, 17, Direction.SOUTH, "sign.quantumdeath.landing.0", "sign.quantumdeath.landing.1", "sign.quantumdeath.landing.2", "sign.quantumdeath.landing.3");
        b.fill(-5, 1, -9, -5, 2, -9, s(ModBlocks.FUNERAL_BARRICADE));
        if (b.rand.nextBoolean()) b.mob(0, 1, 20, ryuna.qdeath.registry.HellEntities.CINDER_KNIGHT, m -> {});
        // Chains hanging from the cavern roof.
        for (int i = 0; i < 10; i++) {
            double a = b.rand.nextDouble() * Math.PI * 2, r = 6 + b.rand.nextDouble() * 14;
            int cx = (int) (Math.cos(a) * r), cz = (int) (Math.sin(a) * r);
            int len = 3 + b.rand.nextInt(6);
            b.pillar(cx, cz, 22 - len, 22, s(Blocks.IRON_CHAIN));
        }
    }

    // ------------------------------------------------------------------ II. The Feasting Pit
    static void feastingPit(Build b) {
        BlockState flesh = s(ModBlocks.FLESH), gristle = s(ModBlocks.GRISTLE), bricks = s(ModBlocks.GRISTLE_BRICKS), bone = s(Blocks.BONE_BLOCK);
        b.carve(28, 16, 28, 9, 0, flesh);
        for (int z = 24; z <= 58; z++) {
            int y = Math.max(0, (z - 26) / 2);
            b.fill(-2, y + 1, z, 2, y + 4, z, AIR);
            b.fill(-2, y, z, 2, y, z, bricks);
        }
        b.disc(0, 0, 0, 26, flesh);
        b.ring(0, 0, 0, 5, 6, gristle);
        b.ring(0, 0, 0, 16, 17, gristle);
        // Six gristle pillars: cover from the Matriarch's inhale.
        for (int i = 0; i < 6; i++) {
            // 0, 60, 120... degrees: keeps the altar (south) and the gate (north) clear.
            double a = i * Math.PI / 3;
            int px = (int) Math.round(Math.cos(a) * 13), pz = (int) Math.round(Math.sin(a) * 13);
            b.fill(px - 1, 1, pz - 1, px + 1, 15, pz + 1, gristle);
            for (Direction d : Direction.Plane.HORIZONTAL) b.set(px + d.getStepX() * 2, 1, pz + d.getStepZ() * 2, s(ModBlocks.FANG_SPIKE));
        }
        // Ribs arching over the pit.
        for (int rib = -1; rib <= 1; rib++) {
            for (int t = 0; t <= 40; t++) {
                double ang = Math.PI * t / 40;
                int x = (int) Math.round(Math.cos(ang) * 22), y = 1 + (int) Math.round(Math.sin(ang) * 13);
                b.set(x, y, rib * 8, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = b.rand.nextDouble() * Math.PI * 2, r = 6 + b.rand.nextDouble() * 4;
            b.disc((int) (Math.cos(a) * r), (int) (Math.sin(a) * r), 0, 2.2, s(ModBlocks.BILE));
        }
        for (int i = 0; i < 10; i++) {
            double a = b.rand.nextDouble() * Math.PI * 2, r = 18 + b.rand.nextDouble() * 5;
            int x = (int) (Math.cos(a) * r), z = (int) (Math.sin(a) * r);
            if (Math.abs(x) > 3 || z < 0) b.set(x, 0, z, s(ModBlocks.MAW_TRAP));
        }
        gate(b, 0, 1, -18, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), s(Blocks.SKELETON_SKULL));
        b.set(0, 1, 12, s(ModBlocks.FEAST_ALTAR));
        b.fill(-1, 1, 15, 1, 1, 21, bricks);
        for (int z = 15; z <= 21; z += 2) b.set(0, 2, z, s(Blocks.SKELETON_SKULL));
        b.chest(2, 1, 13, Direction.WEST, "chests/feasting_pit");
        b.sign(0, 1, 14, Direction.NORTH, "sign.quantumdeath.feast.0", "sign.quantumdeath.feast.1", "sign.quantumdeath.feast.2", "sign.quantumdeath.feast.3");
        votive(b, 0, 1, 25, bricks);
        b.set(6, 1, 20, facing(ModBlocks.PILGRIM_REMAINS, Direction.WEST));
    }

    // ------------------------------------------------------------------ III. The Drowned Choir
    static void drownedChoir(Build b) {
        BlockState bricks = s(ModBlocks.STYGIAN_BRICKS), stone = s(ModBlocks.STYGIAN_STONE), soul = s(ModBlocks.PETRIFIED_SOUL), water = s(Blocks.WATER);
        // Clear the marsh, flood the basin.
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 24 - b.noise(x, 0, z) * 2) continue;
            b.fill(x, 1, z, x, 26, z, AIR);
            if (d <= 10.5) b.fill(x, -8, z, x, 0, z, water);
            else if (d <= 18.5) { b.set(x, 0, z, bricks); b.foundation(x, -1, z, 12, stone); }
            else b.set(x, 0, z, water);
        }
        // Eight drowned singers on their pillars.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 14), pz = (int) Math.round(Math.sin(a) * 14);
            b.fill(px, 1, pz, px + 1, 10, pz + 1, soul);
            b.set(px, 11, pz, s(ModBlocks.PETRIFIED_SOUL));
            b.set(px, 12, pz, s(Blocks.SKELETON_SKULL));
            b.set(px + 1, 11, pz + 1, s(Blocks.SOUL_LANTERN));
        }
        // Sunken figures in the pool.
        for (int i = 0; i < 6; i++) {
            int x = b.rand.nextInt(13) - 6, z = b.rand.nextInt(13) - 6;
            b.fill(x, -8, z, x, -6, z, soul);
            b.set(x, -5, z, s(Blocks.SKELETON_SKULL));
        }
        gate(b, 0, 1, -16, bricks, s(Blocks.SOUL_LANTERN));
        b.set(0, 1, 12, s(ModBlocks.DROWNED_BELL));
        b.chest(2, 1, 13, Direction.WEST, "chests/drowned_choir");
        b.lectern(-3, 1, 13, Direction.EAST, Build.book("hymnal_of_the_sullen", "Hymnal of the Sullen", 3));
        votive(b, 0, 1, 17, bricks);
        // Walkways out into the marsh.
        for (int z = 19; z <= 40; z++) { b.fill(-1, 0, z, 1, 0, z, bricks); b.foundation(0, -1, z, 10, stone); b.fill(-1, 1, z, 1, 3, z, AIR); }
    }

    // ------------------------------------------------------------------ V. The Last Vigil
    static void lastVigil(Build b) {
        BlockState bricks = s(ModBlocks.ZENITH_BRICKS), stone = s(ModBlocks.ZENITH_STONE);
        for (int x = -27; x <= 27; x++) for (int z = -27; z <= 27; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 25) continue;
            b.fill(x, 1, z, x, 40, z, AIR);
            BlockState floor = d < 2 ? s(ModBlocks.RED_STATIC) : Math.abs(d - 6) < 0.6 || Math.abs(d - 20) < 0.6 ? s(ModBlocks.RED_STATIC)
                    : Math.abs(d - 13) < 0.6 ? s(ModBlocks.VOID_GLASS) : ((x + z) & 1) == 0 ? bricks : stone;
            b.set(x, 0, z, floor);
            b.foundation(x, -1, z, 30, stone);
        }
        // Four bell towers; the bells face the centre.
        int[][] towers = {{18, 0}, {-18, 0}, {0, 18}, {0, -18}};
        for (int[] t : towers) {
            b.fill(t[0] - 1, 1, t[1] - 1, t[0] + 1, 12, t[1] + 1, bricks);
            b.set(t[0], 13, t[1], s(ModBlocks.RED_STATIC));
            int bx = t[0] - Integer.signum(t[0]) * 2, bz = t[1] - Integer.signum(t[1]) * 2;
            b.set(bx, 1, bz, ModBlocks.VIGIL_BELL.defaultBlockState().setValue(PuzzleBlocks.VigilBell.RUNG, false));
        }
        // Broken pillars: the gaze cannot pass through stone.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 8 + i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 9), pz = (int) Math.round(Math.sin(a) * 9);
            int h = 4 + b.rand.nextInt(6);
            b.fill(px, 1, pz, px + 1, h, pz + 1, i % 2 == 0 ? bricks : stone);
        }
        b.set(0, 1, 12, s(ModBlocks.LAST_VIGIL_ALTAR));
        b.set(0, 1, 14, bricks);
        b.sign(0, 1, 15, Direction.SOUTH, "sign.quantumdeath.vigil.0", "sign.quantumdeath.vigil.1", "sign.quantumdeath.vigil.2", "sign.quantumdeath.vigil.3");
        // The processional road from the south, lined with kneeling figures and candles that never go out.
        for (int z = 25; z <= 72; z++) {
            b.fill(-1, 0, z, 1, 0, z, bricks);
            b.foundation(0, -1, z, 30, stone);
            b.fill(-2, 1, z, 2, 4, z, AIR);
            if (z % 6 == 0) for (int side : new int[]{-3, 3}) {
                b.set(side, 0, z, bricks);
                b.set(side, 1, z, s(ModBlocks.PALE_ICE));
                b.set(side, 2, z, s(Blocks.SKELETON_SKULL));
                b.set(side, 0, z + 1, bricks);
                b.set(side, 1, z + 1, s(Blocks.CANDLE).setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + b.rand.nextInt(4)));
            }
        }
        b.chest(-3, 1, 22, Direction.EAST, "chests/last_vigil");
        votive(b, 3, 1, 23, bricks);
    }

    static BlockState litCampfire(Block b) { return b.defaultBlockState().setValue(CampfireBlock.LIT, true); }
    static BlockState hangingLantern(Block b) { return b.defaultBlockState().setValue(LanternBlock.HANGING, true); }
    static BlockState concrete(DyeColor c) { return Blocks.CONCRETE.pick(c).defaultBlockState(); }
}
