package ryuna.qdeath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import ryuna.qdeath.limbo.LimboEvents;

/** Every block change of a loaded server chunk, at the moment the section changes: provenance, light index, shared routes. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockChangeMixin {
    @Shadow @Final private Level level;

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState quantumdeath$changed(LevelChunkSection section, int x, int y, int z, BlockState state, Operation<BlockState> op, @Local(argsOnly = true) BlockPos pos) {
        BlockState old = op.call(section, x, y, z, state);
        if (old != state && level instanceof ServerLevel server) LimboEvents.blockChanged(server, (LevelChunk) (Object) this, pos, old, state);
        return old;
    }
}
