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
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Lanky (Larguirucho): a dead stretched to two and a half blocks, long pale limbs wound in dark bandages. Its reach
 * goes over the front row of a crowd: it raises one long arm high and brings it down on a single target, or holds
 * an arm out to the side and sweeps it across everything in front of it. Both need a clear line (never through a wall)
 * and both leave it standing open while it recovers: step in then, or use cover.
 */
public class LankyEntity extends LimboDead {
    private boolean sweep;

    public LankyEntity(EntityType<? extends LankyEntity> type, Level level) { super(type, level); xpReward = 14; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_LANKY; }
    @Override protected double sightRange() { return 22; }
    @Override protected double callRadius() { return 0; }
    /** It starts its blow from well beyond an ordinary dead's reach. */
    @Override protected double reach() { return 3.4; }
    @Override protected int windupTicks() { return 16; }
    @Override protected int attackRecovery() { return 30; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }

    @Override protected void startWindup() {
        // Several living in front of it, or the quarry close: the sweep. Otherwise the long overhead blow.
        sweep = quarry != null && (distanceTo(quarry) < 2.2 || level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5),
                e -> LimboSenses.isLife(e)).size() > 1);
        super.startWindup();
        if (sweep) playAnim(ANIM_SPECIAL, windupTicks() + 8);
        playSound(ModSounds.LANKY_GROAN, 1.2f, 0.7f);
    }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        playSound(ModSounds.LANKY_SWING, 1.3f, 0.9f + random.nextFloat() * 0.2f);
        if (!sweep) {
            if (inReach(q, reach() + 0.4)) doHurtTarget(level, q);
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach() + 0.5), LimboSenses::isLife)) {
            Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            if (to.lengthSqr() > 0.01 && look.dot(to.normalize()) > 0.2 && inReach(e, reach())) {
                var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
                double base = dmg == null ? 7 : dmg.getValue();
                e.hurtServer(level, damageSources().mobAttack(this), (float) (base * 0.7));
            }
        }
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.LANKY_GROAN; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.LANKY_GROAN; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.LANKY_GROAN; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.65f; }
    @Override public int getAmbientSoundInterval() { return 200; }
}
