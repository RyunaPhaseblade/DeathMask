package ryuna.qdeath.client.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ryuna.qdeath.client.render.PossessedRenderState;

/** Carries the possessed flag of PossessedHostRendererMixin from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements PossessedRenderState {
    @Unique private boolean quantumdeath$possessed;

    @Override public boolean quantumdeath$possessed() { return quantumdeath$possessed; }
    @Override public void quantumdeath$setPossessed(boolean possessed) { quantumdeath$possessed = possessed; }
}
