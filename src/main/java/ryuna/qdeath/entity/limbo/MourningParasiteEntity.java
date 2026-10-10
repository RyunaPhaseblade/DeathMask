package ryuna.qdeath.entity.limbo;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboTasks;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.UUID;

/**
 * Mourning Parasite (Parásito del duelo): a small, many-legged sac of grey mourning-tissue with a lamprey mouth. It
 * looks for a living host it can see within ten blocks (an animal that does not live in fire, a villager, a spider),
 * creeps closer, crouches for a moment and leaps onto its head; clinging there for a second and a half it sinks its
 * tendrils in (a hit on it now shakes it off), then the host belongs to the horde (see {@link Possession}). It stays a
 * creature of its own on the host's head, with its own body to hit: killing it frees the host at once. When the host
 * dies it drops off, stunned, and needs a while before it can take another. It never multiplies. Without a host it is a
 * weak biter that keeps to the horde.
 */
public class MourningParasiteEntity extends LimboDead {
    private static final EntityDataAccessor<Integer> DATA_HOST = SynchedEntityData.defineId(MourningParasiteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(MourningParasiteEntity.class, EntityDataSerializers.BYTE);
    public static final byte FREE = 0, PREPARE = 1, LEAP = 2, FIXING = 3, POSSESSING = 4, STUNNED = 5;

    private @Nullable UUID hostId;
    private @Nullable Mob candidate;
    private int timer, stun, cooldown, missing;

    public MourningParasiteEntity(EntityType<? extends MourningParasiteEntity> type, Level level) { super(type, level); xpReward = 5; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_HOST, -1);
        b.define(DATA_PHASE, FREE);
    }

