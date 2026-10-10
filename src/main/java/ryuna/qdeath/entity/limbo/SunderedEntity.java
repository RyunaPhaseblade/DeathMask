package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Sundered (Desdoblado), a rare horror: a person split from the crown to the waist, the two halves of the torso and
 * head still walking on one pair of legs (the deformity is local and legible: arms, legs, clothes are a person's).
 * Before it strikes it opens, both halves swinging apart, then closes them in a sweep in front of it. Afterwards it has
 * to put itself back together, and while it does, it is open to harm. It never splits into new creatures.
 */
public class SunderedEntity extends LimboDead {
    public SunderedEntity(EntityType<? extends SunderedEntity> type, Level level) { super(type, level); xpReward = 10; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return ryuna.qdeath.limbo.LimboTuning.CHASE_SUNDERED; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected double sightRange() { return 14; }
    @Override protected double callRadius() { return 6; }
    @Override protected double reach() { return 2.6; }
    @Override protected int windupTicks() { return 18; }
    /** The recomposition. */
    @Override protected int attackRecovery() { return 40; }
    @Override protected void onWindup() { playSound(ModSounds.SUNDERED_SPLIT, 1.2f, 0.9f + random.nextFloat() * 0.2f); }

    /** The two halves close on everything in front of it. */
    @Override protected void strike(ServerLevel level, LivingEntity q) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach() + 0.5), LimboSenses::isLife)) {
            Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            if (to.length() <= reach() + 0.8 && (to.lengthSqr() < 1 || look.dot(to.normalize()) > 0.3)) doHurtTarget(level, e);
        }
        playAnim(ANIM_STAGGER, attackRecovery()); // the recomposition shows: halves out of line while it is open to harm
    }

    /** Recomposing halves take more damage. */
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (attackCooldown > 0 && windup == 0) damage *= 1.5f;
        return super.hurtServer(level, source, damage);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.SUNDERED_AMBIENT; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.SUNDERED_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
    @Override public float getVoicePitch() { return 0.8f; }
}
