package ryuna.qdeath.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

public class DamperEntity extends Monster {
    private static final int TORCH_ACTION_INTERVAL = 20 * 10;
    private int torchActionCooldown = TORCH_ACTION_INTERVAL;
    private BlockPos torchTarget;
    public DamperEntity(EntityType<? extends DamperEntity> type, Level level) { super(type, level); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() { return net.minecraft.world.entity.monster.Enderman.createAttributes(); }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.DAMPER, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (candidate, server) -> holdsTorchAndVisible((Player) candidate)));
    }
    private boolean holdsTorchAndVisible(Player player) {
        return hasLineOfSight(player) && (isTorch(player.getMainHandItem()) || isTorch(player.getOffhandItem()));
    }
    private boolean isTorch(ItemStack stack) { return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.REDSTONE_TORCH); }
    @Override public void aiStep() {
        super.aiStep();
        // World changes and sounds are authoritative server logic; client still runs the base step for animation.
        if (level().isClientSide()) return;
        if (--torchActionCooldown <= 0) {
            torchActionCooldown = TORCH_ACTION_INTERVAL;
            dampReachableLight();
        }
        pursueAndBreakTorch();
        if (random.nextInt(320) == 0) playAmbientImitation();
    }
    /** Attempts one light-removal action every ten seconds, only when a path actually reaches it. */
    private void dampReachableLight() {
        if (!((ServerLevel) level()).getServer().getGameRules().get(GameRules.MOB_GRIEFING)) return;
        BlockPos center = blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            var state = level().getBlockState(pos);
            boolean torch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
            if (!torch) continue;
            double distance = pos.distSqr(center);
            if (distance >= nearestDistance) continue;
            Path path = getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) { nearestDistance = distance; nearest = pos.immutable(); }
        }
        torchTarget = nearest;
    }

    private void pursueAndBreakTorch() {
        if (torchTarget == null) return;
        var state = level().getBlockState(torchTarget);
        boolean torch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
        if (!torch) { torchTarget = null; return; }
        double x = torchTarget.getX() + .5, y = torchTarget.getY() + .5, z = torchTarget.getZ() + .5;
        if (distanceToSqr(x, y, z) > 4.0) {
            getNavigation().moveTo(x, torchTarget.getY(), z, 1.15);
            return;
        }
        level().playSound(null, torchTarget, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level().destroyBlock(torchTarget, true, this);
        torchTarget = null;
    }
    private void playAmbientImitation() {
        var sound = switch (random.nextInt(5)) { case 0 -> SoundEvents.STONE_BREAK; case 1 -> SoundEvents.STONE_STEP; case 2 -> SoundEvents.DEEPSLATE_HIT; case 3 -> SoundEvents.DEEPSLATE_BREAK; default -> SoundEvents.PARROT_IMITATE_ZOMBIE; };
        playSound(sound, .7f, .75f + random.nextFloat() * .35f);
    }
}
