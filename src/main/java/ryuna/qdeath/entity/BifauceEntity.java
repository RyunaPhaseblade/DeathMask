package ryuna.qdeath.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiCombat;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;
import ryuna.qdeath.yokai.YokaiHooks;

import java.util.EnumSet;

/**
 * Doublemouth. In Hell: its original pursuit and bite. In the Overworld, along dark forests and roads: one mouth
 * mimics everyday sounds and the other breathes backwards. Unseen it only deceives and lures aside; partial sight
 * risks a pursuit once per encounter; with complete sight it charges after opening both mouths for 1.5 s (5 damage,
 * 4 s cooldown), straight and committed, so a wall or a side step makes it miss.
 */
public class BifauceEntity extends Monster implements ryuna.qdeath.entity.hell.HellMob, YokaiCreature {
    public static final int ANIM_OPEN = 1, ANIM_CHARGE = 2;
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(BifauceEntity.class, EntityDataSerializers.INT);
    private final YokaiCore core = new YokaiCore(Species.BIFAUCE, this);
    private int windup, charging, cooldown, mimicCooldown = 100;
    private Vec3 chargeDir = Vec3.ZERO;

    public BifauceEntity(EntityType<? extends BifauceEntity> type, Level level) { super(type, level); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.monster.Enderman.createAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Bifauce.HEALTH);
    }
    @Override public YokaiCore yokai() { return core; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ANIM, 0);
    }
    /** Client: 1 while both mouths open (windup), 2 while charging. */
    public int pose() { return entityData.get(DATA_ANIM); }

    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.BIFAUCE, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BackwardsPursuitGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) { @Override public boolean canUse() { return Perception.exempt(BifauceEntity.this) && super.canUse(); } });
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 32.0f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true) { @Override public boolean canUse() { return Perception.exempt(BifauceEntity.this) && super.canUse(); } });
        targetSelector.addGoal(2, new YokaiGoals.Hostile(this, YokaiBalance.Perception.DETECT_RANGE));
    }
    private class BackwardsPursuitGoal extends Goal {
        BackwardsPursuitGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        // Leave movement control to MeleeAttackGoal when close enough to strike.
        @Override public boolean canUse() { return Perception.exempt(BifauceEntity.this) && getTarget() != null && getTarget().isAlive() && distanceToSqr(getTarget()) > 6.25; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity target = getTarget(); if (target == null) return;
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            float yaw = (float)(Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0f;
            setYRot(yaw); yBodyRot = getYRot(); yHeadRot = getYRot();
            getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (Perception.exempt(this) || !YokaiHooks.serverTick(this, core)) return;
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        if (windup > 0) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 30f, 30f);
            if (--windup == 0) {
                Vec3 to = target != null ? target.position().subtract(position()) : getLookAngle();
                chargeDir = to.multiply(1, 0, 1).normalize();
                charging = YokaiBalance.Bifauce.CHARGE_TICKS;
                entityData.set(DATA_ANIM, 2);
                playSound(ModSounds.BIFAUCE_CHARGE, 1.4f, 1.0f);
            }
            return;
        }
        if (charging > 0) {
            // Committed: a straight line. Walls end it; a step aside makes it miss.
            setDeltaMovement(chargeDir.x * YokaiBalance.Bifauce.CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * YokaiBalance.Bifauce.CHARGE_SPEED);
            if (target != null && getBoundingBox().inflate(0.4).intersects(target.getBoundingBox()) && YokaiGoals.mayHarm(this, target)) {
                target.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Bifauce.CHARGE_DAMAGE);
                target.push(chargeDir.x * 0.8, 0.3, chargeDir.z * 0.8);
                target.syncVelocity = true;
                charging = 1;
            }
            if (horizontalCollision) charging = 1;
            if (--charging == 0) {
                entityData.set(DATA_ANIM, 0);
                cooldown = YokaiBalance.Bifauce.CHARGE_COOLDOWN;
            }
            return;
        }
        if (target instanceof ServerPlayer p && core.mayPursue(p)) {
            double d = distanceToSqr(p);
            if (cooldown == 0 && d > 9 && d < 144 && getSensing().hasLineOfSight(p)) {
                windup = YokaiBalance.Bifauce.CHARGE_WINDUP;
                entityData.set(DATA_ANIM, 1);
                playSound(ModSounds.BIFAUCE_OPEN, 1.3f, 1.0f);
                getNavigation().stop();
            } else if (d > 36) getNavigation().moveTo(p, 1.0);
            return;
        }
        // Deception for those who cannot see it: familiar sounds from where nothing is, and a breath drawn backwards.
        if (--mimicCooldown <= 0) {
            mimicCooldown = 20;
            ServerPlayer near = level.getNearestPlayer(this, 24) instanceof ServerPlayer sp ? sp : null;
            if (near != null && Perception.view(near, this) < 2 && random.nextFloat() < YokaiBalance.Bifauce.MIMIC_CHANCE * 20) {
                mimicCooldown = 200 + random.nextInt(200);
                if (random.nextInt(3) == 0) {
                    playSound(ModSounds.BIFAUCE_BREATH, 1.0f, 1.0f);
                    Bestiary.clue(near, species(), "backward_breath");
                } else {
                    Vec3 side = near.getLookAngle().multiply(1, 0, 1).normalize().yRot((random.nextBoolean() ? 1 : -1) * (float) Math.PI / 2).scale(7 + random.nextInt(4));
                    Vec3 at = near.position().add(side);
                    SoundEvent s = MIMIC[random.nextInt(MIMIC.length)];
                    level.playSound(null, at.x, at.y, at.z, s, getSoundSource(), 1.0f, 0.95f + random.nextFloat() * 0.1f);
                    Bestiary.clue(near, species(), "mimic");
                    // It drifts toward where it led them.
                    getNavigation().moveTo(at.x, at.y, at.z, 0.9);
                }
            }
        }
    }

    /** Everyday sounds the mimicking mouth reproduces. */
    private static final SoundEvent[] MIMIC = {SoundEvents.VILLAGER_AMBIENT, SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.CHEST_OPEN,
            SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundEvents.BELL_BLOCK, SoundEvents.SHEEP_AMBIENT};

    @Override public boolean isPickable() { return super.isPickable() && YokaiHooks.pickable(this); }
    @Override public boolean ignoreExplosion(Explosion explosion) { return YokaiCombat.ignoresExplosion(this, explosion) || super.ignoreExplosion(explosion); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); core.save(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); core.load(in); }
    @Override protected SoundEvent getAmbientSound() { return Perception.exempt(this) ? super.getAmbientSound() : null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return Perception.exempt(this) ? super.getHurtSound(s) : ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return Perception.exempt(this) ? super.getDeathSound() : ModSounds.YOKAI_DEATH; }
}
