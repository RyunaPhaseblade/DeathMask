package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The horde's instincts, after Enhanced Hordes (nojustgavin): the dead climb each other, set each other alight, and dig
 * their fellows up out of soft ground.
 *
 * <p>Climbing: a hunting dead whose feet are inside another dead's body is pushed up over it (its ordinary movement, so it
 * collides with blocks and ceilings like anything else) and, once above, glides down for a moment. The higher climbs the
 * lower; level with each other, the one behind climbs the one ahead. Nothing is placed in the world, nobody rides anybody,
 * nobody is held in the air: a crowd pressing at the foot of a wall becomes a heaving pile whose top spills over the edge,
 * and on open ground the ones behind scramble over the ones ahead. Under a quarry standing above a wall the horde shares
 * one pile, so the bodies go to the same foot.
 */
public final class Horde {
    private Horde() {}

    /** Poses of a body in a pile (synced as LimboDead's mound role): pressing at the foot, bearing others, climbing. */
    public static final byte NONE = 0, PRESS = 1, BEAR = 2, CLIMB = 4;
    /** Soft ground the dead dig their fellows out of. */
    public static final TagKey<Block> DIG_GROUND = TagKey.create(Registries.BLOCK, Quantumdeath.id("dead_dig_ground"));
    /** Counters for /deathmask limbo stats and the tests. */
    public static int lifts, piles, overTop, ignitions, digs, risen;

    /** Where a pile stands: the cell at the foot of the wall, the direction into the wall, and the wall's height. */
    public record Ledge(BlockPos base, Direction wall, int height) {
        public BlockPos exit() { return base.relative(wall).above(height); }
        public Vec3 foot() { return Vec3.atBottomCenterOf(base); }
    }

    private record Pile(ResourceKey<Level> dim, Ledge ledge, long until) {}
    private static final Map<UUID, Pile> PILES = new HashMap<>();

    // ------------------------------------------------------------------ every tick of an expanded dead
    public static void tick(ServerLevel level, LimboDead d) {
        if (d.isOnFire()) burn(level, d);
        if (d.mind() == Mind.PURSUIT && d.moundEligible() && !d.digging() && !d.isNoGravity()) climb(d);
        int now = d.tickCount;
        d.setMoundRole(now - d.lastLift() < 8 ? CLIMB : now - d.lastBorne() < 8 ? BEAR : d.piling() && d.onGround() ? PRESS : NONE);
    }

