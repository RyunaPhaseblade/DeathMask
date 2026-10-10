package ryuna.qdeath.jjk.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.Freeze;
import ryuna.qdeath.jjk.Fx;
import ryuna.qdeath.jjk.JjkBalance;
import ryuna.qdeath.jjk.JjkCombat;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.jogo.Jogo;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashSet;
import java.util.Set;

/**
 * Every projectile thrown by a cursed technique. The carried sprite item decides the kind. Never saved: when its
 * chunk unloads it is simply gone, and every kind has a hard lifetime, so none can be orphaned.
 */
public class TechniqueProjectile extends ThrowableItemProjectile implements CursedObject {
    public enum Kind { BLOOD_DISC, ICE_SHARD, FIRE_BLAST, MAGMA_BOMB, EMBER_INSECT }

    private float damage, freeze;
    private int pierce, life, maxLife = 100;
    private boolean mastery;
    private final Set<Integer> hits = new HashSet<>();
    private @Nullable LivingEntity seek;

    public TechniqueProjectile(EntityType<? extends TechniqueProjectile> type, Level level) { super(type, level); }

    public static TechniqueProjectile create(ServerPlayer owner, Kind kind, Vec3 at) {
        TechniqueProjectile p = new TechniqueProjectile(JjkContent.PROJECTILE, owner.level());
        p.setItem(new ItemStack(item(kind)));
        p.setOwner(owner);
        p.setPos(at);
        return p;
    }

    public TechniqueProjectile damage(float d) { damage = d; return this; }
    public TechniqueProjectile freeze(float f) { freeze = f; return this; }
    public TechniqueProjectile pierce(int n) { pierce = n; return this; }
    public TechniqueProjectile life(int ticks) { maxLife = ticks; return this; }
    public TechniqueProjectile mastery(boolean m) { mastery = m; return this; }
    public TechniqueProjectile seek(@Nullable LivingEntity target) { seek = target; return this; }

    public TechniqueProjectile fire(Vec3 dir, float speed, float inaccuracy) {
        shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
        level().addFreshEntity(this);
        return this;
    }

    private static Item item(Kind k) {
        return switch (k) {
            case BLOOD_DISC -> JjkContent.BLOOD_DISC;
            case ICE_SHARD -> JjkContent.ICE_SHARD;
            case FIRE_BLAST -> JjkContent.FIRE_BLAST;
            case MAGMA_BOMB -> JjkContent.MAGMA_BOMB;
            case EMBER_INSECT -> JjkContent.EMBER_INSECT;
        };
    }

    public Kind kind() {
        Item i = getItem().getItem();
        for (Kind k : Kind.values()) if (item(k) == i) return k;
        return Kind.BLOOD_DISC;
    }

    @Override protected Item getDefaultItem() { return JjkContent.BLOOD_DISC; }

    @Override protected double getDefaultGravity() {
        return switch (kind()) { case MAGMA_BOMB -> 0.05; case ICE_SHARD -> 0.008; default -> 0.0; };
    }

    @Override public boolean shouldBeSaved() { return false; }

    private @Nullable ServerPlayer ownerPlayer() { return getOwner() instanceof ServerPlayer p ? p : null; }

    @Override public void tick() {
        Kind kind = kind();
        if (!level().isClientSide() && kind == Kind.EMBER_INSECT) steer();
        super.tick();
        if (level().isClientSide()) { trail(kind); return; }
        if (++life > maxLife) {
            if (kind == Kind.EMBER_INSECT || kind == Kind.MAGMA_BOMB) explode(kind, position());
            discard();
            return;
        }
        if (kind == Kind.EMBER_INSECT && random.nextInt(40) == 0)
            level().playSound(null, getX(), getY(), getZ(), ModSounds.JJK_EMBER_BUZZ, SoundSource.PLAYERS, 0.35f, 0.9f + random.nextFloat() * 0.3f);
    }

