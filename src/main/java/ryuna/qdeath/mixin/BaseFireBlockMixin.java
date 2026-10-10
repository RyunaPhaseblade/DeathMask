package ryuna.qdeath.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryuna.qdeath.hell.HellLayer;

/** Lets obsidian portals be lit inside every Hell layer, so there is always a way home. */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
    private static void quantumdeath$hellPortals(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (HellLayer.of(level) != null) cir.setReturnValue(true);
    }
}
