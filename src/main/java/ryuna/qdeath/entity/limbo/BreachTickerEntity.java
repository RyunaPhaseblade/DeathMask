package ryuna.qdeath.entity.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.limbo.Breach;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboTasks;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.limbo.Provenance;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * Breach Ticker (Roedor de brechas): a low, gnawing thing carrying a pulsing sac on its back. Two separate ways to use
 * it, never mixed:
 * <ul>
 *   <li><b>Siege.</b> When what it hunts sits behind a barrier a player built, it clings to an accessible face of it and
 *   swells for three seconds, pulses and contractions quickening; the burst opens a directed corridor through blocks
 *   players placed (see {@link Breach}) and does nothing whatsoever to creatures.</li>
 *   <li><b>Adhesion.</b> Within three blocks of its quarry it crouches half a second and leaps onto the chest; the count
 *   lasts two seconds and the burst hurts the living around (up to 10, less with distance and cover) and breaks no
 *   block. The victim can still move and fight.</li>
 * </ul>
 * Any blow that hurts it during either count tears it loose, stunned for two seconds, and it waits five before
 * clinging again. Both bursts consume it. Nothing of a count is saved: a reload never sets one off.
 */
public class BreachTickerEntity extends LimboDead {
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(BreachTickerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_VICTIM = SynchedEntityData.defineId(BreachTickerEntity.class, EntityDataSerializers.INT);
    public static final byte FREE = 0, LEAP_PREP = 1, LEAPING = 2, ON_VICTIM = 3, ON_BARRIER = 4, STUNNED = 5, TO_BARRIER = 6;
    public static final ResourceKey<DamageType> BURST = ResourceKey.create(Registries.DAMAGE_TYPE, Quantumdeath.id("ticker_burst"));

    private int timer, stun, reattach, loadGrace = LimboTuning.TICKER_LOAD_GRACE, nextBarrierCheck;
    private @Nullable LivingEntity victim;
    private Breach.@Nullable Barrier barrier;
    private @Nullable Vec3 attachPos, direction, siegeGoal;

    public BreachTickerEntity(EntityType<? extends BreachTickerEntity> type, Level level) { super(type, level); xpReward = 8; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_MODE, FREE);
        b.define(DATA_VICTIM, -1);
    }

