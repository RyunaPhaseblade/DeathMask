package ryuna.qdeath.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
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
import net.minecraft.world.phys.Vec3;

public class FakeCreeperEntity extends Creeper {
    private int detonationTicks;
    public FakeCreeperEntity(EntityType<? extends FakeCreeperEntity> type, Level level) { super(type, level); this.noPhysics = true; setNoGravity(true); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes().add(Attributes.FOLLOW_RANGE, 32.0);
    }
    @Override protected void registerGoals() {
        // Detect by radius only: neither sight nor a navigable ground path is required.
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.FAKE_CREEPER, server, reason, blockPosition(), random);
    }
    @Override public void tick() {
        noPhysics = true;
        setNoGravity(true);
        LivingEntity target = getTarget();
        if (!level().isClientSide() && target != null && target.isAlive()) {
            Vec3 direction = target.getEyePosition().subtract(getEyePosition());
            if (direction.lengthSqr() > .1) {
                setDeltaMovement(direction.normalize().scale(.12));
                float yaw = (float)(Math.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0f;
                float pitch = (float)-(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI));
                setYRot(yaw); setYHeadRot(yaw); setXRot(pitch);
            }
        }
        super.tick();
        if (!level().isClientSide()) {
            if (target != null && target.isAlive() && distanceToSqr(target) < 9.0) detonationTicks++;
            else detonationTicks = Math.max(0, detonationTicks - 2);
            if (detonationTicks >= 30) {
                level().explode(this, getX(), getY(), getZ(), 3.0f, false, Level.ExplosionInteraction.NONE);
                discard();
            }
        }
    }
    /** Disable Creeper's private explosion path; this mob uses the entity-only explosion above. */
    @Override public int getSwellDir() { return -1; }
    @Override public void setSwellDir(int state) {}
    @Override protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
}