    private static void climb(LimboDead d) {
        double r = LimboTuning.STACK_REACH;
        AABB feet = new AABB(d.getX() - r, d.getY() - r, d.getZ() - r, d.getX() + r, d.getY() + r, d.getZ() + r);
        LivingEntity q = d.quarry();
        LimboDead under = null;
        for (LimboDead o : d.level().getEntitiesOfClass(LimboDead.class, feet, o -> o != d && o.isAlive()))
            if (below(o, d, q)) { under = o; break; }
        if (under == null) return;
        RandomSource rnd = d.getRandom();
        // As in Enhanced Hordes: mostly the full push, now and then less, with a little sideways scramble.
        double lift = Math.min(rnd.nextDouble(), LimboTuning.STACK_LIFT);
        Vec3 v = d.getDeltaMovement();
        d.setDeltaMovement(v.x + (rnd.nextDouble() - 0.5) * lift / 10, lift, v.z + (rnd.nextDouble() - 0.5) * lift / 10);
        d.resetFallDistance();
        d.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LimboTuning.STACK_GLIDE, 0, false, false));
        if (d.tickCount - d.lastLift() > 20) lifts++;
        d.climbed();
        under.borne();
        if (rnd.nextInt(14) == 0) d.playSound(ModSounds.MOUND_CLIMB, 0.8f, 0.9f + rnd.nextFloat() * 0.25f);
    }

    /** {@code o} is under {@code d}: lower, or level with it and nearer the quarry (the one behind climbs the one ahead). */
    private static boolean below(LimboDead o, LimboDead d, @Nullable LivingEntity q) {
        double dy = d.getY() - o.getY();
        if (dy > 0.05) return true;
        if (dy < -0.05 || q == null) return false;
        return horizontalSqr(o.position(), q.position()) < horizontalSqr(d.position(), q.position());
    }

    /** A burning dead runs faster while it burns and sets alight every dead it touches. */
    private static void burn(ServerLevel level, LimboDead d) {
        if (!d.hasEffect(MobEffects.SPEED))
            d.addEffect(new MobEffectInstance(MobEffects.SPEED, Math.max(20, d.getRemainingFireTicks()), LimboTuning.BURN_SPEED, false, false));
        for (LimboDead o : level.getEntitiesOfClass(LimboDead.class, d.getBoundingBox().inflate(0.1), o -> o != d && o.isAlive() && !o.isOnFire() && !o.fireImmune())) {
            o.igniteForSeconds(LimboTuning.FIRE_SPREAD);
            ignitions++;
        }
    }

    // ------------------------------------------------------------------ piles under an elevated quarry
    /**
     * A dead that cannot reach a quarry standing above a wall heads for a pile against it: the one its horde already
     * started (within PILE_JOIN blocks), or one at the wall between it and the quarry. Null when there is no wall to pile
     * against or consecrated light covers its foot or its top.
     */
    public static @Nullable Ledge pile(ServerLevel level, LimboDead d, LivingEntity q) {
        long now = level.getGameTime();
        PILES.values().removeIf(p -> p.until <= now);
        Pile p = PILES.get(q.getUUID());
        boolean shared = p != null && p.dim == level.dimension() && stands(level, p.ledge);
        if (shared && p.ledge.foot().distanceTo(d.position()) <= LimboTuning.PILE_JOIN) return p.ledge;
        Ledge l = findLedge(level, d, q);
        if (l == null || Consecration.at(level, l.foot()) != null || Consecration.at(level, Vec3.atBottomCenterOf(l.exit())) != null) return null;
        // A pile whose wall is gone (or in another dimension) gives way to this one; a valid one far away keeps its own crowd.
        if (!shared) {
            PILES.put(q.getUUID(), new Pile(level.dimension(), l, now + LimboTuning.PILE_LIFE));
            piles++;
            level.playSound(null, l.base(), ModSounds.MOUND_GATHER, SoundSource.HOSTILE, 2.5f, 0.85f + level.getRandom().nextFloat() * 0.1f);
        }
        return l;
    }

    /** The pile of {@code l} stays shared while somebody is using it. */
    public static void refresh(ServerLevel level, LivingEntity q, Ledge l) {
        Pile p = PILES.get(q.getUUID());
        if (p != null && p.ledge.equals(l)) PILES.put(q.getUUID(), new Pile(p.dim, l, level.getGameTime() + LimboTuning.PILE_LIFE));
    }

    /** The quarry is still up there, by the top the pile leads to (the edge may hide it from those below). */
    public static boolean leadsTo(Ledge l, LivingEntity q) {
        Vec3 exit = Vec3.atBottomCenterOf(l.exit());
        return horizontalSqr(q.position(), exit) <= LimboTuning.PILE_REACH * LimboTuning.PILE_REACH && Math.abs(q.getY() - exit.y) <= 2.5;
    }

    /**
     * The wall between {@code from} and {@code target}: the first standable cell on the way whose neighbour toward the
     * target is a wall at least two blocks high with room on top, the whole column in front of it free (no ceiling, no
     * overhang) and solid ground under it. Null when there is none that leads up to where the target stands.
     */
    public static @Nullable Ledge findLedge(ServerLevel level, Entity from, LivingEntity target) {
        Vec3 a = from.position(), b = target.position();
        Vec3 flat = new Vec3(b.x - a.x, 0, b.z - a.z);
        double len = flat.length();
        if (len < 0.5) return null;
        Vec3 step = flat.scale(0.5 / len);
        int y = from.getBlockY();
        BlockPos last = null;
        for (int i = 0; i <= Math.min(24, len * 2 + 2); i++) {
            Vec3 p = a.add(step.scale(i));
            BlockPos cell = BlockPos.containing(p.x, y + 0.1, p.z);
            if (cell.equals(last)) continue;
            last = cell;
            if (!Processions.standable(level, cell)) {
                if (Processions.standable(level, cell.above())) { y++; cell = cell.above(); }
                else if (Processions.standable(level, cell.below())) { y--; cell = cell.below(); }
                else continue;
            }
            Direction w = Direction.getApproximateNearest(target.getX() - (cell.getX() + 0.5), 0, target.getZ() - (cell.getZ() + 0.5));
            if (w.getAxis().isVertical()) continue;
            BlockPos n = cell.relative(w);
            if (free(level, n)) continue; // no wall here yet
            int h = 1;
            while (h <= LimboTuning.PILE_MAX_HEIGHT + 1 && !free(level, n.above(h))) h++;
            if (h < 2) continue; // a step, not a wall
            if (h > LimboTuning.PILE_MAX_HEIGHT) return null;
            if (!free(level, n.above(h + 1))) return null; // no room to stand on top
            for (int k = 0; k <= h + 1; k++) if (!free(level, cell.above(k))) return null; // ceiling or overhang over the foot
            if (target.getY() < cell.getY() + h - 1.5) return null; // the wall is not what keeps it from the target
            return new Ledge(cell.immutable(), w, h);
        }
        return null;
    }

    /** The wall of {@code l} is still as it was found: its height, room on top, the column in front of it open. */
    public static boolean stands(ServerLevel level, Ledge l) {
        BlockPos n = l.base.relative(l.wall);
        for (int k = 0; k < l.height; k++) if (free(level, n.above(k))) return false;
        if (!free(level, n.above(l.height)) || !free(level, n.above(l.height + 1))) return false;
        for (int k = 0; k <= l.height + 1; k++) if (!free(level, l.base.above(k))) return false;
        return true;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    public static double horizontalSqr(Vec3 a, Vec3 b) { double dx = a.x - b.x, dz = a.z - b.z; return dx * dx + dz * dz; }

    /** Bodies a demonstration (and the tests) put round a tower of {@code height} so the pile gets over it. */
    public static int crowdFor(int height) { return 6 + 3 * height; }

    // ------------------------------------------------------------------ digging the dead up
    /** The soft ground under {@code d}'s feet, or null. */
    public static @Nullable BlockPos diggable(ServerLevel level, LimboDead d) {
        BlockPos p = BlockPos.containing(d.getX(), d.getY() - 0.5, d.getZ());
        return level.getBlockState(p).is(DIG_GROUND) ? p : null;
    }

    /** One clawing at the ground: the swing, the clods, the sound of the earth. */
    public static void claw(ServerLevel level, LimboDead d, BlockPos ground) {
        BlockState s = level.getBlockState(ground);
        d.playAnim(LimboDead.ANIM_SIEGE, 14);
        level.playSound(null, ground, s.getSoundType().getHitSound(), SoundSource.HOSTILE, 1.0f, 0.7f + d.getRandom().nextFloat() * 0.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), d.getX(), ground.getY() + 1.05, d.getZ(), 10, 0.35, 0.05, 0.35, 0.1);
    }

    /**
     * The dig is done: a common dead claws its way out of the ground beside the digger and joins the hunt. Those dug up
     * never dig (no chain of diggings), and they carry the digger's plague focus, if any.
     */
    public static @Nullable LimboDead digUp(ServerLevel level, LimboDead digger, LivingEntity q, BlockPos ground) {
        BlockPos at = digger.blockPosition();
        int turn = digger.getRandom().nextInt(4);
        for (int i = 0; i < 4; i++) {
            BlockPos c = at.relative(Direction.from2DDataValue((turn + i) & 3));
            if (Processions.standable(level, c)) { at = c; break; }
        }
        LimboDead d = HellEntities.HOLLOW.create(level, EntitySpawnReason.TRIGGERED);
        if (d == null) return null;
        float yaw = (float) (Mth.atan2(q.getZ() - at.getZ() - 0.5, q.getX() - at.getX() - 0.5) * Mth.RAD_TO_DEG) - 90f;
        d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
        d.markRisen();
        if (digger.fromPlague() && digger.home() != null) d.setPlagueOrigin(digger.plagueFocus(), digger.home());
        level.addFreshEntity(d);
        d.engage(q);
        d.emerge(30);
        BlockState s = level.getBlockState(ground);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 24, 0.4, 0.2, 0.4, 0.15);
        level.playSound(null, at, ModSounds.CRAWLER_SCRAPE, SoundSource.HOSTILE, 1.2f, 0.7f);
        risen++;
        return d;
    }

    public static void clear() {
        PILES.clear();
        lifts = piles = overTop = ignitions = digs = risen = 0;
    }
}
