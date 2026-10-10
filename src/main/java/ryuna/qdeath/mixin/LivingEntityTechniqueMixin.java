package ryuna.qdeath.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ryuna.qdeath.jjk.JjkCombat;

/** Lets the cursed techniques scale incoming damage (freeze vulnerability, Momentum Attack, Frame shatter, Boogie strike). */
@Mixin(LivingEntity.class)
abstract class LivingEntityTechniqueMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float quantumdeath$techniqueDamage(float damage, @Local(argsOnly = true) DamageSource source) {
        return JjkCombat.modifyIncoming((LivingEntity) (Object) this, source, damage);
    }
}
