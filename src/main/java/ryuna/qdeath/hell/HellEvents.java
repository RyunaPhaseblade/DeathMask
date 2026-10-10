package ryuna.qdeath.hell;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import ryuna.qdeath.effects.modEffects;
import ryuna.qdeath.entity.vigil.VigilDirector;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.limbo.LimboDirector;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.plague.PlagueDirector;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-tick Hell rules: arrival titles, layer hazards, sanctuary, armor set bonuses, and the remains of the fallen. */
public final class HellEvents {
    private HellEvents() {}

    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HellEvents::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_DIMENSION.remove(handler.player.getUUID());
            VigilDirector.forget(handler.player.getUUID());
            LimboSenses.forget(handler.player.getUUID());
            LimboDirector.forget(handler.player.getUUID());
            PlagueDirector.forget(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HellScheduler.clear();
            LAST_DIMENSION.clear();
            LimboSenses.clear();
            LimboDirector.clear();
            PlagueDirector.reset();
        });
        // Where a player dies in Hell, their remains stay behind, with a candle, for friends to find.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player) || HellLayer.of(player.level()) == null) return;
            ServerLevel level = (ServerLevel) player.level();
            BlockPos p = player.blockPosition();
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
                level.setBlockAndUpdate(p, ModBlocks.PILGRIM_REMAINS.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, player.getDirection()));
                if (level.getBlockState(p.east()).isAir() && level.getBlockState(p.east().below()).isSolid())
                    level.setBlockAndUpdate(p.east(), ModBlocks.MOURNING_CANDLE.defaultBlockState());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        HellScheduler.tick(server);
        VigilDirector.tick(server);
        LimboDirector.tick(server);
        PlagueDirector.tick(server);
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dim = player.level().dimension();
            ResourceKey<Level> last = LAST_DIMENSION.put(player.getUUID(), dim);
            HellLayer layer = HellLayer.of(dim);
            if (layer != null && last != dim) arrive(player, layer);
            // Crossing from the Overworld into the Limbo makes the border permeable: minor seepages may begin.
            if (layer == HellLayer.VESTIBULE && last == Level.OVERWORLD) PlagueDirector.portalCrossed(server);
            setBonuses(player);
            if (layer == null || !player.isAlive() || player.isSpectator()) continue;
            ServerLevel level = (ServerLevel) player.level();
            if (VigilEntity.inSanctuary(level, player.blockPosition())) player.addEffect(new MobEffectInstance(ModEffects.SANCTUARY, 40, 0, true, true));
            hazards(level, player, layer);
        }
    }

    private static void arrive(ServerPlayer player, HellLayer layer) {
        HellTravel.announce(player, layer);
        HellProgress progress = HellProgress.of(player);
        if (layer == HellLayer.VESTIBULE) LimboDirector.arrive(player, !progress.hasVisited(layer));
        if (progress.hasVisited(layer)) return;
        HellProgress.set(player, progress.withVisited(layer));
        player.sendSystemMessage(Component.translatable("layer.quantumdeath." + layer.id + ".first").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (layer == HellLayer.VESTIBULE) {
            // Onboarding: the compass that points at the Toll Gate, and the first page of the pilgrim's journal.
            give(player, new ItemStack(HellItems.PILGRIMS_COMPASS));
            ItemStack page = new ItemStack(HellItems.JOURNAL_PAGE);
            page.set(HellItems.PAGE, 0);
            give(player, page);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    private static void hazards(ServerLevel level, ServerPlayer player, HellLayer layer) {
        if (player.isCreative()) return;
        switch (layer) {
            case VESTIBULE, DIS -> {
                // Searing heat beside lava and molten iron, unless protected. The original Anti-Perma Burn potion works here too.
                if (nearHeat(level, player.blockPosition()) && !heatProof(player))
                    player.addEffect(new MobEffectInstance(ModEffects.SEARING, 70, layer == HellLayer.DIS ? 1 : 0));
            }
            case GORGE -> {
                // Gluttony: the Gorge makes you hungry.
                if (level.getGameTime() % 400 == 0) player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
                if (player.isInWater() && !player.hasEffect(MobEffects.POISON)) player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            }
            case MIRE -> {
                // Sullenness: the black water drags at limbs.
                if (player.isInWater() && !fullSet(player, HellItems.LEVIATHAN_HELM, HellItems.LEVIATHAN_CUIRASS, HellItems.LEVIATHAN_GREAVES, HellItems.LEVIATHAN_BOOTS)) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 0));
                }
            }
            case ZENITH -> {
                // The Zenith is wrong in quiet ways.
                if (level.getRandom().nextInt(90) == 0)
                    player.sendOverlayMessage(Component.translatable("zenith.quantumdeath.whisper." + level.getRandom().nextInt(12), player.getName()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    private static boolean nearHeat(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(ModBlocks.MOLTEN_DIS_IRON)) return true;
        }
        return false;
    }

    private static boolean heatProof(ServerPlayer p) {
        return p.hasEffect(modEffects.ANTI_PERMA_BURN) || p.hasEffect(MobEffects.FIRE_RESISTANCE)
                || fullSet(p, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)
                || fullSet(p, HellItems.PILGRIM_HOOD, HellItems.PILGRIM_ROBE, HellItems.PILGRIM_LEGGINGS, HellItems.PILGRIM_BOOTS)
                || fullSet(p, HellItems.DIS_IRON_HELMET, HellItems.DIS_IRON_CHESTPLATE, HellItems.DIS_IRON_LEGGINGS, HellItems.DIS_IRON_BOOTS);
    }

    private static boolean fullSet(ServerPlayer p, Item head, Item chest, Item legs, Item feet) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(head) && p.getItemBySlot(EquipmentSlot.CHEST).is(chest)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(legs) && p.getItemBySlot(EquipmentSlot.FEET).is(feet);
    }

    private static void setBonuses(ServerPlayer p) {
        if (fullSet(p, HellItems.LEVIATHAN_HELM, HellItems.LEVIATHAN_CUIRASS, HellItems.LEVIATHAN_GREAVES, HellItems.LEVIATHAN_BOOTS) && p.isInWater()) {
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
            p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false));
        }
        if (fullSet(p, HellItems.DIS_IRON_HELMET, HellItems.DIS_IRON_CHESTPLATE, HellItems.DIS_IRON_LEGGINGS, HellItems.DIS_IRON_BOOTS))
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
        if (p.getItemBySlot(EquipmentSlot.HEAD).is(HellItems.VIGILS_DEATH_MASK)) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false));
            p.removeEffect(MobEffects.DARKNESS);
            p.removeEffect(MobEffects.BLINDNESS);
        }
    }
}
