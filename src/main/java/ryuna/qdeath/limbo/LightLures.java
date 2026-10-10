package ryuna.qdeath.limbo;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.limbo.LimboDead;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Artificial light as a clue: a lit torch, candle, lantern or campfire in plain sight draws the dead to look (block tag
 * {@code quantumdeath:lure_lights}; votive lanterns, lava, the sky and the Limbo's own glow are not in it). Light never
 * reveals anyone and is never put out. The lights of each chunk are indexed lazily (only sections whose palette holds
 * such a block are read) and the index is dropped when one of them changes or the chunk unloads, so a dead looking for
 * light reads a handful of positions and casts a ray to the nearest, within a shared per-tick budget.
 */
public final class LightLures {
    private LightLures() {}

    public static final TagKey<Block> LURES = TagKey.create(Registries.BLOCK, Quantumdeath.id("lure_lights"));

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<long[]>> INDEX = new HashMap<>();
    private static long budgetTick = -1;
    private static int raysLeft;

    public static boolean lure(BlockState s) {
        return s.is(LURES) && s.getLightEmission() > 0 && (!s.hasProperty(BlockStateProperties.LIT) || s.getValue(BlockStateProperties.LIT));
    }

    private static Long2ObjectOpenHashMap<long[]> index(ServerLevel level) {
        return INDEX.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
    }

    /** Lure positions of a loaded chunk (cached). */
    private static long[] lights(ServerLevel level, LevelChunk chunk) {
        return index(level).computeIfAbsent(chunk.getPos().pack(), k -> {
            LongArrayList out = new LongArrayList();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                if (s.hasOnlyAir() || !s.maybeHas(st -> st.is(LURES))) continue;
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    if (s.getBlockState(x, y, z).is(LURES)) out.add(BlockPos.asLong(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
            }
            return out.toLongArray();
        });
    }

    /** A block changed in a loaded chunk: a lure appearing or going away drops that chunk's index. */
    public static void changed(ServerLevel level, LevelChunk chunk, BlockState old, BlockState now) {
        if (old.is(LURES) || now.is(LURES)) {
            Long2ObjectOpenHashMap<long[]> idx = INDEX.get(level.dimension());
            if (idx != null) idx.remove(chunk.getPos().pack());
        }
    }

    public static void unloaded(ServerLevel level, ChunkPos pos) {
        Long2ObjectOpenHashMap<long[]> idx = INDEX.get(level.dimension());
        if (idx != null) idx.remove(pos.pack());
    }

    public static void clear() { INDEX.clear(); }

    /**
     * The nearest lit lure within {@code LIGHT_RANGE} that {@code dead} can see from its eyes, skipping {@code ignored}
     * ones; null when there is none or the shared ray budget of this tick is spent (it will look again later).
     */
    public static @Nullable BlockPos visible(ServerLevel level, LimboDead dead, LongPredicate ignored) {
        double r = LimboTuning.LIGHT_RANGE;
        Vec3 eye = dead.getEyePosition();
        int cx0 = ((int) Math.floor(eye.x - r)) >> 4, cx1 = ((int) Math.floor(eye.x + r)) >> 4;
        int cz0 = ((int) Math.floor(eye.z - r)) >> 4, cz1 = ((int) Math.floor(eye.z + r)) >> 4;
        BlockPos best = null;
        double bestD = r * r;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (long l : lights(level, chunk)) {
                if (ignored.test(l)) continue;
                double dx = BlockPos.getX(l) + 0.5 - eye.x, dy = BlockPos.getY(l) + 0.5 - eye.y, dz = BlockPos.getZ(l) + 0.5 - eye.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d >= bestD) continue;
                BlockPos p = BlockPos.of(l);
                if (!lure(level.getBlockState(p))) continue;
                if (!ray(level)) return best;
                BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(p), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dead));
                if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p)) { best = p; bestD = d; }
            }
        }
        return best;
    }

    private static boolean ray(ServerLevel level) {
        long t = level.getGameTime();
        if (t != budgetTick) { budgetTick = t; raysLeft = LimboTuning.BUDGET_LIGHT_RAYS; }
        return raysLeft-- > 0;
    }
}
