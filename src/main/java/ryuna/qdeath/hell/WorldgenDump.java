package ryuna.qdeath.hell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import ryuna.qdeath.Quantumdeath;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Development tool (run with -Dquantumdeath.worldgenDump=true, see the worldgenDump Gradle run):
 * generates every Hell layer around the origin, writes cross-sections, floor maps and slices as PNG,
 * places every structure for inspection, logs biomes and arena locations, then stops the server.
 */
public final class WorldgenDump {
    private WorldgenDump() {}

    static final int R = Integer.getInteger("quantumdeath.dumpRadius", 128);

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                File dir = new File("worldgen-dump");
                dir.mkdirs();
                String only = System.getProperty("quantumdeath.dumpLayers", "");
                for (HellLayer layer : HellLayer.values()) {
                    if (!only.isEmpty() && !only.contains(layer.id)) continue;
                    dump(server, layer, dir);
                }
                if (Boolean.getBoolean("quantumdeath.limboSurvey"))
                    LimboSurvey.run(server.getLevel(HellLayer.VESTIBULE.level), Integer.getInteger("quantumdeath.surveyRadius", 400), dir);
                if (Boolean.getBoolean("quantumdeath.dumpNether")) reference(server, dir);
                if (Boolean.parseBoolean(System.getProperty("quantumdeath.dumpStructures", "true"))) structures(server, dir);
            } catch (Throwable t) {
                Quantumdeath.LOGGER.error("Worldgen dump failed", t);
            }
            server.halt(false);
        });
    }

    private static void dump(MinecraftServer server, HellLayer layer, File dir) throws Exception {
        ServerLevel level = server.getLevel(layer.level);
        if (level == null) { Quantumdeath.LOGGER.error("DUMP: level {} missing", layer.id); return; }
        long t0 = System.currentTimeMillis();
        for (int cx = -R >> 4; cx <= R >> 4; cx++) for (int cz = -R >> 4; cz <= R >> 4; cz++) level.getChunk(cx, cz);
        Quantumdeath.LOGGER.info("DUMP {}: generated in {} ms", layer.id, System.currentTimeMillis() - t0);
        int minY = level.getMinY(), maxY = level.getMaxY();
        int w = 2 * R + 1, h = maxY - minY;
        BufferedImage sx = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), sz = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage floor = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> biomes = new TreeMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = -R; i <= R; i++) {
            for (int y = minY; y < maxY; y++) {
                sx.setRGB(i + R, maxY - 1 - y, color(level, p.set(i, y, 0)));
                sz.setRGB(i + R, maxY - 1 - y, color(level, p.set(0, y, i)));
            }
        }
        int logical = minY + level.dimensionType().logicalHeight();
        Map<Integer, Integer> lavaY = new TreeMap<>();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            int rgb = 0;
            for (int y = Math.min(logical, maxY) - 2; y > minY; y--) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (!s.isAir() && level.getBlockState(p.set(x, y + 1, z)).isAir()) {
                    if (!s.getFluidState().isEmpty()) lavaY.merge(y, 1, Integer::sum);
                    int c = color(level, p.set(x, y, z));
                    float shade = 0.45f + 0.55f * (y - minY) / (float) (logical - minY);
                    rgb = scale(c, shade);
                    break;
                }
            }
            floor.setRGB(x + R, z + R, rgb);
            if ((x & 7) == 0 && (z & 7) == 0) {
                String b = level.getBiome(p.set(x, level.getSeaLevel() + 4, z)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                biomes.merge(b, 1, Integer::sum);
            }
        }
        if (layer == HellLayer.VESTIBULE) {
            for (int off : new int[]{-96, 96}) {
                BufferedImage extra = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                for (int i = -R; i <= R; i++) for (int y = minY; y < maxY; y++) extra.setRGB(i + R, maxY - 1 - y, color(level, p.set(i, y, off)));
                ImageIO.write(extra, "png", new File(dir, layer.id + "_section_x" + (off < 0 ? "m" : "p") + Math.abs(off) + "_seed" + level.getSeed() + ".png"));
            }
        }
        ImageIO.write(sx, "png", new File(dir, layer.id + "_section_x.png"));
        ImageIO.write(sz, "png", new File(dir, layer.id + "_section_z.png"));
        ImageIO.write(floor, "png", new File(dir, layer.id + "_floor.png"));
        Quantumdeath.LOGGER.info("DUMP {}: biomes {}", layer.id, biomes);
        Quantumdeath.LOGGER.info("DUMP {}: exposed fluid surface by y {}", layer.id, lavaY);
        BlockPos arena = level.findNearestMapStructure(layer.arenaTag, BlockPos.ZERO, 100, false);
        Quantumdeath.LOGGER.info("DUMP {}: nearest arena {}", layer.id, arena);
    }

    /** Vanilla Nether slice for comparison. */
    private static void reference(MinecraftServer server, File dir) throws Exception {
        ServerLevel level = server.getLevel(net.minecraft.world.level.Level.NETHER);
        for (int cx = -R >> 4; cx <= R >> 4; cx++) level.getChunk(cx, 0);
        BufferedImage img = new BufferedImage(2 * R + 1, 256, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = -R; i <= R; i++) for (int y = 0; y < 256; y++) img.setRGB(i + R, 255 - y, color(level, p.set(i, y, 0)));
        ImageIO.write(img, "png", new File(dir, "nether_reference.png"));
    }

    /** Places each structure kind at a fixed spot of its layer and renders slices through its origin. */
    private static void structures(MinecraftServer server, File dir) throws Exception {
        String[][] tests = {
                {"vestibule", "ferry_landing", "2000"}, {"vestibule", "pilgrim_camp", "2400"}, {"vestibule", "mourner_shrine", "2300"},
                {"gorge", "feasting_pit", "2000"}, {"gorge", "butchers_hall", "2200"},
                {"mire", "drowned_choir", "2000"}, {"mire", "sunken_chapel", "2200"},
                {"dis", "ossuary", "2000"}, {"dis", "dis_watchtower", "2300"},
                {"zenith", "last_vigil", "2000"}, {"zenith", "false_house", "2200"}};
        for (String[] t : tests) {
            HellLayer layer = HellLayer.valueOf(t[0].toUpperCase());
            ServerLevel level = server.getLevel(layer.level);
            int x0 = Integer.parseInt(t[2]);
            var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE, Quantumdeath.id(t[1])));
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            BlockPos origin = null;
            for (int attempt = 0; attempt < 12 && origin == null; attempt++) {
                int cx = (x0 >> 4) + attempt * 3;
                for (int dx = -6; dx <= 6; dx++) for (int dz = -9; dz <= 6; dz++) level.getChunk(cx + dx, dz);
                var start = holder.value().generate(holder, level.dimension(), level.registryAccess(), gen, gen.getBiomeSource(),
                        rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), rs,
                        level.getStructureTemplateManager(), level.getSeed(), new net.minecraft.world.level.ChunkPos(cx, 0), 0, level, b -> true);
                if (!start.isValid()) continue;
                var bb = start.getBoundingBox();
                for (int cxx = bb.minX() >> 4; cxx <= bb.maxX() >> 4; cxx++) for (int czz = bb.minZ() >> 4; czz <= bb.maxZ() >> 4; czz++) {
                    var c = new net.minecraft.world.level.ChunkPos(cxx, czz);
                    level.getChunk(cxx, czz);
                    start.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                            new net.minecraft.world.level.levelgen.structure.BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()), c);
                }
                origin = ((ryuna.qdeath.world.HellPiece) start.getPieces().getFirst()).origin();
            }
            if (origin == null) { Quantumdeath.LOGGER.warn("DUMP structure {} could not be placed", t[1]); continue; }
            int half = t[1].equals("ossuary") ? 120 : 64;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dy : new int[]{0, 1}) {
                BufferedImage slice = new BufferedImage(2 * half + 1, 2 * half + 1, BufferedImage.TYPE_INT_RGB);
                int y = origin.getY() + dy + (t[1].equals("ossuary") ? -13 : 0);
                for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++)
                    slice.setRGB(dx + half, dz + half, color(level, p.set(origin.getX() + dx, y, origin.getZ() + dz)));
                ImageIO.write(slice, "png", new File(dir, "struct_" + t[1] + "_y" + dy + ".png"));
            }
            int minY = level.getMinY(), maxY = level.getMaxY();
            BufferedImage sec = new BufferedImage(2 * half + 1, maxY - minY, BufferedImage.TYPE_INT_RGB);
            for (int dz = -half; dz <= half; dz++) for (int y = minY; y < maxY; y++)
                sec.setRGB(dz + half, maxY - 1 - y, color(level, p.set(origin.getX(), y, origin.getZ() + dz)));
            ImageIO.write(sec, "png", new File(dir, "struct_" + t[1] + "_section.png"));
            Quantumdeath.LOGGER.info("DUMP structure {} at {}", t[1], origin);
        }
    }

    private static int color(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return 0x000000;
        if (!s.getFluidState().isEmpty()) return s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? 0xff6a00 : 0x2040ff;
        int c = s.getMapColor(level, p).col;
        return c == 0 ? 0xff00ff : c;
    }

    private static int scale(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
