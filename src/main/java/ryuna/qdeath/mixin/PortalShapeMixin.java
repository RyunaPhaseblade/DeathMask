package ryuna.qdeath.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryuna.qdeath.yokai.Awakening;

/** The first obsidian portal that actually forms in the Overworld wakes the yokai (lighting fire alone never does). */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
    @Shadow @Final private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks", at = @At("TAIL"))
    private void quantumdeath$portalFormed(LevelAccessor level, CallbackInfo ci) {
        Awakening.portalFormed(level, bottomLeft);
    }
}
