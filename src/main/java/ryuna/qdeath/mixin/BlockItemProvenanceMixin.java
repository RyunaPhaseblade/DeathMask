package ryuna.qdeath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ryuna.qdeath.limbo.LimboEvents;

/** A block placed by a player (after its other parts, like a door's upper half, are in place): provenance and noise. */
@Mixin(BlockItem.class)
public abstract class BlockItemProvenanceMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void quantumdeath$placed(Block block, Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack, Operation<Void> op) {
        op.call(block, level, pos, state, by, stack);
        if (level instanceof ServerLevel server && by instanceof ServerPlayer player) LimboEvents.placed(server, player, pos);
    }
}
