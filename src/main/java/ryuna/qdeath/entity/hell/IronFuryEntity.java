package ryuna.qdeath.entity.hell;

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
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;

/**
 * Iron-winged furies circling the towers of Dis. They wheel high above their prey, shriek,
 * and dive. Fight under a roof, or strike as they pull out of the dive.
 */
public class IronFuryEntity extends HellMonster {
    public static final int ANIM_DIVE = 1, ANIM_SHRIEK = 2;
    private int phaseTicks;
    private boolean diving;
    private float circleAngle;

    public IronFuryEntity(EntityType<? extends IronFuryEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            if (tickCount % 60 == 0) {
                Vec3 wander = position().add(random.nextGaussian() * 8, random.nextGaussian() * 3, random.nextGaussian() * 8);
                getMoveControl().setWantedPosition(wander.x, wander.y, wander.z, 0.6);
            }
            return;
        }
        phaseTicks++;
        if (!diving) {
            circleAngle += 0.06f;
            Vec3 c = target.position().add(Math.cos(circleAngle) * 8, 9, Math.sin(circleAngle) * 8);
            getMoveControl().setWantedPosition(c.x, c.y, c.z, 1.0);
            getLookControl().setLookAt(target);
            if (phaseTicks > 80 + random.nextInt(60)) {
                diving = true;
                phaseTicks = 0;
                playAnim(ANIM_SHRIEK, 15);
                playSound(ModSounds.FURY_SHRIEK, 2.0f, 1.0f);
                if (distanceTo(target) < 20) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
            }
        } else {
            playAnim(ANIM_DIVE, 3);
            Vec3 d = target.getEyePosition().subtract(position()).normalize().scale(0.9);
            setDeltaMovement(d);
            if (getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
                doHurtTarget(level, target);
                diving = false;
                phaseTicks = 0;
                setDeltaMovement(getDeltaMovement().scale(-0.5).add(0, 0.8, 0));
            } else if (phaseTicks > 50 || horizontalCollision) {
                diving = false;
                phaseTicks = 0;
            }
        }
    }

    @Override public boolean causeFallDamage(double fallDistance, float mult, DamageSource source) { return false; }
    @Override protected SoundEvent getAmbientSound() { return random.nextInt(4) == 0 ? ModSounds.FURY_SHRIEK : SoundEvents.PHANTOM_FLAP; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.PHANTOM_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public float getVoicePitch() { return 0.7f; }
}
