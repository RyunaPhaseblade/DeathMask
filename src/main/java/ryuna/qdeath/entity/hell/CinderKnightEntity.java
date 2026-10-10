package ryuna.qdeath.entity.hell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;

/** A knight of the first circle who never stopped guarding a gate that no longer exists. Lunges across gaps. */
public class CinderKnightEntity extends HellMonster {
    public static final int ANIM_SWING = 1, ANIM_LUNGE = 2;
    private int lungeCooldown = 60;
    private int lungeTicks;

    public CinderKnightEntity(EntityType<? extends CinderKnightEntity> type, Level level) { super(type, level); xpReward = 25; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 80).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (lungeTicks > 0) {
            lungeTicks--;
            if (lungeTicks == 10 && target != null) {
                Vec3 d = target.position().subtract(position()).normalize();
                setDeltaMovement(d.x * 1.6, 0.35, d.z * 1.6);
                syncVelocity = true;
                playSound(ModSounds.KNIGHT_SWING, 1.2f, 0.8f);
            }
            if (lungeTicks < 10) {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 3, 0.3, 0.5, 0.3, 0.01);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), e -> e instanceof Player)) {
                    if (e.hurtTime == 0) { doHurtTarget(level, e); e.igniteForSeconds(3); }
                }
            }
            return;
        }
        if (target != null && --lungeCooldown <= 0) {
            double d = distanceTo(target);
            if (d > 5 && d < 13 && hasLineOfSight(target)) {
                lungeTicks = 22;
                lungeCooldown = 100 + random.nextInt(60);
                getNavigation().stop();
                playAnim(ANIM_LUNGE, 22);
                playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.6f);
            }
        }
    }

    @Override protected void onHitTarget(ServerLevel level, LivingEntity target) {
        playAnim(ANIM_SWING, 10);
        target.igniteForSeconds(3);
        // A wide cut: anyone beside the target is caught too.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(target.blockPosition()).inflate(1.5), e -> e != target && e instanceof Player))
            e.hurtServer(level, damageSources().mobAttack(this), 4.0f);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.KNIGHT_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.NETHERITE_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public boolean fireImmune() { return true; }
}
