package ryuna.qdeath.entity;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
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

/**
 * False Creeper. In Hell it keeps its old role (sensed by radius, drifts through walls, detonates). In the Overworld it
 * is a nocturnal spectre: unseen (perception 0) it never chases and only detonates at random where it drifts; partial
 * sight risks a pursuit once per encounter; complete sight gets the full chase. Every detonation is announced by a
 * 3-second hiss and distortion, reaches 3 blocks, deals at most 6 (less with distance), never through a solid wall,
 * and never breaks a block or lights a fire.
 */
public class FakeCreeperEntity extends Creeper implements ryuna.qdeath.entity.hell.HellMob, YokaiCreature {
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(FakeCreeperEntity.class, EntityDataSerializers.INT);
    private static final DustColorTransitionOptions DISTORTION = new DustColorTransitionOptions(0x9cf0a8, 0x1a2a1c, 1.2f);
    private final YokaiCore core = new YokaiCore(Species.FALSE_CREEPER, this);
    private int detonationTicks;
    private int fuse = -1;
    private boolean ambientFuse;
    private Vec3 drift = Vec3.ZERO;

    public FakeCreeperEntity(EntityType<? extends FakeCreeperEntity> type, Level level) { super(type, level); this.noPhysics = true; setNoGravity(true); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes().add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.MAX_HEALTH, YokaiBalance.FalseCreeper.HEALTH);
    }
    @Override public YokaiCore yokai() { return core; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_FUSE, -1);
    }
    /** 0..1 while hissing, -1 otherwise (for the model). */
    public float fuseProgress(float partial) {
        int f = entityData.get(DATA_FUSE);
        return f < 0 ? -1 : Math.min(1f, (YokaiBalance.FalseCreeper.FUSE - f + partial) / YokaiBalance.FalseCreeper.FUSE);
    }

    @Override protected void registerGoals() {
        // Hell: detect by radius only, neither sight nor a ground path needed (the original behaviour).
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, (t, l) -> Perception.exempt(this)));
        targetSelector.addGoal(2, new HurtByTargetGoal(this) { @Override public boolean canUse() { return Perception.exempt(FakeCreeperEntity.this) && super.canUse(); } });
        // Overworld: the per-player perception rules.
        targetSelector.addGoal(3, new YokaiGoals.Hostile(this, YokaiBalance.Perception.DETECT_RANGE));
    }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.FAKE_CREEPER, server, reason, blockPosition(), random);
    }
    @Override public void tick() {
        noPhysics = true;
        setNoGravity(true);
        if (!level().isClientSide()) {
            if (Perception.exempt(this)) hellTick();
            else if (!overworldTick()) return;
        }
        super.tick();
        if (!level().isClientSide() && Perception.exempt(this)) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && distanceToSqr(target) < 9.0) detonationTicks++;
            else detonationTicks = Math.max(0, detonationTicks - 2);
            if (detonationTicks >= 30) {
                level().explode(this, getX(), getY(), getZ(), 3.0f, false, Level.ExplosionInteraction.NONE);
                discard();
            }
        }
    }

    private void hellTick() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            Vec3 direction = target.getEyePosition().subtract(getEyePosition());
            if (direction.lengthSqr() > .1) {
                setDeltaMovement(direction.normalize().scale(.12));
                face(direction);
            }
        }
    }

    private void face(Vec3 direction) {
        float yaw = (float)(Math.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float)-(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI));
        setYRot(yaw); setYHeadRot(yaw); setXRot(pitch);
    }

    /** False when the creature is gone. */
    private boolean overworldTick() {
        ServerLevel level = (ServerLevel) level();
        if (!YokaiHooks.serverTick(this, core)) return false;
        // A thing of the night: it thins away at dawn unless it is already hissing.
        if (fuse < 0 && level.isBrightOutside() && level.canSeeSky(blockPosition())) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            discard();
            return false;
        }
        if (fuse >= 0) {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            level.sendParticles(DISTORTION, getX(), getY() + 0.9, getZ(), 4, 0.35, 0.5, 0.35, 0.02);
            entityData.set(DATA_FUSE, fuse);
            if (--fuse < 0) burst(level);
            return true;
        }
        LivingEntity target = getTarget();
        if (target instanceof ServerPlayer p && p.isAlive() && core.mayPursue(p)) {
            Vec3 direction = target.getEyePosition().subtract(getEyePosition());
            setDeltaMovement(direction.normalize().scale(YokaiBalance.FalseCreeper.SPEED));
            face(direction);
            if (direction.lengthSqr() < 2.5 * 2.5) startFuse(false);
        } else {
            setTarget(null);
            // Drifting, never toward anyone.
            if (tickCount % 60 == 0 || drift == Vec3.ZERO) drift = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.2, random.nextGaussian()).normalize().scale(0.03);
            setDeltaMovement(drift);
            if (tickCount % 20 == 0 && !core.calmed() && level.getNearestPlayer(this, 16) instanceof ServerPlayer p && Perception.targetable(p)
                    && random.nextFloat() < YokaiBalance.FalseCreeper.AMBIENT_DETONATION_CHANCE) startFuse(true);
        }
        return true;
    }

    /** Starts the three-second hiss now (operators' demonstrations and tests); {@code ambient}: the unseen random burst. */
    public void ignite(boolean ambient) { if (fuse < 0) startFuse(ambient); }

    private void startFuse(boolean ambient) {
        fuse = YokaiBalance.FalseCreeper.FUSE;
        ambientFuse = ambient;
        playSound(ModSounds.FALSE_CREEPER_HISS, 1.4f, 1.0f);
        Bestiary.clueNear((ServerLevel) level(), position(), 16, species(), "hiss");
    }

    /** The burst: up to 6 damage within 3 blocks, falling off with distance; never through a solid wall; no blocks, no fire. */
    private void burst(ServerLevel level) {
        entityData.set(DATA_FUSE, -1);
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.FALSE_CREEPER_BURST, getSoundSource(), 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(DISTORTION, c.x, c.y, c.z, 40, 1.2, 1.0, 1.2, 0.1);
        Bestiary.clueNear(level, c, 16, species(), "scorch");
        double r = YokaiBalance.FalseCreeper.RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1), e -> e != this && e.isAlive())) {
            double d = e.getBoundingBox().getCenter().distanceTo(c);
            if (d > r) continue;
            if (e instanceof ServerPlayer p ? !Perception.targetable(p) || !ambientFuse && !core.mayPursue(p) : !ambientFuse) continue;
            if (!clear(level, c, e.getEyePosition()) && !clear(level, c, e.getBoundingBox().getCenter())) continue;
            float dmg = (float) (YokaiBalance.FalseCreeper.DAMAGE * (1 - d / r));
            if (dmg > 0.25f) e.hurtServer(level, damageSources().explosion(this, null), dmg);
        }
        discard();
    }

    private boolean clear(ServerLevel level, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Disable Creeper's private explosion path; this mob uses its own bursts. */
    @Override public int getSwellDir() { return -1; }
    @Override public void setSwellDir(int state) {}
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override public boolean isPickable() { return super.isPickable() && YokaiHooks.pickable(this); }
    @Override public boolean ignoreExplosion(Explosion explosion) { return YokaiCombat.ignoresExplosion(this, explosion) || super.ignoreExplosion(explosion); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); core.save(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); core.load(in); }
}
