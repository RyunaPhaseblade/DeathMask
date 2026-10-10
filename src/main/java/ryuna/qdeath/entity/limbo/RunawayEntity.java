package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Runaway (Desbocado): a dead whose tendons have pulled its legs into a runner's crouch, clothes torn by the effort.
 * It keeps up at nine tenths of a sprint and, now and then, pants, compresses and bolts in a short straight run faster
 * than any sprint, ending in a blow; after it, it must stand and heave for breath. Step out of its line, put something
 * solid in the way, and hit it while it recovers.
 */
public class RunawayEntity extends LimboDead {
    private int prep, dash, recover, dashCooldown = 40;
    private Vec3 dashDir = Vec3.ZERO;
    private boolean dashHit;

    public RunawayEntity(EntityType<? extends RunawayEntity> type, Level level) { super(type, level); xpReward = 8; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_RUNAWAY; }
    /** Outside the Limbo it keeps its old irregular surge. */
    @Override protected double legacyChase() { return 0.85 + 0.35 * Mth.sin(tickCount * 0.18f); }
    @Override protected double sightRange() { return 22; }
    /** Runners see fleeing prey from much further. */
    @Override protected boolean sightBonus(LivingEntity e) { return LimboSenses.fleeing(e); }
    @Override protected double callRadius() { return 0; }
    @Override protected int searchTicks() { return 120; }
    @Override protected int windupTicks() { return 8; }
    @Override protected double reach() { return 1.7; }
    @Override protected boolean priorityVoice() { return true; }

    /** Per-tick displacement of the burst: RUNAWAY_BURST x the player's sprint (in the Limbo). */
    private double burst() { return inLimbo() ? LimboTuning.RUNAWAY_BURST * LimboTuning.PLAYER_SPRINT : 0.45; }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (dashCooldown > 0) dashCooldown--;
        if (recover > 0) {
            recover--;
            getNavigation().stop();
            if (recover % 14 == 0) playSound(ModSounds.RUNAWAY_PANT, 0.9f, 0.9f);
            return true;
        }
        if (dash > 0) {
            dash--;
            double v = burst();
            setDeltaMovement(dashDir.x * v, getDeltaMovement().y, dashDir.z * v);
            if (!dashHit && getBoundingBox().inflate(0.4).intersects(q.getBoundingBox())) { dashHit = true; doHurtTarget(level, q); }
            if (horizontalCollision) { dash = 0; stagger(30); recover = 20; return true; } // a wall ends the run
            if (dash == 0) endDash();
            return true;
        }
        if (prep > 0) {
            getNavigation().stop();
            getLookControl().setLookAt(q, 40f, 40f);
            if (--prep == 0) {
                dashDir = new Vec3(q.getX() - getX(), 0, q.getZ() - getZ()).normalize();
                dash = 22;
                dashHit = false;
                playSound(ModSounds.RUNAWAY_SCREAM, 1.5f, 1.0f);
            }
            return true;
        }
        if (visible && dashCooldown == 0 && distance > 5 && distance < 16) {
            prep = 14;
            dashCooldown = 120 + random.nextInt(60);
            playAnim(ANIM_SPECIAL, 14);
            playSound(ModSounds.RUNAWAY_PANT, 1.3f, 1.2f);
            return true;
        }
        return false;
    }

    private void endDash() {
        recover = 40;
        playAnim(ANIM_STAGGER, 40);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.RUNAWAY_SCREAM; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.RUNAWAY_PANT; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.RUNAWAY_PANT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 1.15f; }
    @Override public int getAmbientSoundInterval() { return 160; }
}
