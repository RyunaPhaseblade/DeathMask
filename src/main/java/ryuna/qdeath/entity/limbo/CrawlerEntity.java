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
 * The Crawler (Reptante): a dead whose legs no longer carry it, dragging itself on its arms, caked in grave dirt. Low
 * enough to come through gaps one block high (its hitbox really is that small; it cannot pass through anything solid).
 * A small threat on its own; it comes from the low sides while the others press ahead. Before it pounces it draws its
 * arms in and scrapes the ground. Close the low gaps; keep where you stand visible.
 */
public class CrawlerEntity extends LimboDead {
    private int pounce;
    private boolean pounceHit;

    public CrawlerEntity(EntityType<? extends CrawlerEntity> type, Level level) { super(type, level); xpReward = 7; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_CRAWLER; }
    @Override protected double sightRange() { return 12; }
    @Override protected double fovCos() { return -0.2; }
    @Override protected double hearing() { return 1.5; }
    @Override protected double callRadius() { return 0; }
    @Override protected int searchTicks() { return 140; }
    /** It starts its pounce from a few blocks away. */
    @Override protected double reach() { return 3.5; }
    @Override protected int windupTicks() { return 12; }
    @Override protected int attackRecovery() { return 36; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }
    @Override protected void onWindup() { playSound(ModSounds.CRAWLER_SCRAPE, 1.0f, 0.8f); }

    /** The pounce: a low leap that hurts on contact. */
    @Override protected void strike(ServerLevel level, LivingEntity q) {
        Vec3 d = new Vec3(q.getX() - getX(), 0, q.getZ() - getZ()).normalize();
        setDeltaMovement(d.x * 0.7, 0.3, d.z * 0.7);
        pounce = 12;
        pounceHit = false;
        playSound(ModSounds.CRAWLER_LUNGE, 1.0f, 1.0f);
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (pounce <= 0) return false;
        pounce--;
        if (!pounceHit && getBoundingBox().inflate(0.35).intersects(q.getBoundingBox())) { pounceHit = true; doHurtTarget(level, q); }
        return true;
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && tickCount % 24 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002)
            playSound(ModSounds.CRAWLER_SCRAPE, 0.45f, 0.9f + random.nextFloat() * 0.3f);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.CRAWLER_HISS; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.CRAWLER_HISS; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.CRAWLER_HISS; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 1.1f; }
}
