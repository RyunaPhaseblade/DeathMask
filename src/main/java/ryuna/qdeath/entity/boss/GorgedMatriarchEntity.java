package ryuna.qdeath.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.hell.GluttlingEntity;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellScheduler;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * THE GORGED MATRIARCH, Mother of Mouths. She inhales everything around her (get behind a pillar, or keep running),
 * spits bile, shakes the pit (jump the shockwave rings) and gives birth to Gluttlings.
 */
public class GorgedMatriarchEntity extends HellBoss {
    public static final int DEVOUR = 1, BARRAGE = 2, QUAKE = 3, BROOD = 4;

    public GorgedMatriarchEntity(EntityType<? extends Monster> type, Level level) { super(type, level, BossEvent.BossBarColor.RED); }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 450).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.17).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override public HellLayer layer() { return HellLayer.GORGE; }

    @Override public void beginEntrance(BlockPos home) {
        super.beginEntrance(home);
        setPos(home.getX() + 0.5, home.getY() - 4, home.getZ() + 0.5);
        if (level() instanceof ServerLevel l) l.playSound(null, home, ModSounds.BOSS_RISE, SoundSource.HOSTILE, 4f, 0.6f);
    }

    @Override protected void onEntranceTick(ServerLevel level, int remaining) {
        if (home == null) return;
        noPhysics = remaining > 0;
        setDeltaMovement(Vec3.ZERO);
        float p = 1f - (float) remaining / entranceLength();
        setPos(home.getX() + 0.5, home.getY() - 4 + 4 * p * p, home.getZ() + 0.5);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.FLESH.defaultBlockState()), getX(), home.getY() + 0.5, getZ(), 12, 2, 0.3, 2, 0.2);
        if (remaining % 16 == 0) level.playSound(null, blockPosition(), ModSounds.GLUTTON_AMBIENT, SoundSource.HOSTILE, 3f, 0.4f);
    }

    @Override protected void chooseAttack(ServerLevel level, LivingEntity target, double distance) {
        int roll = random.nextInt(10);
        if (roll < 3) startAttack(DEVOUR, 70);
        else if (roll < 6) startAttack(BARRAGE, 50);
        else if (roll < 8 || distance < 6) startAttack(QUAKE, 40);
        else startAttack(BROOD, 40);
    }

    @Override protected void tickAttack(ServerLevel level, @Nullable LivingEntity target, int id, int t) {
        getNavigation().stop();
        if (target != null) faceTarget(target);
        Vec3 mouth = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.6)).add(0, 2.2, 0);
        switch (id) {
            case DEVOUR -> {
                if (t == 1) playSound(ModSounds.MATRIARCH_INHALE, 4.0f, 1.0f);
                if (t < 58) {
                    double strength = phase >= 2 ? 0.09 : 0.065;
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mouth, mouth).inflate(18), p -> !p.isSpectator() && !p.isCreative())) {
                        if (!hasLineOfSight(p)) continue; // pillars shelter you
                        Vec3 pull = mouth.subtract(p.position()).normalize().scale(strength);
                        p.push(pull.x, 0, pull.z);
                        p.syncVelocity = true;
                        if (t % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                    }
                    if (t % 2 == 0) level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 4, 1.5, 1, 1.5, -0.15);
                }
                if (t == 60) {
                    playSound(ModSounds.MAW_BITE, 4.0f, 0.5f);
                    hurtAround(level, mouth, 3.4, 18f, 0.8);
                }
            }
            case BARRAGE -> {
                if (target != null && t % 10 == 0 && t <= 40) {
                    playSound(ModSounds.GLUTTON_VOMIT, 2.5f, 0.6f);
                    HellBolt.shootAt(level, this, HellBolt.Kind.BILE, mouth, target.position(), 1.0f, 6f);
                    for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), p -> p != target && !p.isSpectator()))
                        HellBolt.shootAt(level, this, HellBolt.Kind.BILE, mouth, other.position(), 1.0f, 8f);
                }
            }
            case QUAKE -> {
                if (t == 6) playSound(SoundEvents.RAVAGER_ROAR, 3.0f, 0.5f);
                if (t == 24) {
                    playSound(ModSounds.BOSS_SLAM, 4.0f, 0.6f);
                    Vec3 c = position();
                    for (int ring = 1; ring <= 6; ring++) {
                        double radius = ring * 2.0;
                        HellScheduler.later(level, ring * 3, l -> shockRing(l, c, radius));
                    }
                }
            }
            case BROOD -> {
                if (t == 10) playSound(ModSounds.GLUTTON_VOMIT, 3.0f, 0.4f);
                if (t == 25) {
                    int n = phase >= 2 ? 5 : 3;
                    for (int i = 0; i < n; i++) {
                        GluttlingEntity g = HellEntities.GLUTTLING.create(level, EntitySpawnReason.MOB_SUMMONED);
                        if (g == null) continue;
                        g.snapTo(mouth.x + random.nextGaussian(), getY() + 0.5, mouth.z + random.nextGaussian(), random.nextFloat() * 360, 0);
                        g.setTarget(target);
                        level.addFreshEntity(g);
                    }
                    level.sendParticles(ModParticles.BILE_DRIP, mouth.x, mouth.y, mouth.z, 30, 1, 0.5, 1, 0.1);
                }
            }
            default -> {}
        }
    }

    /** One ring of the quake: hits everything standing on the ground at that radius. Jump it. */
    private void shockRing(ServerLevel level, Vec3 c, double radius) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.FLESH.defaultBlockState()), c.x + Math.cos(a) * radius, c.y + 0.1, c.z + Math.sin(a) * radius, 2, 0.1, 0.1, 0.1, 0.1);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius + 1, 2, radius + 1), e -> e != this && !(e instanceof GluttlingEntity))) {
            double d = e.position().multiply(1, 0, 1).distanceTo(c.multiply(1, 0, 1));
            if (Math.abs(d - radius) < 1.2 && e.onGround()) {
                e.hurtServer(level, damageSources().mobAttack(this), 10f);
                e.push(0, 0.6, 0);
                e.syncVelocity = true;
            }
        }
    }

    @Override protected void approach(LivingEntity target, double distance) {
        if (distance > 5) getNavigation().moveTo(target, phase >= 2 ? 1.3 : 1.0); else getNavigation().stop();
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.GLUTTON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.GLUTTON_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BOSS_DEFEATED; }
    @Override public float getVoicePitch() { return 0.45f; }
}
