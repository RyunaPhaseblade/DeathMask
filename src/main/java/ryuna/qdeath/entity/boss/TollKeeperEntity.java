package ryuna.qdeath.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellScheduler;
import ryuna.qdeath.limbo.Processions;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * THE TOLL-KEEPER, Warden of the First Shore. Rises from the burning lake when the toll is paid.
 * The ferryman of the Limbo controls passage: oar sweeps, homing soul lanterns, a wake of hellfire, and an oar planted
 * in the ash that closes a lane with a wall of fire. It summons the unpaid dead in announced processions, from the
 * edges of the landing, out of a reserve fixed for the whole encounter; after each call it gives room to reposition.
 * While any of the unpaid stands it is partly shielded.
 */
public class TollKeeperEntity extends HellBoss {
    public static final int SWEEP = 1, SOULS = 2, WAKE = 3, TOLL_CALL = 4, OAR_BARRIER = 5;
    private final List<UUID> unpaid = new ArrayList<>();
    /** Dead the Toll-Keeper may still call in this encounter; never refilled. */
    private int reserve = -1;
    private int calls;
    private int callCooldown = 200;
    private final List<BlockPos> entries = new ArrayList<>();

    public TollKeeperEntity(EntityType<? extends Monster> type, Level level) { super(type, level, BossEvent.BossBarColor.WHITE); }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 320).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override public HellLayer layer() { return HellLayer.VESTIBULE; }

    @Override public void beginEntrance(BlockPos home) {
        super.beginEntrance(home);
        setPos(home.getX() + 0.5, home.getY() - 5, home.getZ() + 0.5);
        if (level() instanceof ServerLevel l) l.playSound(null, home, ModSounds.BOSS_RISE, SoundSource.HOSTILE, 4f, 0.8f);
    }

    @Override protected void onEntranceTick(ServerLevel level, int remaining) {
        if (home == null) return;
        noPhysics = remaining > 0;
        setDeltaMovement(Vec3.ZERO);
        setPos(home.getX() + 0.5, home.getY() - 7.0 * remaining / entranceLength(), home.getZ() + 0.5);
        level.sendParticles(ParticleTypes.LAVA, getX(), home.getY() + 0.2, getZ(), 3, 1.2, 0.1, 1.2, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3, getZ(), 4, 0.6, 1.2, 0.6, 0.02);
        if (remaining % 20 == 0) level.playSound(null, blockPosition(), ModSounds.TOLL_KEEPER_LANTERN, SoundSource.HOSTILE, 2.5f, 0.7f);
    }

    private int budget(ServerLevel level) {
        if (reserve < 0) reserve = level.getDifficulty() == Difficulty.HARD ? 10 : 8;
        return reserve;
    }

    @Override protected void chooseAttack(ServerLevel level, LivingEntity target, double distance) {
        // Phase 1 may call once, phase 2 twice; never while a call is fresh, never past the encounter's reserve.
        // The call lasts 110 ticks although the dead come out at 40: it stands with the lantern raised, room to reposition.
        if (callCooldown <= 0 && calls < phase && budget(level) > 0) { startAttack(TOLL_CALL, 110); return; }
        int roll = random.nextInt(12);
        if (distance < 5.5 && roll < 6) startAttack(SWEEP, 30);
        else if (roll < 4) startAttack(SOULS, 40);
        else if (roll < 8) startAttack(OAR_BARRIER, 45);
        else startAttack(WAKE, 50);
        faceTarget(target);
    }

    @Override protected void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t) {
        getNavigation().stop();
        if (target != null && t < 18) faceTarget(target);
        Vec3 forward = Vec3.directionFromRotation(0, getYRot());
        switch (id) {
            case SWEEP -> {
                if (t == 4) playSound(ModSounds.KNIGHT_SWING, 2.0f, 0.5f);
                if (t == 18) {
                    Vec3 c = position().add(forward.scale(3.0)).add(0, 1, 0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 5, 1.5, 0.2, 1.5, 0);
                    hurtAround(level, c, 3.8, 14f, 1.6);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
                }
            }
            case SOULS -> {
                if (t == 2) playSound(ModSounds.TOLL_KEEPER_LANTERN, 2.0f, 1.0f);
                if (target != null && (t == 12 || t == 22 || t == 32)) {
                    Vec3 lantern = position().add(forward.scale(1.2)).add(0, 3.6, 0);
                    HellBolt.shootAt(level, this, HellBolt.Kind.WISP, lantern, target.getEyePosition(), 0.7f, 6f);
                }
            }
            case WAKE -> {
                if (t == 20 && target != null) {
                    playSound(ModSounds.BOSS_SLAM, 2.5f, 0.7f);
                    Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                    for (int i = 1; i <= 8; i++) {
                        BlockPos at = BlockPos.containing(position().add(dir.scale(i * 2.0)));
                        HellScheduler.later(level, i * 3, l -> HellBolt.fireColumn(l, this, at, 9f));
                    }
                }
            }
            case OAR_BARRIER -> {
                // The oar is planted: a lane across the landing closes behind a wall of fire. Smoke marks the line first.
                if (t == 2) playSound(ModSounds.BOSS_SLAM, 2.0f, 1.2f);
                if (t == 20) {
                    Vec3 side = new Vec3(-forward.z, 0, forward.x);
                    Vec3 mid = position().add(forward.scale(6));
                    for (int i = -4; i <= 4; i++) {
                        BlockPos at = BlockPos.containing(mid.add(side.scale(i * 1.5)));
                        HellScheduler.later(level, Math.abs(i) * 2, l -> HellBolt.fireColumn(l, this, at, 8f));
                    }
                }
            }
            case TOLL_CALL -> {
                if (t == 1) {
                    calls++;
                    level.playSound(null, blockPosition(), ModSounds.VIGIL_TOLL, SoundSource.HOSTILE, 4.0f, 0.6f);
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ARENA_RADIUS)))
                        p.sendOverlayMessage(Component.translatable("entity.quantumdeath.toll_keeper.call").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    pickEntries(level);
                }
                // The entries smoke and glow for two seconds before anyone climbs out: never on top of a player.
                if (t > 1 && t < 40 && t % 5 == 0)
                    for (BlockPos e : entries) level.sendParticles(ParticleTypes.SOUL, e.getX() + 0.5, e.getY() + 0.3, e.getZ() + 0.5, 6, 0.3, 0.6, 0.3, 0.02);
                if (t == 40) {
                    int n = Math.min(budget(level), level.getDifficulty() == Difficulty.HARD ? 5 : 4);
                    for (int i = 0; i < n && !entries.isEmpty(); i++) summonUnpaid(level, target, entries.get(i % entries.size()), i);
                    reserve -= n;
                    callCooldown = 400;
                }
            }
            default -> {}
        }
    }

    private void pickEntries(ServerLevel level) {
        entries.clear();
        BlockPos c = home != null ? home : blockPosition();
        for (int i = 0; i < 12 && entries.size() < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 12 + random.nextDouble() * 6;
            BlockPos g = Processions.ground(level, (int) (c.getX() + Math.cos(a) * r), (int) (c.getZ() + Math.sin(a) * r), c.getY(), 6);
            if (g != null && level.getNearestPlayer(g.getX(), g.getY(), g.getZ(), 7, false) == null) entries.add(g);
        }
        if (entries.isEmpty()) entries.add(c.offset(10, 0, 0));
    }

    private void summonUnpaid(ServerLevel level, @Nullable LivingEntity target, BlockPos at, int i) {
        EntityType<? extends LimboDead> type = i % 3 == 2 ? HellEntities.RUNAWAY : (i % 3 == 1 && random.nextBoolean() ? HellEntities.CRAWLER : HellEntities.HOLLOW);
        LimboDead d = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) return;
        d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
        d.setPersistenceRequired();
        d.setHome(home != null ? home : blockPosition());
        level.addFreshEntity(d);
        d.emerge(25);
        if (target != null) d.engage(target);
        unpaid.add(d.getUUID());
        level.sendParticles(ParticleTypes.SOUL, d.getX(), d.getY() + 1, d.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (callCooldown > 0) callCooldown--;
        unpaid.removeIf(id -> !(level.getEntity(id) instanceof LimboDead h) || !h.isAlive());
        if (!unpaid.isEmpty()) {
            addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 25, 1, false, false));
            if (tickCount % 4 == 0) for (UUID id : unpaid) if (level.getEntity(id) instanceof LimboDead h) {
                Vec3 a = h.position().add(0, 1, 0), b = position().add(0, 2.5, 0);
                for (int i = 0; i < 6; i++) { Vec3 p = a.lerp(b, i / 6.0); level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0); }
            }
        }
    }

    /** The defeat breaks the first seal: the Limbo may now seep into the Overworld in force. */
    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) ryuna.qdeath.limbo.plague.PlagueDirector.breakFirstSeal(level.getServer());
    }

    @Override protected void approach(LivingEntity target, double distance) {
        if (distance > 3.5) getNavigation().moveTo(target, 1.0); else getNavigation().stop();
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("TollReserve", reserve);
        out.putInt("TollCalls", calls);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        reserve = in.getIntOr("TollReserve", -1);
        calls = in.getIntOr("TollCalls", 0);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.TOLL_KEEPER_LANTERN; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.WITHER_SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BOSS_DEFEATED; }
    @Override public float getVoicePitch() { return 0.5f; }
}
