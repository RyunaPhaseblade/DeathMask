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
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Foreman of the Mourning (Capataz del duelo): a broad dead in a greatcoat and a flat cap, an armband on its sleeve. It
 * keeps the line the way it did in life: it stops, barks and points, and the dead hunting around it split up, some
 * pressing straight on while others take reachable routes round to the sides and the back. It knows only what it sees
 * and gives no knowledge of its own; kill it and the groups lose their routes (they keep hunting). Faced, it swings a
 * heavy arm, planted, after a long draw.
 */
public class ForemanEntity extends LimboDead {
    private int signal, signalCooldown = 60;

    public ForemanEntity(EntityType<? extends ForemanEntity> type, Level level) { super(type, level); xpReward = 16; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override public int skinCount() { return 3; }
    @Override protected double chaseFraction() { return LimboTuning.CHASE_FOREMAN; }
    @Override protected double sightRange() { return 22; }
    @Override protected double callRadius() { return 0; }
    @Override protected double reach() { return 2.0; }
    @Override protected int windupTicks() { return 14; }
    @Override protected int attackRecovery() { return 30; }
    @Override protected boolean windupMoves() { return false; }
    @Override protected boolean priorityVoice() { return true; }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        if (signalCooldown > 0) signalCooldown--;
        if (signal > 0) {
            getNavigation().stop();
            getLookControl().setLookAt(q, 30f, 30f);
            if (--signal == 12) direct(level, q);
            return true;
        }
        if (visible && signalCooldown == 0 && distance > 3 && distance < 24 && helpers(level, q).size() >= 2) {
            signal = 26;
            signalCooldown = 140 + random.nextInt(60);
            playAnim(ANIM_SPECIAL, 26);
            playSound(ModSounds.FOREMAN_SIGNAL, 2.2f, 0.9f + random.nextFloat() * 0.1f);
            return true;
        }
        return false;
    }

    private List<LimboDead> helpers(ServerLevel level, LivingEntity q) {
        return level.getEntitiesOfClass(LimboDead.class, getBoundingBox().inflate(20),
                d -> d != this && d.isAlive() && d.quarry() == q && d.mind() == Mind.PURSUIT && !(d instanceof ForemanEntity));
    }

    /**
     * The order: the dead hunting the same quarry are split by where they stand. The nearest third keeps pressing; the
     * others are sent to reachable points to the left, the right and behind the quarry, as seen from the Foreman.
     */
    private void direct(ServerLevel level, LivingEntity q) {
        List<LimboDead> group = new ArrayList<>(helpers(level, q));
        if (group.isEmpty()) return;
        if (expanded() && besiege(level, q, group)) return;
        group.sort(Comparator.comparingDouble(d -> d.distanceToSqr(q)));
        Vec3 front = new Vec3(q.getX() - getX(), 0, q.getZ() - getZ()).normalize();
        double[] angles = {Math.toRadians(100), Math.toRadians(-100), Math.toRadians(180)};
        List<Vec3> accesses = new ArrayList<>();
        for (double a : angles) {
            Vec3 dir = new Vec3(front.x * Math.cos(a) - front.z * Math.sin(a), 0, front.x * Math.sin(a) + front.z * Math.cos(a));
            Vec3 offset = dir.scale(4.5);
            Vec3 at = q.position().add(offset);
            Path probe = group.getLast().getNavigation().createPath(at.x, at.y, at.z, 1);
            if (probe != null && probe.canReach()) accesses.add(offset);
        }
        int keep = Math.max(1, group.size() / 3);
        for (int i = 0; i < group.size(); i++) {
            LimboDead d = group.get(i);
            d.playAnim(ANIM_RECOGNIZE, 8); // heads turn to the gesture: the response is visible
            if (i < keep || accesses.isEmpty()) continue;
            d.assignApproach(accesses.get(i % accesses.size()), 120);
        }
    }

    /**
     * When the quarry is out of reach, the Foreman's priorities are a breach, then a climb: a Ticker of the group is sent
     * at the barrier, and the bodies are pointed at the foot of the wall below an elevated quarry (where they can pile up).
     * It only orders what it sees; its death stops new orders, not the hunt.
     */
    private boolean besiege(ServerLevel level, LivingEntity q, List<LimboDead> group) {
        boolean ordered = false;
        for (LimboDead d : group)
            if (d instanceof BreachTickerEntity t && t.orderBreach(level, q.position())) { d.playAnim(ANIM_RECOGNIZE, 8); ordered = true; break; }
        if (q.getY() - getY() >= 1.5) {
            ryuna.qdeath.limbo.Horde.Ledge ledge = ryuna.qdeath.limbo.Horde.findLedge(level, this, q);
            if (ledge != null) {
                Vec3 foot = Vec3.atBottomCenterOf(ledge.base()).subtract(q.position());
                for (LimboDead d : group) {
                    if (!d.moundEligible()) continue;
                    d.playAnim(ANIM_RECOGNIZE, 8);
                    d.assignApproach(foot, 120);
                }
                ordered = true;
            }
        }
        return ordered;
    }

    @Override public boolean moundEligible() { return false; }

    @Override protected void strike(ServerLevel level, LivingEntity q) {
        if (doHurtTarget(level, q)) {
            Vec3 push = q.position().subtract(position()).normalize().scale(0.9);
            q.push(push.x, 0.25, push.z);
            q.syncVelocity = true;
        }
    }

    @Override protected void onWindup() { playSound(ModSounds.FOREMAN_AMBIENT, 1.3f, 0.8f); }
    @Override protected SoundEvent recognizeSound() { return ModSounds.FOREMAN_SIGNAL; }
    @Override protected @Nullable SoundEvent chaseSound() { return ModSounds.FOREMAN_AMBIENT; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.FOREMAN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.DEAD_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEAD_DEATH; }
    @Override public float getVoicePitch() { return 0.72f; }
}
