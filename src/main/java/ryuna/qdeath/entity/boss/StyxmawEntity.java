package ryuna.qdeath.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.ModSounds;

/**
 * STYXMAW, the Sullen Leviathan. It does not chase: it rises from the Styx, strikes, sprays tar and slams the water,
 * then sinks and surfaces again beneath whoever strayed too far. The bubbles always show where.
 */
public class StyxmawEntity extends HellBoss {
    public static final int LUNGE = 1, TAR = 2, SLAM = 3, SUBMERGE = 4;
    private Vec3 surface = Vec3.ZERO;
    private Vec3 destination = Vec3.ZERO;

    public StyxmawEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 550).add(Attributes.ATTACK_DAMAGE, 16).add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override public HellLayer layer() { return HellLayer.MIRE; }
    @Override protected int entranceLength() { return 100; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }

    @Override public void beginEntrance(BlockPos home) {
        super.beginEntrance(home);
        surface = Vec3.atBottomCenterOf(home);
        setPos(surface.x, surface.y - 7, surface.z);
        if (level() instanceof ServerLevel l) l.playSound(null, home, ModSounds.STYXMAW_EMERGE, SoundSource.HOSTILE, 5f, 0.8f);
    }

    @Override protected void onEntranceTick(ServerLevel level, int remaining) {
        noPhysics = remaining > 0;
        setDeltaMovement(Vec3.ZERO);
        float p = 1f - (float) remaining / entranceLength();
        setPos(surface.x, surface.y - 7 + 7 * p, surface.z);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, surface.x, surface.y, surface.z, 10, 1.5, 0.2, 1.5, 0.1);
        level.sendParticles(ParticleTypes.SPLASH, surface.x, surface.y + 1, surface.z, 15, 1.5, 0.3, 1.5, 0.2);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (surface == Vec3.ZERO) surface = position();
        super.customServerAiStep(level);
        if (!inEntrance() && attack != SUBMERGE) { setDeltaMovement(Vec3.ZERO); setPos(surface.x, surface.y, surface.z); }
    }

    @Override protected void chooseAttack(ServerLevel level, LivingEntity target, double distance) {
        double flat = target.position().multiply(1, 0, 1).distanceTo(position().multiply(1, 0, 1));
        if (flat > 12 || random.nextInt(6) == 0) { startAttack(SUBMERGE, 80); return; }
        int roll = random.nextInt(10);
        if (flat < 6 && roll < 5) startAttack(LUNGE, 30);
        else if (roll < 7) startAttack(TAR, 50);
        else startAttack(SLAM, 40);
        faceTarget(target);
    }

    @Override protected void approach(LivingEntity target, double distance) { faceTarget(target); }

    @Override protected void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t) {
        Vec3 forward = Vec3.directionFromRotation(0, getYRot());
        switch (id) {
            case LUNGE -> {
                if (t < 16 && target != null) faceTarget(target);
                if (t == 12) playSound(ModSounds.BOSS_ROAR, 2.5f, 1.2f);
                if (t == 18) hurtAround(level, position().add(forward.scale(4)).add(0, 1, 0), 3.2, 18f, 1.2);
            }
            case TAR -> {
                if (target != null) faceTarget(target);
                if (target != null && t >= 14 && t <= 38 && t % 4 == 2) {
                    Vec3 mouth = position().add(forward.scale(1.5)).add(0, 4.5, 0);
                    Vec3 aim = target.position().add(forward.yRot((float) ((t - 26) * 0.04)).scale(2));
                    HellBolt.shootAt(level, this, HellBolt.Kind.TAR, mouth, aim, 1.1f, 3f);
                }
            }
            case SLAM -> {
                if (t == 22) {
                    playSound(ModSounds.BOSS_SLAM, 4f, 0.5f);
                    level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, 5, 0.5, 5, 0.4);
                    hurtAround(level, position(), 7.5, 12f, 2.0);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this))
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
            }
            case SUBMERGE -> tickSubmerge(level, target, t);
            default -> {}
        }
    }

    private void tickSubmerge(ServerLevel level, @Nullable LivingEntity target, int t) {
        if (t <= 30) {
            setPos(surface.x, surface.y - 6.0 * t / 30, surface.z);
            if (t == 1) playSound(SoundEvents.GENERIC_SPLASH, 3f, 0.5f);
        } else if (t == 31) {
            Vec3 aim = target != null ? target.position() : surface;
            destination = new Vec3(aim.x + random.nextGaussian() * 2, surface.y, aim.z + random.nextGaussian() * 2);
            setPos(destination.x, destination.y - 6, destination.z);
        } else if (t < 60) {
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, destination.x, destination.y, destination.z, 6, 1.2, 0.1, 1.2, 0.05);
            if (t % 8 == 0) level.playSound(null, BlockPos.containing(destination), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 2f, 0.6f);
        } else {
            float p = Math.min(1f, (t - 60) / 6f);
            setPos(destination.x, destination.y - 6 + 6 * p, destination.z);
            if (t == 62) {
                playSound(ModSounds.STYXMAW_EMERGE, 4f, 1.1f);
                level.sendParticles(ParticleTypes.SPLASH, destination.x, destination.y + 1, destination.z, 80, 2, 0.5, 2, 0.4);
                hurtAround(level, destination.add(0, 1, 0), 4.0, 16f, 1.5);
            }
            if (t == 66) surface = destination;
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (attack == SUBMERGE && attackTime > 8 && attackTime < 62) return false;
        return super.hurtServer(level, source, damage);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.SULLEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ELDER_GUARDIAN_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BOSS_DEFEATED; }
    @Override public float getVoicePitch() { return 0.5f; }
}
