package ryuna.qdeath.limbo;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Consecrated votive lanterns of a level, persisted on the level itself so the dead can ask "am I on holy ground?"
 * every tick without scanning blocks. The VIGIL sanctuary of every lantern is untouched: this only adds the
 * fuelled protection against the common dead.
 */
public final class Consecration {
    private Consecration() {}

    public static final AttachmentType<List<Long>> TYPE = AttachmentRegistry.create(Quantumdeath.id("consecrated_lanterns"),
            b -> b.persistent(Codec.LONG.listOf()).initializer(List::of));

    public static void init() { /* class load registers the attachment */ }

    public static void set(ServerLevel level, BlockPos pos, boolean consecrated) {
        List<Long> now = level.getAttachedOrCreate(TYPE);
        long key = pos.asLong();
        if (now.contains(key) == consecrated) return;
        List<Long> next = new ArrayList<>(now);
        if (consecrated) next.add(key); else next.remove(key);
        level.setAttached(TYPE, List.copyOf(next));
    }

    /** The consecrated lantern whose sanctuary contains {@code pos}, or null. */
    public static @Nullable BlockPos at(ServerLevel level, Vec3 pos) {
        List<Long> all = level.getAttachedOrCreate(TYPE);
        if (all.isEmpty()) return null;
        int r = VotiveLantern.SANCTUARY_RADIUS;
        for (long key : all) {
            BlockPos lp = BlockPos.of(key);
            double dx = pos.x - (lp.getX() + 0.5), dz = pos.z - (lp.getZ() + 0.5), dy = pos.y - lp.getY();
            if (dx * dx + dz * dz > r * r || Math.abs(dy) > 6) continue;
            if (!level.isLoaded(lp)) continue;
            if (!fuelled(level.getBlockState(lp))) { set(level, lp, false); continue; } // broken or burnt out while unloaded
            return lp;
        }
        return null;
    }

    public static boolean fuelled(BlockState state) {
        return state.is(ModBlocks.VOTIVE_LANTERN) && state.getValue(VotiveLantern.FUEL) > 0;
    }

    /** A point just outside the sanctuary of {@code lantern}, on the side of {@code from}. */
    public static Vec3 edge(BlockPos lantern, Vec3 from, double margin) {
        Vec3 c = Vec3.atBottomCenterOf(lantern);
        Vec3 d = new Vec3(from.x - c.x, 0, from.z - c.z);
        if (d.lengthSqr() < 1.0e-4) d = new Vec3(1, 0, 0);
        return c.add(d.normalize().scale(VotiveLantern.SANCTUARY_RADIUS + margin)).add(0, from.y - c.y, 0);
    }
}
