package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Companions;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Provenance;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiGoals;

/**
 * Chest tsukumogami: the spirit of a natural, untouched chest. Awakening moves the chest's contents into the creature
 * (its loot is rolled then, exactly as opening it would) and remembers its place; it never takes, adds or deletes an
 * item. When it sleeps or dies it puts the chest back where it stood with every item in it, or, if that place is no
 * longer free (or it was a companion), drops the chest and its contents. One object, never two.
 */
public class ChestTsukumogami extends AbstractYokai implements Companions.Storage {
    public static final int ANIM_LID = 1, ANIM_WARN = 2, ANIM_BITE = 3;
    private static final int FLAG_REPAIRED = 1, FLAG_OPEN = 2;

    private final Storage items = new Storage();
    private @Nullable BlockPos home;
    private BlockState homeState = Blocks.CHEST.defaultBlockState();
    private int lonely, biteWarn, signCooldown = 100;
    private @Nullable LivingEntity biteTarget;
    private boolean returned;

    /** The chest's own 27 slots, valid only while its holder stays close. */
    private final class Storage extends SimpleContainer {
        private final java.util.List<ContainerUser> users = new java.util.ArrayList<>();
        Storage() { super(27); }
        @Override public boolean stillValid(Player player) { return isAlive() && player.distanceToSqr(ChestTsukumogami.this) < 64; }
        @Override public void startOpen(ContainerUser user) {
            users.add(user);
            flag(FLAG_OPEN, true);
            playSound(SoundEvents.CHEST_OPEN, 0.8f, 0.8f);
        }
        @Override public void stopOpen(ContainerUser user) {
            users.remove(user);
            if (users.isEmpty()) {
                flag(FLAG_OPEN, false);
                playSound(SoundEvents.CHEST_CLOSE, 0.8f, 0.8f);
            }
        }
    }

