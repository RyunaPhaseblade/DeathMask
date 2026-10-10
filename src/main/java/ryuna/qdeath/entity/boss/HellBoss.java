package ryuna.qdeath.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.hell.HellMonster;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellProgress;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Layer boss: boss bar, cinematic entrance (invulnerable while it plays), phases, an attack state machine,
 * a leash to its arena, and progression credit for every player who fought or stood in the arena.
 * Bosses that are left alone despawn, so the arena always resets and can be fought again.
 */
public abstract class HellBoss extends HellMonster {
    public static final int ANIM_ENTRANCE = 99;
    public static final double ARENA_RADIUS = 48;

    protected final ServerBossEvent bossBar;
    protected int entrance;
    protected @Nullable BlockPos home;
    protected int phase = 1;
    protected int attack;
    protected int attackTime;
    protected int attackLength;
    protected int attackCooldown = 40;
    private int lonely;
    private final Set<UUID> participants = new HashSet<>();

    protected HellBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        xpReward = 250;
        setPersistenceRequired();
    }

    public abstract HellLayer layer();
    protected int entranceLength() { return 80; }
    protected void onEntranceTick(ServerLevel level, int remaining) {}
    protected int phaseCount() { return 2; }
    protected void onPhaseChange(ServerLevel level, int newPhase) {}
    /** Pick and start an attack against {@code target} (call {@link #startAttack}). */
    protected abstract void chooseAttack(ServerLevel level, LivingEntity target, double distance);
    /** Run one tick of the current attack. {@code t} counts up from 1. */
    protected abstract void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t);
    /** Movement when not attacking. */
    protected void approach(LivingEntity target, double distance) {
        if (distance > 4.0) getNavigation().moveTo(target, 1.0);
        else getNavigation().stop();
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override protected boolean canBeElite() { return false; }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                                                  net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.world.entity.SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        // Spawn eggs play the full entrance where the egg was used (showcase).
        if (reason == net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE) beginEntrance(blockPosition());
        return result;
    }
    public boolean inEntrance() { return entrance > 0; }
    public int phase() { return phase; }

    public void beginEntrance(BlockPos home) {
        this.home = home;
        entrance = entranceLength();
        playAnim(ANIM_ENTRANCE, entrance);
    }

    protected void startAttack(int id, int length) {
        attack = id;
        attackTime = 0;
        attackLength = length;
        playAnim(id, length);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (entrance > 0) {
            entrance--;
            getNavigation().stop();
            onEntranceTick(level, entrance);
            if (entrance == 0) announce(level);
            return;
        }
        int newPhase = 1 + (int) ((1 - getHealth() / getMaxHealth()) * phaseCount());
        newPhase = Math.min(phaseCount(), newPhase);
        if (newPhase > phase) {
            phase = newPhase;
            onPhaseChange(level, phase);
        }
        BlockPos anchor = home != null ? home : blockPosition();
        if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > ARENA_RADIUS * ARENA_RADIUS) {
            teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 1, 1, 1, 0.05);
        }
        boolean anyone = !level.getEntitiesOfClass(ServerPlayer.class, new AABB(anchor).inflate(ARENA_RADIUS + 16), p -> !p.isSpectator() && !p.isCreative()).isEmpty();
        lonely = anyone ? 0 : lonely + 1;
        if (lonely > 1200) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 60, 1.5, 2, 1.5, 0.05);
            discard();
            return;
        }
        LivingEntity target = getTarget();
        if (attack != 0) {
            attackTime++;
            tickAttack(level, target, attack, attackTime);
            if (attackTime >= attackLength) {
                attack = 0;
                attackCooldown = Math.max(10, (phase >= 2 ? 25 : 40) - random.nextInt(15));
            }
        } else if (target != null && target.isAlive()) {
            double distance = distanceTo(target);
            if (--attackCooldown <= 0) chooseAttack(level, target, distance);
            if (attack == 0) approach(target, distance);
        }
    }

    private void announce(ServerLevel level) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ARENA_RADIUS + 16))) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(getType().getDescriptionId() + ".epithet").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            p.connection.send(new ClientboundSetTitleTextPacket(getDisplayName().copy().withStyle(layer().color, ChatFormatting.BOLD)));
        }
        level.playSound(null, blockPosition(), ModSounds.BOSS_ROAR, SoundSource.HOSTILE, 4.0f, 0.8f);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (entrance > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.getEntity() instanceof ServerPlayer player) participants.add(player.getUUID());
        return super.hurtServer(level, source, damage);
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            Vec3 center = home != null ? Vec3.atCenterOf(home) : position();
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                boolean present = p.level() == level && p.position().distanceTo(center) < ARENA_RADIUS + 16;
                if (!present && !participants.contains(p.getUUID())) continue;
                HellProgress.grantBossCredit(p, layer());
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.quantumdeath.boss_fallen.sub." + layer().id).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.quantumdeath.boss_fallen", getDisplayName()).withStyle(ChatFormatting.GOLD)));
            }
            level.playSound(null, blockPosition(), ModSounds.BOSS_DEFEATED, SoundSource.HOSTILE, 4.0f, 1.0f);
        }
        bossBar.removeAllPlayers();
    }

    @Override protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            if (deathTime % 4 == 0) level.sendParticles(ParticleTypes.SOUL, getRandomX(1), getRandomY(), getRandomZ(1), 6, 0.5, 0.5, 0.5, 0.05);
            if (deathTime % 10 == 0) level.sendParticles(ParticleTypes.EXPLOSION, getRandomX(1), getRandomY(), getRandomZ(1), 1, 0, 0, 0, 0);
            if (deathTime >= 60 && !isRemoved()) {
                level.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void setCustomName(@Nullable Component name) { super.setCustomName(name); bossBar.setName(getDisplayName()); }
    @Override public void checkDespawn() {}
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected boolean canRide(Entity vehicle) { return false; }
    @Override public boolean canUsePortal(boolean force) { return false; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Phase", phase);
        out.putInt("Entrance", entrance);
        if (home != null) out.putLong("Home", home.asLong());
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        phase = in.getIntOr("Phase", 1);
        entrance = in.getIntOr("Entrance", 0);
        long h = in.getLongOr("Home", Long.MIN_VALUE);
        home = h == Long.MIN_VALUE ? null : BlockPos.of(h);
        bossBar.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ attack helpers
    protected void hurtAround(ServerLevel level, Vec3 center, double radius, float damage, double knock) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), e -> e != this && !(e instanceof HellBoss) && e.isAlive())) {
            if (e.position().distanceTo(center) > radius) continue;
            e.hurtServer(level, damageSources().mobAttack(this), damage);
            Vec3 push = e.position().subtract(center).normalize().scale(knock);
            e.push(push.x, 0.35 * knock, push.z);
            e.syncVelocity = true;
        }
    }

    protected void faceTarget(LivingEntity target) {
        getLookControl().setLookAt(target, 30f, 30f);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }
}
