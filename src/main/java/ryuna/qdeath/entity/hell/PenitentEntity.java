package ryuna.qdeath.entity.hell;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;

/**
 * Frozen into the lake of the last circle, kneeling. A Penitent moves only while no one is looking at it.
 * Turn your back and the ice cracks.
 */
public class PenitentEntity extends HellMonster {
    private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(PenitentEntity.class, EntityDataSerializers.BOOLEAN);
    private int unwatched;

    public PenitentEntity(EntityType<? extends PenitentEntity> type, Level level) { super(type, level); xpReward = 15; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_WATCHED, true); }
    public boolean isWatched() { return entityData.get(DATA_WATCHED); }

    @Override protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        boolean watched = false;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48), p -> !p.isSpectator())) {
            Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
            if (p.getViewVector(1f).dot(to) > 0.55 && p.hasLineOfSight(this)) { watched = true; break; }
        }
        entityData.set(DATA_WATCHED, watched);
        LivingEntity target = getTarget();
        if (watched || target == null) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            unwatched = 0;
            return;
        }
        if (unwatched++ == 0) playSound(ModSounds.PENITENT_CRACK, 1.5f, 0.8f + random.nextFloat() * 0.4f);
        getNavigation().moveTo(target, 1.2);
        if (distanceTo(target) < 2.2 && target.hurtTime == 0) {
            doHurtTarget(level, target);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 200));
            playAnim(1, 10);
        }
    }

    // While watched it is an icon of ice: it does not turn its head, and arrows chip off it.
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isWatched() && source.getDirectEntity() != source.getEntity()) damage *= 0.5f;
        return super.hurtServer(level, source, damage);
    }
    @Override public boolean isPushable() { return false; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.GLASS_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GLASS_BREAK; }
    @Override public boolean canFreeze() { return false; }
}
