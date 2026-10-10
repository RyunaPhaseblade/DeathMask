package ryuna.qdeath.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.hell.HellMob;
import ryuna.qdeath.hell.HellScheduler;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModParticles;

import java.util.function.Supplier;

/** Every Hell projectile. The carried item decides the kind (and the sprite rendered). */
public class HellBolt extends ThrowableItemProjectile {
    public enum Kind {
        WISP(() -> HellItems.SOUL_WISP, 6f, 0.0),
        BILE(() -> HellItems.BILE_GLOB, 4f, 0.04),
        TAR(() -> HellItems.TAR_GLOB, 5f, 0.03),
        CHAIN(() -> HellItems.CHAIN_HOOK, 4f, 0.0),
        EMBER(() -> HellItems.HELL_EMBER, 6f, 0.01),
        HARPOON(() -> HellItems.HARPOON_BOLT, 9f, 0.02),
        HOOK(() -> HellItems.HOOK_HEAD, 0f, 0.0),
        /** A thrown Breath Lure (Limbo): bursts into a lingering false breath. */
        BREATH(() -> HellItems.BREATH_LURE, 0f, 0.05);
        final Supplier<Item> item;
        final float damage;
        final double gravity;
        Kind(Supplier<Item> item, float damage, double gravity) { this.item = item; this.damage = damage; this.gravity = gravity; }
    }

    private int life;

    public HellBolt(EntityType<? extends HellBolt> type, Level level) { super(type, level); }

    public Kind kind() {
        Item item = getItem().getItem();
        for (Kind k : Kind.values()) if (k.item.get() == item) return k;
        return Kind.WISP;
    }

    /** Fires along the owner's look direction. */
    public static HellBolt shoot(ServerLevel level, LivingEntity owner, Kind kind, float speed) {
        HellBolt bolt = new HellBolt(HellEntities.HELL_BOLT, level);
        bolt.setItem(new ItemStack(kind.item.get()));
        bolt.setOwner(owner);
        bolt.setPos(owner.getX(), owner.getEyeY() - 0.15, owner.getZ());
        bolt.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0f, speed, 0.5f);
        level.addFreshEntity(bolt);
        return bolt;
    }

    /** Fires from {@code from} toward {@code target}. */
    public static HellBolt shootAt(ServerLevel level, LivingEntity owner, Kind kind, Vec3 from, Vec3 target, float speed, float inaccuracy) {
        HellBolt bolt = new HellBolt(HellEntities.HELL_BOLT, level);
        bolt.setItem(new ItemStack(kind.item.get()));
        bolt.setOwner(owner);
        bolt.setPos(from);
        Vec3 d = target.subtract(from);
        double lift = kind.gravity > 0 ? d.horizontalDistance() * kind.gravity * 2.2 : 0;
        bolt.shoot(d.x, d.y + lift, d.z, speed, inaccuracy);
        level.addFreshEntity(bolt);
        return bolt;
    }

    @Override protected Item getDefaultItem() { return HellItems.SOUL_WISP; }
    @Override protected double getDefaultGravity() { return kind().gravity; }

    @Override public void tick() {
        super.tick();
        Kind kind = kind();
        if (level().isClientSide()) {
            ParticleOptions trail = switch (kind) {
                case WISP -> ParticleTypes.SOUL_FIRE_FLAME;
                case BILE -> ModParticles.BILE_DRIP;
                case TAR -> ModParticles.TAR_DRIP;
                case EMBER -> ParticleTypes.FLAME;
                case CHAIN, HARPOON, HOOK -> ParticleTypes.CRIT;
                case BREATH -> ModParticles.BREATH;
            };
            level().addParticle(trail, getX(), getY() + 0.1, getZ(), 0, 0, 0);
            return;
        }
        if (++life > 120) discard();
        if (kind == Kind.WISP && getOwner() instanceof Mob owner && owner.getTarget() != null && life > 6) {
            Vec3 want = owner.getTarget().getEyePosition().subtract(position()).normalize().scale(0.55);
            setDeltaMovement(getDeltaMovement().scale(0.88).add(want.scale(0.12)));
        }
    }

    @Override protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner instanceof HellMob && target instanceof HellMob) return false;
        return super.canHitEntity(target) && target != owner;
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)) return;
        Kind kind = kind();
        Entity owner = getOwner();
        if (kind.damage > 0) target.hurtServer(level, damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), kind.damage);
        switch (kind) {
            case WISP -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
            case TAR -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
            }
            case EMBER -> target.igniteForSeconds(5);
            case CHAIN, HARPOON -> {
                if (owner != null) {
                    Vec3 pull = owner.position().subtract(target.position()).normalize().scale(kind == Kind.CHAIN ? 1.6 : 1.1);
                    target.push(pull.x, 0.45, pull.z);
                    target.syncVelocity = true;
                }
            }
            default -> {}
        }
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level)) return;
        Kind kind = kind();
        Vec3 at = hit.getLocation();
        switch (kind) {
            case BILE -> {
                AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
                cloud.setRadius(2.5f);
                cloud.setDuration(100);
                cloud.setRadiusPerTick(-0.02f);
                cloud.setCustomParticle(ModParticles.BILE_DRIP);
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                if (getOwner() instanceof LivingEntity owner) cloud.setOwner(owner);
                level.addFreshEntity(cloud);
                level.playSound(null, BlockPos.containing(at), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0f, 0.6f);
            }
            case EMBER -> level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.1);
            case HOOK -> {
                if (hit instanceof BlockHitResult && getOwner() instanceof LivingEntity owner) {
                    Vec3 d = at.subtract(owner.position());
                    owner.setDeltaMovement(d.normalize().scale(Math.min(2.6, 0.7 + d.length() * 0.11)).add(0, 0.3, 0));
                    owner.syncVelocity = true;
                    owner.resetFallDistance();
                    owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
                    level.playSound(null, BlockPos.containing(at), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
                }
            }
            case BREATH -> {
                // A cloud that only shows: it carries no effect. The lure itself is the clue registered with it.
                AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
                cloud.setRadius(1.6f);
                cloud.setDuration(ryuna.qdeath.limbo.LimboTuning.LURE_LIFE);
                cloud.setRadiusPerTick(-0.001f);
                cloud.setCustomParticle(ModParticles.BREATH);
                level.addFreshEntity(cloud);
                ryuna.qdeath.limbo.BreathClues.addLure(level, at);
                level.playSound(null, BlockPos.containing(at), ryuna.qdeath.registry.ModSounds.LURE_BREATH, SoundSource.PLAYERS, 1.2f, 1.0f);
            }
            default -> {}
        }
        discard();
    }

    /**
     * A telegraphed pillar of hellfire: smoke marks the spot for a second, then flame erupts there.
     * Used by Heretics, the Heresiarch and the Heretic's Brand.
     */
    public static void fireColumn(ServerLevel level, Entity owner, BlockPos at, float damage) {
        Vec3 c = Vec3.atBottomCenterOf(at);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.2, c.z, 14, 0.4, 0.05, 0.4, 0.01);
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8f, 0.5f);
        HellScheduler.later(level, 22, l -> {
            for (int y = 0; y < 5; y++) l.sendParticles(ParticleTypes.FLAME, c.x, c.y + y, c.z, 10, 0.35, 0.3, 0.35, 0.03);
            l.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 4, 0.2, 0.2, 0.2, 0.1);
            l.playSound(null, at, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.3, 4, 1.3), e -> e != owner && !(owner instanceof HellMob && e instanceof HellMob))) {
                e.hurtServer(l, l.damageSources().onFire(), damage);
                e.igniteForSeconds(4);
            }
        });
    }
}
