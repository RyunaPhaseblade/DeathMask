package ryuna.qdeath.entity.hell;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModSounds;

/**
 * Burning heretics of Dis, risen from their open tombs. They keep their distance and call fire up from the ground
 * beneath you (watch for the smoke). One sleeps in the Ossuary's sealed sarcophagus: the Unburied Inquisitor.
 */
public class HereticEntity extends HellMonster {
    public static final int ANIM_PYRE = 1, ANIM_THROW = 2;
    private static final EntityDataAccessor<Boolean> DATA_INQUISITOR = SynchedEntityData.defineId(HereticEntity.class, EntityDataSerializers.BOOLEAN);
    private int pyreCooldown = 50, throwCooldown = 30, chainCooldown = 80;
    private boolean calledChoir;
    private @Nullable ServerBossEvent bar;

    public HereticEntity(EntityType<? extends HereticEntity> type, Level level) { super(type, level); xpReward = 10; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_INQUISITOR, false); }
    public boolean isInquisitor() { return entityData.get(DATA_INQUISITOR); }

    /** Turns this heretic into the Ossuary miniboss. */
    public void makeInquisitor() {
        entityData.set(DATA_INQUISITOR, true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(180);
        getAttribute(Attributes.SCALE).setBaseValue(1.5);
        getAttribute(Attributes.ARMOR).setBaseValue(8);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.quantumdeath.unburied_inquisitor").withStyle(ChatFormatting.GOLD));
        setPersistenceRequired();
        xpReward = 80;
    }

    @Override protected boolean canBeElite() { return !isInquisitor(); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isInquisitor()) tickBar(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        getLookControl().setLookAt(target, 30, 30);
        double d = distanceTo(target);
        // Keep the preferred casting range.
        if (d < 7 && getNavigation().isDone()) {
            Vec3 away = DefaultRandomPos.getPosAway(this, 10, 5, target.position());
            if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.2);
        } else if (d > 15) getNavigation().moveTo(target, 1.0);
        boolean sight = hasLineOfSight(target);
        if (--pyreCooldown <= 0 && d < 22) {
            pyreCooldown = isInquisitor() ? 45 : 80 + random.nextInt(30);
            playAnim(ANIM_PYRE, 22);
            playSound(ModSounds.HERETIC_CHANT, 1.0f, isInquisitor() ? 0.7f : 1.0f);
            HellBolt.fireColumn(level, this, target.blockPosition(), isInquisitor() ? 9f : 6f);
            if (isInquisitor()) for (int i = 0; i < 3; i++)
                HellBolt.fireColumn(level, this, target.blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), 7f);
        }
        if (sight && --throwCooldown <= 0 && d < 16) {
            throwCooldown = 50 + random.nextInt(30);
            playAnim(ANIM_THROW, 12);
            HellBolt.shootAt(level, this, HellBolt.Kind.EMBER, getEyePosition(), target.getEyePosition(), 1.1f, 3f);
        }
        if (isInquisitor() && sight && --chainCooldown <= 0) {
            chainCooldown = 100;
            HellBolt.shootAt(level, this, HellBolt.Kind.CHAIN, getEyePosition(), target.getEyePosition(), 1.6f, 1f);
        }
        if (isInquisitor() && !calledChoir && getHealth() < getMaxHealth() / 2) {
            calledChoir = true;
            for (int i = 0; i < 2; i++) {
                HereticEntity h = HellEntities.HERETIC.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (h == null) continue;
                h.snapTo(getX() + random.nextInt(5) - 2, getY(), getZ() + random.nextInt(5) - 2, 0, 0);
                h.setTarget(target);
                level.addFreshEntity(h);
            }
        }
    }

    private void tickBar(ServerLevel level) {
        if (bar == null) bar = new ServerBossEvent(java.util.UUID.randomUUID(), getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
        bar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 20 == 0) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) bar.addPlayer(p);
            for (ServerPlayer p : java.util.List.copyOf(bar.getPlayers())) if (p.distanceTo(this) > 40) bar.removePlayer(p);
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (bar != null) bar.removeAllPlayers();
        super.remove(reason);
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (isInquisitor()) {
            spawnAtLocation(level, new ItemStack(HellItems.OSSUARY_KEY, 2));
            spawnAtLocation(level, new ItemStack(HellItems.HERETIC_EMBER, 4));
            spawnAtLocation(level, new ItemStack(HellItems.JOURNAL_PAGE));
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); out.putBoolean("Inquisitor", isInquisitor()); }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        if (in.getBooleanOr("Inquisitor", false)) entityData.set(DATA_INQUISITOR, true);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.HERETIC_CHANT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HERETIC_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public boolean removeWhenFarAway(double d) { return !isInquisitor() && super.removeWhenFarAway(d); }
}
