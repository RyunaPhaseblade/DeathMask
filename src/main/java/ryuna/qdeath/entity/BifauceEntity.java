package ryuna.qdeath.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import java.util.EnumSet;

public class BifauceEntity extends Monster {
    public BifauceEntity(EntityType<? extends BifauceEntity> type, Level level) { super(type, level); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() { return net.minecraft.world.entity.monster.Enderman.createAttributes(); }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.BIFAUCE, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BackwardsPursuitGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 32.0f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, true));
    }
    private class BackwardsPursuitGoal extends Goal {
        BackwardsPursuitGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        // Leave movement control to MeleeAttackGoal when close enough to strike.
        @Override public boolean canUse() { return getTarget() != null && getTarget().isAlive() && distanceToSqr(getTarget()) > 6.25; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            LivingEntity target = getTarget(); if (target == null) return;
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            float yaw = (float)(Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0f;
            setYRot(yaw); yBodyRot = getYRot(); yHeadRot = getYRot();
            getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }
    }
}
