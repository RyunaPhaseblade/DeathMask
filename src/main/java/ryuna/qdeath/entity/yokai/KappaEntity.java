package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.AnchoredYokai;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiGoals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kappa: territorial and neutral. Attacking it, or tearing up water plants within 6 blocks, provokes it; bubbles and
 * splashes warn for 3 s. A provoked victim who enters its water is held within 8 blocks of its anchor and bitten
 * every 2 s until the kappa is defeated or three sea pickles are thrown to it (anyone can throw them, even a player
 * who cannot see it). It never drags anyone to the bottom. Sea pickles also earn its trust: three visits a minute
 * apart seal a pact.
 */
public class KappaEntity extends AbstractYokai implements AnchoredYokai {
    public static final int ANIM_WARN = 1, ANIM_BITE = 2, ANIM_GRAB = 3;
    private BlockPos anchor = BlockPos.ZERO;
    private final Map<UUID, Long> provoked = new HashMap<>();
    private final Map<UUID, Integer> appeased = new HashMap<>();
    private @Nullable UUID victim;
    private @Nullable UUID warnFor;
    private int warnTicks, biteCooldown;

    public KappaEntity(EntityType<? extends KappaEntity> type, Level level) {
        super(type, level, Species.KAPPA);
        moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.1f, 0.5f, false);
        setPathfindingMalus(PathType.WATER, 0f);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Kappa.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, YokaiBalance.Kappa.BITE_DAMAGE).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ARMOR, 4);
    }

    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }

    @Override public void travel(Vec3 input) {
        if (isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else super.travel(input);
    }

    @Override public void setAnchor(BlockPos pos) {
        anchor = pos;
        setHomeTo(pos, (int) YokaiBalance.Kappa.HOLD_RADIUS);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8, 60));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        targetSelector.addGoal(1, new YokaiGoals.Defend(this));
    }

    // ------------------------------------------------------------------------------------------------ provocation

    /** Water plants were torn up near {@code pos} by {@code player}: every kappa within 6 blocks is provoked. */
    public static void vegetationBroken(ServerLevel level, BlockPos pos, ServerPlayer player) {
        double r = YokaiBalance.Kappa.PROVOKE_RADIUS;
        for (KappaEntity k : level.getEntitiesOfClass(KappaEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(r), k -> !k.core.bonded()))
            // A kappa that has been given sea pickles forgives its friends a torn plant (an attack still provokes it).
            if (k.distanceToSqr(Vec3.atCenterOf(pos)) <= r * r && k.relation.getOrDefault(player.getUUID(), 0) < YokaiBalance.Kappa.APPEASE_PICKLES) k.provoke(player);
    }

    private void provoke(ServerPlayer player) {
        if (core.bonded() || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        provoked.put(player.getUUID(), level().getGameTime() + 20 * 90);
        appeased.remove(player.getUUID());
        warnFor = player.getUUID();
        warnTicks = YokaiBalance.Kappa.WARNING;
        preparing(true);
        playAnim(ANIM_WARN, warnTicks);
        playSound(ModSounds.KAPPA_BUBBLES, 1.4f, 1.0f);
        Bestiary.clueNear((ServerLevel) level(), position(), 24, species(), "bubbles");
    }

    private boolean provokedBy(UUID who) {
        Long until = provoked.get(who);
        return until != null && until > level().getGameTime() && !(who.equals(warnFor) && warnTicks > 0);
    }

    /** Sea pickles given or thrown by {@code who} (or, if unknown, credited to the current victim). */
    private void appease(@Nullable UUID who, int n) {
        if (who == null) who = victim;
        if (who == null) return;
        int total = appeased.merge(who, n, Integer::sum);
        level().playSound(null, blockPosition(), ModSounds.KAPPA_SPLASH, getSoundSource(), 0.8f, 1.3f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 5, 0.3, 0.3, 0.3, 0);
        if (total >= YokaiBalance.Kappa.APPEASE_PICKLES) {
            provoked.remove(who);
            appeased.remove(who);
            if (who.equals(victim)) release();
        }
    }

    private void release() {
        victim = null;
        preparing(false);
        setTarget(null);
    }

    public boolean holding() { return victim != null; }
    public boolean provoked(UUID who) { return provoked.containsKey(who); }

    @Override public @Nullable String storeBlocker() { return holding() ? "holding" : null; }

    // ------------------------------------------------------------------------------------------------ ticking

    @Override protected void serverTick(ServerLevel level) {
        if (biteCooldown > 0) biteCooldown--;
        if (tickCount % 20 == 0) {
            if (!anchor.equals(BlockPos.ZERO) && !territoryHasWater(level)) {
                release();
                provoked.clear();
            }
            provoked.values().removeIf(t -> t < level.getGameTime());
            eatThrownPickles(level);
        }
        if (warnTicks > 0) {
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.2, getZ(), 4, 0.6, 0.1, 0.6, 0.05);
            if (warnTicks % 10 == 0) level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.8, getZ(), 12, 0.8, 0.1, 0.8, 0.1);
            if (--warnTicks == 0) preparing(false);
        }
        if (victim != null) hold(level);
        else for (UUID who : provoked.keySet()) {
            if (!provokedBy(who)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
            if (p == null || p.level() != level || !p.isAlive()) continue;
            if (p.isInWater() && inTerritory(p.position())) {
                victim = who;
                playAnim(ANIM_GRAB, 12);
                playSound(ModSounds.KAPPA_SPLASH, 1.5f, 0.8f);
                break;
            }
            if (p.distanceToSqr(this) < 64 && inTerritory(p.position())) {
                getNavigation().moveTo(p, 1.3);
                if (p.distanceToSqr(this) < 4.5) bite(level, p);
            }
        }
        if (tickCount % 30 == 0 && !isInWater() && getDeltaMovement().horizontalDistanceSqr() > 1e-4) {
            level.sendParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + 0.1, getZ(), 3, 0.2, 0, 0.2, 0);
            Bestiary.clueNear(level, position(), 12, species(), "wet_prints");
        }
    }

    private void hold(ServerLevel level) {
        ServerPlayer v = level.getServer().getPlayerList().getPlayer(victim);
        // Death, disconnection and a change of dimension end the hold: nothing persists.
        if (v == null || v.level() != level || !v.isAlive() || !provoked.containsKey(victim)) { release(); return; }
        Vec3 home = Vec3.atBottomCenterOf(anchor);
        double max = YokaiBalance.Kappa.HOLD_RADIUS;
        Vec3 off = v.position().subtract(home);
        if (off.horizontalDistanceSqr() > max * max) {
            // Held within 8 blocks of the anchor: pulled back horizontally, never down.
            Vec3 back = off.multiply(-1, 0, -1).normalize().scale(0.25);
            v.setDeltaMovement(v.getDeltaMovement().multiply(0.3, 1, 0.3).add(back));
            v.syncVelocity = true;
        }
        getNavigation().moveTo(v, 1.4);
        getLookControl().setLookAt(v);
        if (distanceToSqr(v) < 5) bite(level, v);
    }

    private void bite(ServerLevel level, LivingEntity target) {
        if (biteCooldown > 0) return;
        biteCooldown = YokaiBalance.Kappa.BITE_INTERVAL;
        playAnim(ANIM_BITE, 8);
        playSound(ModSounds.KAPPA_BITE, 1.2f, 1.0f);
        target.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Kappa.BITE_DAMAGE);
    }

    private boolean inTerritory(Vec3 p) {
        return Vec3.atBottomCenterOf(anchor).distanceToSqr(p) <= YokaiBalance.Kappa.HOLD_RADIUS * YokaiBalance.Kappa.HOLD_RADIUS;
    }

    private boolean territoryHasWater(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-3, -2, -3), anchor.offset(3, 1, 3)))
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        return false;
    }

    private void eatThrownPickles(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(4, 2, 4), i -> i.getItem().is(Items.SEA_PICKLE))) {
            int n = item.getItem().getCount();
            UUID thrower = item.getOwner() != null ? item.getOwner().getUUID() : null;
            item.discard();
            if (core.bonded()) continue;
            appease(thrower, n);
            if (thrower != null) trust(level, thrower, n);
        }
    }

    /** Sea pickles received from each player (its relation with them). */
    private final Map<UUID, Integer> relation = new HashMap<>();

    private void trust(ServerLevel level, UUID who, int n) { relation.merge(who, n, Integer::sum); }

    // ------------------------------------------------------------------------------------------------ offerings and pact

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(Items.SEA_PICKLE)) return InteractionResult.PASS;
        ServerLevel level = (ServerLevel) level();
        if (provoked.containsKey(player.getUUID())) {
            consume(player, stack, 1);
            appease(player.getUUID(), 1);
            return InteractionResult.SUCCESS;
        }
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            // Without Pacts an offering still earns its goodwill.
            consume(player, stack, 1);
            trust(level, player.getUUID(), 1);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 5, 0.3, 0.3, 0.3, 0);
            return InteractionResult.SUCCESS;
        }
        YokaiCore.Ritual r = core.ritual(player);
        long now = level.getGameTime();
        if (r.count > 0 && now - r.time < YokaiBalance.Kappa.PACT_SPACING) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.kappa_wait",
                    (YokaiBalance.Kappa.PACT_SPACING - (now - r.time)) / 20).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (r.count + 1 >= YokaiBalance.Kappa.PACT_VISITS) {
            if (Bonds.pact(player, this)) {
                consume(player, stack, 1);
                core.resetRitual(player);
            }
            return InteractionResult.SUCCESS;
        }
        r.count++;
        r.time = now;
        consume(player, stack, 1);
        trust(level, player.getUUID(), 1);
        playSound(ModSounds.KAPPA_AMBIENT, 1.0f, 1.3f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 8, 0.3, 0.3, 0.3, 0);
        player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.kappa_visit", r.count, YokaiBalance.Kappa.PACT_VISITS).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof ServerPlayer p && !core.bonded()) provoke(p);
        return hurt;
    }

    @Override public void die(DamageSource source) {
        release();
        super.die(source);
    }

    @Override public boolean canStandAt(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 0, 2)))
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        return super.canStandAt(level, pos);
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.KAPPA_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Anchor", BlockPos.CODEC, anchor);
        var list = out.childrenList("Relation");
        relation.forEach((uuid, n) -> {
            ValueOutput e = list.addChild();
            e.store("player", net.minecraft.core.UUIDUtil.CODEC, uuid);
            e.putInt("pickles", n);
        });
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("Anchor", BlockPos.CODEC).ifPresent(this::setAnchor);
        relation.clear();
        for (ValueInput e : in.childrenListOrEmpty("Relation"))
            e.read("player", net.minecraft.core.UUIDUtil.CODEC).ifPresent(u -> relation.put(u, e.getIntOr("pickles", 0)));
    }
}
