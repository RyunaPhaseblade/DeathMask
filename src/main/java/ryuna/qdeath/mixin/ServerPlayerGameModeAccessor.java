package ryuna.qdeath.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mining in progress, read by the dead's hearing (ryuna.qdeath.limbo.Stimuli). */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
    @Accessor("isDestroyingBlock") boolean quantumdeath$isDestroyingBlock();
    @Accessor("destroyPos") BlockPos quantumdeath$destroyPos();
}
