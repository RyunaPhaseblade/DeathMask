package ryuna.qdeath.entity.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Walking Ossuary: a heap of bones, shrouds and remains on uneven supports, with no human shape left in charge.
 * Slow and heavy, it fills a passage. Before it strikes it shifts its mass to one side; before it sheds one of the
 * dead it carries, those remains rattle. It carries a finite number (saved with it): once they are out, they are out,
 * even after the area reloads. It can batter down wooden doors, fences and barricades (and nothing else) after a
 * long, visible effort that a hit interrupts.
 */
public class WalkingOssuaryEntity extends LimboDead {
    private int occupants = 3;
    private int shedPrep, shedCooldown = 80;

    public WalkingOssuaryEntity(EntityType<? extends WalkingOssuaryEntity> type, Level level) { super(type, level); xpReward = 20; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 70).add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.17).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.STEP_HEIGHT, 1.0);
    }

    public int occupants() { return occupants; }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        occupants = level.getDifficulty() == Difficulty.HARD ? 4 : 3;
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override protected double sightRange() { return 14; }
    @Override protected double callRadius() { return 0; }
    @Override protected int searchTicks() { return 80; }
    @Override protected boolean human() { return false; }
    @Override protected double chaseSpeed() { return 1.0; }
    @Override protected double wanderSpeed() { return 0.6; }
    @Override protected double reach() { return 2.8; }
    @Override protected int windupTicks() { return 20; }
    @Override protected int attackRecovery() { return 40; }
    @Override protected int siegePrepare() { return ryuna.qdeath.limbo.LimboTuning.SIEGE_PREPARE_OSSUARY; }
    @Override protected void onWindup() { playSound(ModSounds.OSSUARY_RATTLE, 1.0f, 0.7f); }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        doHurtTarget(level, q);
        Vec3 push = q.position().subtract(position()).normalize().scale(1.2);
        q.push(push.x, 0.3, push.z);
        q.syncVelocity = true;
        playSound(ModSounds.OSSUARY_SLAM, 1.4f, 0.8f);
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (shedCooldown > 0) shedCooldown--;
        if (shedPrep > 0) {
            getNavigation().stop();
            if (--shedPrep == 0) shed(level, q);
            return true;
        }
        if (occupants > 0 && shedCooldown == 0 && visible && distance > 3 && distance < 12) { prepareShed(); return true; }
        return false;
    }

    private void prepareShed() {
        shedPrep = 24;
        shedCooldown = 260;
        playAnim(ANIM_SPECIAL, 24);
        playSound(ModSounds.OSSUARY_RATTLE, 1.4f, 1.0f);
    }

    /** One of the carried dead drops out of the heap beside it. No new reserve is ever created. */
    private void shed(ServerLevel level, @Nullable LivingEntity q) {
        if (occupants <= 0) return;
        occupants--;
        EntityType<? extends LimboDead> type = random.nextFloat() < 0.4f ? HellEntities.CRAWLER : HellEntities.HOLLOW;
        LimboDead d = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) return;
        double a = random.nextDouble() * Math.PI * 2;
        for (int t = 0; t < 6 && !ryuna.qdeath.limbo.Processions.clearOfPlayers(level, BlockPos.containing(getX() + Math.cos(a) * 1.6, getY(), getZ() + Math.sin(a) * 1.6), 1.2); t++)
            a += Math.PI / 3; // drops out on the side away from whoever is pressed against it
        d.snapTo(getX() + Math.cos(a) * 1.6, getY(), getZ() + Math.sin(a) * 1.6, random.nextFloat() * 360, 0);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        d.setHome(home());
        if (fromPlague() && home() != null) d.setPlagueOrigin(plagueFocus(), home()); // the plague's mark passes to what it carries
        level.addFreshEntity(d);
        d.emerge(20);
        if (q != null && LimboSenses.isLife(q)) d.engage(q);
        level.sendParticles(ParticleTypes.POOF, d.getX(), d.getY() + 0.6, d.getZ(), 14, 0.4, 0.4, 0.4, 0.02);
        playSound(SoundEvents.SKELETON_STEP, 1.2f, 0.6f);
    }

    /** Wounds loosen the heap: it sheds at 70%, 40% and 15% health even when nobody is in view. */
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float before = getHealth() / getMaxHealth();
        boolean hurt = super.hurtServer(level, source, damage);
        float after = getHealth() / getMaxHealth();
        if (hurt && isAlive() && occupants > 0 && shedPrep == 0)
            for (float t : new float[]{0.7f, 0.4f, 0.15f}) if (before > t && after <= t) { prepareShed(); shedCooldown = 60; break; }
        return hurt;
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Occupants", occupants);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        occupants = in.getIntOr("Occupants", 3);
    }

    @Override protected SoundEvent recognizeSound() { return ModSounds.OSSUARY_RATTLE; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.OSSUARY_RATTLE; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }
    @Override public float getVoicePitch() { return 0.6f; }
}
