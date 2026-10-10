package ryuna.qdeath.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryuna.qdeath.limbo.LimboEvents;

/** A player opens a chest or barrel (the server's own count of openers): the dead may hear the lid. */
@Mixin(ContainerOpenersCounter.class)
public abstract class ContainerOpenersCounterMixin {
    @Inject(method = "incrementOpeners", at = @At("HEAD"))
    private void quantumdeath$opened(LivingEntity entity, Level level, BlockPos pos, BlockState state, double range, CallbackInfo ci) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer) LimboEvents.containerOpened(server, pos, state);
    }
}