    @Override protected boolean human() { return false; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_TICKER; }
    @Override protected double sightRange() { return 16; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 1.3; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected boolean canInvestigate() { byte m = mode(); return m == FREE || m == TO_BARRIER; }

    public byte mode() { return entityData.get(DATA_MODE); }
    public boolean clinging() { byte m = mode(); return m == ON_VICTIM || m == ON_BARRIER; }
    private void setMode(byte m) { entityData.set(DATA_MODE, m); timer = 0; }
    public @Nullable Entity victimEntity() { int id = entityData.get(DATA_VICTIM); return id < 0 ? null : level().getEntity(id); }

    // ------------------------------------------------------------------ tick
    @Override public void tick() {
        super.tick();
        if (mode() == ON_VICTIM) followVictim();
        else if (mode() == ON_BARRIER && attachPos != null) { setPos(attachPos); setDeltaMovement(Vec3.ZERO); }
    }

    /** Clings to the front of its victim's chest, where the victim (and anyone beside it) can see and hit it. */
    private void followVictim() {
        Entity v = victimEntity();
        if (v == null) return;
        float yaw = v instanceof LivingEntity l ? l.yBodyRot : v.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        setPos(v.getX() + fwd.x * (v.getBbWidth() * 0.5 + 0.25), v.getY() + v.getBbHeight() * 0.42, v.getZ() + fwd.z * (v.getBbWidth() * 0.5 + 0.25));
        setDeltaMovement(Vec3.ZERO);
        setYRot(yaw + 180f);
        setYBodyRot(yaw + 180f);
        setYHeadRot(yaw + 180f);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (loadGrace > 0) loadGrace--;
        if (reattach > 0) reattach--;
        switch (mode()) {
            case STUNNED -> { getNavigation().stop(); if (--stun <= 0) setMode(FREE); return; }
            case LEAP_PREP -> { leapPrep(level); return; }
            case LEAPING -> { leaping(level); return; }
            case ON_VICTIM -> { countdownVictim(level); return; }
            case ON_BARRIER -> { countdownBarrier(level); return; }
            case TO_BARRIER -> { if (toBarrier(level)) return; }
            default -> {}
        }
        super.customServerAiStep(level);
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (mode() == TO_BARRIER) return true;
        if (loadGrace > 0 || reattach > 0) return false;
        if (visible && distance <= LimboTuning.TICKER_LEAP_RANGE && onGround() && Consecration.at(level, q.position()) == null) {
            victim = q;
            getNavigation().stop();
            setMode(LEAP_PREP);
            playAnim(ANIM_SPECIAL, LimboTuning.TICKER_LEAP_PREPARE);
            playSound(ModSounds.TICKER_HISS, 1.2f, 1.2f);
            return true;
        }
        // Out of reach behind something a player built: open it.
        if (--nextBarrierCheck <= 0) {
            nextBarrierCheck = 20;
            if (!reachable(q.position(), distance)) return seekBarrier(level, q.position());
        }
        return false;
    }

    /** In a search, the place it is looking for may be walled in too (a sealed tunnel). */
    @Override protected boolean searchSpecial(ServerLevel level, @Nullable Vec3 goal) {
        if (mode() == TO_BARRIER) return true;
        if (goal == null || loadGrace > 0 || reattach > 0 || --nextBarrierCheck > 0) return false;
        nextBarrierCheck = 20;
        return !reachable(goal, Math.sqrt(distanceToSqr(goal))) && seekBarrier(level, goal);
    }

    private boolean reachable(Vec3 goal, double distance) {
        if (distance < 2) return true;
        Path p = getNavigation().createPath(BlockPos.containing(goal), 1);
        return p != null && p.canReach();
    }

    /** A Foreman's order: open the barrier between this Ticker and {@code goal}, if there is one it may open. */
    public boolean orderBreach(ServerLevel level, Vec3 goal) {
        if (mode() != FREE || loadGrace > 0 || reattach > 0) return false;
        return seekBarrier(level, goal);
    }

    private boolean seekBarrier(ServerLevel level, Vec3 goal) {
        Breach.Barrier b = Breach.toward(level, this, goal);
        if (b == null || !LimboTasks.reserve(level, "breach:" + b.pos().asLong(), getUUID(), 200)) return false;
        barrier = b;
        siegeGoal = goal;
        setMode(TO_BARRIER);
        return true;
    }

    /** Walks to the reachable face of its barrier and clings to it. */
    private boolean toBarrier(ServerLevel level) {
        Breach.Barrier b = barrier;
        if (b == null || !Provenance.breachable(level, b.pos()) || timer++ > 300) { abandonBarrier(level); return false; }
        LimboTasks.reserve(level, "breach:" + b.pos().asLong(), getUUID(), 200);
        BlockPos stand = b.pos().relative(b.face());
        Vec3 face = Vec3.atCenterOf(b.pos()).add(Vec3.atLowerCornerOf(b.face().getUnitVec3i()).scale(0.5));
        if (position().add(0, 0.3, 0).distanceToSqr(face) < 1.6 * 1.6 || (b.face() == Direction.DOWN && distanceToSqr(Vec3.atBottomCenterOf(stand)) < 2.5)) {
            attachToBarrier(level, b, face);
            return true;
        }
        if (timer % 10 == 1 || getNavigation().isDone()) {
            Path p = getNavigation().createPath(stand, 0);
            if (p == null) { abandonBarrier(level); return false; }
            getNavigation().moveTo(p, chaseSpeed());
        }
        return true;
    }

    private void attachToBarrier(ServerLevel level, Breach.Barrier b, Vec3 face) {
        Vec3 out = Vec3.atLowerCornerOf(b.face().getUnitVec3i());
        attachPos = face.add(out.scale(0.32)).subtract(0, b.face().getAxis() == Direction.Axis.Y ? 0 : 0.3, 0);
        Vec3 goal = siegeGoal != null ? siegeGoal : Vec3.atCenterOf(b.pos()).subtract(out);
        // The direction is fixed now: from the Ticker, through the barrier, toward what it hunts.
        Vec3 dir = goal.subtract(attachPos);
        direction = dir.lengthSqr() < 1.0e-4 ? out.scale(-1) : dir.normalize();
        if (direction.dot(out.scale(-1)) < 0.1) direction = out.scale(-1); // never aim back out of the face it holds
        getNavigation().stop();
        setNoGravity(true);
        setMode(ON_BARRIER);
        playAnim(ANIM_CALL, LimboTuning.TICKER_SIEGE_PREPARE);
        level.playSound(null, b.pos(), ModSounds.TICKER_ATTACH, SoundSource.HOSTILE, 1.2f, 0.9f);
    }

    private void abandonBarrier(ServerLevel level) {
        if (barrier != null) LimboTasks.release(level, "breach:" + barrier.pos().asLong(), getUUID());
        barrier = null;
        siegeGoal = null;
        setNoGravity(false);
        setMode(FREE);
    }

    private void countdownBarrier(ServerLevel level) {
        Breach.Barrier b = barrier;
        if (b == null || attachPos == null || direction == null || !Provenance.breachable(level, b.pos())) { abandonBarrier(level); return; }
        getNavigation().stop();
        pulse(level, LimboTuning.TICKER_SIEGE_PREPARE);
        if (timer % 8 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(b.pos())), attachPos.x, attachPos.y, attachPos.z, 3, 0.2, 0.2, 0.2, 0.02);
        if (++timer < LimboTuning.TICKER_SIEGE_PREPARE) return;
        // The siege burst: blocks only. No damage, no push, no effect on any creature.
        Vec3 start = attachPos.subtract(direction.scale(0.2));
        int n = Breach.open(level, this, start, direction);
        level.playSound(null, b.pos(), ModSounds.TICKER_BREACH, SoundSource.HOSTILE, 2.4f, 0.9f + random.nextFloat() * 0.15f);
        level.sendParticles(ModParticles.BILE_DRIP, attachPos.x, attachPos.y, attachPos.z, 40, 0.6, 0.6, 0.6, 0.1);
        level.sendParticles(ModParticles.ASH_FLAKE, attachPos.x, attachPos.y, attachPos.z, 30, 1.0, 1.0, 1.0, 0.05);
        Quantumdeath.LOGGER.debug("Ticker breach at {} along {} opened {} blocks", b.pos(), direction, n);
        LimboTasks.release(level, "breach:" + b.pos().asLong(), getUUID());
        discard();
    }

