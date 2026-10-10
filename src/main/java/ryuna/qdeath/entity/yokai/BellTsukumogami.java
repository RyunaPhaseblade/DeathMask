package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Provenance;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;

/**
 * Bell tsukumogami: the spirit of a village's natural bell. It hums near presences and rings a warning when something
 * hostile is within 24 blocks (at most every 10 s); the ring tells nothing about what or where. The original bell is
 * kept: it goes back to its place, or is dropped, never copied. Attending three different warnings and offering a
 * copper ingot seals a pact.
 */
public class BellTsukumogami extends AbstractYokai {
    public static final int ANIM_TINKLE = 1, ANIM_WARN = 2;
    private @Nullable BlockPos home;
    private BlockState homeState = Blocks.BELL.defaultBlockState();
    private int lonely, warnCooldown, tinkleCooldown = 60;
    /** Id and time of the latest warning, for players to attend. */
    private int warning;
    private long warnedAt = -10000;
    private boolean returned;

    public BellTsukumogami(EntityType<? extends BellTsukumogami> type, Level level) {
        super(type, level, Species.BELL);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Bell.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ARMOR, 8).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5, 240));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    public static @Nullable BellTsukumogami awaken(ServerLevel level, BlockPos pos) {
        if (!Provenance.eligibleBell(level, pos)) return null;
        BellTsukumogami b = YokaiContent.BELL.create(level, EntitySpawnReason.EVENT);
        if (b == null) return null;
        b.home = pos.immutable();
        b.homeState = level.getBlockState(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        b.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        b.setPersistenceRequired();
        level.addFreshEntity(b);
        b.ring(level, false);
        return b;
    }

    private boolean hostileNear(ServerLevel level) {
        double r = YokaiBalance.Bell.WARN_RADIUS;
        return !level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r), m -> m != this && m.isAlive() && m.distanceToSqr(this) <= r * r
                && (m instanceof YokaiCreature y ? !y.yokai().bonded() && y.species().offensive() : m instanceof Enemy)).isEmpty();
    }

    private void ring(ServerLevel level, boolean warn) {
        playAnim(warn ? ANIM_WARN : ANIM_TINKLE, warn ? 30 : 16);
        playSound(warn ? ModSounds.BELL_WARN : ModSounds.BELL_TINKLE, warn ? 1.6f : 0.7f, 1.0f);
        level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.1, getZ(), warn ? 3 : 1, 0.3, 0.1, 0.3, 0.6);
        Bestiary.clueNear(level, position(), warn ? 32 : 12, species(), "tinkle");
    }

    @Override protected void serverTick(ServerLevel level) {
        if (warnCooldown > 0) warnCooldown--;
        if (tickCount % 20 == 0) {
            if (!core.bonded()) {
                lonely = level.getNearestPlayer(this, 32) != null ? 0 : lonely + 20;
                if (lonely >= YokaiBalance.Director.OBJECT_SLEEP_TICKS && home != null && restore(level)) {
                    core.storing = true;
                    discard();
                    return;
                }
            }
            if (warnCooldown == 0 && (!core.bonded() || core.ownerPlayer() != null) && hostileNear(level)) {
                warnCooldown = YokaiBalance.Bell.WARN_COOLDOWN;
                warning++;
                warnedAt = level.getGameTime();
                ring(level, true);
            }
            if (!core.bonded()) attend(level);
        }
        if (--tinkleCooldown <= 0) {
            tinkleCooldown = 160 + random.nextInt(160);
            if (!core.bonded() && level.getNearestPlayer(this, 6) != null) ring(level, false);
        }
    }

    /** Players with complete sight who come close soon after a warning have attended it (once per warning). */
    private void attend(ServerLevel level) {
        if (level.getGameTime() - warnedAt > YokaiBalance.Bell.ATTEND_WINDOW) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) > YokaiBalance.Bell.ATTEND_RADIUS * YokaiBalance.Bell.ATTEND_RADIUS || !Perception.canInteract(p, this)) continue;
            YokaiCore.Ritual r = core.ritual(p);
            if (r.stage == warning) continue;
            r.stage = warning;
            r.count++;
            p.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.bell_attended", Math.min(r.count, YokaiBalance.Bell.PACT_WARNINGS),
                    YokaiBalance.Bell.PACT_WARNINGS).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(Items.COPPER_INGOT)) return InteractionResult.PASS;
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.need_pacts").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        YokaiCore.Ritual r = core.ritual(player);
        if (r.count < YokaiBalance.Bell.PACT_WARNINGS) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.bell_need", r.count, YokaiBalance.Bell.PACT_WARNINGS).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (Bonds.pact(player, this)) {
            consume(player, stack, 1);
            core.resetRitual(player);
            home = null;
        }
        return InteractionResult.SUCCESS;
    }

    private boolean restore(ServerLevel level) {
        if (returned || home == null || !level.isLoaded(home) || !level.getBlockState(home).canBeReplaced()) return false;
        level.setBlock(home, homeState, 3);
        Provenance.remark(level, home);
        returned = true;
        return true;
    }

    private void dropBell(ServerLevel level) {
        if (returned) return;
        returned = true;
        spawnAtLocation(level, new ItemStack(Items.BELL));
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && !core.storing && !returned && (core.bonded() || !restore(level))) dropBell(level);
    }

    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && reason == RemovalReason.DISCARDED && !core.storing && !returned && !isDeadOrDying()) {
            if (core.bonded() || !restore(level)) dropBell(level);
        }
        super.remove(reason);
    }

    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.BELL_BLOCK; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BELL_BLOCK; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (home != null) out.store("Home", BlockPos.CODEC, home);
        out.store("HomeState", BlockState.CODEC, homeState);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        home = in.read("Home", BlockPos.CODEC).orElse(null);
        homeState = in.read("HomeState", BlockState.CODEC).filter(s -> s.is(Blocks.BELL)).orElse(Blocks.BELL.defaultBlockState());
    }
}
