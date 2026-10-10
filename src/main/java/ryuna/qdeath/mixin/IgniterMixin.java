package ryuna.qdeath.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryuna.qdeath.yokai.Awakening;

/** Remembers who is lighting a fire, so a portal formed by it has an identifiable cause. */
@Mixin({FlintAndSteelItem.class, FireChargeItem.class})
public abstract class IgniterMixin {
    @Inject(method = "useOn", at = @At("HEAD"))
    private void quantumdeath$igniteStart(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Awakening.igniting(ctx.getPlayer() instanceof ServerPlayer p ? p : null);
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void quantumdeath$igniteEnd(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Awakening.igniting(null);
    }
}
