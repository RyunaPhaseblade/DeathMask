package ryuna.qdeath.entity.vigil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.block.HellBlockTypes;
import ryuna.qdeath.entity.hell.HellMob;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModEffects;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.UUID;

/**
 * VIGIL, the Mourner who followed the dead down and never came back up.
 * Outside the Zenith it cannot be harmed: it watches from far away, follows when unobserved, and sometimes hunts.
 * Escape it with distance and broken sight lines, a votive lantern's light, a Mourning Bell or the Mirror of Lethe.
 */
public class VigilEntity extends Monster implements HellMob {
    public enum Mode { WATCH, STALK, PURSUE, FADE }

    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(VigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(VigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FROZEN = SynchedEntityData.defineId(VigilEntity.class, EntityDataSerializers.INT);
    public static final int FADE_TICKS = 30;

    private @Nullable UUID quarry;
    private int age;
    private int unseen;
    private int fadeTicks;
    private int stopAtSanctuary;
    private @Nullable ServerBossEvent dread;
    /** Placed by /deathmask vigil show for filming: stands still, does not fade when approached. */
    private boolean showcase;
    /** Client: tick the fade began (for the render alpha). */
    public int clientFadeStart = -1;

    public VigilEntity(EntityType<? extends VigilEntity> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 96).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_FORM, 1);
        b.define(DATA_MODE, Mode.WATCH.ordinal());
        b.define(DATA_FROZEN, 0);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MODE.equals(key) && mode() == Mode.FADE) clientFadeStart = tickCount;
    }

    public int form() { return entityData.get(DATA_FORM); }
    public Mode mode() { return Mode.values()[entityData.get(DATA_MODE)]; }
    public boolean isFrozen() { return entityData.get(DATA_FROZEN) > 0; }

    /** Prepares a manifestation hunting/watching {@code player}. */
    public void manifest(ServerPlayer player, Mode mode, int form) {
        quarry = player.getUUID();
        entityData.set(DATA_FORM, Mth.clamp(form, 1, 5));
        entityData.set(DATA_MODE, mode.ordinal());
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        // Deeper forms are faster. Form I is slower than a sprint; form IV matches it.
        if (speed != null) speed.setBaseValue(0.24 + 0.025 * form);
        // It grows with every circle: a tall mourner in the Vestibule, a colossus by the Zenith.
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(new double[]{1.0, 1.1, 1.25, 1.6, 2.0}[Mth.clamp(form, 1, 5) - 1]);
        lookAt(player, 360, 360);
        if (mode == Mode.PURSUE) {
            dread = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("VIGIL").withStyle(ChatFormatting.WHITE, ChatFormatting.OBFUSCATED),
                    BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
            dread.setDarkenScreen(true).setCreateWorldFog(true);
            dread.setProgress(0);
            dread.addPlayer(player);
        }
    }

    public void setShowcase() { showcase = true; }

    public @Nullable ServerPlayer quarry() {
        if (quarry == null || !(level() instanceof ServerLevel level)) return null;
        return level.getServer().getPlayerList().getPlayer(quarry);
    }

    public void fade() {
        if (mode() == Mode.FADE) return;
        entityData.set(DATA_MODE, Mode.FADE.ordinal());
        fadeTicks = FADE_TICKS;
        getNavigation().stop();
        playSound(ModSounds.VIGIL_VANISH, 1.5f, 1.0f);
        if (dread != null) dread.removeAllPlayers();
    }

    /** Mourning Bell: a pursuing VIGIL is driven off. */
    public void banish(Player by) {
        if (mode() == Mode.PURSUE || mode() == Mode.STALK) {
            by.sendOverlayMessage(Component.translatable("message.quantumdeath.vigil_banished").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            fade();
        }
    }

    /** Mirror of Lethe: it cannot move while watched in the glass. */
    public void freeze(int ticks) { entityData.set(DATA_FROZEN, Math.max(entityData.get(DATA_FROZEN), ticks)); }

    @Override protected void registerGoals() {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (random.nextInt(form() >= 4 ? 2 : 5) == 0)
                level().addParticle(ModParticles.VIGIL_MOTE, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.01, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        age++;
        int frozen = entityData.get(DATA_FROZEN);
        if (frozen > 0) entityData.set(DATA_FROZEN, frozen - 1);
        if (mode() == Mode.FADE) {
            if (--fadeTicks <= 0) {
                level.sendParticles(ModParticles.VIGIL_MOTE, getX(), getY() + getBbHeight() / 2, getZ(), 40, 0.4, getBbHeight() / 3, 0.4, 0.02);
                // Where it stood, something is sometimes left behind.
                if (random.nextFloat() < 0.3f) spawnAtLocation(level, new ItemStack(HellItems.MASK_FRAGMENT));
                discard();
            }
            return;
        }
        ServerPlayer player = quarry();
        if (player == null || player.level() != level || !player.isAlive() || player.isSpectator() || HellLayer.of(level) == null) { fade(); return; }
        lookAt(player, 10, 10);
        getLookControl().setLookAt(player, 10, 10);
        double dist = distanceTo(player);
        boolean seesUs = isLookedAtBy(player);
        switch (mode()) {
            case WATCH -> {
                getNavigation().stop();
                // It does not let you come close, and does not stay long.
                if (showcase) { if (age > 2400) fade(); }
                else if (dist < 20 || age > 600 || (seesUs && random.nextInt(80) == 0)) fade();
            }
            case STALK -> {
                if (frozen > 0 || seesUs) getNavigation().stop();
                else if (dist > 14) getNavigation().moveTo(player, 1.0);
                else getNavigation().stop();
                if (dist < 10 || age > 900) fade();
            }
            case PURSUE -> tickPursuit(level, player, dist, frozen > 0);
            default -> {}
        }
    }

    private void tickPursuit(ServerLevel level, ServerPlayer player, double dist, boolean frozen) {
        if (dread != null) dread.setProgress((float) Mth.clamp(1.0 - dist / 64.0, 0, 1));
        if (age % 12 == 0) level.playSound(null, blockPosition(), ModSounds.VIGIL_STEP, SoundSource.HOSTILE, 1.4f, 0.9f - form() * 0.05f);
        if (dist < 32 && age % 40 == 0 && !holdsLantern(player)) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        if (dist < 10 && age % 50 == 0) level.playSound(null, blockPosition(), ModSounds.VIGIL_BREATH, SoundSource.HOSTILE, 1.0f, 1.0f);
        if (frozen) { getNavigation().stop(); return; }
        // Sanctuary: it stops at the edge of a votive lantern's light and waits, then leaves.
        if (inSanctuary(level, player.blockPosition())) {
            getNavigation().stop();
            if (++stopAtSanctuary > 100) fade();
            return;
        }
        stopAtSanctuary = 0;
        boolean sight = hasLineOfSight(player);
        // Hiding: crouched, still and out of sight, you are lost to it twice as fast.
        int lose = player.isCrouching() && player.getDeltaMovement().horizontalDistanceSqr() < 0.001 ? 2 : 1;
        unseen = sight ? 0 : unseen + lose;
        if ((unseen > 200 && dist > 20) || age > 1800) { fade(); return; }
        if (dist < 1.8) { touch(level, player); return; }
        getNavigation().moveTo(player, 1.0);
        // If the path fails while unobserved, it closes the distance anyway. Never in plain view.
        if (getNavigation().isDone() && !isLookedAtBy(player) && dist > 12 && age % 30 == 0) {
            Vec3 toward = player.position().subtract(position()).normalize().scale(Math.min(8, dist - 8));
            BlockPos dest = BlockPos.containing(position().add(toward));
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos p = dest.above(dy);
                if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                    level.sendParticles(ModParticles.VIGIL_MOTE, getX(), getY() + 1, getZ(), 20, 0.3, 1, 0.3, 0.01);
                    teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    break;
                }
            }
        }
    }

    private void touch(ServerLevel level, ServerPlayer player) {
        float damage = 4 + 2 * form();
        player.hurtServer(level, damageSources().mobAttack(this), damage);
        player.addEffect(new MobEffectInstance(ModEffects.VIGILS_MARK, 2400, 0));
        player.addEffect(new MobEffectInstance(ModEffects.MOURNED, 160, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
        level.playSound(null, player.blockPosition(), ModSounds.VIGIL_TOUCH, SoundSource.HOSTILE, 1.0f, 1.0f);
        player.sendSystemMessage(Component.translatable("message.quantumdeath.vigil_touch." + Mth.clamp(form(), 1, 5)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        fade();
    }

    public static boolean inSanctuary(ServerLevel level, BlockPos pos) {
        int r = HellBlockTypes.VotiveLantern.SANCTUARY_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -4, -r), pos.offset(r, 4, r))) {
            if (level.getBlockState(p).is(ModBlocks.VOTIVE_LANTERN) && p.distSqr(pos) <= r * r) return true;
        }
        return false;
    }

    private static boolean holdsLantern(Player player) {
        return player.getMainHandItem().is(HellItems.FERRYMAN_LANTERN) || player.getOffhandItem().is(HellItems.FERRYMAN_LANTERN)
                || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(HellItems.VIGILS_DEATH_MASK);
    }

    /** True if the player's view is pointed at this entity and nothing blocks the line. */
    public boolean isLookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1f).normalize();
        Vec3 to = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double len = to.length();
        return view.dot(to.normalize()) > 1.0 - 0.025 / Math.max(1, len / 16) && player.hasLineOfSight(this);
    }

    // It cannot be hurt, pushed, or saved. It is never really here.
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) { discard(); return false; } // /kill, the void
        if (source.getEntity() instanceof Player p && mode() != Mode.FADE) {
            p.sendOverlayMessage(Component.translatable("message.quantumdeath.vigil_untouchable").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            if (mode() == Mode.WATCH) fade();
        }
        return false;
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) {}
    @Override public boolean isPickable() { return mode() != Mode.FADE; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void checkDespawn() {}
    @Override public boolean canUsePortal(boolean force) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean causeFallDamage(double fallDistance, float mult, DamageSource source) { return false; }

    @Override public void remove(RemovalReason reason) {
        if (dread != null) dread.removeAllPlayers();
        super.remove(reason);
    }
}
