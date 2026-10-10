package ryuna.qdeath.yokai;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Goals shared by every yokai: perception-aware hostility, and the companion orders. */
public final class YokaiGoals {
    private YokaiGoals() {}

    // ------------------------------------------------------------------------------------------------ Kodama shelters

    private record Shelter(ResourceKey<Level> dim, Vec3 pos, long until) {}
    private static final List<Shelter> SHELTERS = new ArrayList<>();

    public static void shelter(ServerLevel level, Vec3 pos, long until) {
        SHELTERS.removeIf(s -> s.until < level.getGameTime());
        SHELTERS.add(new Shelter(level.dimension(), pos, until));
    }

    /** Detection distance multiplier for hostile yokai looking at someone at {@code at}: reduced inside a Kodama's shelter. */
    public static double detectionFactor(Level level, Vec3 at) {
        double r2 = YokaiBalance.Kodama.SHELTER_RADIUS * YokaiBalance.Kodama.SHELTER_RADIUS;
        for (Shelter s : SHELTERS)
            if (s.dim == level.dimension() && s.until >= level.getGameTime() && s.pos.distanceToSqr(at) <= r2) return YokaiBalance.Kodama.DETECTION_FACTOR;
        return 1.0;
    }

    public static boolean sheltered(Level level, Vec3 at) { return detectionFactor(level, at) < 1.0; }

    public static void clearShelters() { SHELTERS.clear(); }

    // ------------------------------------------------------------------------------------------------ hostility

    /**
     * Picks the nearest player this creature may pursue: detected within range (shrunk by a Kodama shelter), with line
     * of sight or very close, and allowed by the per-pair perception rules. Every detection keeps the encounter alive.
     */
    public static class Hostile extends TargetGoal {
        private final YokaiCreature yokai;
        private final double range;
        private int scan;

        public Hostile(Mob mob, double range) {
            super(mob, false, false);
            this.yokai = (YokaiCreature) mob;
            this.range = range;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            // The four original creatures keep their own Hell targeting there.
            if (yokai.yokai().bonded() || Perception.exempt(mob) || --scan > 0) return false;
            scan = 10;
            ServerPlayer best = null;
            double bestD = Double.MAX_VALUE;
            for (ServerPlayer p : ((ServerLevel) mob.level()).players()) {
                if (!Perception.targetable(p)) continue;
                double r = range * detectionFactor(mob.level(), p.position());
                double d = p.distanceToSqr(mob);
                if (d > r * r || !(d < 16 || mob.getSensing().hasLineOfSight(p))) continue;
                yokai.yokai().detect(p);
                if (yokai.yokai().mayPursue(p) && d < bestD) { bestD = d; best = p; }
            }
            if (best == null) return false;
            targetMob = best;
            return true;
        }

        @Override public void start() {
            mob.setTarget(targetMob);
            super.start();
        }

        @Override public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            if (!(t instanceof ServerPlayer p) || !p.isAlive() || !yokai.yokai().mayPursue(p)) return false;
            if (mob.distanceToSqr(p) > range * range * 2.25) return false;
            if (mob.getSensing().hasLineOfSight(p)) yokai.yokai().detect(p);
            return yokai.yokai().encounter(p) != null;
        }

        @Override protected double getFollowDistance() { return range * 1.5; }
    }

    /** The creature hurts {@code target} only when the pair rules allow it (used by area attacks). */
    public static boolean mayHarm(YokaiCreature yokai, LivingEntity target) {
        if (target instanceof ServerPlayer p) return yokai.yokai().mayPursue(p);
        if (yokai.yokai().bonded()) return yokai.yokai().defendTarget(target);
        return false;
    }

    // ------------------------------------------------------------------------------------------------ companions

    private static boolean resting(YokaiCreature y) { return y.yokai().bonded() && !y.yokai().ownerOnline(); }

    /** Follows the owner (teleporting when far, only into loaded ground). */
    public static class Follow extends Goal {
        private final Mob mob;
        private final YokaiCreature y;
        private final double speed;
        private int recalc;

        public Follow(Mob mob, double speed) {
            this.mob = mob;
            this.y = (YokaiCreature) mob;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private @Nullable ServerPlayer owner() { return y.yokai().order() == YokaiCore.Order.STAY ? null : y.yokai().ownerPlayer(); }

        @Override public boolean canUse() {
            ServerPlayer o = owner();
            return o != null && !o.isSpectator() && mob.distanceToSqr(o) > 36 && mob.getTarget() == null && !mob.isPassenger();
        }

        @Override public boolean canContinueToUse() {
            ServerPlayer o = owner();
            return o != null && mob.distanceToSqr(o) > 9 && mob.getTarget() == null && !mob.getNavigation().isDone();
        }

        @Override public void tick() {
            ServerPlayer o = owner();
            if (o == null) return;
            mob.getLookControl().setLookAt(o, 10f, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            double far = YokaiBalance.Companion.FOLLOW_TELEPORT;
            if (mob.distanceToSqr(o) > far * far) teleportNear(o);
            else mob.getNavigation().moveTo(o, speed);
        }

        @Override public void stop() { mob.getNavigation().stop(); }

        private void teleportNear(ServerPlayer o) {
            BlockPos base = o.blockPosition();
            for (int i = 0; i < 10; i++) {
                BlockPos p = base.offset(mob.getRandom().nextInt(7) - 3, mob.getRandom().nextInt(3) - 1, mob.getRandom().nextInt(7) - 3);
                if (!mob.level().isLoaded(p) || p.distSqr(base) < 4) continue;
                if (!y.canStandAt((ServerLevel) mob.level(), p)) continue;
                mob.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, mob.getYRot(), mob.getXRot());
                if (mob.level().noCollision(mob)) { mob.getNavigation().stop(); return; }
            }
        }
    }

    /** STAY, or the owner is away: hold position and do nothing else. */
    public static class Rest extends Goal {
        private final Mob mob;
        private final YokaiCreature y;

        public Rest(Mob mob) {
            this.mob = mob;
            this.y = (YokaiCreature) mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override public boolean canUse() {
            return y.yokai().bonded() && (resting(y) || y.yokai().order() == YokaiCore.Order.STAY && mob.getTarget() == null);
        }

        @Override public void start() {
            mob.getNavigation().stop();
            if (resting(y)) mob.setTarget(null);
        }
    }

    /** DEFEND: attacks what attacks the owner or what the owner attacks (players only as PvP and teams allow). */
    public static class Defend extends TargetGoal {
        private final YokaiCreature y;

        public Defend(Mob mob) {
            super(mob, false, false);
            this.y = (YokaiCreature) mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            if (!y.yokai().bonded() || y.yokai().order() != YokaiCore.Order.DEFEND || resting(y)) return false;
            ServerPlayer o = y.yokai().ownerPlayer();
            if (o == null) return false;
            LivingEntity t = o.getLastHurtByMob();
            if (t == null || o.tickCount - o.getLastHurtByMobTimestamp() > 200 || !y.yokai().defendTarget(t)) t = o.getLastHurtMob();
            if (t == null || !y.yokai().defendTarget(t) || t.distanceToSqr(mob) > 32 * 32) return false;
            targetMob = t;
            return true;
        }

        @Override public void start() {
            mob.setTarget(targetMob);
            super.start();
        }

        @Override public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            return t != null && y.yokai().order() == YokaiCore.Order.DEFEND && y.yokai().defendTarget(t) && !resting(y) && super.canContinueToUse();
        }
    }
}
