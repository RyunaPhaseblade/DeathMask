package ryuna.qdeath.hell;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import ryuna.qdeath.Quantumdeath;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Development survey of the Limbo's terrain (run by the worldgen dump, -Dquantumdeath.limboSurvey=true): over a square
 * of side 2R it measures floor layers per column, free height above floors, chamber sizes, exposed lava, and walks the
 * terrain from the arrival point (step up 1, drop up to 3, two blocks of headroom) to find which floors and which
 * structures are reachable on foot. Writes limbo_survey_&lt;seed&gt;.txt plus a floor-layer map and a reachability map.
 */
public final class LimboSurvey {
    private LimboSurvey() {}

    static boolean standable(ServerLevel level, BlockPos.MutableBlockPos p) {
        BlockState below = level.getBlockState(p.move(Direction.DOWN));
        p.move(Direction.UP);
        if (!below.isFaceSturdy(level, p.below(), Direction.UP) || !below.getFluidState().isEmpty()) return false;
        BlockState at = level.getBlockState(p), up = level.getBlockState(p.above());
        return at.getCollisionShape(level, p).isEmpty() && at.getFluidState().isEmpty() && up.getCollisionShape(level, p.above()).isEmpty() && up.getFluidState().isEmpty();
    }

    public static void run(ServerLevel level, int r, File dir) throws Exception {
        long seed = level.getSeed();
        long t0 = System.currentTimeMillis();
        for (int cx = -r >> 4; cx <= r >> 4; cx++) for (int cz = -r >> 4; cz <= r >> 4; cz++) level.getChunk(cx, cz);
        long genMs = System.currentTimeMillis() - t0;
        int w = 2 * r + 1;
        int minY = level.getMinY() + 1, maxY = level.getMinY() + 250;
        BufferedImage layersImg = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        int[] layerHist = new int[8];
        Map<Integer, Integer> roomHist = new TreeMap<>();
        long columns = 0, multi = 0, lavaCols = 0, floorsTotal = 0, bigRoom = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int[] floorBands = new int[13]; // floors by 20-block bands
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            columns++;
            int layers = 0, maxRoom = 0;
            boolean lava = false;
            int run = 0;
            for (int y = maxY; y >= minY; y--) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (s.isAir()) { run++; continue; }
                if (!s.getFluidState().isEmpty() && run > 0) lava = true;
                if (run >= 2 && s.isSolid()) {
                    layers++;
                    floorBands[Math.min(12, (y + 1) / 20)]++;
                    maxRoom = Math.max(maxRoom, run);
                }
                run = 0;
            }
            floorsTotal += layers;
            layerHist[Math.min(7, layers)]++;
            if (layers >= 2) multi++;
            if (lava) lavaCols++;
            if (maxRoom >= 40) bigRoom++;
            roomHist.merge(Math.min(200, maxRoom / 10 * 10), 1, Integer::sum);
            int[] palette = {0x000000, 0x5a5550, 0x9a7a3a, 0xc8a050, 0xe0d070, 0xf0f0a0, 0xffffff, 0xffffff};
            layersImg.setRGB(x + r, z + r, palette[Math.min(7, layers)]);
        }
        // Walk from the arrival point.
        BlockPos start = HellTravel.findSafe(level, HellLayer.VESTIBULE, BlockPos.ZERO.atY(64));
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start.asLong());
        queue.enqueue(start.asLong());
        int minReachY = start.getY(), maxReachY = start.getY();
        BufferedImage reach = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        int[] reachTop = new int[w * w];
        java.util.Arrays.fill(reachTop, -1);
        while (!queue.isEmpty()) {
            BlockPos at = BlockPos.of(queue.dequeueLong());
            int ix = at.getX() + r, iz = at.getZ() + r;
            reachTop[iz * w + ix] = Math.max(reachTop[iz * w + ix], at.getY());
            minReachY = Math.min(minReachY, at.getY());
            maxReachY = Math.max(maxReachY, at.getY());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = at.getX() + d.getStepX(), nz = at.getZ() + d.getStepZ();
                if (Math.abs(nx) > r || Math.abs(nz) > r) continue;
                for (int dy = 1; dy >= -3; dy--) {
                    p.set(nx, at.getY() + dy, nz);
                    if (dy == 1 && !level.getBlockState(at.above(2)).getCollisionShape(level, at.above(2)).isEmpty()) continue; // no headroom to step up
                    if (standable(level, p)) {
                        long key = p.asLong();
                        if (seen.add(key)) queue.enqueue(key);
                        break;
                    }
                    if (dy < 1 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) break; // ground in the way of the drop
                }
            }
        }
        long reachCols = 0;
        for (int i = 0; i < reachTop.length; i++) if (reachTop[i] >= 0) {
            reachCols++;
            int y = reachTop[i];
            int c = Math.max(40, Math.min(255, (y - 20) * 2));
            reach.setRGB(i % w, i / w, (c << 16) | (c << 8) | (c / 2));
        }
        // Structures in the square and whether their origin is reachable.
        List<String> structures = new ArrayList<>();
        for (String id : new String[]{"ferry_landing", "pilgrim_camp", "funeral_village", "procession_pit", "limbo_ossuary", "mourner_shrine"}) {
            var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Quantumdeath.id(id)));
            if (holder.isEmpty()) continue;
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), BlockPos.ZERO, 40, false);
            if (found == null) { structures.add(id + ": none within search"); continue; }
            BlockPos o = found.getFirst();
            boolean inside = Math.abs(o.getX()) <= r - 8 && Math.abs(o.getZ()) <= r - 8;
            boolean reachable = false;
            int bestY = -1;
            if (inside) {
                for (int dx = -12; dx <= 12 && !reachable; dx++) for (int dz = -12; dz <= 12 && !reachable; dz++) {
                    int ix = o.getX() + dx + r, iz = o.getZ() + dz + r;
                    if (ix < 0 || iz < 0 || ix >= w || iz >= w) continue;
                    if (reachTop[iz * w + ix] >= 0) { reachable = true; bestY = reachTop[iz * w + ix]; }
                }
            }
            structures.add(id + " at " + o.toShortString() + (inside ? (reachable ? " REACHABLE on foot (y" + bestY + ")" : " NOT reached from arrival") : " (outside survey square)"));
        }
        String tag = "seed" + seed;
        ImageIO.write(layersImg, "png", new File(dir, "limbo_layers_" + tag + ".png"));
        ImageIO.write(reach, "png", new File(dir, "limbo_reach_" + tag + ".png"));
        try (PrintWriter out = new PrintWriter(new File(dir, "limbo_survey_" + tag + ".txt"))) {
            out.printf("Limbo survey, seed %d, square %dx%d around the origin (generation %d ms)%n", seed, w, w, genMs);
            out.printf("floor layers per column: mean %.2f, columns with >=2 layers %.1f%%, histogram %s%n", floorsTotal / (double) columns, 100.0 * multi / columns,
                    java.util.Arrays.toString(layerHist));
            out.printf("columns with >=40 blocks of free height above a floor: %.1f%%%n", 100.0 * bigRoom / columns);
            out.printf("max free height per column (10-block bins): %s%n", roomHist);
            out.printf("floors by height band (y/20): %s%n", java.util.Arrays.toString(floorBands));
            out.printf("columns with exposed lava: %.2f%%%n", 100.0 * lavaCols / columns);
            out.printf("arrival %s; walkable nodes reached %d; columns reached %.1f%% of the square; reached heights y%d..y%d%n",
                    start.toShortString(), seen.size(), 100.0 * reachCols / columns, minReachY, maxReachY);
            for (String s : structures) out.println("structure " + s);
        }
        Quantumdeath.LOGGER.info("LIMBO SURVEY {} written ({} ms)", tag, System.currentTimeMillis() - t0);
    }
}
