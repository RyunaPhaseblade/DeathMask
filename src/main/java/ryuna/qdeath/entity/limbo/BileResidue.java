package ryuna.qdeath.entity.limbo;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ryuna.qdeath.entity.projectile.BodyProjectile;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.registry.ModParticles;

/**
 * A puddle of bile where a bolus burst: for three seconds, anything alive standing in it (1.5 blocks) is slowed, the
 * slowness only topped up, never stacked. Not a block, harmless to terrain, and never saved: it is gone if the area
 * unloads.
 */
public class BileResidue extends Entity {
    public BileResidue(EntityType<? extends BileResidue> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            if (random.nextInt(4) == 0) level().addParticle(ModParticles.BILE_DRIP, getRandomX(1.2), getY() + 0.05, getRandomZ(1.2), 0, 0.01, 0);
            return;
        }
        if (tickCount > LimboTuning.RESIDUE_LIFE) { discard(); return; }
        if (tickCount % 5 != 0) return;
        double r = LimboTuning.RESIDUE_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 0.6, r))) {
            if (e instanceof LimboDead || Possession.isPossessed(e) || !e.isAlive()) continue;
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz <= r * r && Math.abs(e.getY() - getY()) < 0.7) BodyProjectile.slow(e, 10);
        }
    }

    /** 0 -> 1 over its life (the renderer shrinks and darkens it). */
    public float age(float partial) { return Math.min(1f, (tickCount + partial) / LimboTuning.RESIDUE_LIFE); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput in) {}
    @Override protected void addAdditionalSaveData(ValueOutput out) {}
}
