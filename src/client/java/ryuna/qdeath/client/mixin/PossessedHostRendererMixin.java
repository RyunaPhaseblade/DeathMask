package ryuna.qdeath.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryuna.qdeath.client.render.PossessedRenderState;
import ryuna.qdeath.limbo.Possession;

/**
 * A host held by a Mourning Parasite keeps its own model (the species stays recognizable) but turns a sickly grey-green
 * and its head jerks in spasms the host would never make; the parasite itself sits on its head, a creature of its own.
 * The bond is the host's synced attachment, so every client sees the same.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PossessedHostRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void quantumdeath$possessedLook(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        boolean possessed = Possession.isPossessed(entity);
        ((PossessedRenderState) state).quantumdeath$setPossessed(possessed);
        if (!possessed) return;
        float t = state.ageInTicks + (entity.getId() % 37);
        // Spasms: the head snaps to the side and back, now and then, and trembles all the time.
        float jerk = Mth.sin(t * 0.21f) > 0.8f ? Mth.sin(t * 2.7f) * 35f : 0f;
        state.yRot += jerk + Mth.sin(t * 1.9f) * 3f;
        state.xRot += Mth.cos(t * 2.3f) * 4f + (Mth.sin(t * 0.13f) > 0.9f ? 25f : 0f);
    }

    @ModifyReturnValue(method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I", at = @At("RETURN"))
    private int quantumdeath$sicklyTint(int tint, LivingEntityRenderState state) {
        return ((PossessedRenderState) state).quantumdeath$possessed() ? ARGB.multiply(tint, 0xFFA2B096) : tint;
    }
}