    private void leapPrep(ServerLevel level) {
        LivingEntity v = victim;
        getNavigation().stop();
        if (v == null || !v.isAlive()) { setMode(FREE); return; }
        getLookControl().setLookAt(v, 40f, 40f);
        if (++timer < LimboTuning.TICKER_LEAP_PREPARE) return;
        Vec3 to = new Vec3(v.getX(), v.getY() + v.getBbHeight() * 0.45, v.getZ()).subtract(position());
        double flight = Math.max(4, Math.min(9, to.horizontalDistance() / 0.5));
        setDeltaMovement(to.x / flight, Math.min(0.7, to.y / flight + 0.04 * flight), to.z / flight);
        setMode(LEAPING);
    }

    private void leaping(ServerLevel level) {
        LivingEntity v = victim;
        timer++;
        if (v != null && v.isAlive() && Consecration.at(level, v.position()) == null && getBoundingBox().inflate(0.3).intersects(v.getBoundingBox())) {
            entityData.set(DATA_VICTIM, v.getId());
            setNoGravity(true);
            noPhysics = true;
            setMode(ON_VICTIM);
            playAnim(ANIM_CALL, LimboTuning.TICKER_COUNTDOWN);
            level.playSound(null, v.blockPosition(), ModSounds.TICKER_ATTACH, SoundSource.HOSTILE, 1.3f, 1.2f);
            return;
        }
        if (timer > 20 || (onGround() && timer > 3)) { victim = null; reattach = 20; setMode(FREE); }
    }

