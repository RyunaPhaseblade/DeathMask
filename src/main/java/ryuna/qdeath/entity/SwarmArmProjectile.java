package ryuna.qdeath.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SwarmArmProjectile extends ThrowableProjectile implements ItemSupplier {
    public SwarmArmProjectile(EntityType<? extends SwarmArmProjectile> type, Level level) { super(type, level); }
    public SwarmArmProjectile(Level level, LivingEntity owner) { this(ModEntities.SWARM_ARM, level); setOwner(owner); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return 0.0; }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide() && hit.getEntity() instanceof LivingEntity living) {
            LivingEntity owner = getOwner() instanceof LivingEntity entity ? entity : null;
            living.hurt(owner == null ? damageSources().generic() : damageSources().mobProjectile(this, owner), 6.0f);
        }
        discard();
    }
    @Override protected void onHit(net.minecraft.world.phys.HitResult hit) { super.onHit(hit); if (!level().isClientSide()) discard(); }
    @Override public boolean isPickable() { return true; }
    @Override public ItemStack getItem() { return new ItemStack(Items.BLAZE_ROD); }
}
