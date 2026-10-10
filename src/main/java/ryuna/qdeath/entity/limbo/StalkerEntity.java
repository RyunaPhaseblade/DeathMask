package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Stalker (Acechador): a thin body with one shoulder knotted into a hump and a jaw that juts out. Getting behind
 * its quarry is all it wants: it reads where the victim is facing and walks a real route round to the back, starting
 * over whenever the victim turns. Before the slash it scrapes its claws and lifts the hunched shoulder. From behind the
 * slash is vicious; face it, or keep a wall at your back, and it can only strike from the front, weakly, after
 * giving up on the flank. No teleporting, no passing through blocks, nothing tied to being watched.
 */
public class StalkerEntity extends LimboDead {
    private static final net.minecraft.resources.Identifier BACKSTAB = Quantumdeath.id("stalker_backstab");
    private int flankFail, repath, signal;
    private float side = 1;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) { super(type, level); xpReward = 10; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_STALKER; }
    @Override protected double sightRange() { return 22; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 1.9; }
    @Override protected int windupTicks() { return 7; }
    @Override protected int attackRecovery() { return 30; }
    @Override protected boolean priorityVoice() { return true; }

    /** True when this stalker stands behind {@code q} (outside a 110 degree cone in front of its face). */
    public boolean behind(LivingEntity q) {
        Vec3 look = Vec3.directionFromRotation(0, q.getYHeadRot());
        Vec3 to = new Vec3(getX() - q.getX(), 0, getZ() - q.getZ()).normalize();
        return look.dot(to) < -0.34;
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (signal > 0) {
            // The tell: claws scrape, the shoulder rises; then the slash.
            if (--signal == 0) startWindup();
            return true;
        }
        boolean back = behind(q);
        if (back && distance <= reach() + 0.3 && attackCooldown == 0 && visible) { tell(); return true; }
        if (flankFail > 70) {
            // No way round (a wall at the victim's back): it comes from the front, without its advantage.
            if (distance <= reach() && attackCooldown == 0 && visible) { tell(); return true; }
            if (--repath <= 0) { getNavigation().moveTo(q, chaseSpeed()); repath = 6; }
            if (back) flankFail = 0;
            return true;
        }
        if (back && distance < 6) {
            if (--repath <= 0) { getNavigation().moveTo(q, chaseSpeed()); repath = 4; }
            return true;
        }
        if (distance > 16) return false; // close the distance normally first
        // Circle toward the back: a point on a ring around the victim, 70 degrees further round than where it stands now.
        if (--repath <= 0) {
            float face = q.getYHeadRot() * Mth.DEG_TO_RAD;
            Vec3 to = position().subtract(q.position());
            double ang = Math.atan2(to.z, to.x);
            double backAng = Math.atan2(Math.cos(face), -Math.sin(face)) + Math.PI; // direction behind the face
            double diff = Mth.wrapDegrees((float) Math.toDegrees(backAng - ang));
            side = diff >= 0 ? 1 : -1;
            double target = ang + side * Math.toRadians(Math.min(70, Math.abs(diff)));
            double r = Math.max(2.5, Math.min(4.5, distance));
            Vec3 goal = q.position().add(Math.cos(target) * r, 0, Math.sin(target) * r);
            boolean ok = getNavigation().moveTo(goal.x, goal.y, goal.z, chaseSpeed());
            var path = getNavigation().getPath();
            if (!ok || path == null || !path.canReach()) flankFail += 6; else flankFail = Math.max(0, flankFail - 2);
            repath = 6;
        }
        return true;
    }

    private void tell() {
        signal = 6;
        getNavigation().stop();
        playAnim(ANIM_SPECIAL, 10);
        playSound(ModSounds.STALKER_SCRAPE, 1.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean back = behind(q);
        if (back && dmg != null) dmg.addTransientModifier(new AttributeModifier(BACKSTAB, 4.0, AttributeModifier.Operation.ADD_VALUE));
        doHurtTarget(level, q);
        if (dmg != null) dmg.removeModifier(BACKSTAB);
        if (!back) flankFail = 0; // try the back again next time
    }

    @Override protected boolean windupMoves() { return false; }
    @Override protected SoundEvent recognizeSound() { return ModSounds.STALKER_HISS; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.STALKER_HISS; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.STALKER_HISS; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 1.15f + (random.nextFloat() - 0.5f) * 0.1f; }
    @Override public int getAmbientSoundInterval() { return 220; }
}
