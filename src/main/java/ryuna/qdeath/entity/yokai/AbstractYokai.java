package ryuna.qdeath.entity.yokai;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Companions;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiCombat;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Base of the new yokai: the shared {@link YokaiCore}, a synced animation channel (id + length, read by the models as
 * a 0..1 progress, the same scheme as the Hell creatures), the owner reference of a shikigami, interaction routing
 * (amulet, order screen, ritual offerings) and the common per-second bookkeeping.
 */
public abstract class AbstractYokai extends PathfinderMob implements YokaiCreature, OwnableEntity {
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(AbstractYokai.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_LEN = SynchedEntityData.defineId(AbstractYokai.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
            SynchedEntityData.defineId(AbstractYokai.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** bit 0: a windup is in progress (Reading Intent); other bits are species-specific. */
    protected static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(AbstractYokai.class, EntityDataSerializers.BYTE);

    /** Client hook: whether the local player may target this entity with the crosshair (set by the client). */
    public static Predicate<net.minecraft.world.entity.Entity> CLIENT_PICKABLE = e -> true;

    protected final YokaiCore core;
    public int animStartTick;
    private int animCounter;

    protected AbstractYokai(EntityType<? extends PathfinderMob> type, Level level, Species species) {
        super(type, level);
        this.core = new YokaiCore(species, this);
    }

    @Override public YokaiCore yokai() { return core; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ANIM, 0);
        b.define(DATA_ANIM_LEN, 1);
        b.define(DATA_OWNER, Optional.empty());
        b.define(DATA_FLAGS, (byte) 0);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ANIM.equals(key)) animStartTick = tickCount;
    }

    // ------------------------------------------------------------------------------------------------ animation

    public void playAnim(int id, int length) {
        entityData.set(DATA_ANIM_LEN, Math.max(1, length));
        entityData.set(DATA_ANIM, (++animCounter << 8) | (id & 0xff));
        animStartTick = tickCount;
    }

    public int animId() { return entityData.get(DATA_ANIM) & 0xff; }

    public float animProgress(float partial) {
        if (animId() == 0) return -1;
        float p = (tickCount - animStartTick + partial) / entityData.get(DATA_ANIM_LEN);
        return p >= 1 ? -1 : Math.max(0, p);
    }

    protected boolean flag(int bit) { return (entityData.get(DATA_FLAGS) & (1 << bit)) != 0; }

    protected void flag(int bit, boolean on) {
        byte b = entityData.get(DATA_FLAGS);
        entityData.set(DATA_FLAGS, (byte) (on ? b | (1 << bit) : b & ~(1 << bit)));
    }

    /** A visible attack preparation (shown by Reading Intent to players who see the creature). */
    public boolean preparing() { return flag(0); }
    protected void preparing(boolean on) { flag(0, on); }

    // ------------------------------------------------------------------------------------------------ owner

    @Override public @Nullable EntityReference<LivingEntity> getOwnerReference() { return entityData.get(DATA_OWNER).orElse(null); }

    private void syncOwner() {
        entityData.set(DATA_OWNER, Optional.ofNullable(core.owner()).map(EntityReference::of));
    }

    public boolean bondedClient() { return entityData.get(DATA_OWNER).isPresent(); }

    // ------------------------------------------------------------------------------------------------ ticking

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        core.tick();
        if (core.bonded() != entityData.get(DATA_OWNER).isPresent()) syncOwner();
        if (tickCount % 20 == 0) {
            Bestiary.observe(this, species());
            if (core.bonded()) {
                if (!Bonds.current(this)) {
                    // An outdated copy of a companion that lives elsewhere (stored, rebound or dead): it must not act twice.
                    core.storing = true;
                    discard();
                    return;
                }
                Bonds.snapshot(this);
            }
        }
        serverTick(level);
    }

    /** Species logic, every server tick. */
    protected void serverTick(ServerLevel level) {}

    @Override public void checkDespawn() {
        if (core.bonded()) return;
        if (level().getDifficulty() == Difficulty.PEACEFUL && species().offensive()) {
            discard();
            return;
        }
        super.checkDespawn();
    }

    @Override public boolean removeWhenFarAway(double distance) { return !core.bonded() && !isPersistenceRequired(); }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) Bonds.died(this);
    }

    @Override public boolean ignoreExplosion(Explosion explosion) { return YokaiCombat.ignoresExplosion(this, explosion) || super.ignoreExplosion(explosion); }

    @Override public boolean isPickable() { return super.isPickable() && (!level().isClientSide() || CLIENT_PICKABLE.test(this)); }

    @Override public boolean canBeLeashed() { return false; }

    // ------------------------------------------------------------------------------------------------ interaction

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The server decides everything; the client's interact packet is sent regardless of this result.
        // The client sends both hands for one click: an empty off hand must not act again (orders screen, riding).
        if (!(player instanceof ServerPlayer sp) || hand == InteractionHand.OFF_HAND && stack.isEmpty()) return InteractionResult.PASS;
        if (stack.is(YokaiContent.AMULET)) {
            if (core.isOwner(sp)) Bonds.useOn(sp, stack, this);
            return InteractionResult.SUCCESS;
        }
        if (core.isOwner(sp) && stack.isEmpty() && !sp.isShiftKeyDown()) {
            Companions.open(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (core.bonded()) return ownerInteract(sp, hand, stack);
        if (!Perception.canInteract(sp, this)) return InteractionResult.PASS;
        return ritual(sp, hand, stack);
    }

    /** A player with complete sight offers {@code stack} (species rituals and offerings). */
    protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) { return InteractionResult.PASS; }

    /** The owner interacts holding something (sneak + empty hand reaches here too: species extras such as riding). */
    protected InteractionResult ownerInteract(ServerPlayer owner, InteractionHand hand, ItemStack stack) { return InteractionResult.PASS; }

    /** Consumes {@code n} of the offering held (never in creative). */
    protected static void consume(Player player, ItemStack stack, int n) {
        if (!player.getAbilities().instabuild) stack.shrink(n);
    }

    // ------------------------------------------------------------------------------------------------ persistence

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        core.save(out);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        core.load(in);
        syncOwner();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Server particles everybody sees (environmental signs). */
    protected void signParticles(ParticleOptions p, int count, double spread) {
        if (level() instanceof ServerLevel level) level.sendParticles(p, getX(), getY() + 0.1, getZ(), count, spread, 0.05, spread, 0.01);
    }

    protected void playSign(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, getSoundSource(), volume, pitch);
    }

    @Override protected float getSoundVolume() { return 1.0f; }
}
