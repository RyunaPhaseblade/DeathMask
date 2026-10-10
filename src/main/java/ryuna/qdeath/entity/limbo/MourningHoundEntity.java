package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * Mourning Hound: ribs, a jaw and funeral rags on four long legs. It sniffs, walks the last trail it found and only
 * speeds up when it regains contact. It keeps the breath of the living for a while; the trail fades, never updates
 * across the map, and a Breath Lure draws it off. Before it bites it chatters its teeth and lowers its body.
 */
public class MourningHoundEntity extends LimboDead {
    private int lunge;

    public MourningHoundEntity(EntityType<? extends MourningHoundEntity> type, Level level) { super(type, level); xpReward = 9; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected double sightRange() { return 16; }
    @Override protected double hearing() { return 1.3; }
    @Override protected double callRadius() { return 0; }
    @Override protected int searchTicks() { return 400; }
    @Override protected double searchSpeed() { return 0.65; }
    @Override protected boolean human() { return false; }
    @Override protected double chaseFraction() { return 0.92; }
    @Override protected double legacyChase() { return 1.3; }
    @Override protected double wanderSpeed() { return 0.5; }
    @Override protected boolean followsBreath() { return true; }
    @Override protected double lurePull() { return LimboTuning.LURE_PULL_HOUND; }
    @Override protected double reach() { return 3.0; }
    @Override protected int windupTicks() { return 12; }
    @Override protected void onWindup() { playSound(ModSounds.HOUND_CHATTER, 1.1f, 1.0f); }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        Vec3 d = new Vec3(q.getX() - getX(), 0, q.getZ() - getZ()).normalize();
        setDeltaMovement(d.x * 0.6, 0.25, d.z * 0.6);
        lunge = 8;
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (lunge <= 0) return false;
        if (--lunge >= 0 && getBoundingBox().inflate(0.4).intersects(q.getBoundingBox())) { lunge = 0; doHurtTarget(level, q); }
        return true;
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.HOUND_CHATTER; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.HOUND_SNIFF; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }
    @Override public int getAmbientSoundInterval() { return 120; }
}
