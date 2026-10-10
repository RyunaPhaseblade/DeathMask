package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.AnchoredYokai;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiGoals;

/**
 * Kodama: a gentle tree spirit. It shows a shelter at the foot of a grown tree in its grove; given a sapling, that
 * shelter makes hostile yokai notice people 25% later for five minutes (within 8 blocks). It does not end a pursuit
 * already begun, and it never leaves its forest (no pact).
 */
public class KodamaEntity extends AbstractYokai implements AnchoredYokai {
    public static final int ANIM_RATTLE = 1, ANIM_POINT = 2;
    private static final DustParticleOptions LIGHT = new DustParticleOptions(0xe8f4d8, 0.7f);
    private static final DustParticleOptions BLESSED = new DustParticleOptions(0xb8f0a0, 1.0f);
    private BlockPos anchor = BlockPos.ZERO;
    private @Nullable BlockPos shelter;
    private long blessedUntil;
    private int rattleCooldown = 100;

    public KodamaEntity(EntityType<? extends KodamaEntity> type, Level level) {
        super(type, level, Species.KODAMA);
        xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Kodama.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override public void setAnchor(BlockPos pos) {
        anchor = pos;
        setHomeTo(pos, 8);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5, 160));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    /** The foot of a grown tree (a log under natural leaves) near its anchor. */
    private @Nullable BlockPos findShelter(ServerLevel level) {
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-8, -3, -8), anchor.offset(8, 3, 8))) {
            if (!level.getBlockState(p).is(BlockTags.LOGS) || level.getBlockState(p.below()).is(BlockTags.LOGS)) continue;
            boolean grown = false;
            for (int dy = 2; dy < 9 && !grown; dy++) {
                var s = level.getBlockState(p.above(dy));
                grown = s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);
            }
            if (grown && (best == null || p.distSqr(anchor) < best.distSqr(anchor))) best = p.immutable();
        }
        return best;
    }

    @Override protected void serverTick(ServerLevel level) {
        if (anchor.equals(BlockPos.ZERO)) setAnchor(blockPosition());
        if (shelter == null && tickCount % 100 == 1) shelter = findShelter(level);
        boolean blessed = level.getGameTime() < blessedUntil;
        // After a restart the blessing (saved with the kodama) is put back in force for the time it has left.
        if (blessed && shelter != null && tickCount % 100 == 2 && !YokaiGoals.sheltered(level, Vec3.atBottomCenterOf(shelter)))
            YokaiGoals.shelter(level, Vec3.atBottomCenterOf(shelter), blessedUntil);
        if (shelter != null && tickCount % 10 == 0) {
            // Little lights mark the shelter; brighter while it is blessed.
            Vec3 c = Vec3.atBottomCenterOf(shelter).add(0, 0.2, 0);
            double r = blessed ? YokaiBalance.Kodama.SHELTER_RADIUS : 1.5;
            for (int k = 0; k < (blessed ? 6 : 2); k++) {
                double a = random.nextDouble() * Math.PI * 2;
                level.sendParticles(blessed ? BLESSED : LIGHT, c.x + Math.cos(a) * r * random.nextDouble(), c.y + random.nextDouble(), c.z + Math.sin(a) * r * random.nextDouble(),
                        1, 0, 0.05, 0, 0);
            }
            if (!blessed && distanceToSqr(Vec3.atCenterOf(shelter)) > 9) getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 1.5, 0.6);
        }
        if (--rattleCooldown <= 0) {
            rattleCooldown = 200 + random.nextInt(300);
            if (level.getNearestPlayer(this, 20) != null) {
                playAnim(ANIM_RATTLE, 24);
                playSound(ModSounds.KODAMA_RATTLE, 0.9f, 0.9f + random.nextFloat() * 0.3f);
                Bestiary.clueNear(level, position(), 20, species(), "rattle");
                if (shelter != null) Bestiary.clueNear(level, Vec3.atCenterOf(shelter), 12, species(), "lights");
            }
        }
    }

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(ItemTags.SAPLINGS)) return InteractionResult.PASS;
        ServerLevel level = (ServerLevel) level();
        if (shelter == null) shelter = findShelter(level);
        if (shelter == null) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.kodama_no_shelter").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        consume(player, stack, 1);
        blessedUntil = level.getGameTime() + YokaiBalance.Kodama.SHELTER_TICKS;
        YokaiGoals.shelter(level, Vec3.atBottomCenterOf(shelter), blessedUntil);
        playAnim(ANIM_POINT, 30);
        playSound(ModSounds.KODAMA_RATTLE, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.8, getZ(), 8, 0.3, 0.3, 0.3, 0);
        player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.kodama_shelter").withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    public @Nullable BlockPos shelter() { return shelter; }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.KODAMA_RATTLE; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Anchor", BlockPos.CODEC, anchor);
        out.putLong("BlessedUntil", blessedUntil);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("Anchor", BlockPos.CODEC).ifPresent(this::setAnchor);
        blessedUntil = in.getLongOr("BlessedUntil", 0);
    }
}