    public ChestTsukumogami(EntityType<? extends ChestTsukumogami> type, Level level) {
        super(type, level, Species.CHEST);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Chest.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 16).add(Attributes.ATTACK_DAMAGE, YokaiBalance.Chest.BITE_DAMAGE);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6, 200));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        targetSelector.addGoal(1, new YokaiGoals.Defend(this));
    }

    public boolean repairedClient() { return flag(FLAG_REPAIRED); }
    /** Items it carries (its chest's contents). */
    public java.util.List<ItemStack> contents() { return items.getItems().stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(); }
    public @Nullable BlockPos home() { return home; }
    public boolean openClient() { return flag(FLAG_OPEN); }

    // ------------------------------------------------------------------------------------------------ awakening

    /** Wakes the natural chest at {@code pos}, or returns null when it is not eligible (placed, opened, double, wet...). */
    public static @Nullable ChestTsukumogami awaken(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
        if (!Provenance.eligibleChest(level, pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity be)) return null;
        ChestTsukumogami c = YokaiContent.CHEST.create(level, EntitySpawnReason.EVENT);
        if (c == null) return null;
        be.unpackLootTable(by);
        for (int i = 0; i < be.getContainerSize() && i < 27; i++) c.items.setItem(i, be.removeItemNoUpdate(i));
        c.home = pos.immutable();
        c.homeState = level.getBlockState(pos);
        float yaw = c.homeState.getValue(ChestBlock.FACING).toYRot();
        // The chest is empty now, so removing it drops nothing: its contents live in the spirit.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        c.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        c.setPersistenceRequired();
        level.addFreshEntity(c);
        c.playAnim(ANIM_LID, 20);
        c.playSound(ModSounds.CHEST_LID, 1.0f, 1.0f);
        Bestiary.clueNear(level, c.position(), 16, Species.CHEST, "lid");
        return c;
    }

    /** Demonstration spirit (commands): a few ordinary items, no home to return to. */
    public void fillDemo() {
        items.setItem(0, new ItemStack(Items.BREAD, 3));
        items.setItem(4, new ItemStack(Items.IRON_INGOT, 2));
        items.setItem(13, new ItemStack(Items.STRING, 5));
        setPersistenceRequired();
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @Override protected void serverTick(ServerLevel level) {
        if (tickCount % 20 == 0 && !core.bonded()) {
            boolean anyone = level.getNearestPlayer(this, 32) != null;
            lonely = anyone ? 0 : lonely + 20;
            if (lonely >= YokaiBalance.Director.OBJECT_SLEEP_TICKS && home != null) {
                // Unattended for a while: it falls asleep in its place again.
                if (restore(level)) {
                    core.storing = true;
                    discard();
                    return;
                }
            }
        }
        if (biteWarn > 0) {
            getNavigation().stop();
            if (biteTarget != null) getLookControl().setLookAt(biteTarget);
            if (--biteWarn == 0) {
                preparing(false);
                if (biteTarget != null && biteTarget.isAlive() && distanceToSqr(biteTarget) < 6.5) {
                    playAnim(ANIM_BITE, 8);
                    playSound(ModSounds.CHEST_BITE, 1.0f, 1.0f);
                    biteTarget.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Chest.BITE_DAMAGE);
                }
                biteTarget = null;
            }
            return;
        }
        if (core.bonded()) {
            if (core.order() == YokaiCore.Order.DEFEND && getTarget() != null && distanceToSqr(getTarget()) < 4) startBite(getTarget());
            else if (getTarget() != null) getNavigation().moveTo(getTarget(), 1.2);
            return;
        }
        // Signs: knocking from inside and a lid that lifts by itself.
        if (--signCooldown <= 0) {
            signCooldown = 200 + random.nextInt(300);
            if (level.getNearestPlayer(this, 16) != null) {
                boolean knock = random.nextBoolean();
                playSound(knock ? ModSounds.CHEST_KNOCK : ModSounds.CHEST_LID, 0.9f, 0.9f + random.nextFloat() * 0.2f);
                if (!knock) playAnim(ANIM_LID, 16);
                Bestiary.clueNear(level, position(), 16, species(), knock ? "knocking" : "lid");
            }
        }
        // Mischief: it hops away from anyone who can see it coming.
        if (tickCount % 10 == 0) {
            Player near = level.getNearestPlayer(this, 4);
            if (near instanceof ServerPlayer sp && Perception.canInteract(sp, this) && !sp.isCreative()) {
                Vec3 away = position().subtract(near.position()).multiply(1, 0, 1).normalize().scale(5);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.3);
            }
        }
    }

    private void startBite(LivingEntity target) {
        if (biteWarn > 0) return;
        biteTarget = target;
        biteWarn = YokaiBalance.Chest.WARNING;
        preparing(true);
        playAnim(ANIM_WARN, biteWarn);
        playSound(ModSounds.CHEST_LID, 1.2f, 0.7f);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && !core.isOwner(attacker)) startBite(attacker);
        return hurt;
    }

    // ------------------------------------------------------------------------------------------------ pact and storage

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(ItemTags.PLANKS) && !stack.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.need_pacts").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        YokaiCore.Ritual r = core.ritual(player);
        if (stack.is(ItemTags.PLANKS)) {
            if (r.stage >= 1) return InteractionResult.SUCCESS;
            if (stack.getCount() < YokaiBalance.Chest.REPAIR_PLANKS && !player.getAbilities().instabuild) {
                player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.chest_planks", YokaiBalance.Chest.REPAIR_PLANKS).withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, YokaiBalance.Chest.REPAIR_PLANKS);
            r.stage = 1;
            flag(FLAG_REPAIRED, true);
            playSound(SoundEvents.WOOD_PLACE, 1.0f, 1.0f);
            ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.8, getZ(), 8, 0.4, 0.3, 0.4, 0);
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.chest_repaired").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        if (r.stage < 1) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.chest_planks", YokaiBalance.Chest.REPAIR_PLANKS).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (Bonds.pact(player, this)) {
            consume(player, stack, 1);
            core.resetRitual(player);
            home = null; // a companion now: it goes where its owner goes
        }
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult ownerInteract(ServerPlayer owner, InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty() && owner.isShiftKeyDown()) {
            openStorage(owner);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override public void openStorage(ServerPlayer owner) {
        if (!core.isOwner(owner) || owner.distanceToSqr(this) > 64) return;
        owner.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, items), getDisplayName()));
    }

    // ------------------------------------------------------------------------------------------------ returning the object

    /** Puts the chest back at home with its contents. False if home is gone or occupied. */
    private boolean restore(ServerLevel level) {
        if (returned || home == null || !level.isLoaded(home)) return false;
        BlockState here = level.getBlockState(home);
        if (!here.canBeReplaced() || !level.getFluidState(home).isEmpty()) return false;
        level.setBlock(home, homeState.setValue(ChestBlock.WATERLOGGED, false), 3);
        if (!(level.getBlockEntity(home) instanceof ChestBlockEntity be)) return false;
        for (int i = 0; i < 27; i++) be.setItem(i, items.removeItemNoUpdate(i));
        Provenance.remark(level, home);
        returned = true;
        level.playSound(null, home, SoundEvents.CHEST_CLOSE, getSoundSource(), 0.9f, 0.7f);
        return true;
    }

    /** The chest and everything in it, dropped where the spirit is. */
    private void dropAll(ServerLevel level) {
        if (returned) return;
        returned = true;
        Containers.dropContents(level, blockPosition(), items);
        spawnAtLocation(level, new ItemStack(Items.CHEST));
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && !core.storing && !returned && (core.bonded() || !restore(level))) dropAll(level);
    }

    @Override public void remove(RemovalReason reason) {
        // Removed without dying (commands, cleanup): the object must still go back.
        if (level() instanceof ServerLevel level && reason == RemovalReason.DISCARDED && !core.storing && !returned && !isDeadOrDying()) {
            if (core.bonded() || !restore(level)) dropAll(level);
        }
        super.remove(reason);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.WOOD_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WOOD_BREAK; }
    @Override public boolean isPushable() { return false; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        items.storeAsItemList(out.list("Items", ItemStack.CODEC));
        if (home != null) out.store("Home", BlockPos.CODEC, home);
        out.store("HomeState", BlockState.CODEC, homeState);
        out.putBoolean("Repaired", flag(FLAG_REPAIRED));
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        items.fromItemList(in.listOrEmpty("Items", ItemStack.CODEC));
        home = in.read("Home", BlockPos.CODEC).orElse(null);
        homeState = in.read("HomeState", BlockState.CODEC).filter(s -> s.is(Blocks.CHEST)).orElse(Blocks.CHEST.defaultBlockState());
        flag(FLAG_REPAIRED, in.getBooleanOr("Repaired", false));
    }
}
