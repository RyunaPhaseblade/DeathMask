package ryuna.qdeath.hell;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ryuna.qdeath.entity.boss.BossSummons;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.entity.vigil.VigilDirector;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;

import java.util.Collection;
import java.util.List;

/** /deathmask: travel, progression, bosses, VIGIL events and a showcase kit. Operator only. */
public final class HellCommands {
    private HellCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("deathmask").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        var tp = Commands.literal("tp");
        var progressSet = Commands.literal("set");
        var bossSummon = Commands.literal("summon");
        for (HellLayer layer : HellLayer.values()) {
            tp.then(Commands.literal(layer.id)
                    .executes(c -> tp(c, List.of(c.getSource().getPlayerOrException()), layer))
                    .then(Commands.argument("targets", EntityArgument.players()).executes(c -> tp(c, EntityArgument.getPlayers(c, "targets"), layer))));
            bossSummon.then(Commands.literal(layer.id).executes(c -> summon(c, layer)));
        }
        progressSet.then(Commands.argument("depth", IntegerArgumentType.integer(1, 5))
                .executes(c -> setDepth(c, List.of(c.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(c, "depth")))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> setDepth(c, EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "depth")))));

        root.then(tp);
        root.then(Commands.literal("progress")
                .then(Commands.literal("get").executes(c -> get(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player()).executes(c -> get(c, EntityArgument.getPlayer(c, "target")))))
                .then(progressSet)
                .then(Commands.literal("reset").executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> reset(c, EntityArgument.getPlayers(c, "targets"))))));
        root.then(Commands.literal("boss").then(bossSummon)
                .then(Commands.literal("kill").executes(HellCommands::killBosses)));

        var vigil = Commands.literal("vigil");
        for (VigilDirector.Event e : VigilDirector.Event.values()) {
            vigil.then(Commands.literal(e.name().toLowerCase())
                    .executes(c -> vigil(c, List.of(c.getSource().getPlayerOrException()), e))
                    .then(Commands.argument("targets", EntityArgument.players()).executes(c -> vigil(c, EntityArgument.getPlayers(c, "targets"), e))));
        }
        vigil.then(Commands.literal("show").then(Commands.argument("form", IntegerArgumentType.integer(1, 5)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            var v = ryuna.qdeath.registry.HellEntities.VIGIL.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (v == null) return 0;
            // Stand it on open ground about 8 blocks ahead (closer if a wall is in the way).
            BlockPos spot = p.blockPosition();
            for (int dist = 8; dist >= 2; dist--) {
                var ahead = BlockPos.containing(p.position().add(p.getViewVector(1f).multiply(1, 0, 1).normalize().scale(dist)));
                BlockPos found = null;
                for (int dy = -6; dy <= 6 && found == null; dy++) {
                    BlockPos q = ahead.above(dy);
                    if (p.level().getBlockState(q).isAir() && p.level().getBlockState(q.above()).isAir() && p.level().getBlockState(q.above(2)).isAir()
                            && p.level().getBlockState(q.below()).isSolid()) found = q;
                }
                if (found != null) { spot = found; break; }
            }
            v.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            v.manifest(p, ryuna.qdeath.entity.vigil.VigilEntity.Mode.WATCH, IntegerArgumentType.getInteger(c, "form"));
            v.setShowcase();
            p.level().addFreshEntity(v);
            return 1;
        })));
        vigil.then(Commands.literal("pause").executes(c -> { VigilDirector.paused = true; c.getSource().sendSuccess(() -> Component.literal("VIGIL director paused"), true); return 1; }));
        vigil.then(Commands.literal("resume").executes(c -> { VigilDirector.paused = false; c.getSource().sendSuccess(() -> Component.literal("VIGIL director resumed"), true); return 1; }));
        vigil.then(Commands.literal("provoke").executes(c -> { VigilDirector.provoke(c.getSource().getPlayerOrException()); return 1; }));
        root.then(vigil);

        root.then(ryuna.qdeath.limbo.LimboCommands.limbo());
        root.then(ryuna.qdeath.limbo.LimboCommands.plague());
        root.then(Commands.literal("locate").executes(HellCommands::locate));
        root.then(Commands.literal("kit").executes(c -> kit(c.getSource().getPlayerOrException())));
        root.then(Commands.literal("journal").then(Commands.argument("page", IntegerArgumentType.integer(1, 12)).executes(c -> {
            ItemStack page = new ItemStack(HellItems.JOURNAL_PAGE);
            page.set(HellItems.PAGE, IntegerArgumentType.getInteger(c, "page") - 1);
            c.getSource().getPlayerOrException().addItem(page);
            return 1;
        })));
        d.register(root);
    }

    private static int tp(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, HellLayer layer) {
        for (ServerPlayer p : players) HellTravel.sendTo(p, layer, true);
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.tp", players.size(), layer.title()), true);
        return players.size();
    }

    private static int get(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        HellProgress h = HellProgress.of(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.progress", p.getDisplayName(), h.depth(), h.encounters(),
                Integer.bitCount(h.pages()), h.freed() ? "yes" : "no"), false);
        return h.depth();
    }

    private static int setDepth(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int depth) {
        for (ServerPlayer p : players) HellProgress.set(p, HellProgress.of(p).withDepth(depth));
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.progress_set", players.size(), depth), true);
        return players.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) { HellProgress.set(p, HellProgress.START); VigilDirector.forget(p.getUUID()); }
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.progress_reset", players.size()), true);
        return players.size();
    }

    private static int summon(CommandContext<CommandSourceStack> c, HellLayer layer) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        // The boss rises 8 blocks in front of the player (arena centre = altar + 12 north).
        BlockPos center = p.blockPosition().relative(p.getDirection(), 8);
        boolean ok = BossSummons.summon(layer, p.level(), center.south(BossSummons.ARENA_OFFSET), null);
        return ok ? 1 : 0;
    }

    private static int killBosses(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        var bosses = level.getEntitiesOfClass(HellBoss.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(256));
        bosses.forEach(b -> b.kill(level));
        return bosses.size();
    }

    private static int vigil(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, VigilDirector.Event e) {
        int done = 0;
        for (ServerPlayer p : players) {
            HellLayer layer = HellLayer.of(p.level());
            if (layer == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.not_in_hell", p.getDisplayName())); continue; }
            if (VigilDirector.trigger(p, e, layer)) done++;
        }
        int n = done;
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.vigil", e.name().toLowerCase(), n), true);
        return done;
    }

    private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HellLayer layer = HellLayer.of(p.level());
        if (layer == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.not_in_hell", p.getDisplayName())); return 0; }
        BlockPos found = p.level().findNearestMapStructure(layer.arenaTag, p.blockPosition(), 100, false);
        if (found == null) { c.getSource().sendFailure(Component.literal("No arena found")); return 0; }
        String cmd = "/tp @s " + found.getX() + " ~ " + found.getZ();
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.locate", layer.title(),
                Component.literal("[" + found.getX() + ", ~, " + found.getZ() + "]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.SuggestCommand(cmd)))), false);
        return 1;
    }

    private static int kit(ServerPlayer p) {
        List<Item> items = List.of(HellItems.PILGRIMS_COMPASS, HellItems.CINDER_HOOK, HellItems.MOURNING_BELL, HellItems.MIRROR_OF_LETHE, HellItems.FERRYMAN_LANTERN,
                HellItems.FERRYMAN_TOLL, HellItems.GLUTTONS_OFFERING, HellItems.STYGIAN_LURE, HellItems.HERETICS_CONFESSION, HellItems.LAST_CANDLE,
                HellItems.TOLL_KEEPERS_OAR, HellItems.MAW_CLEAVER, HellItems.STYGIAN_HARPOON, HellItems.HERETICS_BRAND, HellItems.REQUIEM);
        for (Item i : items) p.addItem(new ItemStack(i));
        p.addItem(new ItemStack(ModBlocks.VOTIVE_LANTERN, 8));
        return items.size();
    }
}
