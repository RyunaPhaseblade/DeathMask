package ryuna.qdeath.entity.hell;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/** The wrathful of the Styx. Every wound makes it faster and stronger; at full rage it roars and the others come. */
public class WrathbornEntity extends HellMonster {
    private static final EntityDataAccessor<Integer> DATA_RAGE = SynchedEntityData.defineId(WrathbornEntity.class, EntityDataSerializers.INT);
    public static final int MAX_RAGE = 5;

    public WrathbornEntity(EntityType<? extends WrathbornEntity> type, Level level) { super(type, level); xpReward = 12; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.KNOCKBACK_RESISTANCE, 0.4).add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_RAGE, 0); }
    public int rage() { return entityData.get(DATA_RAGE); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && isAlive() && rage() < MAX_RAGE) {
            int rage = rage() + 1;
            entityData.set(DATA_RAGE, rage);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
            if (speed != null) { speed.removeModifier(Quantumdeath.id("rage_speed")); speed.addTransientModifier(new AttributeModifier(Quantumdeath.id("rage_speed"), 0.08 * rage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)); }
            if (dmg != null) { dmg.removeModifier(Quantumdeath.id("rage_damage")); dmg.addTransientModifier(new AttributeModifier(Quantumdeath.id("rage_damage"), 1.5 * rage, AttributeModifier.Operation.ADD_VALUE)); }
            if (rage == MAX_RAGE) {
                playAnim(2, 30);
                playSound(ModSounds.WRATHBORN_ROAR, 2.0f, 0.9f);
                if (getTarget() != null) for (WrathbornEntity other : level.getEntitiesOfClass(WrathbornEntity.class, getBoundingBox().inflate(24), e -> e != this && e.getTarget() == null))
                    other.setTarget(getTarget());
            }
        }
        return hurt;
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide() && rage() >= 3 && random.nextInt(MAX_RAGE + 1 - rage() + 1) == 0)
            level().addParticle(ModParticles.BLOOD_EMBER, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.05, 0);
    }

    @Override protected void onHitTarget(ServerLevel level, net.minecraft.world.entity.LivingEntity target) { playAnim(1, 10); }
    @Override protected SoundEvent getAmbientSound() { return random.nextInt(3) == 0 ? ModSounds.WRATHBORN_ROAR : null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.WRATHBORN_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public float getVoicePitch() { return 0.8f + random.nextFloat() * 0.2f; }
}
