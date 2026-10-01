package ryuna.qdeath.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** A neutral blaze-shaped stalker which only attacks after being harmed. */
public class SwarmEyeEntity extends Monster implements RangedAttackMob {
    public SwarmEyeEntity(EntityType<? extends SwarmEyeEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 10, false);
        setNoGravity(true);
        this.xpReward = 10;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.monster.Blaze.createAttributes().add(Attributes.FLYING_SPEED, 0.35);
    }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(ModEntities.SWARM_EYE, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 50, 20.0f));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.45));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }
    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && getTarget() == null) {
            Player player = level().getNearestPlayer(this, 12.0);
            if (player != null) {
                getNavigation().moveTo(player, 0.35);
                getLookControl().setLookAt(player, 30.0f, 30.0f);
            }
        }
    }
    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (level().isClientSide()) return;
        Vec3 origin = position().add(0, getBbHeight() * .55, 0);
        Vec3 direction = target.getEyePosition().subtract(origin).normalize();
        SwarmArmProjectile arm = new SwarmArmProjectile(level(), this);
        arm.setPos(origin);
        arm.shoot(direction.x, direction.y, direction.z, 0.55f, 1.0f);
        level().addFreshEntity(arm);
    }
    @Override public boolean isOnFire() { return false; }
}
