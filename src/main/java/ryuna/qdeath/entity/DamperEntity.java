package ryuna.qdeath.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiCombat;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Damper (Apagador). In Hell: its original role. In the Overworld's caves it mimics steps, mining and block placing,
 * and turns on anyone carrying a light in either hand (the configurable item tag {@code quantumdeath:damper_lights}),
 * whatever their perception: a creak for 2 s warns before the first blow (5 damage, one every 2 s). Putting the light
 * away calms it after 5 s, unless it was attacked. It still puts out one reachable torch every 10 s (mobGriefing).
 */
public class DamperEntity extends Monster implements ryuna.qdeath.entity.hell.HellMob, YokaiCreature {
    public static final TagKey<Item> LIGHTS = TagKey.create(Registries.ITEM, Quantumdeath.id("damper_lights"));
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(DamperEntity.class, EntityDataSerializers.INT);
    private static final int TORCH_ACTION_INTERVAL = 20 * 10;
    private final YokaiCore core = new YokaiCore(Species.DAMPER, this);
    private int torchActionCooldown = TORCH_ACTION_INTERVAL;
    private BlockPos torchTarget;
    /** Overworld provocation by the player it is after: last time a light was seen, warning left, whether it was attacked. */
    private @Nullable UUID quarry;
    private long lastLight;
    private int warning, hitCooldown, mimicCooldown = 200;
    private final Set<UUID> warned = new HashSet<>();
    private final Set<UUID> aggressors = new HashSet<>();

