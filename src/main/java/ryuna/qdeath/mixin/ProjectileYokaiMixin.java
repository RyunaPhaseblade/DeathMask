package ryuna.qdeath.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryuna.qdeath.yokai.YokaiCombat;

/** A player's projectiles pass through yokai that player cannot fully see (no damage, no knockback, no stop). */
@Mixin(Projectile.class)
public abstract class ProjectileYokaiMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void quantumdeath$unseenYokai(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Projectile self = (Projectile) (Object) this;
        if (!self.level().isClientSide() && self.getOwner() != null && !YokaiCombat.projectileMayHit(self, target, self.getOwner())) cir.setReturnValue(false);
    }
}
