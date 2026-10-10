package ryuna.qdeath.entity.hell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Wandering Dead (Zombi errante), the base of every horde: a person in the body of a player, dead and wearing what
 * they died in. It drifts slowly while nothing is alive near it; once it has found life it runs after it, a little
 * slower than a sprint and much faster than a walk, and strikes or bites after raising its arms. Seventeen skins.
 * (Registry id, class and save data are those of the original Hollow: existing worlds keep their Hollows. Outside the
 * Limbo it keeps the pace it always had.)
 */
public class HollowEntity extends LimboDead {
    public static final int VARIANTS = 17;

    public HollowEntity(EntityType<? extends HollowEntity> type, Level level) { super(type, level); xpReward = 5; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.21).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override public int skinCount() { return VARIANTS; }
    public int variant() { return skin(); }

    @Override protected float eliteChance(int depth) { return 0.025f; }
    /** Natural packs: up to eight together. */
    @Override public int getMaxSpawnClusterSize() { return 8; }

    @Override protected double sightRange() { return 18; }
    @Override protected double callRadius() { return 10; }
    @Override protected int callTicks() { return 16; }
    @Override protected int windupTicks() { return 7; }
    @Override protected int attackRecovery() { return 17 + random.nextInt(6); }
    @Override protected double reach() { return 1.6; }

    @Override protected void onWindup() { voice(ModSounds.DEAD_ATTACK, 1.0f, 0.85f + random.nextFloat() * 0.3f); }

    @Override protected SoundEvent recognizeSound() { return ModSounds.DEAD_CHASE; }
    @Override protected SoundEvent callSound() { return ModSounds.HOLLOW_CALL; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.DEAD_CHASE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.DEAD_GROAN; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public int getAmbientSoundInterval() { return 160; }
    @Override public float getVoicePitch() { return 0.85f + (getUUID().hashCode() & 7) * 0.04f + (random.nextFloat() - 0.5f) * 0.1f; }

    /** Dragging feet: a procession is heard before it is seen. */
    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        if (random.nextInt(3) == 0) playSound(ModSounds.DEAD_STEP, 0.3f, 0.85f + random.nextFloat() * 0.3f);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        int old = in.getIntOr("HollowVariant", -1); // Hollows saved before the refinement
        if (old >= 0 && in.getIntOr("LimboSkin", -1) < 0) setSkin(old);
    }
}
