package ryuna.qdeath.entity.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.projectile.BodyProjectile;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;

/**
 * The dead that throw parts of themselves. They keep six to twelve blocks from their quarry when the ground lets them,
 * and throw only along a path they can see is clear: never through cover, never through one of their own (a dead in the
 * way cancels the throw). Each throw is announced by a pose and a sound held long enough to step out of it or raise a
 * shield; a hit during the windup spoils it. When the quarry hides they go to look or move round, they do not shoot
 * blind.
 */
public abstract class RangedDead extends LimboDead {
    private int shotCooldown = 30, aim = -1, nextStep, clearRetry;
    private @Nullable LivingEntity aimed;
    /** What it observed of its quarry: last position seen and the motion between sightings. */
    private @Nullable Vec3 seenAt;
    private Vec3 seenMotion = Vec3.ZERO;
    private long seenTick;

    protected RangedDead(EntityType<? extends Monster> type, Level level) { super(type, level); }

    protected abstract double range();
    protected abstract int prepareTicks();
    protected abstract int cooldownTicks();
    protected abstract double gravity();
    /** Flight time, in ticks, to a point {@code distance} away. */
    protected abstract int flightTicks(double distance);
    /** Ticks of observed motion the aim may lead by (0: aim at where it is). */
    protected int leadTicks() { return 0; }
    protected abstract void onAim(ServerLevel level);
    protected abstract BodyProjectile createShot(ServerLevel level);
    protected abstract void onThrow(ServerLevel level);

    public boolean aiming() { return aim >= 0; }
    /** It throws from a distance: it never holds up or climbs a mound. */
    @Override public boolean moundEligible() { return false; }

    /** Where the throw leaves the body (chest height, a little forward). */
    protected Vec3 muzzle() {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(f.x * 0.4, 1.25, f.z * 0.4);
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (shotCooldown > 0) shotCooldown--;
        if (aim >= 0) { aiming(level); return; }
        super.customServerAiStep(level);
    }

    private void observe(LivingEntity q, boolean visible) {
        if (!visible) return;
        long now = level().getGameTime();
        if (seenAt != null && now - seenTick <= 4 && now > seenTick)
            seenMotion = seenMotion.scale(0.5).add(q.position().subtract(seenAt).scale(0.5 / (now - seenTick)));
        else if (seenAt == null || now - seenTick > 4) seenMotion = Vec3.ZERO;
        seenAt = q.position();
        seenTick = now;
    }

    /** Where to aim: the chest, led by what was observed (never more than {@link #leadTicks()} of it). */
    private Vec3 aimPoint(LivingEntity q) {
        Vec3 chest = q.position().add(0, q.getBbHeight() * 0.55, 0);
        int lead = Math.min(leadTicks(), flightTicks(muzzle().distanceTo(chest)));
        return lead <= 0 ? chest : chest.add(seenMotion.x * lead, 0, seenMotion.z * lead);
    }

    @Override
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) {
        observe(q, visible);
        // The whole arc is checked against blocks and the horde; a blocked line (an ally in the way) is looked at again
        // a quarter of a second later, not every tick.
        boolean ready = visible && distance <= range() && shotCooldown == 0 && --clearRetry <= 0 && Consecration.at(level, q.position()) == null;
        if (ready && !clear(level, q)) clearRetry = 5;
        else if (ready) {
            aimed = q;
            aim = 0;
            getNavigation().stop();
            playAnim(ANIM_SPECIAL, prepareTicks() + 6);
            onAim(level);
            return true;
        }
        if (!visible) return false; // lost from view: go and look (the ordinary pursuit), never shoot blind
        if (distance < LimboTuning.SHOOTER_MIN) {
            // Too close: back off to throwing distance if the ground allows it.
            if (--nextStep <= 0) {
                nextStep = 10;
                Vec3 away = position().subtract(q.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0e-3) away = new Vec3(1, 0, 0);
                Vec3 to = q.position().add(away.normalize().scale(9));
                Path p = getNavigation().createPath(to.x, to.y, to.z, 1);
                if (p != null && p.canReach()) getNavigation().moveTo(p, chaseSpeed());
                else return false; // cornered: it fights with its hands
            }
            getLookControl().setLookAt(q, 30f, 30f);
            return true;
        }
        if (distance <= LimboTuning.SHOOTER_MAX) {
            // In the band: hold the place and wait for the throw.
            getNavigation().stop();
            getLookControl().setLookAt(q, 30f, 30f);
            return true;
        }
        return false;
    }

    /** The path of the throw is clear of blocks and of the horde. */
    private boolean clear(ServerLevel level, LivingEntity q) {
        Vec3 from = muzzle(), to = aimPoint(q);
        int t = Math.max(2, flightTicks(from.distanceTo(to)));
        Vec3[] pts = BodyProjectile.arc(from, BodyProjectile.launch(from, to, t, gravity()), t, gravity(), 6);
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 a = pts[i], b = i == pts.length - 2 ? to : pts[i + 1];
            if (level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) return false;
            AABB box = new AABB(a, b).inflate(0.4);
            boolean ally = !level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e != q
                    && (e instanceof LimboDead || Possession.isPossessed(e)) && e.getBoundingBox().inflate(0.3).clip(a, b).isPresent()).isEmpty();
            if (ally) return false;
        }
        return true;
    }

    private void aiming(ServerLevel level) {
        LivingEntity q = aimed;
        getNavigation().stop();
        if (q == null || !q.isAlive()) { aim = -1; return; }
        getLookControl().setLookAt(q, 40f, 40f);
        observe(q, getSensing().hasLineOfSight(q));
        if (++aim < prepareTicks()) return;
        aim = -1;
        aimed = null;
        // Checked again at release: cover that came up, a dead stepping into the line, consecrated light.
        if (!getSensing().hasLineOfSight(q) || Consecration.at(level, q.position()) != null || !clear(level, q)) { shotCooldown = 20; return; }
        Vec3 from = muzzle(), to = aimPoint(q);
        int t = Math.max(2, flightTicks(from.distanceTo(to)));
        BodyProjectile shot = createShot(level);
        shot.setOwner(this);
        shot.setPos(from);
        shot.setDeltaMovement(BodyProjectile.launch(from, to, t, gravity()));
        level.addFreshEntity(shot);
        shotCooldown = cooldownTicks();
        onThrow(level);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        // A hit during the windup spoils the throw.
        if (hurt && isAlive() && aim >= 0) { aim = -1; aimed = null; shotCooldown = 20; stagger(12); }
        return hurt;
    }
}
