package ryuna.qdeath.entity.limbo;

import net.minecraft.core.particles.ParticleTypes;
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
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.LimboDirector;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * Crier of the Last Breath (Pregonero): a town crier dead in his coat and tricorn, a tumour swelling his throat. When it
 * sees life it plants itself, the throat fills through a long inhale, and it screams: every dead within earshot goes to
 * look where it saw you (they are not told where you go next) and sealed graves nearby stir. Strike it during the inhale
 * and the call is lost; walk out of its sight and it has nothing to call about. It only gathers the dead already there.
 */
public class CrierEntity extends LimboDead {
    public static final double SCREAM_RADIUS = 32;

    public CrierEntity(EntityType<? extends CrierEntity> type, Level level) { super(type, level); xpReward = 12; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_CRIER; }
    @Override protected double sightRange() { return 24; }
    @Override protected double fovCos() { return 0.0; }
    @Override protected double callRadius() { return SCREAM_RADIUS; }
    @Override protected int callTicks() { return 46; }
    @Override protected boolean callInterruptible() { return true; }
    @Override protected int searchTicks() { return 100; }
    @Override protected boolean priorityVoice() { return true; }

    @Override protected void onCallComplete(ServerLevel level) {
        level.playSound(null, blockPosition(), ModSounds.CRIER_SCREAM, SoundSource.HOSTILE, 3.5f, 0.9f + random.nextFloat() * 0.15f);
        level.sendParticles(ryuna.qdeath.registry.ModParticles.BREATH, getX(), getEyeY(), getZ(), 40, 0.6, 0.4, 0.6, 0.08);
        LimboDirector.stirGraves(level, blockPosition(), 12);
    }

    @Override protected void onCallInterrupted(ServerLevel level) {
        level.playSound(null, blockPosition(), ModSounds.DEAD_HURT, SoundSource.HOSTILE, 1.2f, 0.5f);
        level.sendParticles(ParticleTypes.POOF, getX(), getEyeY(), getZ(), 12, 0.3, 0.2, 0.3, 0.02);
    }

    /** It stays where it can see and watches; once it has breath again, it calls again (it still needs its own sight). */
    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (visible && callCooldown == 0 && hasListeners(callRadius())) { startCall(); return true; }
        if (visible && distance > reach() + 0.3 && distance < 9) { getNavigation().stop(); return true; }
        return false;
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.CRIER_THROAT; }
    @Override protected SoundEvent callSound() { return ModSounds.CRIER_INHALE; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.CRIER_THROAT; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.CRIER_THROAT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.8f; }
}
