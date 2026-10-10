package ryuna.qdeath.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryuna.qdeath.limbo.Possession;

/**
 * A host possessed by a Mourning Parasite: its goals, targets and brain are suspended (not cleared) and the bond drives
 * it instead; it picks nothing up while possessed. Everything resumes untouched when the bond ends.
 */
@Mixin(Mob.class)
public abstract class MobPossessionMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void quantumdeath$possessed(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (Possession.isPossessed(self)) {
            Possession.drive(self);
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;canPickUpLoot()Z"))
    private boolean quantumdeath$noLootWhilePossessed(boolean original) {
        return original && !Possession.isPossessed((Mob) (Object) this);
    }
}