    public DamperEntity(EntityType<? extends DamperEntity> type, Level level) { super(type, level); this.xpReward = 5; }
    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.monster.Enderman.createAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Damper.HEALTH);
    }
    @Override public YokaiCore yokai() { return core; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ANIM, 0);
    }
    /** Client: 1 while creaking (warning), 2 for a blow. */
    public int pose() { return entityData.get(DATA_ANIM); }

    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && Monster.checkMonsterSpawnRules(ModEntities.DAMPER, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) { @Override public boolean canUse() { return Perception.exempt(DamperEntity.this) && super.canUse(); } });
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) { @Override public boolean canUse() { return Perception.exempt(DamperEntity.this) && super.canUse(); } });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (candidate, server) -> Perception.exempt(this) && holdsTorch((Player) candidate) && hasLineOfSight(candidate)));
    }
    /** Hell keeps its original rule: torches in hand. */
    private static boolean holdsTorch(Player player) {
        for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()})
            if (s.is(net.minecraft.world.item.Items.TORCH) || s.is(net.minecraft.world.item.Items.SOUL_TORCH) || s.is(net.minecraft.world.item.Items.REDSTONE_TORCH)) return true;
        return false;
    }
    public static boolean holdsLight(Player player) { return isLight(player.getMainHandItem()) || isLight(player.getOffhandItem()); }
    private static boolean isLight(ItemStack stack) { return stack.is(LIGHTS); }
    @Override public void aiStep() {
        super.aiStep();
        // World changes and sounds are authoritative server logic; client still runs the base step for animation.
        if (level().isClientSide()) return;
        if (--torchActionCooldown <= 0) {
            torchActionCooldown = TORCH_ACTION_INTERVAL;
            dampReachableLight();
        }
        pursueAndBreakTorch();
        if (Perception.exempt(this)) {
            if (random.nextInt(320) == 0) playAmbientImitation(null);
            return;
        }
        if (!YokaiHooks.serverTick(this, core)) return;
        overworld((ServerLevel) level());
    }

    // ------------------------------------------------------------------------------------------------ Overworld

    private void overworld(ServerLevel level) {
        if (hitCooldown > 0) hitCooldown--;
        long now = level.getGameTime();
        ServerPlayer target = quarry == null ? null : level.getServer().getPlayerList().getPlayer(quarry);
        if (target != null && (target.level() != level || !Perception.targetable(target))) target = null;
        if (target == null) {
            quarry = null;
            entityData.set(DATA_ANIM, 0);
            if (tickCount % 10 == 0 && !core.calmed()) {
                for (ServerPlayer p : level.players()) {
                    if (Perception.targetable(p) && p.distanceToSqr(this) < 16 * 16 && holdsLight(p) && hasLineOfSight(p)) {
                        quarry = p.getUUID();
                        lastLight = now;
                        core.detect(p);
                        break;
                    }
                }
            }
            if (--mimicCooldown <= 0) {
                mimicCooldown = 200 + random.nextInt(300);
                if (level.getNearestPlayer(this, 24) instanceof ServerPlayer near) playAmbientImitation(near);
            }
            return;
        }
        setTarget(target);
        if (holdsLight(target)) lastLight = now;
        // Light put away: it gives up after 5 s, unless that player attacked it.
        if (now - lastLight > YokaiBalance.Damper.GIVE_UP && !aggressors.contains(target.getUUID())) {
            quarry = null;
            setTarget(null);
            warned.remove(target.getUUID());
            entityData.set(DATA_ANIM, 0);
            return;
        }
        if (!warned.contains(target.getUUID())) {
            if (warning == 0) {
                warning = YokaiBalance.Damper.WARNING;
                entityData.set(DATA_ANIM, 1);
                playSound(ModSounds.DAMPER_CREAK, 1.3f, 1.0f);
                Bestiary.clueNear(level, position(), 16, species(), "creak");
            }
            getNavigation().stop();
            getLookControl().setLookAt(target);
            if (--warning == 0) {
                warned.add(target.getUUID());
                entityData.set(DATA_ANIM, 0);
            }
            return;
        }
        getNavigation().moveTo(target, 1.15);
        if (distanceToSqr(target) < 4.5 && hitCooldown == 0) {
            hitCooldown = YokaiBalance.Damper.HIT_INTERVAL;
            entityData.set(DATA_ANIM, 2);
            playSound(ModSounds.DAMPER_STRIKE, 1.0f, 1.0f);
            target.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Damper.DAMAGE);
        } else if (hitCooldown == YokaiBalance.Damper.HIT_INTERVAL - 10) entityData.set(DATA_ANIM, 0);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !Perception.exempt(this) && source.getEntity() instanceof ServerPlayer p) {
            aggressors.add(p.getUUID());
            quarry = p.getUUID();
            warned.add(p.getUUID());
            lastLight = level.getGameTime();
        }
        return hurt;
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
        if (torchTarget == null || quarry != null) return;
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
        if (!Perception.exempt(this)) Bestiary.clueNear((ServerLevel) level(), Vec3.atCenterOf(torchTarget), 16, species(), "torch_out");
        torchTarget = null;
    }

    /** Steps, mining and a block being placed, where nobody is working. */
    private void playAmbientImitation(@Nullable ServerPlayer witness) {
        var sound = switch (random.nextInt(5)) { case 0 -> SoundEvents.STONE_BREAK; case 1 -> SoundEvents.STONE_STEP; case 2 -> SoundEvents.DEEPSLATE_HIT; case 3 -> SoundEvents.STONE_PLACE; default -> SoundEvents.DEEPSLATE_BREAK; };
        playSound(sound, .7f, .75f + random.nextFloat() * .35f);
        if (witness != null && witness.distanceToSqr(this) < 24 * 24) Bestiary.clue(witness, species(), "mimic_work");
    }

    @Override public boolean isPickable() { return super.isPickable() && YokaiHooks.pickable(this); }
    @Override public boolean ignoreExplosion(Explosion explosion) { return YokaiCombat.ignoresExplosion(this, explosion) || super.ignoreExplosion(explosion); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); core.save(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); core.load(in); }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return Perception.exempt(this) ? super.getHurtSound(s) : ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return Perception.exempt(this) ? super.getDeathSound() : ModSounds.YOKAI_DEATH; }
    @Override protected SoundEvent getAmbientSound() { return Perception.exempt(this) ? super.getAmbientSound() : null; }
}
