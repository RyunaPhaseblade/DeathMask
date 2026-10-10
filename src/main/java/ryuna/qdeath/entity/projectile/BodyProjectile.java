package ryuna.qdeath.entity.projectile;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.registry.ModParticles;

/**
 * What the shooters of the Limbo throw: pieces of their own bodies (a bolus of bile and tissue, a torn rib). Plain
 * ballistics without air drag, so the thrower's aim is exact; blocks stop them (cover works), the horde is never hit,
 * and consecrated light swallows them at its edge.
 */
public abstract class BodyProjectile extends ThrowableProjectile {
    protected BodyProjectile(EntityType<? extends BodyProjectile> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected float getAirDrag() { return 1f; }

    /** Initial velocity that lands on {@code to} after {@code ticks} ticks under {@code gravity} (no drag). */
    public static Vec3 launch(Vec3 from, Vec3 to, int ticks, double gravity) {
        Vec3 d = to.subtract(from);
        return new Vec3(d.x / ticks, (d.y + gravity * ticks * (ticks + 1) / 2.0) / ticks, d.z / ticks);
    }

    /** The points the projectile passes through: the shooter checks them for cover and for its own kind in the way. */
    public static Vec3[] arc(Vec3 from, Vec3 v, int ticks, double gravity, int samples) {
        Vec3[] out = new Vec3[samples + 1];
        for (int i = 0; i <= samples; i++) {
            int k = Math.round(ticks * i / (float) samples);
            out[i] = from.add(v.x * k, v.y * k - gravity * k * (k + 1) / 2.0, v.z * k);
        }
        return out;
    }

    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (tickCount > 100) discard();
            else if (Consecration.at(level, position()) != null) {
                level.sendParticles(ModParticles.VOTIVE_SPARK, getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.02);
                discard();
            }
        } else if (tickCount > 1) trail();
    }

    protected abstract void trail();

    @Override protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != getOwner() && !(target instanceof LimboDead) && !Possession.isPossessed(target);
    }

    /** Slowness I that never grows in level and never adds up in time: it is only topped up to {@code ticks}. */
    public static void slow(LivingEntity e, int ticks) {
        MobEffectInstance cur = e.getEffect(MobEffects.SLOWNESS);
        if (cur == null || (cur.getAmplifier() == 0 && cur.getDuration() < ticks && !cur.isInfiniteDuration()))
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0));
    }
}
