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
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Putrescent (Putrescente): a dead swollen with rot, the belly huge and mottled, tumours on the shoulder, back and
 * head. It waddles, spasms and gurgles. Killing it is the danger: it leaves a corpse ({@link BloaterCorpse}) that
 * swells for a few seconds and bursts, hurting the living and the dead around it. Choose where it dies.
 */
public class BloaterEntity extends LimboDead {
    private int spasm = 60;

    public BloaterEntity(EntityType<? extends BloaterEntity> type, Level level) { super(type, level); xpReward = 10; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_BLOATER; }
    @Override protected double sightRange() { return 16; }
    @Override protected double callRadius() { return 8; }
    @Override protected double reach() { return 1.8; }
    @Override protected int windupTicks() { return 10; }
    @Override protected int attackRecovery() { return 26; }
    @Override protected boolean priorityVoice() { return true; }

    /** Renderer: the belly's breathing and the spasms (0..1). */
    public float swelling(float partial) {
        float p = animId() == ANIM_SPECIAL ? Math.max(0, animProgress(partial)) : 0;
        return 0.25f * (float) Math.sin(p * Math.PI);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (--spasm <= 0) {
            spasm = (mind().alert() ? 70 : 160) + random.nextInt(80);
            playAnim(ANIM_SPECIAL, 20);
            playSound(ModSounds.BLOATER_GURGLE, 1.0f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            BloaterCorpse corpse = HellEntities.BLOATER_CORPSE.create(level, EntitySpawnReason.TRIGGERED);
            if (corpse != null) {
                corpse.snapTo(getX(), getY(), getZ(), getYRot(), 0);
                corpse.setup(LimboTuning.CORPSE_FUSE_MIN + random.nextInt(LimboTuning.CORPSE_FUSE_MAX - LimboTuning.CORPSE_FUSE_MIN + 1), skin(), yBodyRot);
                level.addFreshEntity(corpse);
            }
        }
    }

    /** The corpse takes its place at once (no death fall: the body is already lying there). */
    @Override protected void tickDeath() {
        if (!level().isClientSide() && !isRemoved()) remove(RemovalReason.KILLED);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.BLOATER_GURGLE; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.BLOATER_GURGLE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.BLOATER_GURGLE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.BLOATER_GURGLE; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.7f; }
}
