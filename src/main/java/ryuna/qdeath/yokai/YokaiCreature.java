package ryuna.qdeath.yokai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * A yokai: one of the four original creatures or one of the new ones. All share a {@link YokaiCore} with their
 * per-player encounters, bond, rituals and orders. Implemented by Mobs only.
 */
public interface YokaiCreature {
    YokaiCore yokai();

    default Species species() { return yokai().species; }

    default Mob self() { return (Mob) this; }

    /** Why this companion cannot be stored right now (a lang key suffix), or null. */
    default @Nullable String storeBlocker() { return null; }

    /** Whether a summoned companion of this kind may appear at {@code pos}: by default on solid ground. */
    default boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
