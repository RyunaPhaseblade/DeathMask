package ryuna.qdeath.entity.vigil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.block.PuzzleBlocks;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.entity.hell.EchoEntity;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellScheduler;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VIGIL, THE UNENDING WAKE. The final encounter at the Last Vigil.
 * <ol>
 * <li>The Mourner: tolling shockwaves, ash hands from below, blinking behind the farthest of you, echoes.</li>
 * <li>The Face Beneath: the mask breaks. A gaze that burns along its line of sight (hide behind pillars),
 *     chains, and the arena floor begins to forget itself.</li>
 * <li>Zenith: it draws a veil and cannot be hurt. Ring all four bells of the Last Vigil to tear it away
 *     for fifteen seconds. Then the bells fall silent and must be rung again.</li>
 * </ol>
 */
public class VigilWakeEntity extends HellBoss {
    public static final int TOLL = 1, HANDS = 2, BLINK = 3, ECHOES = 4, GAZE = 5, CHAINS = 6;
    private static final EntityDataAccessor<Boolean> DATA_MASK_BROKEN = SynchedEntityData.defineId(VigilWakeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_VEILED = SynchedEntityData.defineId(VigilWakeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_GAZE_YAW = SynchedEntityData.defineId(VigilWakeEntity.class, EntityDataSerializers.FLOAT);

    private Vec3 entranceStart = Vec3.ZERO;
    private final Set<BlockPos> rung = new HashSet<>();
    private int firstRing = -1;
    private int unveiled;
    private int stunned;
    private List<BlockPos> handSpots = List.of();
    private Vec3 gazeDir = Vec3.ZERO;

    public VigilWakeEntity(EntityType<? extends Monster> type, Level level) { super(type, level, BossEvent.BossBarColor.WHITE); xpReward = 1000; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 900).add(Attributes.ATTACK_DAMAGE, 18).add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 96).add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_MASK_BROKEN, false);
        b.define(DATA_VEILED, false);
        b.define(DATA_GAZE_YAW, 0f);
    }

    public boolean maskBroken() { return entityData.get(DATA_MASK_BROKEN); }
    public boolean veiled() { return entityData.get(DATA_VEILED); }

    @Override public HellLayer layer() { return HellLayer.ZENITH; }
    @Override protected int entranceLength() { return 160; }
    @Override protected int phaseCount() { return 3; }

    @Override public void beginEntrance(BlockPos home) {
        super.beginEntrance(home);
        entranceStart = Vec3.atBottomCenterOf(home).add(0, 0, -34);
        setPos(entranceStart);
    }

    @Override protected void onEntranceTick(ServerLevel level, int remaining) {
        if (home == null) return;
        float p = 1f - (float) remaining / entranceLength();
        Vec3 at = entranceStart.lerp(Vec3.atBottomCenterOf(home), p);
        setPos(at.x, home.getY(), at.z);
        setYRot(0);
        yBodyRot = 0;
        if (remaining % 40 == 0) level.playSound(null, home, ModSounds.VIGIL_TOLL, SoundSource.HOSTILE, 6f, 0.7f);
        if (remaining % 12 == 0) level.playSound(null, blockPosition(), ModSounds.VIGIL_STEP, SoundSource.HOSTILE, 3f, 0.6f);
        level.sendParticles(ModParticles.VIGIL_MOTE, getX(), getY() + 3, getZ(), 6, 1, 3, 1, 0.01);
        if (remaining == 1) {
            for (ServerPlayer pl : arenaPlayers(level)) {
                pl.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
                pl.connection.send(new ClientboundSoundPacket(ModSounds.MUSIC_VIGIL, SoundSource.MUSIC, pl.getX(), pl.getY(), pl.getZ(), 1f, 1f, 0));
            }
        }
    }

    private List<ServerPlayer> arenaPlayers(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ARENA_RADIUS + 16), p -> !p.isSpectator());
    }

    @Override protected void onPhaseChange(ServerLevel level, int newPhase) {
        attack = 0;
        if (newPhase == 2) {
            entityData.set(DATA_MASK_BROKEN, true);
            level.playSound(null, blockPosition(), ModSounds.VIGIL_MASK_BREAK, SoundSource.HOSTILE, 6f, 1f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getEyeY(), getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.STATIC, getX(), getEyeY(), getZ(), 80, 1, 1, 1, 0.2);
            titleAll(level, "boss.quantumdeath.vigil.phase2", "boss.quantumdeath.vigil.phase2.sub");
        } else if (newPhase == 3) {
            entityData.set(DATA_VEILED, true);
            bossBar.setColor(BossEvent.BossBarColor.PURPLE);
            level.playSound(null, blockPosition(), ModSounds.VIGIL_ROAR, SoundSource.HOSTILE, 6f, 0.7f);
            titleAll(level, "boss.quantumdeath.vigil.phase3", "boss.quantumdeath.vigil.phase3.sub");
            resetBells(level);
        }
    }

    private void titleAll(ServerLevel level, String title, String sub) {
        for (ServerPlayer p : arenaPlayers(level)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Called by a {@link PuzzleBlocks.VigilBell} within range. */
    public void onBellRung(BlockPos pos, Player player) {
        if (!(level() instanceof ServerLevel level)) return;
        if (!veiled()) {
            // Before the veil, a bell only staggers it, then falls silent for a while.
            stunned = 40;
            attack = 0;
            level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(), 1, 0, 0, 0, 0);
            HellScheduler.later(level, 200, l -> resetBell(l, pos));
            return;
        }
        if (rung.isEmpty()) firstRing = tickCount;
        rung.add(pos.immutable());
        player.sendOverlayMessage(Component.translatable("boss.quantumdeath.vigil.bells", rung.size(), 4).withStyle(ChatFormatting.WHITE));
        if (rung.size() >= 4) {
            entityData.set(DATA_VEILED, false);
            unveiled = 300;
            rung.clear();
            level.playSound(null, blockPosition(), ModSounds.VIGIL_MASK_BREAK, SoundSource.HOSTILE, 5f, 0.6f);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getEyeY(), getZ(), 120, 2, 3, 2, 0.15);
            titleAll(level, "boss.quantumdeath.vigil.unveiled", "boss.quantumdeath.vigil.unveiled.sub");
        }
    }

    private void resetBells(ServerLevel level) {
        BlockPos c = home != null ? home : blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-30, -6, -30), c.offset(30, 10, 30))) resetBell(level, p);
        rung.clear();
        firstRing = -1;
    }

    private static void resetBell(ServerLevel level, BlockPos p) {
        var s = level.getBlockState(p);
        if (s.is(ModBlocks.VIGIL_BELL) && s.getValue(PuzzleBlocks.VigilBell.RUNG)) level.setBlockAndUpdate(p, s.setValue(PuzzleBlocks.VigilBell.RUNG, false));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (inEntrance()) return;
        if (stunned > 0) { stunned--; getNavigation().stop(); }
        if (phase == 3) {
            if (unveiled > 0 && --unveiled == 0) {
                entityData.set(DATA_VEILED, true);
                resetBells(level);
                titleAll(level, "boss.quantumdeath.vigil.reveiled", "boss.quantumdeath.vigil.phase3.sub");
            }
            if (veiled() && firstRing >= 0 && tickCount - firstRing > 600) resetBells(level);
        }
        if (phase >= 2 && tickCount % 60 == 0 && home != null) crumble(level);
    }

    /** The edge of the arena stops being real: brick turns into false ground that vanishes underfoot. */
    private void crumble(ServerLevel level) {
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 16 + random.nextDouble() * 8;
            BlockPos p = home.offset((int) (Math.cos(a) * r), -1, (int) (Math.sin(a) * r));
            var s = level.getBlockState(p);
            if (s.is(ModBlocks.ZENITH_BRICKS) || s.is(ModBlocks.ZENITH_STONE)) {
                level.setBlockAndUpdate(p, ModBlocks.FALSE_BLOCK.defaultBlockState());
                level.sendParticles(ModParticles.STATIC, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0);
            }
        }
    }

    @Override protected void chooseAttack(ServerLevel level, LivingEntity target, double distance) {
        if (stunned > 0) return;
        int roll = random.nextInt(phase >= 2 ? 12 : 8);
        if (roll < 2) startAttack(TOLL, 50);
        else if (roll < 4) startAttack(HANDS, 60);
        else if (roll < 6) startAttack(BLINK, 24);
        else if (roll < 8) startAttack(ECHOES, 40);
        else if (roll < 10) startAttack(GAZE, 80);
        else startAttack(CHAINS, 30);
    }

    @Override protected void approach(LivingEntity target, double distance) {
        if (stunned > 0) return;
        double speed = veiled() ? 1.25 : 1.0;
        if (distance > 4) getNavigation().moveTo(target, speed); else getNavigation().stop();
    }

    @Override protected void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t) {
        if (stunned > 0) { attackTime = attackLength; return; }
        getNavigation().stop();
        switch (id) {
            case TOLL -> {
                if (t == 20) {
                    level.playSound(null, blockPosition(), ModSounds.VIGIL_TOLL, SoundSource.HOSTILE, 6f, 0.5f);
                    Vec3 c = position();
                    for (int wave = 0; wave < 3; wave++) for (int ring = 1; ring <= 8; ring++) {
                        double radius = ring * 2.2;
                        HellScheduler.later(level, wave * 18 + ring * 2, l -> tollRing(l, c, radius));
                    }
                }
            }
            case HANDS -> {
                if (t == 10) {
                    var spots = new java.util.ArrayList<BlockPos>();
                    for (ServerPlayer p : arenaPlayers(level)) spots.add(p.blockPosition());
                    for (int i = 0; i < 2 + phase; i++) spots.add(blockPosition().offset(random.nextInt(21) - 10, 0, random.nextInt(21) - 10));
                    handSpots = spots;
                }
                if (t > 10 && t < 35) for (BlockPos p : handSpots) level.sendParticles(ParticleTypes.WHITE_ASH, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 4, 0.6, 0.05, 0.6, 0);
                if (t == 35) for (BlockPos p : handSpots) {
                    level.sendParticles(ModParticles.VIGIL_MOTE, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 30, 0.3, 1.2, 0.3, 0.05);
                    level.playSound(null, p, ModSounds.VIGIL_TOUCH, SoundSource.HOSTILE, 1.2f, 1.3f);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p).inflate(1.2, 2, 1.2), e -> e != this && !(e instanceof EchoEntity))) {
                        e.hurtServer(level, damageSources().mobAttack(this), 10f);
                        e.push(0, 0.9, 0);
                        e.syncVelocity = true;
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                    }
                }
            }
            case BLINK -> {
                if (t == 2) level.sendParticles(ModParticles.VIGIL_MOTE, getX(), getY() + 3, getZ(), 50, 0.6, 2.5, 0.6, 0.02);
                if (t == 8) {
                    ServerPlayer far = null;
                    for (ServerPlayer p : arenaPlayers(level)) if (far == null || p.distanceTo(this) > far.distanceTo(this)) far = p;
                    if (far != null) {
                        Vec3 behind = far.position().subtract(far.getViewVector(1f).multiply(1, 0, 1).normalize().scale(3));
                        teleportTo(behind.x, far.getY(), behind.z);
                        faceTarget(far);
                        level.playSound(null, blockPosition(), ModSounds.VIGIL_BREATH, SoundSource.HOSTILE, 2f, 0.7f);
                    }
                }
                if (t == 16) hurtAround(level, position().add(Vec3.directionFromRotation(0, getYRot()).scale(2)).add(0, 1, 0), 3.0, 14f, 1.2);
            }
            case ECHOES -> {
                if (t == 20) for (int i = 0; i < 3; i++) {
                    EchoEntity e = HellEntities.ECHO.create(level, EntitySpawnReason.MOB_SUMMONED);
                    if (e == null) continue;
                    e.snapTo(getX() + random.nextInt(9) - 4, getY(), getZ() + random.nextInt(9) - 4, 0, 0);
                    level.addFreshEntity(e);
                    level.sendParticles(ModParticles.VIGIL_MOTE, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 1, 0.3, 0.02);
                }
            }
            case GAZE -> tickGaze(level, target, t);
            case CHAINS -> {
                if (t == 12) for (ServerPlayer p : arenaPlayers(level).stream().limit(2).toList())
                    HellBolt.shootAt(level, this, HellBolt.Kind.CHAIN, getEyePosition(), p.getEyePosition(), 1.7f, 1f);
            }
            default -> {}
        }
    }

    private void tollRing(ServerLevel level, Vec3 c, double radius) {
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28;
            level.sendParticles(ParticleTypes.WHITE_ASH, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 3, 0.1, 0.1, 0.1, 0);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius + 1, 2, radius + 1), e -> e != this && !(e instanceof EchoEntity))) {
            double d = e.position().multiply(1, 0, 1).distanceTo(c.multiply(1, 0, 1));
            if (Math.abs(d - radius) < 1.1 && e.onGround()) e.hurtServer(level, damageSources().mobAttack(this), 8f);
        }
    }

    /** A burning gaze along its line of sight. It turns slowly; stone stops it. */
    private void tickGaze(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 eye = getEyePosition();
        if (t == 1) {
            gazeDir = getViewVector(1f);
            level.playSound(null, blockPosition(), ModSounds.VIGIL_ROAR, SoundSource.HOSTILE, 4f, 1.4f);
        }
        if (t < 20) { level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 3, 0.3, 0.3, 0.3, 0.02); return; }
        if (target != null) {
            Vec3 want = target.getEyePosition().subtract(eye).normalize();
            gazeDir = gazeDir.lerp(want, 0.06).normalize();
        }
        entityData.set(DATA_GAZE_YAW, (float) Math.toDegrees(Math.atan2(-gazeDir.x, gazeDir.z)));
        Vec3 end = eye.add(gazeDir.scale(40));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = hit.getLocation();
        double len = stop.distanceTo(eye);
        for (double d = 1; d < len; d += 1.0) {
            Vec3 p = eye.add(gazeDir.scale(d));
            level.sendParticles(ModParticles.STATIC, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        if (t % 5 == 0) for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1.0), e -> e != this && !(e instanceof EchoEntity))) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(eye);
            double along = rel.dot(gazeDir);
            if (along < 0 || along > len) continue;
            if (rel.subtract(gazeDir.scale(along)).length() < 1.1) {
                e.hurtServer(level, damageSources().indirectMagic(this, this), 4f);
                e.igniteForSeconds(2);
            }
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (veiled() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.getEntity() instanceof Player p) p.sendOverlayMessage(Component.translatable("boss.quantumdeath.vigil.veiled_hit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return false;
        }
        if (phase == 3) damage *= 1.5f;
        return super.hurtServer(level, source, damage);
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            for (ServerPlayer p : arenaPlayers(level)) {
                p.connection.send(new ClientboundStopSoundPacket(ModSounds.MUSIC_VIGIL.value().location(), SoundSource.MUSIC));
                p.connection.send(new ClientboundSoundPacket(Holder.direct(ModSounds.VIGIL_DEATH), SoundSource.HOSTILE, getX(), getY(), getZ(), 3f, 1f, 0));
            }
            resetBells(level);
        }
    }

    @Override protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.VIGIL_MOTE, getRandomX(1.5), getRandomY(), getRandomZ(1.5), 4, 0.3, 0.3, 0.3, 0.03);
            if (deathTime % 20 == 0) level.sendParticles(ParticleTypes.END_ROD, getX(), getEyeY(), getZ(), 30, 1, 2, 1, 0.1);
            if (deathTime >= 100 && !isRemoved()) {
                level.sendParticles(ParticleTypes.END_ROD, getX(), getEyeY(), getZ(), 200, 0.5, 1.5, 0.5, 0.4);
                level.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    public float gazeYaw() { return entityData.get(DATA_GAZE_YAW); }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.VIGIL_BREATH; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.VIGIL_TOUCH; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.VIGIL_DEATH; }
    @Override public boolean fireImmune() { return true; }
}