    private void countdownVictim(ServerLevel level) {
        LivingEntity v = victim;
        if (v == null || !v.isAlive() || v.level() != level || v.isRemoved() || Consecration.at(level, v.position()) != null) { tearLoose(level, 20); return; }
        pulse(level, LimboTuning.TICKER_COUNTDOWN);
        if (++timer < LimboTuning.TICKER_COUNTDOWN) return;
        burst(level);
    }

    /** Pulses that quicken toward the burst: the warning, by ear and by eye. */
    private void pulse(ServerLevel level, int total) {
        float p = timer / (float) total;
        int every = p < 0.4f ? 10 : p < 0.75f ? 6 : 3;
        if (timer % every == 0) level.playSound(null, blockPosition(), ModSounds.TICKER_PULSE, SoundSource.HOSTILE, 0.9f + p, 0.8f + p * 0.9f);
    }

    /** The flesh burst on a victim: hurts the living around (not the horde), breaks nothing. */
    private void burst(ServerLevel level) {
        Vec3 c = position().add(0, 0.3, 0);
        double r = LimboTuning.TICKER_BURST_RADIUS;
        DamageSource src = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BURST), this, this);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1))) {
            if (e == this || !e.isAlive() || e instanceof LimboDead || Possession.isPossessed(e)) continue;
            double d = Math.max(0, e.getBoundingBox().getCenter().distanceTo(c) - e.getBbWidth() * 0.5);
            if (d > r) continue;
            float k = (float) ((1 - d / r) * BloaterCorpse.cover(level, c, e, this));
            if (k > 0.02f) e.hurtServer(level, src, LimboTuning.TICKER_BURST_DAMAGE * k);
        }
        level.playSound(null, blockPosition(), ModSounds.TICKER_BURST, SoundSource.HOSTILE, 2.2f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.BILE_DRIP, c.x, c.y, c.z, 50, 0.9, 0.6, 0.9, 0.15);
        level.sendParticles(ModParticles.BLOOD_EMBER, c.x, c.y, c.z, 20, 0.8, 0.5, 0.8, 0.05);
        discard();
    }

    /** Torn loose: falls off, stunned, and waits before it may cling again. */
    private void tearLoose(ServerLevel level, int stunTicks) {
        if (barrier != null) LimboTasks.release(level, "breach:" + barrier.pos().asLong(), getUUID());
        barrier = null;
        victim = null;
        attachPos = null;
        entityData.set(DATA_VICTIM, -1);
        setNoGravity(false);
        noPhysics = false;
        stun = stunTicks;
        reattach = LimboTuning.TICKER_REATTACH;
        setMode(STUNNED);
        playAnim(ANIM_STAGGER, stunTicks);
        setDeltaMovement(random.nextGaussian() * 0.1, 0.15, random.nextGaussian() * 0.1);
    }

    // ------------------------------------------------------------------ interruption
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (clinging() && source.is(DamageTypeTags.IS_FALL)) return false;
        byte m = mode();
        boolean hurt = super.hurtServer(level, source, damage);
        // Any blow that hurts it during a count tears it loose: stunned two seconds, five before it clings again.
        if (hurt && isAlive() && (m == ON_VICTIM || m == ON_BARRIER || m == LEAP_PREP || m == LEAPING)) tearLoose(level, LimboTuning.TICKER_STUN);
        return hurt;
    }

    @Override public boolean isPushable() { return !clinging() && super.isPushable(); }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        loadGrace = LimboTuning.TICKER_LOAD_GRACE; // a loaded Ticker never resumes a count
    }

    // ------------------------------------------------------------------ voice
    @Override protected SoundEvent recognizeSound() { return ModSounds.TICKER_HISS; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.TICKER_PULSE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.TICKER_PULSE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.TICKER_HISS; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.TICKER_HISS; }
    @Override public float getVoicePitch() { return 1.1f + (random.nextFloat() - 0.5f) * 0.15f; }
    @Override public int getAmbientSoundInterval() { return 120; }
}
