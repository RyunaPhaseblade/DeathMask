package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.projectile.BileBolus;
import ryuna.qdeath.entity.projectile.BodyProjectile;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * Bile Spitter (Escupidor): a bloated dead whose ribcage opens like a pair of doors. It retches for a second, the chest
 * splitting and the throat working, then lobs a bolus of bile and tissue in a high arc (18 blocks): 5 on a direct hit and
 * a short slowness, and a puddle that slows whoever steps in it for three seconds. Cover and shields stop the bolus.
 */
public class BileSpitterEntity extends RangedDead {
    public BileSpitterEntity(EntityType<? extends BileSpitterEntity> type, Level level) { super(type, level); xpReward = 12; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_SPITTER; }
    @Override protected double sightRange() { return 22; }
    @Override protected double callRadius() { return 0; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected boolean windupMoves() { return false; }

    @Override protected double range() { return LimboTuning.BILE_RANGE; }
    @Override protected int prepareTicks() { return LimboTuning.BILE_PREPARE; }
    @Override protected int cooldownTicks() { return LimboTuning.BILE_COOLDOWN; }
    @Override protected double gravity() { return LimboTuning.BILE_GRAVITY; }
    /** A high lob: slower the further it goes. */
    @Override protected int flightTicks(double distance) { return (int) Math.max(12, Math.min(30, distance / 0.75)); }

    @Override protected void onAim(ServerLevel level) {
        playSound(ModSounds.SPITTER_RETCH, 1.4f, 0.9f + random.nextFloat() * 0.15f);
        Vec3 m = muzzle();
        level.sendParticles(ModParticles.BILE_DRIP, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.02);
    }

    @Override protected BodyProjectile createShot(ServerLevel level) { return HellEntities.BILE_BOLUS.create(level, EntitySpawnReason.MOB_SUMMONED); }

    @Override protected void onThrow(ServerLevel level) { playSound(ModSounds.SPITTER_SPIT, 1.4f, 1.0f); }

    @Override protected SoundEvent recognizeSound() { return ModSounds.SPITTER_RETCH; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.DEAD_CHASE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.BLOATER_GURGLE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.SPITTER_SPLAT; }
    @Override public float getVoicePitch() { return 0.85f + (random.nextFloat() - 0.5f) * 0.1f; }
}
