package ryuna.qdeath.entity.limbo;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Breaker (Quebrantador): a huge labourer dead, the head sunk between the shoulders, one shoulder grown into a hump.
 * Slow and very hard to put down. Its blow is prepared for more than a second (it lifts both fists overhead and
 * grunts); when it lands it throws its victim back, knocks a raised shield out of use and staggers for a moment.
 * Afterwards it stands heaving: that is the time to hit it. It can batter down the light defences the dead are
 * allowed to break (with mobGriefing), nothing else, and it does not ignore armour.
 */
public class BreakerEntity extends LimboDead {
    private static final Map<UUID, Long> DAZED = new HashMap<>();
    private boolean heavy;

    public BreakerEntity(EntityType<? extends BreakerEntity> type, Level level) { super(type, level); xpReward = 24; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 75).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override public int skinCount() { return 3; }
    @Override protected float eliteChance(int depth) { return 0.02f; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_BREAKER; }
    @Override protected double sightRange() { return 18; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 2.4; }
    @Override protected int windupTicks() { return 24; }
    @Override protected int attackRecovery() { return 34; }
    @Override protected int siegePrepare() { return LimboTuning.SIEGE_PREPARE_BREAKER; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }

    @Override protected void onWindup() { playSound(ModSounds.BREAKER_GRUNT, 1.6f, 0.8f + random.nextFloat() * 0.15f); }

    /** Only the prepared blow knocks a shield out of use (vanilla disables the shield of whoever blocks it). */
    @Override public float getSecondsToDisableBlocking() { return heavy ? 5f : 0f; }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        heavy = true;
        boolean hit = doHurtTarget(level, q);
        heavy = false;
        playSound(ModSounds.BREAKER_SLAM, 1.6f, 0.85f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), getX(), getY() + 0.1, getZ(),
                20, 0.8, 0.1, 0.8, 0.1);
        Vec3 push = q.position().subtract(position()).normalize().scale(1.4);
        q.push(push.x, 0.35, push.z);
        q.syncVelocity = true;
        // A short daze after an announced blow, never chained: one per victim every five seconds.
        long now = level.getGameTime();
        if (hit && now - DAZED.getOrDefault(q.getUUID(), -1000L) > 100) {
            DAZED.put(q.getUUID(), now);
            q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 16, 1));
        }
        stagger(attackRecovery() - 6);
        attackCooldown = attackRecovery();
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.BREAKER_ROAR; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.BREAKER_GRUNT; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.BREAKER_GRUNT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.BREAKER_GRUNT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BREAKER_ROAR; }
    @Override public float getVoicePitch() { return 0.7f; }
    @Override public int getAmbientSoundInterval() { return 220; }
}
