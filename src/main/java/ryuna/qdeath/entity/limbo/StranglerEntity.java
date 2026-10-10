package ryuna.qdeath.entity.limbo;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.Grips;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Strangler (Estrangulador): a dead man whose jaw hangs dislocated and whose throat has stretched into a sac; its tongue
 * is the attack. Within fourteen blocks and in sight, it plants itself and for a second its throat bulges and the tongue
 * slides out over the hanging jaw; then the tongue flies (it travels, it collides, it never passes through a wall) and,
 * if it reaches someone, drags them toward the Strangler at up to 2.5 blocks a second for at most four seconds, one
 * point of damage a second. The victim can still look, strike and use things. The grip breaks when the tongue is cut
 * (by the victim or a friend), when the Strangler is hurt, when a wall comes between, at death, disconnection, a change
 * of dimension, consecrated light or the end of its hold; a shield turned toward the throw stops it reaching at all.
 * Eight seconds of recovery follow every release or miss; the freed victim is immune to tongues for six.
 */
public class StranglerEntity extends LimboDead {
    private static final EntityDataAccessor<Byte> DATA_TONGUE = SynchedEntityData.defineId(StranglerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3fc> DATA_TIP = SynchedEntityData.defineId(StranglerEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_VICTIM = SynchedEntityData.defineId(StranglerEntity.class, EntityDataSerializers.INT);
    public static final byte NONE = 0, PREPARE = 1, FLYING = 2, HOLDING = 3, RETRACT = 4;

    private int timer, cooldown = 40;
    private @Nullable LivingEntity victim;
    private @Nullable LivingEntity aimed;
    private Vec3 tip = Vec3.ZERO, dir = Vec3.ZERO;
    private double travelled;
    private final List<TongueSegment> segments = new ArrayList<>();

    public StranglerEntity(EntityType<? extends StranglerEntity> type, Level level) { super(type, level); xpReward = 14; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TONGUE, NONE);
        b.define(DATA_TIP, new Vector3f());
        b.define(DATA_VICTIM, -1);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_STRANGLER; }
    @Override protected double sightRange() { return 22; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 2.0; }
    @Override protected int windupTicks() { return 14; }
    @Override protected int attackRecovery() { return 28; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }
    /** It throws from a distance: it never holds up or climbs a mound. */
    @Override public boolean moundEligible() { return false; }

    public byte tongue() { return entityData.get(DATA_TONGUE); }
    public boolean tongueOut() { byte t = tongue(); return t == FLYING || t == HOLDING; }
    private void setTongue(byte t) { entityData.set(DATA_TONGUE, t); timer = 0; }

    /** Where the tongue leaves the body: the hanging jaw, a little in front of the face. */
    public Vec3 tongueStart(float partial) {
        float yaw = Mth.lerp(partial, yBodyRotO, yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 base = new Vec3(Mth.lerp(partial, xo, getX()), Mth.lerp(partial, yo, getY()), Mth.lerp(partial, zo, getZ()));
        return base.add(-Mth.sin(yaw) * 0.32, 1.38, Mth.cos(yaw) * 0.32);
    }

    /** Where the tongue ends: the victim's chest while it holds one, the flying tip otherwise; null when there is none. */
    public @Nullable Vec3 tongueEnd(float partial) {
        byte t = tongue();
        if (t == HOLDING && level().getEntity(entityData.get(DATA_VICTIM)) instanceof LivingEntity v) {
            return new Vec3(Mth.lerp(partial, v.xo, v.getX()), Mth.lerp(partial, v.yo, v.getY()) + v.getBbHeight() * 0.6, Mth.lerp(partial, v.zo, v.getZ()));
        }
        if (t == FLYING || t == RETRACT) { Vector3fc p = entityData.get(DATA_TIP); return new Vec3(p.x(), p.y(), p.z()); }
        return null;
    }

    private void syncTip() { entityData.set(DATA_TIP, new Vector3f((float) tip.x, (float) tip.y, (float) tip.z)); }

    // ------------------------------------------------------------------ the tongue
    /** The tongue runs every tick whatever the mind does, so a grip can never be left behind un-ticked. */
    @Override protected void customServerAiStep(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        switch (tongue()) {
            case PREPARE -> prepare(level);
            case FLYING -> fly(level);
            case HOLDING -> hold(level);
            case RETRACT -> retract();
            default -> {}
        }
        byte t = tongue();
        if (t == PREPARE || t == FLYING || t == HOLDING) { getNavigation().stop(); return; } // planted while it throws and pulls
        super.customServerAiStep(level);
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (cooldown == 0 && visible && distance > 3 && distance <= LimboTuning.TONGUE_RANGE && Grips.canGrab(q)
                && Consecration.at(level, q.position()) == null && clearLine(level, chest(q))) {
            aimed = q;
            getNavigation().stop();
            setTongue(PREPARE);
            playAnim(ANIM_SPECIAL, LimboTuning.TONGUE_PREPARE + 4);
            playSound(ModSounds.STRANGLER_GURGLE, 1.4f, 0.9f + random.nextFloat() * 0.15f);
            return true;
        }
        // It keeps its distance: in sight and within reach of the tongue it waits for the next throw instead of walking
        // into the reach of hands (close in, or out of sight, it walks as any dead).
        if (visible && distance > 5 && distance <= LimboTuning.TONGUE_RANGE - 2 && clearLine(level, chest(q))) {
            getNavigation().stop();
            getLookControl().setLookAt(q, 30f, 30f);
            return true;
        }
        return false;
    }

    private static Vec3 chest(LivingEntity e) { return e.position().add(0, e.getBbHeight() * 0.6, 0); }

    private boolean clearLine(ServerLevel level, Vec3 to) {
        return level.clip(new ClipContext(tongueStart(1f), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void prepare(ServerLevel level) {
        LivingEntity q = aimed;
        getNavigation().stop();
        if (q == null || !q.isAlive()) { fail(level); return; }
        getLookControl().setLookAt(q, 40f, 40f);
        if (++timer < LimboTuning.TONGUE_PREPARE) return;
        if (!getSensing().hasLineOfSight(q) || distanceTo(q) > LimboTuning.TONGUE_RANGE + 1 || !clearLine(level, chest(q))) { fail(level); return; }
        tip = tongueStart(1f);
        dir = chest(q).subtract(tip).normalize();
        travelled = 0;
        syncTip();
        setTongue(FLYING);
        playAnim(ANIM_SPECIAL, 60);
        playSound(ModSounds.STRANGLER_LASH, 1.5f, 1.0f);
        for (int i = 0; i < TongueSegment.COUNT; i++) {
            TongueSegment s = HellEntities.TONGUE_SEGMENT.create(level, EntitySpawnReason.EVENT);
            if (s == null) continue;
            s.setup(this, i);
            level.addFreshEntity(s);
            segments.add(s);
        }
    }

    /** The tongue travels: blocks stop it, the horde does not, the first living thing it meets is caught (or blocks it). */
    private void fly(ServerLevel level) {
        Vec3 next = tip.add(dir.scale(LimboTuning.TONGUE_SPEED));
        HitResult block = level.clip(new ClipContext(tip, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
        LivingEntity hit = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(tip, end).inflate(0.4), this::catchable)) {
            var clip = e.getBoundingBox().inflate(0.3).clip(tip, end);
            if (clip.isEmpty()) continue;
            double d = clip.get().distanceToSqr(tip);
            if (d < best) { best = d; hit = e; }
        }
        if (hit != null) {
            tip = chest(hit);
            syncTip();
            connect(level, hit);
            return;
        }
        tip = end;
        travelled += LimboTuning.TONGUE_SPEED;
        syncTip();
        if (block.getType() != HitResult.Type.MISS || travelled > LimboTuning.TONGUE_RANGE + 1 || Consecration.at(level, tip) != null) {
            level.playSound(null, net.minecraft.core.BlockPos.containing(tip), ModSounds.STRANGLER_REEL, SoundSource.HOSTILE, 1.0f, 1.2f);
            fail(level);
        }
    }

    private boolean catchable(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof LimboDead) && !Possession.isPossessed(e) && !e.isSpectator();
    }

    private void connect(ServerLevel level, LivingEntity v) {
        // A shield turned toward the throw stops the tongue reaching at all.
        DamageSource probe = level.damageSources().mobProjectile(segments.isEmpty() ? this : segments.getLast(), this);
        if (v.isBlocking() && v.applyItemBlocking(level, probe, 1f) > 0) {
            Grips.blocked++;
            level.playSound(null, v.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
            fail(level);
            return;
        }
        if (Consecration.at(level, v.position()) != null || !Grips.grab(v, this)) { fail(level); return; }
        victim = v;
        entityData.set(DATA_VICTIM, v.getId());
        setTongue(HOLDING);
        playAnim(ANIM_CALL, LimboTuning.TONGUE_HOLD);
        level.playSound(null, v.blockPosition(), ModSounds.STRANGLER_LASH, SoundSource.HOSTILE, 1.4f, 0.75f);
    }

    private void hold(ServerLevel level) {
        LivingEntity v = victim;
        getNavigation().stop();
        if (v == null || !v.isAlive() || v.isRemoved() || v.level() != level || v.isSpectator() || distanceTo(v) > LimboTuning.TONGUE_RANGE + 4
                || Consecration.at(level, v.position()) != null || !clearLine(level, chest(v)) || ++timer > LimboTuning.TONGUE_HOLD) {
            release(level);
            return;
        }
        getLookControl().setLookAt(v, 40f, 40f);
        if (timer % 20 == 0) v.hurtServer(level, level.damageSources().mobAttack(this), LimboTuning.TONGUE_DAMAGE);
        if (timer % 25 == 5) playSound(ModSounds.STRANGLER_REEL, 1.0f, 0.8f + random.nextFloat() * 0.2f);
        // The drag: toward the Strangler along the ground, never through blocks (the victim's own movement collides).
        Vec3 to = new Vec3(getX() - v.getX(), 0, getZ() - v.getZ());
        if (to.lengthSqr() > 1.4 * 1.4) {
            Vec3 pull = to.normalize().scale(LimboTuning.TONGUE_PULL);
            v.setDeltaMovement(pull.x, v.getDeltaMovement().y, pull.z);
            v.syncVelocity = true;
        }
    }

    private void retract() {
        Vec3 home = tongueStart(1f);
        Vec3 back = home.subtract(tip);
        tip = back.length() < 1.5 ? home : tip.add(back.normalize().scale(1.5));
        syncTip();
        if (++timer > 12 || tip == home) setTongue(NONE);
    }

    /** The grip ends (any reason): the victim is free and immune for a while, the tongue snaps back, eight seconds to recover. */
    private void release(ServerLevel level) {
        if (victim != null) {
            Grips.release(victim, this);
            tip = chest(victim);
        }
        victim = null;
        entityData.set(DATA_VICTIM, -1);
        fail(level);
    }

    private void fail(ServerLevel level) {
        clearSegments();
        aimed = null;
        cooldown = LimboTuning.TONGUE_COOLDOWN;
        syncTip();
        setTongue(tongue() == PREPARE || tongue() == NONE ? NONE : RETRACT);
    }

    /** A valid attack on the tongue cut it (TongueSegment). */
    public void sever(ServerLevel level) {
        if (!tongueOut()) return;
        Vec3 at = tongueEnd(1f) != null ? tongueStart(1f).add(tongueEnd(1f)).scale(0.5) : tongueStart(1f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), ModSounds.STRANGLER_SEVER, SoundSource.HOSTILE, 1.4f, 1.0f);
        level.sendParticles(ModParticles.BLOOD_EMBER, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.05);
        playAnim(ANIM_STAGGER, 20);
        release(level);
    }

    private void clearSegments() {
        for (TongueSegment s : segments) s.discard();
        segments.clear();
    }

    // ------------------------------------------------------------------ interruption and cleanup
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        byte t = tongue();
        boolean hurt = super.hurtServer(level, source, damage);
        // Any hurt to the captor breaks the grip; a hit during the windup costs it the throw.
        if (hurt && (t == HOLDING || t == FLYING || t == PREPARE)) release(level);
        return hurt;
    }

    @Override public void remove(RemovalReason reason) {
        // Death, discard, unloading, a change of dimension: the grip never outlives the Strangler's presence.
        if (victim != null) Grips.release(victim, this);
        victim = null;
        clearSegments();
        super.remove(reason);
    }

    @Override protected void toSearch(@Nullable Vec3 where) {
        if (tongue() != NONE && level() instanceof ServerLevel l) release(l);
        super.toSearch(where);
    }

    // ------------------------------------------------------------------ voice
    @Override protected SoundEvent recognizeSound() { return ModSounds.STRANGLER_GURGLE; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.STRANGLER_GURGLE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.STRANGLER_GURGLE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.85f + (random.nextFloat() - 0.5f) * 0.1f; }
}
