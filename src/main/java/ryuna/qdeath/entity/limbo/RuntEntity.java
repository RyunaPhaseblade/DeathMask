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
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Runt (Menudo): a small, wiry dead running crouched, faster than a sprint and very fragile. It circles the legs and,
 * a few blocks away, stops short with a chirp and springs at its quarry; after the leap it has to gather itself.
 * Its body is small enough to slip through gaps a man cannot. Cut its line, or step aside from the leap.
 */
public class RuntEntity extends LimboDead {
    private int pause, leap, recover, leapCooldown = 40;
    private boolean leapHit;

    public RuntEntity(EntityType<? extends RuntEntity> type, Level level) { super(type, level); xpReward = 6; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 13).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_RUNT; }
    @Override protected double sightRange() { return 16; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 1.4; }
    @Override protected int windupTicks() { return 6; }
    @Override protected int attackRecovery() { return 18; }
    @Override protected boolean priorityVoice() { return true; }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (leapCooldown > 0) leapCooldown--;
        if (recover > 0) { recover--; getNavigation().stop(); return true; }
        if (leap > 0) {
            leap--;
            if (!leapHit && getBoundingBox().inflate(0.3).intersects(q.getBoundingBox())) { leapHit = true; doHurtTarget(level, q); }
            if (leap == 0 || (onGround() && leap < 8)) { leap = 0; recover = 24; playAnim(ANIM_STAGGER, 24); }
            return true;
        }
        if (pause > 0) {
            getNavigation().stop();
            getLookControl().setLookAt(q, 40f, 40f);
            if (--pause == 0) {
                Vec3 d = new Vec3(q.getX() - getX(), 0, q.getZ() - getZ()).normalize();
                setDeltaMovement(d.x * 0.62, 0.38, d.z * 0.62);
                leap = 14;
                leapHit = false;
                playSound(ModSounds.RUNT_SHRIEK, 1.1f, 1.3f);
            }
            return true;
        }
        if (visible && leapCooldown == 0 && distance > 3 && distance < 6 && onGround()) {
            pause = 8;
            leapCooldown = 70 + random.nextInt(40);
            playAnim(ANIM_SPECIAL, 10);
            playSound(ModSounds.RUNT_CHIRP, 1.0f, 1.2f + random.nextFloat() * 0.2f);
            return true;
        }
        return false;
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.RUNT_SHRIEK; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.RUNT_CHIRP; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.RUNT_CHIRP; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.RUNT_SHRIEK; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 1.4f + (random.nextFloat() - 0.5f) * 0.15f; }
}
