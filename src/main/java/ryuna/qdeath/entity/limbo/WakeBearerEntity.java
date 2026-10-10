package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * Bearer of the Wake: a great shrouded figure clutching remains to its chest, lopsided under the cloth.
 * A rare funeral elite at the head of a procession, solemn and slow; every action is preceded by a marked stillness.
 * It is the heavy threat of its group, not a commander (directing the dead is the Foreman's role): it gathers the
 * remains against its torso before a wide blow and can batter down wooden doors and barricades. An elite encounter,
 * not a boss and not a form of VIGIL. It drops consecrated funeral wax.
 */
public class WakeBearerEntity extends LimboDead {
    public WakeBearerEntity(EntityType<? extends WakeBearerEntity> type, Level level) { super(type, level); xpReward = 40; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 90).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.19).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected boolean canBeElite() { return false; }
    @Override protected boolean human() { return false; }
    @Override protected boolean isProcessionLeader() { return true; }
    @Override protected double sightRange() { return 20; }
    @Override protected double callRadius() { return 0; }
    @Override protected double chaseSpeed() { return 1.0; }
    @Override protected double wanderSpeed() { return 0.5; }
    @Override protected double reach() { return 3.4; }
    @Override protected int windupTicks() { return 26; }
    @Override protected int attackRecovery() { return 50; }
    @Override protected int siegePrepare() { return LimboTuning.SIEGE_PREPARE_BEARER; }
    @Override protected void onWindup() { playSound(ModSounds.BEARER_GATHER, 1.3f, 0.9f); }

    /** A wide, slow blow in front of it. */
    @Override protected void strike(ServerLevel level, LivingEntity q) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach() + 0.6), LimboSenses::isLife)) {
            Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            if (to.length() <= reach() + 0.9 && (to.lengthSqr() < 1 || look.dot(to.normalize()) > 0.0)) doHurtTarget(level, e);
        }
        level.playSound(null, blockPosition(), ModSounds.BEARER_SLAM, SoundSource.HOSTILE, 1.6f, 0.8f);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.BEARER_DIRGE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.BEARER_DIRGE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HOLLOW_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public float getVoicePitch() { return 0.6f; }
    @Override public int getAmbientSoundInterval() { return 240; }
}
