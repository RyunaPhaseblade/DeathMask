package ryuna.qdeath.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellProgress;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModEffects;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.Optional;
import java.util.function.Consumer;

/** Behaviour classes for Hell items. */
public final class HellItemTypes {
    private HellItemTypes() {}

    /** Item with a one-line italic lore entry ({@code <description id>.lore}). */
    public static class Lore extends Item {
        private final boolean foil;
        public Lore(Properties p) { this(p, false); }
        public Lore(Properties p, boolean foil) { super(p); this.foil = foil; }
        @Override public boolean isFoil(ItemStack stack) { return foil || super.isFoil(stack); }
        @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable(getDescriptionId() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Points to the nearest boss arena of the current layer (needle via a lodestone tracker). */
    public static class PilgrimsCompass extends Lore {
        public PilgrimsCompass(Properties p) { super(p); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel server) {
                ItemStack stack = player.getItemInHand(hand);
                HellLayer layer = HellLayer.of(level);
                if (layer == null) {
                    player.sendOverlayMessage(Component.translatable("item.quantumdeath.pilgrims_compass.spins").withStyle(ChatFormatting.GRAY));
                } else {
                    BlockPos found = server.findNearestMapStructure(layer.arenaTag, player.blockPosition(), 64, false);
                    if (found == null) {
                        player.sendOverlayMessage(Component.translatable("item.quantumdeath.pilgrims_compass.lost").withStyle(ChatFormatting.GRAY));
                    } else {
                        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found)), false));
                        int dist = (int) Math.sqrt(found.distSqr(player.blockPosition().atY(found.getY())));
                        player.sendOverlayMessage(Component.translatable("item.quantumdeath.pilgrims_compass.found", dist).withStyle(ChatFormatting.GOLD));
                        server.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0f, 0.6f);
                    }
                }
                player.getCooldowns().addCooldown(stack, 60);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Fires a hook; when it bites stone, the wielder is hauled to it. */
    public static class CinderHook extends Lore {
        public CinderHook(Properties p) { super(p); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                HellBolt hook = HellBolt.shoot(server, player, HellBolt.Kind.HOOK, 2.4f);
                hook.setNoGravity(true);
                server.playSound(null, player.blockPosition(), ModSounds.HOOK_FIRE, SoundSource.PLAYERS, 1.0f, 1.0f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            player.getCooldowns().addCooldown(stack, 24);
            return InteractionResult.SUCCESS;
        }
    }

    /** A funeral hand-bell. Its toll banishes a pursuing VIGIL and staggers nearby monsters. */
    public static class MourningBell extends Lore {
        public MourningBell(Properties p) { super(p); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                server.playSound(null, player.blockPosition(), ModSounds.MOURNING_BELL, SoundSource.PLAYERS, 3.0f, 1.0f);
                server.sendParticles(ModParticles.SOUL_MOTE, player.getX(), player.getY() + 1, player.getZ(), 40, 2.5, 1, 2.5, 0.05);
                for (VigilEntity vigil : server.getEntitiesOfClass(VigilEntity.class, player.getBoundingBox().inflate(40))) vigil.banish(player);
                // Against the Limbo's hordes the toll carries further (12 blocks for the dead, 8 for other monsters): a window to break away.
                for (Monster m : server.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12),
                        m -> m instanceof ryuna.qdeath.entity.limbo.LimboDead || m.distanceTo(player) <= 8)) {
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                    m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                }
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            player.getCooldowns().addCooldown(stack, 900);
            return InteractionResult.SUCCESS;
        }
    }

    /** Hold to gaze. A VIGIL caught in the mirror cannot move while it is watched. */
    public static class MirrorOfLethe extends Lore {
        public MirrorOfLethe(Properties p) { super(p); }
        @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.SPYGLASS; }
        @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 1200; }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            if (level.isClientSide()) player.playSound(ModSounds.MIRROR_GAZE, 0.6f, 1.0f);
            return InteractionResult.CONSUME;
        }
        @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (!(level instanceof ServerLevel server) || remaining % 5 != 0) return;
            Vec3 look = user.getViewVector(1f);
            for (VigilEntity vigil : server.getEntitiesOfClass(VigilEntity.class, user.getBoundingBox().inflate(64))) {
                Vec3 to = vigil.getEyePosition().subtract(user.getEyePosition()).normalize();
                if (look.dot(to) > 0.94 && user.hasLineOfSight(vigil)) {
                    vigil.freeze(8);
                    server.sendParticles(ModParticles.SOUL_MOTE, vigil.getX(), vigil.getEyeY(), vigil.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
                    if (remaining % 20 == 0) stack.hurtAndBreak(1, user, user.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
            }
        }
    }

    /** The Toll-Keeper's lantern. Carried, it lifts darkness and outlines VIGIL wherever it hides. */
    public static class FerrymanLantern extends Lore {
        public FerrymanLantern(Properties p) { super(p, true); }
        @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
            if (!(owner instanceof Player player) || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) || level.getGameTime() % 20 != 0) return;
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
            for (VigilEntity vigil : level.getEntitiesOfClass(VigilEntity.class, player.getBoundingBox().inflate(72)))
                vigil.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        }
    }

    /** One of twelve pages of the last pilgrim's journal. Reading all of them is rewarded. */
    public static class JournalPage extends Item {
        public static final int PAGES = 12;
        private static final Identifier BLESSING = Quantumdeath.id("pilgrims_blessing");
        public JournalPage(Properties p) { super(p); }
        public static int page(ItemStack stack) { return Math.floorMod(stack.getOrDefault(HellItems.PAGE, 0), PAGES); }
        @Override public Component getName(ItemStack stack) {
            return Component.translatable("item.quantumdeath.journal_page.numbered", page(stack) + 1);
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("journal.quantumdeath.title." + page(stack)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                int page = page(player.getItemInHand(hand));
                sp.sendSystemMessage(Component.translatable("journal.quantumdeath.title." + page).withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE));
                sp.sendSystemMessage(Component.translatable("journal.quantumdeath.page." + page).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                HellProgress progress = HellProgress.of(sp);
                if ((progress.pages() & (1 << page)) == 0) {
                    progress = progress.withPage(page);
                    HellProgress.set(sp, progress);
                    int read = Integer.bitCount(progress.pages());
                    sp.sendOverlayMessage(Component.translatable("journal.quantumdeath.collected", read, PAGES).withStyle(ChatFormatting.DARK_AQUA));
                    if (read == PAGES) {
                        var health = sp.getAttribute(Attributes.MAX_HEALTH);
                        if (health != null && health.getModifier(BLESSING) == null)
                            health.addPermanentModifier(new AttributeModifier(BLESSING, 4.0, AttributeModifier.Operation.ADD_VALUE));
                        sp.sendSystemMessage(Component.translatable("journal.quantumdeath.complete").withStyle(ChatFormatting.LIGHT_PURPLE));
                        level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Eaten: permanently raises maximum health once (each heart type stacks with the others). */
    public static class Heart extends Lore {
        private final Identifier modifier;
        private final double amount;
        public Heart(Properties p, String id, double amount) { super(p, true); this.modifier = Quantumdeath.id(id); this.amount = amount; }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide() && entity instanceof Player player) {
                var health = player.getAttribute(Attributes.MAX_HEALTH);
                if (health != null && health.getModifier(modifier) == null) {
                    health.addPermanentModifier(new AttributeModifier(modifier, amount, AttributeModifier.Operation.ADD_VALUE));
                    player.sendOverlayMessage(Component.translatable("item.quantumdeath.heart.absorbed").withStyle(ChatFormatting.RED));
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
            }
            return super.finishUsingItem(stack, level, entity);
        }
    }

    /** Boss weapons. Their special effect triggers after a hit lands. */
    public static class HellWeapon extends Lore {
        public enum Kind { OAR, CLEAVER, HARPOON, BRAND, REQUIEM }
        private final Kind kind;
        public HellWeapon(Properties p, Kind kind) { super(p); this.kind = kind; }

        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (kind != Kind.HARPOON) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                HellBolt.shoot(server, player, HellBolt.Kind.HARPOON, 2.6f);
                server.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            player.getCooldowns().addCooldown(stack, 30);
            return InteractionResult.SUCCESS;
        }

        @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.postHurtEnemy(stack, target, attacker);
            if (!(attacker.level() instanceof ServerLevel level)) return;
            switch (kind) {
                case OAR -> {
                    target.igniteForSeconds(4);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    if (attacker.getRandom().nextInt(4) == 0) soulBurst(level, attacker, target.position(), 3.0, 4.0f);
                }
                case CLEAVER -> {
                    attacker.heal(2.0f);
                    target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
                    level.sendParticles(ModParticles.BLOOD_EMBER, target.getX(), target.getY(0.6), target.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                }
                case HARPOON -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                case BRAND -> {
                    target.igniteForSeconds(6);
                    if (attacker.fallDistance > 0 && !attacker.onGround()) HellBolt.fireColumn(level, attacker, target.blockPosition(), 6.0f);
                }
                case REQUIEM -> {
                    target.addEffect(new MobEffectInstance(ModEffects.MOURNED, 100, 0));
                    if (!target.isAlive()) {
                        attacker.heal(4.0f);
                        soulBurst(level, attacker, target.position(), 4.0, 6.0f);
                    }
                }
            }
        }

        private static void soulBurst(ServerLevel level, LivingEntity attacker, Vec3 at, double radius, float damage) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1, at.z, 30, radius / 2, 0.5, radius / 2, 0.05);
            level.playSound(null, BlockPos.containing(at), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e != attacker && e instanceof Monster)) {
                e.hurtServer(level, level.damageSources().indirectMagic(attacker, attacker), damage);
            }
        }
    }
}