    private void trail(Kind kind) {
        Level l = level();
        switch (kind) {
            case BLOOD_DISC -> {
                for (int i = 0; i < 3; i++) l.addParticle(Fx.BLOOD, getRandomX(0.4), getRandomY(), getRandomZ(0.4), 0, -0.02, 0);
                if (random.nextInt(3) == 0) l.addParticle(Fx.BLOOD_DARK, getX(), getY(), getZ(), 0, -0.05, 0);
            }
            case ICE_SHARD -> {
                l.addParticle(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 0, 0, 0);
                l.addParticle(Fx.FROST, getRandomX(0.3), getRandomY(), getRandomZ(0.3), 0, 0, 0);
            }
            case FIRE_BLAST -> {
                l.addParticle(ParticleTypes.FLAME, getRandomX(0.3), getRandomY(), getRandomZ(0.3), 0, 0, 0);
                l.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0.01, 0);
                if (random.nextInt(2) == 0) l.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
            }
            case MAGMA_BOMB -> {
                l.addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
                if (random.nextInt(3) == 0) l.addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
            }
            case EMBER_INSECT -> {
                l.addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0, 0, 0);
                if (random.nextInt(4) == 0) l.addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0, 0);
            }
        }
    }

    /** Ember insects: hover, pick a target, then home in with an erratic, buzzing flight. */
    private void steer() {
        ServerPlayer owner = ownerPlayer();
        if (owner == null) { discard(); return; }
        if (seek != null && (!seek.isAlive() || seek.isRemoved() || seek.level() != level() || seek.distanceToSqr(this) > 40 * 40)) seek = null;
        if (seek == null && life % 10 == 0) {
            double r = JjkBalance.Jogo.INSECT_SEEK_RANGE;
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r),
                    e -> JjkCombat.canHit(owner, e) && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == owner) || e.getLastHurtByMob() == owner))) {
                double d = e.distanceToSqr(this);
                if (d < bestD) { bestD = d; best = e; }
            }
            seek = best;
        }
        Vec3 v = getDeltaMovement();
        Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.08);
        Vec3 want;
        if (life < 10) want = new Vec3(0, 0.12, 0);
        else if (seek != null) want = seek.getBoundingBox().getCenter().subtract(position()).normalize().scale(mastery ? 0.75 : 0.6);
        else {
            Vec3 orbit = owner.position().add(Math.cos(life * 0.2 + getId()) * 2, 2.2, Math.sin(life * 0.2 + getId()) * 2);
            want = orbit.subtract(position()).scale(0.15);
        }
        setDeltaMovement(v.scale(0.75).add(want.scale(0.25)).add(jitter));
    }

    @Override protected boolean canHitEntity(Entity target) {
        ServerPlayer owner = ownerPlayer();
        if (target instanceof TechniqueProjectile || target instanceof TechniqueBody) return false;
        if (hits.contains(target.getId())) return false;
        if (owner != null && !JjkCombat.canHit(owner, target)) return false;
        return super.canHitEntity(target);
    }

    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) return;
        Kind kind = kind();
        if (hit instanceof EntityHitResult eh) {
            hitEntity(level, kind, eh.getEntity());
            if (pierce-- > 0) return;
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 at = hit.getLocation();
            switch (kind) {
                case BLOOD_DISC -> {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.1);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 0.6f);
                }
                case ICE_SHARD -> {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.1);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
                }
                case FIRE_BLAST -> {
                    splash(level, at);
                    ServerPlayer owner = ownerPlayer();
                    if (owner != null) Jogo.react(owner, at);
                }
                case MAGMA_BOMB, EMBER_INSECT -> explode(kind, at);
            }
        }
        discard();
    }

    private void hitEntity(ServerLevel level, Kind kind, Entity e) {
        ServerPlayer owner = ownerPlayer();
        hits.add(e.getId());
        if (owner == null || !(e instanceof LivingEntity target)) return;
        Vec3 at = target.getBoundingBox().getCenter();
        switch (kind) {
            case BLOOD_DISC -> {
                JjkCombat.hurt(owner, target, this, JjkCombat.TECHNIQUE, damage, true);
                level.sendParticles(Fx.BLOOD_BRIGHT, at.x, at.y, at.z, 14, 0.25, 0.3, 0.25, 0.15);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.1);
                level.playSound(null, at.x, at.y, at.z, ModSounds.JJK_BLOOD_SHOT, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            case ICE_SHARD -> {
                boolean split = mastery && Freeze.stage(target) >= 2;
                JjkCombat.hurt(owner, target, this, JjkCombat.FROST, damage, true);
                Freeze.add(target, freeze, owner);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y, at.z, 10, 0.2, 0.3, 0.2, 0.1);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.7f);
                if (split) for (int i = 0; i < 3; i++) {
                    double a = Math.PI * 2 * i / 3 + random.nextDouble();
                    TechniqueProjectile s = create(owner, Kind.ICE_SHARD, at.add(0, 0.3, 0)).damage(damage * 0.5f).freeze(freeze * 0.6f).life(20);
                    s.hits.add(target.getId());
                    s.fire(new Vec3(Math.cos(a), 0.1, Math.sin(a)), 1.2f, 0);
                }
            }
            case FIRE_BLAST -> {
                JjkCombat.hurt(owner, target, this, JjkCombat.FIRE, damage, true);
                target.igniteForSeconds(4);
                splash(level, at);
                Jogo.react(owner, at);
            }
            case MAGMA_BOMB, EMBER_INSECT -> explode(kind, at);
        }
    }

    private void splash(ServerLevel level, Vec3 at) {
        ServerPlayer owner = ownerPlayer();
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.06);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (owner == null) return;
        for (LivingEntity e : JjkCombat.around(owner, at, 1.5)) {
            if (hits.contains(e.getId())) continue;
            JjkCombat.hurt(owner, e, this, JjkCombat.FIRE, JjkBalance.Jogo.BLAST_SPLASH, true);
            e.igniteForSeconds(3);
        }
    }

    private void explode(Kind kind, Vec3 at) {
        if (!(level() instanceof ServerLevel level)) return;
        ServerPlayer owner = ownerPlayer();
        boolean insect = kind == Kind.EMBER_INSECT;
        float radius = insect ? JjkBalance.Jogo.INSECT_RADIUS : JjkBalance.Jogo.BOMB_RADIUS;
        float dmg = damage > 0 ? damage : insect ? JjkBalance.Jogo.INSECT_DAMAGE : JjkBalance.Jogo.BOMB_DAMAGE;
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        Fx.burst(level, ParticleTypes.FLAME, at, insect ? 10 : 18, 0.2);
        if (!insect) level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 5, 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, at.x, at.y, at.z, insect ? SoundEvents.FIRECHARGE_USE : SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, insect ? 0.7f : 0.6f, insect ? 1.6f : 1.3f);
        if (owner == null) return;
        for (LivingEntity e : JjkCombat.around(owner, at, radius)) {
            float falloff = (float) (1 - 0.5 * e.getBoundingBox().getCenter().distanceTo(at) / (radius + 1));
            JjkCombat.hurt(owner, e, this, JjkCombat.FIRE, dmg * falloff, true);
            e.igniteForSeconds(insect ? 3 : 4);
        }
    }
}
