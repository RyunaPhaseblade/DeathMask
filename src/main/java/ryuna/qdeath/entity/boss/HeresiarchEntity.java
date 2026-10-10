package ryuna.qdeath.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.hell.HereticEntity;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE HERESIARCH, First of the Unbelieving. Floats above the tomb it rose from, swinging three chained censers.
 * Phase 1: censer orbit, pyre columns, chain hook, a choir of heretics. Phase 2 adds iron rain from the vault above.
 */
public class HeresiarchEntity extends HellBoss {
    public static final int CENSERS = 1, PYRE = 2, CHAIN = 3, CHOIR = 4, IRON_RAIN = 5;
    private final Map<UUID, Integer> censerHits = new HashMap<>();

    public HeresiarchEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 650).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override public HellLayer layer() { return HellLayer.DIS; }
    @Override protected int entranceLength() { return 100; }

    @Override public void beginEntrance(BlockPos home) {
        super.beginEntrance(home);
        setPos(home.getX() + 0.5, home.getY() - 2, home.getZ() + 0.5);
        if (level() instanceof ServerLevel l) l.playSound(null, home, ModSounds.HERESIARCH_CHANT, SoundSource.HOSTILE, 4f, 0.8f);
    }

    @Override protected void onEntranceTick(ServerLevel level, int remaining) {
        if (home == null) return;
        noPhysics = remaining > 0;
        setDeltaMovement(Vec3.ZERO);
        float p = 1f - (float) remaining / entranceLength();
        setPos(home.getX() + 0.5, home.getY() - 2 + 4.5 * p, home.getZ() + 0.5);
        level.sendParticles(ParticleTypes.FLAME, getX(), home.getY() + 0.5, getZ(), 10, 0.8, 0.5, 0.8, 0.05);
        if (remaining % 25 == 0) level.playSound(null, home, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3f, 0.5f);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (inEntrance()) return;
        // Hover about 2.5 blocks above the floor.
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPosition().getX(), blockPosition().getZ());
        double wantY = Math.min(floor + 2.5, getY() + 0.3);
        if (floor > getY() + 5 || floor < getY() - 12) wantY = getY();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.85, (wantY - getY()) * 0.1, v.z * 0.85);
        if (level.getRandom().nextInt(3) == 0) level.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.2, getZ(), 2, 0.3, 0.1, 0.3, 0.01);
    }

    @Override protected void approach(LivingEntity target, double distance) {
        Vec3 toward = target.position().subtract(position()).multiply(1, 0, 1);
        double want = distance > 10 ? 0.03 : distance < 6 ? -0.03 : 0;
        Vec3 drift = toward.normalize().scale(want);
        setDeltaMovement(getDeltaMovement().add(drift));
        faceTarget(target);
    }

    @Override protected void chooseAttack(ServerLevel level, LivingEntity target, double distance) {
        if (phase >= 2 && random.nextInt(4) == 0) { startAttack(IRON_RAIN, 100); return; }
        int roll = random.nextInt(10);
        if (roll < 3) startAttack(CENSERS, 80);
        else if (roll < 6) startAttack(PYRE, 50);
        else if (roll < 8) startAttack(CHAIN, 30);
        else startAttack(CHOIR, 40);
    }

    /** Server-side positions of the three censers during the orbit (the model draws them at the same angles). */
    public static Vec3 censerPos(Vec3 center, int i, int t) {
        double angle = t * 0.15 + i * Math.PI * 2 / 3;
        double radius = 3 + t * 0.08;
        return center.add(Math.cos(angle) * radius, -1.0, Math.sin(angle) * radius);
    }

    @Override protected void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t) {
        if (target != null) faceTarget(target);
        switch (id) {
            case CENSERS -> {
                if (t == 1) { censerHits.clear(); playSound(SoundEvents.CHAIN_PLACE, 3f, 0.5f); }
                for (int i = 0; i < 3; i++) {
                    Vec3 c = censerPos(position(), i, t);
                    level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.01);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.3, 2, 1.3), e -> e != this && !(e instanceof HereticEntity))) {
                        int last = censerHits.getOrDefault(e.getUUID(), -100);
                        if (t - last < 15) continue;
                        censerHits.put(e.getUUID(), t);
                        e.hurtServer(level, damageSources().mobAttack(this), 9f);
                        e.igniteForSeconds(3);
                    }
                }
            }
            case PYRE -> {
                if (t == 1) playSound(ModSounds.HERESIARCH_CHANT, 3f, 1.0f);
                if (t == 10) for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32), p -> !p.isSpectator())) {
                    HellBolt.fireColumn(level, this, p.blockPosition(), 10f);
                    for (int i = 0; i < 3; i++) HellBolt.fireColumn(level, this, p.blockPosition().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 8f);
                }
            }
            case CHAIN -> {
                if (t == 10 && target != null) {
                    playSound(SoundEvents.CHAIN_BREAK, 3f, 0.6f);
                    HellBolt.shootAt(level, this, HellBolt.Kind.CHAIN, getEyePosition(), target.getEyePosition(), 1.8f, 0.5f);
                }
                if (t == 28) HellBolt.fireColumn(level, this, blockPosition().below(2), 10f);
            }
            case CHOIR -> {
                if (t == 1) playSound(ModSounds.HERESIARCH_CHANT, 3f, 0.6f);
                if (t == 30) for (int i = 0; i < (phase >= 2 ? 3 : 2); i++) {
                    HereticEntity h = HellEntities.HERETIC.create(level, EntitySpawnReason.MOB_SUMMONED);
                    if (h == null) continue;
                    double a = random.nextDouble() * Math.PI * 2;
                    h.snapTo(getX() + Math.cos(a) * 7, getY() - 2, getZ() + Math.sin(a) * 7, 0, 0);
                    h.setTarget(target);
                    level.addFreshEntity(h);
                    level.sendParticles(ParticleTypes.LAVA, h.getX(), h.getY(), h.getZ(), 10, 0.3, 0.1, 0.3, 0.1);
                }
            }
            case IRON_RAIN -> {
                if (t == 1) playSound(SoundEvents.ANVIL_LAND, 2f, 0.3f);
                if (t % 5 == 0 && t < 90) {
                    Vec3 c = target != null ? target.position() : position();
                    BlockPos at = BlockPos.containing(c.x + random.nextGaussian() * 6, c.y, c.z + random.nextGaussian() * 6);
                    level.sendParticles(ParticleTypes.DRIPPING_LAVA, at.getX() + 0.5, at.getY() + 8, at.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0);
                    HellBolt.fireColumn(level, this, at, 8f);
                }
            }
            default -> {}
        }
    }

    @Override public boolean causeFallDamage(double fallDistance, float mult, DamageSource source) { return false; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.HERESIARCH_CHANT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HERETIC_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BOSS_DEFEATED; }
    @Override public float getVoicePitch() { return 0.6f; }
}
