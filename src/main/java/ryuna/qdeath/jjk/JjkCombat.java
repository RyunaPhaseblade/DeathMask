package ryuna.qdeath.jjk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Targeting, damage and the damage hooks shared by every technique. All server-side. */
public final class JjkCombat {
    private JjkCombat() {}

    public static final ResourceKey<DamageType> TECHNIQUE = ResourceKey.create(Registries.DAMAGE_TYPE, Quantumdeath.id("cursed_technique"));
    /** Tagged {@code #minecraft:is_fire}: fire-immune creatures (and Jogo) shrug it off. */
    public static final ResourceKey<DamageType> FIRE = ResourceKey.create(Registries.DAMAGE_TYPE, Quantumdeath.id("cursed_fire"));
    /** Tagged {@code #minecraft:is_freezing}. */
    public static final ResourceKey<DamageType> FROST = ResourceKey.create(Registries.DAMAGE_TYPE, Quantumdeath.id("cursed_frost"));

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, owner);
    }

    public static boolean isTechnique(DamageSource s) { return s.is(TECHNIQUE) || s.is(FIRE) || s.is(FROST); }

    /** Whether {@code owner}'s techniques may affect {@code e}: alive, not the caster, not the caster's pets, PvP respected. */
    public static boolean canHit(ServerPlayer owner, Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive() || e == owner || e.isSpectator()) return false;
        if (e instanceof Player other) {
            if (other.isCreative()) return false;
            if (!owner.canHarmPlayer(other)) return false;
        }
        if (e instanceof OwnableEntity pet && pet.getOwner() == owner) return false;
        // Techniques (and their areas) never reach a yokai the caster cannot fully see.
        if (e instanceof ryuna.qdeath.yokai.YokaiCreature && !ryuna.qdeath.yokai.Perception.mayHurt(owner, e)) return false;
        return !(e instanceof ArmorStand stand && stand.isMarker());
    }

    /** Deals technique damage. {@code ignoreFrames}: separate hits of one technique all land (no invulnerability window). */
    public static boolean hurt(ServerPlayer owner, LivingEntity target, @Nullable Entity direct, ResourceKey<DamageType> type, float amount, boolean ignoreFrames) {
        if (!canHit(owner, target) || amount <= 0) return false;
        ServerLevel level = (ServerLevel) target.level();
        if (ignoreFrames) target.setInvulnerableTime(0);
        return target.hurtServer(level, source(level, type, direct == null ? owner : direct, owner), amount);
    }

    public static List<LivingEntity> around(ServerPlayer owner, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> canHit(owner, e)))
            if (e.getBoundingBox().getCenter().distanceToSqr(c) <= (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5)) out.add(e);
        return out;
    }

    /** Living targets in a cone from {@code origin} along {@code dir} (normalized), with line of sight. */
    public static List<LivingEntity> cone(ServerPlayer owner, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
        double cos = Math.cos(Math.toRadians(halfAngleDeg));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(range), e -> canHit(owner, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
            double d = to.length();
            if (d > range + e.getBbWidth() || d < 1e-3) continue;
            if (to.scale(1 / d).dot(dir) < cos && d > 1.5) continue;
            if (los(owner.level(), origin, e.getBoundingBox().getCenter())) out.add(e);
        }
        return out;
    }

    public static boolean los(Level level, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /** The entity closest to the crosshair within {@code coneDeg} (aim assist), in range and in sight. */
    public static @Nullable Entity aimed(ServerPlayer p, double range, double coneDeg, Predicate<Entity> filter) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double cos = Math.cos(Math.toRadians(coneDeg));
        Entity best = null;
        double bestScore = -1;
        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(Math.max(2, range * Math.tan(Math.toRadians(coneDeg)) + 1));
        for (Entity e : p.level().getEntities(p, box, filter)) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 to = c.subtract(eye);
            double d = to.length();
            if (d > range || d < 0.5) continue;
            boolean direct = e.getBoundingBox().inflate(0.3).clip(eye, eye.add(look.scale(range))).isPresent();
            double dot = to.scale(1 / d).dot(look);
            if (!direct && dot < cos) continue;
            if (!los(p.level(), eye, c) && !los(p.level(), eye, e.getEyePosition())) continue;
            double score = (direct ? 2 : dot) - d / (range * 10);
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /** Where the crosshair lands (block or living target), or the point at {@code range}. */
    public static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 hit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        Entity e = aimed(p, eye.distanceTo(hit), 3, x -> canHit(p, x));
        return e != null ? e.position() : hit;
    }

    /** The ground under {@code v} (searching {@code down} blocks), or {@code v} itself in the air. */
    public static Vec3 ground(Level level, Vec3 v, double down) {
        BlockHitResult r = level.clip(new ClipContext(v.add(0, 0.5, 0), v.add(0, -down, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return r.getType() == HitResult.Type.MISS ? v : r.getLocation();
    }

    public static void knock(Entity e, Vec3 dir, double strength, double up) {
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        if (h.lengthSqr() > 1e-6) h = h.normalize().scale(strength);
        e.push(h.x, up, h.z);
        e.syncVelocity = true;
    }

    // ------------------------------------------------------------------------------------------------ hooks

    /** From the LivingEntity damage mixin: technique-driven damage multipliers on everything a living thing takes. */
    public static float modifyIncoming(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !(target.level() instanceof ServerLevel)) return amount;
        float mult = 1f + Freeze.vulnerability(target);
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            Cast c = Techniques.cast(attacker, null);
            if (c != null) mult *= c.cls.hooks.outgoingMultiplier(c, target, source, amount);
        }
        return amount * mult;
    }

    /** Immunities: Jogo to fire, Uraume to frost. */
    private static boolean allowDamage(LivingEntity e, DamageSource s, float amount) {
        if (!(e instanceof ServerPlayer p)) return true;
        if (Techniques.isClass(p, "jogo") && s.is(DamageTypeTags.IS_FIRE)) { p.clearFire(); return false; }
        return !(Techniques.isClass(p, "uraume") && s.is(DamageTypeTags.IS_FREEZING));
    }

    static void afterHit(ServerPlayer p, LivingEntity target, DamageSource source, float taken, boolean technique) {
        Cast c = Techniques.cast(p, null);
        if (c != null) c.cls.hooks.afterHit(c, target, source, taken, technique);
    }

    /** True when the hit is a plain melee strike by the player (not a projectile or technique). */
    public static boolean isMelee(DamageSource s, Entity attacker) {
        return s.getDirectEntity() == attacker && s.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK);
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(JjkCombat::allowDamage);
    }
}
