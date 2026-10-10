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
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.projectile.BodyProjectile;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

/**
 * Rib Thrower (Descostillado): a gaunt dead with its ribcage laid open. It reaches into its own chest, cracks a rib
 * free (0.9 s) and hurls it hard and nearly flat (24 blocks): 6 on a direct hit, no area, nothing broken, stopped by a
 * shield. It leads a moving target by at most half a second of what it saw of its motion. The torn rib grows back.
 */
public class RibThrowerEntity extends RangedDead {
    public RibThrowerEntity(EntityType<? extends RibThrowerEntity> type, Level level) { super(type, level); xpReward = 12; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 28).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_RIBS; }
    @Override protected double sightRange() { return 26; }
    @Override protected double callRadius() { return 0; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected boolean windupMoves() { return false; }

    @Override protected double range() { return LimboTuning.RIB_RANGE; }
    @Override protected int prepareTicks() { return LimboTuning.RIB_PREPARE; }
    @Override protected int cooldownTicks() { return LimboTuning.RIB_COOLDOWN; }
    @Override protected double gravity() { return LimboTuning.RIB_GRAVITY; }
    @Override protected int flightTicks(double distance) { return (int) Math.max(3, Math.ceil(distance / LimboTuning.RIB_SPEED)); }
    @Override protected int leadTicks() { return LimboTuning.RIB_LEAD; }

    @Override protected void onAim(ServerLevel level) { playSound(ModSounds.RIBS_CRACK, 1.4f, 0.9f + random.nextFloat() * 0.2f); }

    @Override protected BodyProjectile createShot(ServerLevel level) { return HellEntities.RIB_SHARD.create(level, EntitySpawnReason.MOB_SUMMONED); }

    @Override protected void onThrow(ServerLevel level) { playSound(ModSounds.RIBS_THROW, 1.4f, 1.0f); }

    @Override protected SoundEvent recognizeSound() { return ModSounds.RIBS_CRACK; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.DEAD_CHASE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.DEAD_GROAN; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.9f + (random.nextFloat() - 0.5f) * 0.1f; }
}
