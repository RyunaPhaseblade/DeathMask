package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Companions;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanuki: a trickster of forests and villages. It drums on its belly and leaves illusory treasures that vanish when
 * investigated; it never touches a real item. Bonded, it distracts ordinary enemies for 3 s (15 s cooldown) and never
 * redirects a boss. Pact: find three of its decoys, then offer an apple.
 */
public class TanukiEntity extends AbstractYokai implements Companions.Special {
    public static final int ANIM_DRUM = 1, ANIM_TRICK = 2;
    public static final TagKey<EntityType<?>> UNDISTRACTABLE = TagKey.create(Registries.ENTITY_TYPE, Quantumdeath.id("yokai_undistractable"));
    private int decoyCooldown = 200, drumCooldown = 300, distractCooldown, distractTicks;
    private final List<Mob> distracted = new ArrayList<>();
    private Vec3 lure = Vec3.ZERO;

    public TanukiEntity(EntityType<? extends TanukiEntity> type, Level level) {
        super(type, level, Species.TANUKI);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Tanuki.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 6f, 1.0, 1.3, p -> !core.bonded() && !p.isSpectator() && !p.isHolding(Items.APPLE)));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
    }

    @Override protected void serverTick(ServerLevel level) {
        if (distractCooldown > 0) distractCooldown--;
        if (distractTicks > 0) {
            distractTicks--;
            for (Mob m : distracted) {
                if (!m.isAlive()) continue;
                m.setTarget(null);
                m.getNavigation().moveTo(lure.x, lure.y, lure.z, 1.0);
            }
            if (distractTicks == 0) distracted.clear();
        }
        if (core.bonded()) {
            ServerPlayer owner = core.ownerPlayer();
            if (owner != null && core.order() == YokaiCore.Order.DEFEND && distractCooldown == 0 && owner.hurtTime > 0) special(owner);
            return;
        }
        Player near = level.getNearestPlayer(this, 24);
        if (near == null) return;
        if (--drumCooldown <= 0) {
            drumCooldown = 400 + random.nextInt(400);
            playAnim(ANIM_DRUM, 30);
            playSound(ModSounds.TANUKI_DRUM, 1.2f, 0.9f + random.nextFloat() * 0.2f);
            Bestiary.clueNear(level, position(), 32, species(), "drum");
        }
        if (--decoyCooldown <= 0) {
            decoyCooldown = 500 + random.nextInt(600);
            if (level.getEntitiesOfClass(TanukiDecoy.class, getBoundingBox().inflate(32)).size() < 2) placeDecoy(level, near.position(), 5, 10);
        }
    }

    private void placeDecoy(ServerLevel level, Vec3 around, double min, double max) {
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(around.x + Math.cos(a) * d), z = (int) Math.floor(around.z + Math.sin(a) * d);
            if (!level.isLoaded(new BlockPos(x, (int) around.y, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - around.y) > 6) continue;
            TanukiDecoy decoy = YokaiContent.TANUKI_DECOY.create(level, EntitySpawnReason.EVENT);
            if (decoy == null) return;
            decoy.setup(getUUID());
            decoy.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            level.addFreshEntity(decoy);
            playAnim(ANIM_TRICK, 20);
            playSound(ModSounds.TANUKI_CHITTER, 0.8f, 1.1f);
            lure = decoy.position();
            return;
        }
    }

    /** A player investigated one of this tanuki's decoys. */
    void decoyFound(ServerPlayer player) {
        if (core.bonded() || !Perception.canInteract(player, this) || !SkillManager.has(player, SkillId.BOND_I)) return;
        YokaiCore.Ritual r = core.ritual(player);
        if (r.count >= YokaiBalance.Tanuki.PACT_DECOYS) return;
        r.count++;
        player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tanuki_decoy", r.count, YokaiBalance.Tanuki.PACT_DECOYS).withStyle(ChatFormatting.GOLD));
    }

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(Items.APPLE)) return InteractionResult.PASS;
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.need_pacts").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        YokaiCore.Ritual r = core.ritual(player);
        if (r.count < YokaiBalance.Tanuki.PACT_DECOYS) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tanuki_need", r.count, YokaiBalance.Tanuki.PACT_DECOYS).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (Bonds.pact(player, this)) {
            consume(player, stack, 1);
            core.resetRitual(player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Distraction: ordinary enemies near the owner lose their target and go after an illusion for 3 s. Bosses never do. */
    @Override public void special(ServerPlayer owner) {
        if (distractCooldown > 0 || !(level() instanceof ServerLevel level)) return;
        distracted.clear();
        for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(16), m -> m.isAlive() && m.getTarget() != null && m != this
                && !(m instanceof HellBoss) && !m.is(UNDISTRACTABLE) && !(m instanceof YokaiCreature y && y.yokai().bonded())))
            distracted.add(m);
        distractCooldown = YokaiBalance.Tanuki.DISTRACT_COOLDOWN;
        if (distracted.isEmpty()) return;
        Vec3 away = owner.position().add(owner.getLookAngle().multiply(-1, 0, -1).normalize().scale(-8));
        placeDecoy(level, away, 0, 3);
        distractTicks = YokaiBalance.Tanuki.DISTRACT_TICKS;
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.TANUKI_CHITTER; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }
    @Override public int getAmbientSoundInterval() { return 300; }
}
