package ryuna.qdeath.entity.limbo;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * What a Putrescent leaves when it dies: a physical corpse, not a creature. It swells, spasms and gurgles louder for a
 * few seconds, then bursts once. The burst hurts everything alive and every dead within a few blocks, less with
 * distance and much less behind cover; it breaks no blocks. Hitting the corpse sets it off at once (so it can be
 * detonated inside a crowd). It drops nothing. Fuse and state are saved: reloading never restarts the count or
 * lets it burst twice. After bursting it lies as residue for a moment, then is gone.
 */
public class BloaterCorpse extends Entity {
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(BloaterCorpse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX = SynchedEntityData.defineId(BloaterCorpse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(BloaterCorpse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BURST = SynchedEntityData.defineId(BloaterCorpse.class, EntityDataSerializers.BOOLEAN);
    private int residue;

    public BloaterCorpse(EntityType<? extends BloaterCorpse> type, Level level) { super(type, level); }

    public void setup(int fuse, int skin, float yaw) {
        entityData.set(DATA_FUSE, fuse);
        entityData.set(DATA_MAX, fuse);
        entityData.set(DATA_SKIN, skin);
        setYRot(yaw);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_FUSE, LimboTuning.CORPSE_FUSE_MIN);
        b.define(DATA_MAX, LimboTuning.CORPSE_FUSE_MIN);
        b.define(DATA_SKIN, 0);
        b.define(DATA_BURST, false);
    }

    public int fuse() { return entityData.get(DATA_FUSE); }
    public int skin() { return entityData.get(DATA_SKIN); }
    public boolean burst() { return entityData.get(DATA_BURST); }
    /** 0 when it falls, 1 when it bursts (client: interpolated). */
    public float progress(float partial) {
        int max = Math.max(1, entityData.get(DATA_MAX));
        return Math.min(1f, (max - fuse() + partial) / max);
    }

    @Override public void tick() {
        super.tick();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5));
        if (!(level() instanceof ServerLevel level)) {
            if (!burst() && random.nextInt(Math.max(1, 6 - (int) (progress(0) * 5))) == 0)
                level().addParticle(ModParticles.BILE_DRIP, getRandomX(0.6), getY() + 0.3, getRandomZ(0.6), 0, 0.02, 0);
            return;
        }
        if (burst()) {
            if (++residue > LimboTuning.CORPSE_RESIDUE) discard();
            return;
        }
        int f = fuse() - 1;
        entityData.set(DATA_FUSE, f);
        // The gurgle comes faster and higher as the burst nears: the warning to get away.
        float p = progress(0);
        int every = p < 0.5f ? 16 : p < 0.8f ? 8 : 4;
        if (f % every == 0) level.playSound(null, blockPosition(), ModSounds.BLOATER_SWELL, SoundSource.HOSTILE, 0.8f + p, 0.8f + 0.7f * p);
        if (f <= 0) detonate(level);
    }

    /** The single burst: damage falling off with distance, cut by cover, to the living and the dead alike. */
    public void detonate(ServerLevel level) {
        if (burst()) return;
        ryuna.qdeath.limbo.LimboStats.bursts++;
        entityData.set(DATA_BURST, true);
        entityData.set(DATA_FUSE, 0);
        Vec3 c = position().add(0, 0.4, 0);
        double r = LimboTuning.CORPSE_RADIUS;
        level.playSound(null, blockPosition(), ModSounds.BLOATER_BURST, SoundSource.HOSTILE, 2.2f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.BILE_DRIP, c.x, c.y + 0.4, c.z, 60, 1.4, 0.6, 1.4, 0.15);
        level.sendParticles(ModParticles.BLOOD_EMBER, c.x, c.y + 0.4, c.z, 25, 1.2, 0.5, 1.2, 0.05);
        DamageSource src = level.damageSources().explosion(this, null);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r))) {
            double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c);
            if (d > r || !e.isAlive()) continue;
            float base = e instanceof LimboDead ? LimboTuning.CORPSE_DAMAGE_DEAD : LimboTuning.CORPSE_DAMAGE;
            float k = (float) Math.pow(1 - d / r, 0.75) * (float) cover(level, c, e, this);
            if (k <= 0.02f) continue;
            e.hurtServer(level, src, base * k);
            Vec3 push = e.position().subtract(c).normalize().scale(0.6 * k);
            e.push(push.x, 0.25 * k, push.z);
        }
    }

    /** 1 in the open, 0.55 when half the body is shielded, 0.2 behind full cover (also used by the Breach Ticker's burst). */
    public static double cover(ServerLevel level, Vec3 from, LivingEntity e, Entity source) {
        int clear = 0;
        for (double h : new double[]{0.15, e.getBbHeight() * 0.5, e.getBbHeight() * 0.9}) {
            Vec3 to = e.position().add(0, h, 0);
            if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS) clear++;
        }
        return clear == 3 ? 1.0 : clear >= 1 ? 0.55 : 0.2;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // Struck or shot: it bursts now, where it lies.
        if (!burst() && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) { detonate(level); return true; }
        return false;
    }

    @Override public boolean isPickable() { return !burst(); }
    @Override public boolean isAttackable() { return !burst(); }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Fuse", fuse());
        out.putInt("MaxFuse", entityData.get(DATA_MAX));
        out.putInt("Skin", skin());
        out.putBoolean("Burst", burst());
        out.putInt("Residue", residue);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(DATA_FUSE, in.getIntOr("Fuse", LimboTuning.CORPSE_FUSE_MIN));
        entityData.set(DATA_MAX, in.getIntOr("MaxFuse", LimboTuning.CORPSE_FUSE_MIN));
        entityData.set(DATA_SKIN, in.getIntOr("Skin", 0));
        entityData.set(DATA_BURST, in.getBooleanOr("Burst", false));
        residue = in.getIntOr("Residue", 0);
    }
}
