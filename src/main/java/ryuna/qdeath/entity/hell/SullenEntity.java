package ryuna.qdeath.entity.hell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * The sullen lie beneath the black water of the Styx, sunk in their own gloom.
 * They rise only to drag the living down with them: a long arm, a pull, a slow drowning.
 */
public class SullenEntity extends HellMonster {
    public static final int ANIM_GRAB = 1;
    private int grabCooldown = 40;

    public SullenEntity(EntityType<? extends SullenEntity> type, Level level) { super(type, level); xpReward = 8; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 36).add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 20).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        // Without prey it settles to the bottom and waits.
        if (target == null && isInWater()) setDeltaMovement(getDeltaMovement().multiply(0.8, 1, 0.8).add(0, -0.01, 0));
        if (target == null || --grabCooldown > 0) return;
        double d = distanceTo(target);
        if (d > 2.5 && d < 7 && hasLineOfSight(target)) {
            grabCooldown = 120;
            playAnim(ANIM_GRAB, 16);
            playSound(ModSounds.SULLEN_GRAB, 1.2f, 1.0f);
            Vec3 pull = position().subtract(target.position()).normalize().scale(1.1);
            target.push(pull.x, isInWater() ? -0.2 : 0.2, pull.z);
            target.syncVelocity = true;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1));
            level.sendParticles(ModParticles.TAR_DRIP, target.getX(), target.getY() + 1, target.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide() && isInWater() && random.nextInt(6) == 0)
            level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.5), getY() + getBbHeight(), getRandomZ(0.5), 0, 0.05, 0);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.SULLEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.DROWNED_HURT_WATER; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH_WATER; }
}
