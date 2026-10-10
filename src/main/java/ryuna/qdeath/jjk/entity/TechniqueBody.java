package ryuna.qdeath.jjk.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.JjkBalance;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.jogo.Jogo;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The large, short-lived bodies conjured by techniques: Jogo's volcanoes and Maximum: Meteor, Uraume's falling ice.
 * Drawn as scaled blocks on the client. Never saved, bound to an online owner in the same dimension, and always on a
 * clock: none can outlive its cast.
 */
public class TechniqueBody extends Entity implements CursedObject {
    public enum Kind { VOLCANO, METEOR, ICE_MASS }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(TechniqueBody.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(TechniqueBody.class, EntityDataSerializers.FLOAT);

    private @Nullable UUID ownerId;
    public int age, life = 200, timer;
    public float health = 20;
    public boolean controlled, mastery;
    private Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
    private @Nullable Consumer<TechniqueBody> onImpact;

    public TechniqueBody(EntityType<? extends TechniqueBody> type, Level level) { super(type, level); }

    public static TechniqueBody volcano(ServerPlayer owner, Vec3 at, int life) {
        TechniqueBody b = make(owner, Kind.VOLCANO, at, 1f);
        b.life = life;
        b.health = JjkBalance.Jogo.VOLCANO_HEALTH;
        b.timer = JjkBalance.Jogo.VOLCANO_FIRE_INTERVAL / 2;
        return b;
    }

    /** A body that travels from {@code from} to {@code to} in {@code ticks}, accelerating, and then calls {@code impact}. */
    public static TechniqueBody falling(ServerPlayer owner, Kind kind, Vec3 from, Vec3 to, int ticks, float scale, Consumer<TechniqueBody> impact) {
        TechniqueBody b = make(owner, kind, from, scale);
        b.from = from;
        b.to = to;
        b.life = Math.max(1, ticks);
        b.onImpact = impact;
        return b;
    }

    private static TechniqueBody make(ServerPlayer owner, Kind kind, Vec3 at, float scale) {
        TechniqueBody b = new TechniqueBody(JjkContent.BODY, owner.level());
        b.ownerId = owner.getUUID();
        b.entityData.set(DATA_KIND, kind.ordinal());
        b.entityData.set(DATA_SCALE, scale);
        b.setPos(at);
        b.setYRot(owner.getRandom().nextFloat() * 360);
        return b;
    }

    public Kind kind() { return Kind.values()[Math.floorMod(entityData.get(DATA_KIND), Kind.values().length)]; }
    public float scale() { return entityData.get(DATA_SCALE); }
    public @Nullable UUID ownerId() { return ownerId; }

    public @Nullable ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel level)) return null;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(ownerId);
        return p != null && p.level() == level && p.isAlive() ? p : null;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, 0);
        b.define(DATA_SCALE, 1f);
    }

    @Override public void tick() {
        super.tick();
        Kind kind = kind();
        if (!(level() instanceof ServerLevel level)) {
            clientParticles(kind);
            return;
        }
        age++;
        ServerPlayer owner = owner();
        if (owner == null) { discard(); return; }
        switch (kind) {
            case VOLCANO -> {
                if (age >= life) { crumble(level); return; }
                Jogo.tickVolcano(this, owner, level);
            }
            case METEOR, ICE_MASS -> {
                float t = Math.min(1f, age / (float) life);
                float eased = t * t;
                setPos(from.lerp(to, eased));
                if (kind == Kind.METEOR) Jogo.meteorTrail(this, level, t);
                else if (age % 2 == 0) level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 4, scale() * 0.4, scale() * 0.4, scale() * 0.4, 0.02);
                if (age >= life) {
                    if (onImpact != null) onImpact.accept(this);
                    discard();
                }
            }
        }
    }

    private void clientParticles(Kind kind) {
        if (kind == Kind.VOLCANO) {
            float g = Math.min(1f, tickCount / 15f);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.0 * g, getZ(), 0, 0.06, 0);
            if (random.nextInt(8) == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY() + 2.0 * g, getZ(), 0, 0, 0);
        } else if (kind == Kind.METEOR) {
            for (int i = 0; i < 4; i++) level().addParticle(ParticleTypes.FLAME, getRandomX(scale() * 0.6), getY() + (random.nextDouble() - 0.5) * scale(), getRandomZ(scale() * 0.6), 0, 0.1, 0);
        }
    }

    public void crumble(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), getX(), getY() + 0.8, getZ(), 24, 0.7, 0.6, 0.7, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 10, 0.6, 0.6, 0.6, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (kind() != Kind.VOLCANO || isRemoved()) return false;
        if (source.getEntity() != null && source.getEntity().getUUID().equals(ownerId)) return false;
        health -= amount;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BASALT_HIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (health <= 0) crumble(level);
        return true;
    }

    @Override public boolean isPickable() { return kind() == Kind.VOLCANO && !isRemoved(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
    @Override public boolean isOnFire() { return false; }
}
