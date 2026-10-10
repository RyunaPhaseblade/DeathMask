package ryuna.qdeath.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

/**
 * The Limbo's volumetric terrain ({@code quantumdeath:limbo_terrain}), modelled on the Nether's topology: enormous
 * caverns under irregular vaults, terraced basins and broken mesas, canyons, natural walls with passes, pillars,
 * natural bridges and balconies at the level of the mesa tops (so the upper routes join the terraces they grow out of),
 * hanging masses near the vault and secondary tunnels through the rock.
 *
 * <p>The relief follows the biome: the same temperature/humidity noises that the biome source reads are sampled here
 * and turned into soft weights over four relief profiles, so the shape changes smoothly across biome borders. The
 * biome points below must match {@code BIOMES} in tools/worldgen.py.
 *
 * <p>Output is a signed distance in blocks / 12 (positive = rock), clamped; the noise settings wrap it in the usual
 * interpolation, blending and beardifier (structures adapt the ground around them).
 */
public record LimboTerrain(DensityFunction temperature, DensityFunction humidity, List<Holder<NormalNoise>> noises) implements DensityFunction {
    public static final MapCodec<LimboTerrain> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("temperature").forGetter(LimboTerrain::temperature),
            DensityFunction.CODEC.fieldOf("humidity").forGetter(LimboTerrain::humidity),
            NormalNoise.CODEC.listOf().fieldOf("noises").forGetter(LimboTerrain::noises)
    ).apply(i, LimboTerrain::new));

    /** Order of {@link #noises}: see NOISE_ORDER in tools/worldgen.py. */
    static final int RELIEF = 0, MESA = 1, DETAIL = 2, DUNE = 3, CANYON = 4, BOWL = 5, CEILING = 6, SHELF = 7, BRIDGE = 8,
            PILLAR = 9, RAMP = 10, MASS = 11, TUNNEL_A = 12, TUNNEL_B = 13, WARP = 14, WALL = 15, PASS = 16, VAULT = 17, MEANDER = 18, NOISE_COUNT = 19;

    /** Lava sea of the dimension; only the Deltas' channels may dip below it. */
    public static final int LAVA_LEVEL = 31;
    static final int BOTTOM = 5, TOP = 248, VAULT_LIMIT = 236;

    // ------------------------------------------------------------------ relief profiles (blended by biome weight)
    // floorBase, floorAmp, mesaHeight, mesaThreshold, terraceStep, terraceSharp, duneAmp, canyonDepth, canyonWidth, bowlDepth,
    // gapMin, gapMax, mass, pillars, shelves, bridges, tunnels, detail, floorMin, walls
    static final int P_FLOOR = 0, P_AMP = 1, P_MESA = 2, P_MESA_T = 3, P_STEP = 4, P_SHARP = 5, P_DUNE = 6, P_CANYON = 7, P_CANYON_W = 8,
            P_BOWL = 9, P_GAP_MIN = 10, P_GAP_MAX = 11, P_MASS = 12, P_PILLAR = 13, P_SHELF = 14, P_BRIDGE = 15, P_TUNNEL = 16, P_DETAIL = 17,
            P_FLOOR_MIN = 18, P_WALLS = 19, P_COUNT = 20;
    /** Ashen Wastes: vast basins and mineral dunes under the highest vaults, broken terraced mesas, many pillars. */
    static final float[] WASTES = {46, 17, 52, 0.08f, 10, 0.8f, 5, 12, 0.045f, 0, 58, 98, 0.45f, 0.75f, 0.4f, 0.45f, 0.25f, 2.0f, 34, 0.8f};
    /** Weeping Fields: rolling terraced hills cut by ravines, lower vaults hung with root masses, tunnels everywhere. */
    static final float[] FIELDS = {60, 16, 24, 0.16f, 6, 0.45f, 1.5f, 30, 0.075f, 0, 52, 84, 0.7f, 0.25f, 0.55f, 0.35f, 0.65f, 3.0f, 34, 0.6f};
    /** Cinder Deltas: deep channels (lava only at the bottom), sheer rock islands and arches. */
    static final float[] DELTAS = {36, 6, 36, 0.0f, 8, 0.95f, 0, 9, 0.11f, 0, 62, 98, 0.4f, 0.5f, 0.35f, 0.95f, 0.3f, 1.5f, 26, 0.4f};
    /** Gardens of Remembrance: amphitheatres of terraces around sunken patios, hanging-garden balconies. */
    static final float[] GARDENS = {68, 10, 16, 0.24f, 5, 0.65f, 0, 0, 0.05f, 32, 46, 72, 0.3f, 0.4f, 0.8f, 0.35f, 0.2f, 1.2f, 34, 0.3f};
    static final float[][] PROFILES = {WASTES, FIELDS, DELTAS, GARDENS};
    /** Biome points (temperature, humidity, offset) of tools/worldgen.py, same order as PROFILES. */
    static final float[][] POINTS = {{0f, 0f, 0f}, {0f, -0.5f, 0f}, {-0.5f, 0f, 0.175f}, {0.55f, 0.35f, 0.42f}};
    /** Width of biome blending in climate-fitness units (~25 blocks of transition). */
    static final float BLEND = 0.05f;

    @Override
    public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        if (noises.size() < NOISE_COUNT) throw new IllegalStateException("limbo_terrain needs " + NOISE_COUNT + " noises, got " + noises.size());
        Noise[] n = new Noise[NOISE_COUNT];
        for (int i = 0; i < NOISE_COUNT; i++) n[i] = context.createNoiseSampler(noises.get(i));
        return new Sampler(temperature.compileSampler(context), humidity.compileSampler(context), n);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction t = rule.rewrite(temperature), h = rule.rewrite(humidity);
        return t == temperature && h == humidity ? this : new LimboTerrain(t, h, noises);
    }

    @Override public Interval range() { return Interval.of(-1.5f, 1.5f); }
    @Override public @DensityFunction.Axes int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<LimboTerrain> codec() { return CODEC; }

    // ------------------------------------------------------------------ math helpers
    static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** Smooth maximum: joins two shapes with a fillet of about {@code k} blocks instead of a crease. */
    static float smax(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0f) / k;
        return Math.max(a, b) + h * h * k * 0.25f;
    }

    /** Relief parameters and the 2D shape of one column, shared by every height of it. */
    static final class Column {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        final float[] p = new float[P_COUNT];
        /** Floor and vault heights. */
        float floor, vault;
        /** Balcony (shelf) top and strength; natural bridge deck, thickness. */
        float shelfTop, shelf, bridgeTop, bridgeThick;
        /** Pillar field and its threshold. */
        float pillar, pillarThreshold;
    }

    record Sampler(DensitySampler temperature, DensitySampler humidity, Noise[] n) implements DensitySampler {
        private static final ThreadLocal<Column> LAST = ThreadLocal.withInitial(Column::new);

        @Override
        public float sampleValue(SamplerContext ctx, int x, int y, int z) {
            Column c = LAST.get();
            if (c.x != x || c.z != z) column(ctx, x, z, c);
            return density(c, x, y, z);
        }

        @Override
        public void sampleVolume(SamplerContext ctx, DensityBuffer out, DensityVolume v) {
            Column c = new Column();
            int index = 0;
            for (int iz = 0; iz < v.sizeZ(); iz++) {
                int z = v.blockZ(iz);
                for (int ix = 0; ix < v.sizeX(); ix++) {
                    int x = v.blockX(ix);
                    column(ctx, x, z, c);
                    for (int iy = 0; iy < v.sizeY(); iy++) out.set(index++, density(c, x, v.blockY(iy), z));
                }
            }
        }

        private float n2(int i, int x, int z) { return n[i].get(x, 0, z); }

        /** Floor, vault, balconies, bridges and pillars of column (x, z). */
        void column(SamplerContext ctx, int x, int z, Column c) {
            c.x = x;
            c.z = z;
            float t = temperature.sampleValue(ctx, x, 0, z), h = humidity.sampleValue(ctx, x, 0, z);
            float[] fit = new float[PROFILES.length];
            float best = Float.MAX_VALUE;
            for (int i = 0; i < PROFILES.length; i++) {
                float dt = t - POINTS[i][0], dh = h - POINTS[i][1];
                fit[i] = dt * dt + dh * dh + POINTS[i][2] * POINTS[i][2];
                best = Math.min(best, fit[i]);
            }
            float total = 0;
            for (int i = 0; i < PROFILES.length; i++) total += fit[i] = (float) Math.exp(-(fit[i] - best) / BLEND);
            float[] p = c.p;
            java.util.Arrays.fill(p, 0f);
            for (int i = 0; i < PROFILES.length; i++) {
                float w = fit[i] / total;
                if (w < 1e-4f) continue;
                for (int k = 0; k < P_COUNT; k++) p[k] += PROFILES[i][k] * w;
            }

            float relief = n2(RELIEF, x, z);
            float base = p[P_FLOOR] + p[P_AMP] * relief * 2.2f;
            // Mesas of varied stature: low benches and tall broken tables.
            float mesa = smoothstep(p[P_MESA_T], p[P_MESA_T] + 0.14f, n2(MESA, x, z)) * (0.55f + 0.9f * smoothstep(-0.35f, 0.35f, n[DUNE].get(z * 0.25, 0, x * 0.25)));
            float dune = 1 - Math.abs(n2(DUNE, x, z)) * 2.2f;
            float bowl = smoothstep(0.12f, 0.5f, n2(BOWL, x, z));
            float raw = base + p[P_MESA] * mesa + p[P_DUNE] * Math.max(0, dune) * Math.max(0, dune) - p[P_BOWL] * bowl + p[P_DETAIL] * n2(DETAIL, x, z) * 2.5f;
            // Terraces: flat steps whose risers are cliffs where the ramp field is high and walkable slopes where it is low,
            // so every terrace edge has ramps somewhere along it.
            float step = p[P_STEP];
            float sharp = p[P_SHARP] * smoothstep(-0.25f, 0.3f, n2(RAMP, x, z));
            float tt = raw / step, fl = Mth.floor(tt), fr = tt - fl;
            float riser = 1 - 0.85f * sharp;
            float floor = (fl + smoothstep(1 - riser, 1, fr)) * step;
            // Canyons cut through the terraces with steep walls and flat beds.
            float can = Math.abs(n2(CANYON, x, z));
            if (can < p[P_CANYON_W]) floor -= p[P_CANYON] * smoothstep(0f, 0.55f, 1 - can / p[P_CANYON_W]);
            c.floor = Math.max(floor, p[P_FLOOR_MIN]);
            // The vault follows the broad relief, not the terraces: mesas rise toward it, chambers keep their height.
            float gap = p[P_GAP_MIN] + (p[P_GAP_MAX] - p[P_GAP_MIN]) * (0.5f + 0.75f * n2(CEILING, x, z));
            // Domes and low hanging lobes break the vault into irregular shapes.
            float vd = n2(VAULT, x, z);
            c.vault = Math.min(VAULT_LIMIT, base + p[P_MESA] * 0.35f + gap + 22 * vd - 10 * Math.max(0, -vd - 0.25f) * 4);
            // Natural walls between the great chambers, broken by passes.
            // The walls meander (domain-warped) instead of running straight.
            float mx = 90 * n2(MEANDER, x, z), mz = 90 * n2(MEANDER, z + 7919, x - 3571);
            float ww = 0.035f * p[P_WALLS], q = ww > 0 ? Math.abs(n[WALL].get(x + mx, 0, z + mz)) / ww : 9f;
            if (q < 1) {
                float top = c.vault + 6, profile = top - (top - c.floor + 4) * q * q;
                float solid = smoothstep(-0.2f, 0.05f, n2(PASS, x, z));
                c.floor = Math.max(c.floor, c.floor + (profile - c.floor) * solid);
            }
            // Balconies at the height of the nearby mesa tops: they grow out of the terraces and stay connected to them.
            float shelfLevel = Math.round((base + p[P_MESA]) / step) * step;
            float st = 0.6f - 0.4f * p[P_SHELF];
            c.shelf = shelfLevel > c.floor + 7 && shelfLevel < c.vault - 10 ? smoothstep(st, st + 0.12f, n2(SHELF, x, z)) : 0f;
            c.shelfTop = shelfLevel;
            // Natural bridges along thin ridges of noise, at a terrace level between basin and mesa.
            float br = Math.abs(n2(BRIDGE, x, z)), bw = 0.012f + 0.03f * p[P_BRIDGE];
            float bridgeLevel = Math.round((base + p[P_MESA] * 0.7f) / step) * step;
            c.bridgeThick = br < bw && bridgeLevel > c.floor + 5 && bridgeLevel < c.vault - 8 ? 2.5f + 4.5f * (1 - br / bw) : 0f;
            c.bridgeTop = bridgeLevel;
            c.pillar = n2(PILLAR, x, z);
            c.pillarThreshold = p[P_PILLAR] <= 0.01f ? 9f : 0.95f - 0.33f * p[P_PILLAR];
        }

        float density(Column c, int x, int y, int z) {
            if (y <= BOTTOM) return 1.5f;
            if (y >= TOP) return 1.5f;
            float[] p = c.p;
            float s = Math.max(c.floor - y, y - c.vault);
            float warp = n[WARP].get(x, y * 1.2, z);
            if (c.shelf > 0) {
                float under = c.shelfTop - 4 - 3 * Math.abs(warp);
                s = smax(s, Math.min(c.shelfTop - y, y - under) - (1 - c.shelf) * 6, 3);
            }
            if (c.bridgeThick > 0) s = smax(s, Math.min(c.bridgeTop - y, y - (c.bridgeTop - c.bridgeThick)), 2.5f);
            float rel = Mth.clamp((y - c.floor) / Math.max(8, c.vault - c.floor), 0, 1);
            if (c.pillar > c.pillarThreshold - 0.08f) {
                // Pillars narrow toward the middle of the chamber, like the necks of hourglasses.
                float th = c.pillarThreshold + 0.05f * (1 - 4 * (rel - 0.5f) * (rel - 0.5f));
                s = smax(s, (c.pillar - th) * 60, 3);
            }
            if (p[P_MASS] > 0.02f) {
                float m = n[MASS].get(x, y * 1.35, z);
                // Masses hang from the vault and stay off the floors people walk on.
                float bias = 7 * smoothstep(0.55f, 1f, rel) - 10 * (1 - smoothstep(0.12f, 0.4f, rel));
                s = smax(s, (m - (0.62f - 0.3f * p[P_MASS])) * 34 + bias, 4);
            }
            s += 1.6f * warp;
            if (p[P_TUNNEL] > 0.02f && y > LAVA_LEVEL + 6 && s > -3) {
                float a = n[TUNNEL_A].get(x, y * 1.3, z), b = n[TUNNEL_B].get(x, y * 1.3, z);
                float r = (float) Math.sqrt(a * a + b * b), tw = 0.06f * p[P_TUNNEL];
                if (r < tw) s = Math.min(s, (r - tw) * 110 - 0.5f);
            }
            if (y < BOTTOM + 4) s = Math.max(s, (BOTTOM + 4 - y) * 1.5f);
            return Mth.clamp(s / 12f, -1.5f, 1.5f);
        }
    }
}