    @Override protected boolean human() { return false; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_PARASITE; }
    @Override protected double sightRange() { return 16; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 1.2; }
    @Override protected int windupTicks() { return 8; }
    @Override protected int attackRecovery() { return 24; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected boolean canInvestigate() { return !attached(); }

    public byte phase() { return entityData.get(DATA_PHASE); }
    public boolean attached() { byte p = phase(); return p == FIXING || p == POSSESSING; }
    public @Nullable UUID hostId() { return phase() == POSSESSING || phase() == FIXING ? hostId : null; }
    /** Client and server: the host it clings to, if loaded. */
    public @Nullable Entity hostEntity() {
        int id = entityData.get(DATA_HOST);
        return id < 0 ? null : level().getEntity(id);
    }

    private void setPhase(byte p) { entityData.set(DATA_PHASE, p); timer = 0; }

    // ------------------------------------------------------------------ the tick
    @Override public void tick() {
        super.tick();
        if (attached()) follow();
    }

    /** Sits on the host's head, facing where it faces (both sides, so it never lags behind its host on screen). */
    private void follow() {
        Entity host = hostEntity();
        if (host == null) return;
        setPos(host.getX(), host.getY() + host.getBbHeight() - 0.12, host.getZ());
        setDeltaMovement(Vec3.ZERO);
        float yaw = host instanceof LivingEntity l ? l.getYHeadRot() : host.getYRot();
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        switch (phase()) {
            case FIXING, POSSESSING -> { attachedStep(level); return; }
            case STUNNED -> {
                getNavigation().stop();
                if (--stun <= 0) setPhase(FREE);
                return;
            }
            case PREPARE -> { prepareStep(level); return; }
            case LEAP -> { leapStep(level); return; }
            default -> {}
        }
        if (seekHost(level)) return;
        super.customServerAiStep(level);
    }

    /** Free: find a host it can see within reach, creep closer, and crouch to leap from two to five blocks away. */
    private boolean seekHost(ServerLevel level) {
        if (cooldown > 0) return false;
        if (candidate != null && (!Possession.eligible(candidate) || distanceToSqr(candidate) > 14 * 14 || !LimboTasks.free(level, "host:" + candidate.getUUID(), getUUID()))) candidate = null;
        if (candidate == null && (tickCount + getId()) % 10 == 0) candidate = findHost(level);
        Mob host = candidate;
        if (host == null) return false;
        LimboTasks.reserve(level, "host:" + host.getUUID(), getUUID(), 100);
        double d = distanceTo(host);
        getLookControl().setLookAt(host, 30f, 30f);
        if (d >= LimboTuning.PARASITE_LEAP_MIN && d <= LimboTuning.PARASITE_LEAP_MAX && onGround() && getSensing().hasLineOfSight(host)) {
            getNavigation().stop();
            setPhase(PREPARE);
            playAnim(ANIM_SPECIAL, LimboTuning.PARASITE_PREPARE);
            playSound(ModSounds.PARASITE_CHITTER, 1.1f, 1.1f + random.nextFloat() * 0.2f);
            return true;
        }
        if ((tickCount + getId()) % 5 == 0 || getNavigation().isDone()) {
            if (d < LimboTuning.PARASITE_LEAP_MIN) {
                // Too close to leap: skitter back a little first.
                Vec3 away = position().add(position().subtract(host.position()).normalize().scale(2.5));
                getNavigation().moveTo(away.x, away.y, away.z, chaseSpeed());
            } else getNavigation().moveTo(host, chaseSpeed());
        }
        return true;
    }

    /** A living creature it could take is never bitten: it is the host it goes for. */
    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (q instanceof Mob m && Possession.eligible(m) && cooldown == 0 && LimboTasks.free(level, "host:" + m.getUUID(), getUUID())) {
            candidate = m;
            return seekHost(level);
        }
        return false;
    }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        if (!(q instanceof Mob m && Possession.eligible(m))) super.strike(level, q);
    }

    private @Nullable Mob findHost(ServerLevel level) {
        Mob best = null;
        double bestD = LimboTuning.PARASITE_SEEK * LimboTuning.PARASITE_SEEK;
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LimboTuning.PARASITE_SEEK), Possession::eligible)) {
            double d = distanceToSqr(m);
            if (d >= bestD || Consecration.at(level, m.position()) != null || !getSensing().hasLineOfSight(m)) continue;
            if (!LimboTasks.free(level, "host:" + m.getUUID(), getUUID())) continue; // another parasite is already on its way
            best = m;
            bestD = d;
        }
        return best;
    }

    private void prepareStep(ServerLevel level) {
        Mob host = candidate;
        getNavigation().stop();
        if (host == null || !Possession.eligible(host)) { setPhase(FREE); return; }
        getLookControl().setLookAt(host, 40f, 40f);
        if (++timer < LimboTuning.PARASITE_PREPARE) return;
        // The leap: a short arc onto the host's head, where the host will be when it lands.
        Vec3 to = new Vec3(host.getX(), host.getY() + host.getBbHeight(), host.getZ()).subtract(position());
        double flight = Math.max(5, Math.min(12, to.horizontalDistance() / 0.45));
        Vec3 drift = host.getDeltaMovement().multiply(1, 0, 1).scale(flight);
        to = to.add(drift);
        setDeltaMovement(to.x / flight, Math.min(0.75, to.y / flight + 0.04 * flight), to.z / flight);
        setPhase(LEAP);
        playSound(ModSounds.PARASITE_LEAP, 1.2f, 1.0f + random.nextFloat() * 0.2f);
    }

    private void leapStep(ServerLevel level) {
        Mob host = candidate;
        timer++;
        if (host != null && host.isAlive() && Possession.eligible(host) && getBoundingBox().inflate(0.25).intersects(host.getBoundingBox())) {
            latch(level, host);
            return;
        }
        if (timer > 24 || (onGround() && timer > 4)) { // missed: it lands and gathers itself
            setPhase(STUNNED);
            stun = 16;
            cooldown = 40;
        }
    }

    private void latch(ServerLevel level, Mob host) {
        hostId = host.getUUID();
        entityData.set(DATA_HOST, host.getId());
        setPhase(FIXING);
        setNoGravity(true);
        noPhysics = true;
        getNavigation().stop();
        playAnim(ANIM_CALL, LimboTuning.PARASITE_FIXATION);
        level.playSound(null, host.blockPosition(), ModSounds.PARASITE_LATCH, SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    private void attachedStep(ServerLevel level) {
        getNavigation().stop();
        Entity e = hostId == null ? null : level.getEntity(hostId);
        if (!(e instanceof Mob host)) {
            // The host may just not be loaded yet (a reload): wait, then let go of a bond that is gone.
            if (++missing > LimboTuning.LINK_GRACE) loseHost();
            return;
        }
        missing = 0;
        if (entityData.get(DATA_HOST) != host.getId()) entityData.set(DATA_HOST, host.getId());
        if (!host.isAlive()) { detach(level, true); return; }
        if (phase() == FIXING) {
            if (Consecration.at(level, host.position()) != null || Possession.isPossessed(host)) { detach(level, false); return; }
            if (timer % 6 == 0) level.sendParticles(ModParticles.BILE_DRIP, getX(), getY() + 0.2, getZ(), 3, 0.15, 0.1, 0.15, 0.02);
            if (++timer >= LimboTuning.PARASITE_FIXATION) {
                setPhase(POSSESSING);
                Possession.possess(level, host, this);
            }
            return;
        }
        Possession.Link link = Possession.link(host);
        if (link == null || !link.parasite().equals(getUUID())) loseHost(); // the host was freed (consecration) or taken
    }

    /** Lets go of the host: freed if it was possessed; {@code hostDied} means stunned two seconds and eight before another. */
    public void detach(ServerLevel level, boolean hostDied) {
        if (hostId != null && level.getEntity(hostId) instanceof Mob host && Possession.isPossessed(host)) {
            Possession.Link link = Possession.link(host);
            if (link != null && link.parasite().equals(getUUID())) Possession.release(host);
        }
        clearHost();
        setPhase(STUNNED);
        stun = hostDied ? LimboTuning.PARASITE_STUN : 20;
        cooldown = hostDied ? LimboTuning.PARASITE_COOLDOWN : 60;
        playAnim(ANIM_STAGGER, stun);
        setDeltaMovement(random.nextGaussian() * 0.15, 0.2, random.nextGaussian() * 0.15);
        playSound(ModSounds.PARASITE_CHITTER, 1.0f, 0.7f);
    }

    private void loseHost() {
        clearHost();
        setPhase(FREE);
    }

    private void clearHost() {
        hostId = null;
        candidate = null;
        missing = 0;
        entityData.set(DATA_HOST, -1);
        setNoGravity(false);
        noPhysics = false;
    }

    /** Its host is about to strike: the sac clenches and the parasite shrieks (Possession). */
    public void signalStrike() {
        playAnim(ANIM_ATTACK, LimboTuning.HOST_WINDUP + 6);
        playSound(ModSounds.PARASITE_CHITTER, 1.0f, 1.4f);
    }

    // ------------------------------------------------------------------ damage, death and removal
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (attached() && source.is(DamageTypeTags.IS_FALL)) return false;
        boolean hurt = super.hurtServer(level, source, damage);
        // A blow while it is still sinking its tendrils in shakes it off; once in, only death frees the host.
        if (hurt && isAlive() && (phase() == FIXING || phase() == PREPARE || phase() == LEAP)) detach(level, false);
        return hurt;
    }

    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) freeHost(level);
        super.die(source);
    }

    @Override public void remove(RemovalReason reason) {
        // Killed or discarded: the host is freed now. Unloaded or carried to another dimension: the bond is kept (saved).
        if (reason.shouldDestroy() && level() instanceof ServerLevel level) freeHost(level);
        super.remove(reason);
    }

    private void freeHost(ServerLevel level) {
        if (hostId != null && level.getEntity(hostId) instanceof Mob host) {
            Possession.Link link = Possession.link(host);
            if (link != null && link.parasite().equals(getUUID())) Possession.release(host);
        }
    }

    @Override public boolean isPushable() { return !attached() && super.isPushable(); }
    @Override public boolean removeWhenFarAway(double distSqr) { return !attached() && super.removeWhenFarAway(distSqr); }

    // ------------------------------------------------------------------ persistence
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        // Only a finished possession is saved; a leap or a half-done latch starts over after a reload.
        if (phase() == POSSESSING && hostId != null) out.putString("ParasiteHost", hostId.toString());
        out.putInt("ParasiteCooldown", cooldown);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String h = in.getStringOr("ParasiteHost", "");
        cooldown = in.getIntOr("ParasiteCooldown", 0);
        if (!h.isEmpty()) {
            hostId = UUID.fromString(h);
            entityData.set(DATA_PHASE, POSSESSING);
            setNoGravity(true);
            noPhysics = true;
        }
    }

    // ------------------------------------------------------------------ voice
    @Override protected SoundEvent recognizeSound() { return ModSounds.PARASITE_CHITTER; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.PARASITE_CHITTER; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.PARASITE_CHITTER; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.PARASITE_LEAP; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.PARASITE_POSSESS; }
    @Override public float getVoicePitch() { return 1.25f + (random.nextFloat() - 0.5f) * 0.2f; }
    @Override public int getAmbientSoundInterval() { return 160; }
}
