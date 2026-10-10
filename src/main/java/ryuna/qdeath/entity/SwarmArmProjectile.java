package ryuna.qdeath.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The Swarm Eye's shot (id kept as "swarm_arm" for old saves): one of its small eyes (3D model: SwarmEyeShotRenderer). */
public class SwarmArmProjectile extends ThrowableProjectile {
    private float damage = 3.0f;

    public SwarmArmProjectile(EntityType<? extends SwarmArmProjectile> type, Level level) { super(type, level); }
    public SwarmArmProjectile(Level level, LivingEntity owner) { this(ModEntities.SWARM_ARM, level); setOwner(owner); }
    public void setDamage(float damage) { this.damage = damage; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return 0.0; }
    @Override protected boolean canHitEntity(Entity target) { return target != getOwner() && super.canHitEntity(target); }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide() && hit.getEntity() instanceof LivingEntity living) {
            LivingEntity owner = getOwner() instanceof LivingEntity entity ? entity : null;
            living.hurt(owner == null ? damageSources().generic() : damageSources().mobProjectile(this, owner), damage);
        }
        discard();
    }
    @Override protected void onHit(net.minecraft.world.phys.HitResult hit) { super.onHit(hit); if (!level().isClientSide()) discard(); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 100) discard();
    }
    @Override public boolean isPickable() { return true; }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putFloat("Damage", damage); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); damage = in.getFloatOr("Damage", 3.0f); }
}
