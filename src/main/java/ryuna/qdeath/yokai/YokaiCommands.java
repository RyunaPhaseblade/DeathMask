package ryuna.qdeath.yokai;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.yokai.BellTsukumogami;
import ryuna.qdeath.entity.yokai.ChestTsukumogami;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /yokai: operator demonstration and test commands. None of them changes a player's skill tree, points or technique
 * progress: perception and the yokai nodes are lent through a demonstration mode that is switched off afterwards.
 */
public final class YokaiCommands {
    private YokaiCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("yokai").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        root.then(Commands.literal("info").executes(c -> info(c, c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player()).executes(c -> info(c, EntityArgument.getPlayer(c, "target")))));
        root.then(Commands.literal("awaken").executes(c -> awaken(c, null))
                .then(Commands.argument("responsible", EntityArgument.player()).executes(c -> awaken(c, EntityArgument.getPlayer(c, "responsible")))));
        root.then(Commands.literal("sleep").executes(c -> {
            Awakening.sleep(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.yokai.sleep"), true);
            return 1;
        }));
        root.then(Commands.literal("grace")
                .then(Commands.literal("skip").executes(c -> grace(c, 0)))
                .then(Commands.literal("reset").executes(c -> grace(c, YokaiBalance.World.GRACE_TICKS))));
        var perception = Commands.literal("perception");
        for (int lvl = 0; lvl <= 2; lvl++) {
            int l = lvl;
            perception.then(Commands.literal(String.valueOf(lvl)).executes(c -> perception(c, List.of(c.getSource().getPlayerOrException()), l))
                    .then(Commands.argument("targets", EntityArgument.players()).executes(c -> perception(c, EntityArgument.getPlayers(c, "targets"), l))));
        }
        perception.then(Commands.literal("reset").executes(c -> perception(c, List.of(c.getSource().getPlayerOrException()), -1))
                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> perception(c, EntityArgument.getPlayers(c, "targets"), -1))));
        root.then(perception);
        root.then(Commands.literal("demo")
                .then(Commands.literal("on").executes(c -> demo(c, List.of(c.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> demo(c, EntityArgument.getPlayers(c, "targets"), true))))
                .then(Commands.literal("off").executes(c -> demo(c, List.of(c.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> demo(c, EntityArgument.getPlayers(c, "targets"), false)))));
        var summon = Commands.literal("summon");
        for (Species s : Species.values()) {
            summon.then(Commands.literal(s.id).executes(c -> summon(c, s, false))
                    .then(Commands.literal("bonded").executes(c -> summon(c, s, true))));
        }
        root.then(summon);
        root.then(Commands.literal("showcase").executes(YokaiCommands::showcase));
        root.then(Commands.literal("kit").executes(YokaiCommands::kit));
        root.then(Commands.literal("pact").executes(YokaiCommands::pact));
        root.then(Commands.literal("object").executes(YokaiCommands::object));
        root.then(Commands.literal("bestiary")
                .then(Commands.literal("reveal").executes(c -> bestiary(c, List.of(c.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> bestiary(c, EntityArgument.getPlayers(c, "targets"), true))))
                .then(Commands.literal("reset").executes(c -> bestiary(c, List.of(c.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> bestiary(c, EntityArgument.getPlayers(c, "targets"), false)))));
        root.then(Commands.literal("director")
                .then(Commands.literal("pause").executes(c -> { EncounterDirector.pause(true); return ok(c, "director.paused"); }))
                .then(Commands.literal("resume").executes(c -> { EncounterDirector.pause(false); return ok(c, "director.resumed"); }))
                .then(Commands.literal("now").executes(YokaiCommands::directorNow))
                .then(Commands.literal("stats").executes(YokaiCommands::stats)));
        root.then(Commands.literal("companions").executes(c -> companions(c, c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player()).executes(c -> companions(c, EntityArgument.getPlayer(c, "target")))));
        root.then(Commands.literal("clear").executes(YokaiCommands::clear));
        root.then(Commands.literal("calm").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(c -> calm(c, IntegerArgumentType.getInteger(c, "seconds")))));
        d.register(root);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.yokai." + key, args), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        YokaiWorld w = YokaiWorld.of(c.getSource().getServer());
        YokaiPlayer d = YokaiPlayer.of(p);
        long identified = d.bestiary().values().stream().filter(v -> (v & YokaiPlayer.IDENTIFIED) != 0).count();
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.yokai.info", p.getDisplayName(), w.awakened(), w.graceLeft() / 20,
                w.pendingAdvancement(), Perception.level(p), d.perceptionOverride(), d.demo(), identified, Bonds.of(c.getSource().getServer(), p.getUUID()).size()), false);
        return 1;
    }

    private static int awaken(CommandContext<CommandSourceStack> c, @Nullable ServerPlayer responsible) {
        boolean done = Awakening.awaken(c.getSource().getServer(), responsible);
        return ok(c, done ? "awakened" : "already_awake");
    }

    private static int grace(CommandContext<CommandSourceStack> c, int ticks) {
        var server = c.getSource().getServer();
        YokaiWorld.set(server, YokaiWorld.of(server).withGrace(ticks));
        return ok(c, "grace", ticks / 20);
    }

    private static int perception(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int level) {
        for (ServerPlayer p : players) {
            YokaiPlayer.set(p, YokaiPlayer.of(p).withOverride(level));
            YokaiNet.syncSelf(p);
        }
        return ok(c, level < 0 ? "perception_reset" : "perception", players.size(), level);
    }

    private static int demo(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean on) {
        for (ServerPlayer p : players) {
            YokaiPlayer.set(p, YokaiPlayer.of(p).withDemo(on));
            YokaiNet.syncSelf(p);
            Companions.push(p);
        }
        return ok(c, on ? "demo_on" : "demo_off", players.size());
    }

    private static Vec3 ahead(ServerPlayer p, double dist) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(dist));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(p.getLookAngle().multiply(1, 0, 1).normalize());
        BlockPos ground = BlockPos.containing(at);
        for (int i = 0; i < 6 && p.level().getBlockState(ground.below()).isAir(); i++) ground = ground.below();
        for (int i = 0; i < 6 && !p.level().getBlockState(ground).isAir(); i++) ground = ground.above();
        return Vec3.atBottomCenterOf(ground);
    }

    private static @Nullable Mob create(ServerLevel level, Species s, Vec3 at) {
        Mob m = EncounterDirector.spawnAt(level, s, at, EntitySpawnReason.COMMAND);
        if (m instanceof ChestTsukumogami chest) chest.fillDemo();
        if (m != null) m.setPersistenceRequired();
        return m;
    }

    private static int summon(CommandContext<CommandSourceStack> c, Species s, boolean bonded) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        Mob m = create(level, s, ahead(p, 5).add(0, s == Species.SWARM_EYE || s == Species.ITTAN_MOMEN ? 1 : 0, 0));
        if (m == null) {
            c.getSource().sendFailure(Component.translatable("command.quantumdeath.yokai.no_room"));
            return 0;
        }
        if (bonded && !forcePact(p, m)) {
            c.getSource().sendFailure(Component.translatable("command.quantumdeath.yokai.not_pactable"));
            return 0;
        }
        return ok(c, "summoned", Component.translatable(s.key()));
    }

    /** Demonstration pact: binds without the ritual and gives a fresh amulet. The player's skills stay as they are. */
    public static boolean forcePact(ServerPlayer p, Mob m) {
        if (!(m instanceof YokaiCreature y) || !y.species().pactable || y.yokai().bonded()) return false;
        boolean free = false;
        for (ItemStack s : p.getInventory()) if (s.is(YokaiContent.AMULET) && Bonds.data(s).free()) { free = true; break; }
        if (!free) p.getInventory().add(new ItemStack(YokaiContent.AMULET));
        YokaiPlayer before = YokaiPlayer.of(p);
        // Bonds.pact checks sight and the Pacts node: lend them for this one call, then restore exactly what was there.
        YokaiPlayer.set(p, before.withDemo(true));
        boolean ok = Bonds.pact(p, m);
        YokaiPlayer.set(p, YokaiPlayer.of(p).withDemo(before.demo()).withOverride(before.perceptionOverride()));
        YokaiNet.syncSelf(p);
        return ok;
    }

    private static int showcase(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        Species[] all = Species.values();
        int made = 0;
        for (int i = 0; i < all.length; i++) {
            double a = Math.PI * 2 * i / all.length;
            Vec3 at = p.position().add(Math.cos(a) * 9, 0, Math.sin(a) * 9);
            BlockPos ground = BlockPos.containing(at);
            for (int k = 0; k < 8 && level.getBlockState(ground.below()).isAir(); k++) ground = ground.below();
            for (int k = 0; k < 8 && !level.getBlockState(ground).isAir(); k++) ground = ground.above();
            Vec3 spot = Vec3.atBottomCenterOf(ground).add(0, all[i] == Species.SWARM_EYE || all[i] == Species.ITTAN_MOMEN || all[i] == Species.FALSE_CREEPER ? 1.2 : 0, 0);
            Mob m = create(level, all[i], spot);
            if (m instanceof YokaiCreature y) {
                y.yokai().calm(20 * 120);
                m.lookAt(p, 180, 0);
                made++;
            }
        }
        return ok(c, "showcase", made);
    }

    private static int kit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack[] kit = {new ItemStack(YokaiContent.AMULET), new ItemStack(YokaiContent.AMULET), new ItemStack(YokaiContent.AMULET), new ItemStack(YokaiContent.AMULET),
                new ItemStack(YokaiContent.RITUAL_THREAD, 4), new ItemStack(Items.SNOWBALL, 16), new ItemStack(Items.SEA_PICKLE, 12), new ItemStack(Items.OAK_PLANKS, 8),
                new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.APPLE, 2), new ItemStack(Items.FEATHER, 2), new ItemStack(Items.SPIDER_EYE, 2),
                new ItemStack(Items.COPPER_INGOT, 2), new ItemStack(Items.OAK_SAPLING, 2), new ItemStack(Items.TORCH, 4), new ItemStack(Items.LANTERN)};
        for (ItemStack s : kit) if (!p.addItem(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
        return ok(c, "kit");
    }

    private static @Nullable Entity looked(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1), e -> e instanceof YokaiCreature, range * range);
        return hit == null ? null : hit.getEntity();
    }

    private static int pact(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Entity e = looked(p, 16);
        if (!(e instanceof Mob m) || !forcePact(p, m)) {
            c.getSource().sendFailure(Component.translatable("command.quantumdeath.yokai.not_pactable"));
            return 0;
        }
        return ok(c, "pact", e.getDisplayName());
    }

    /** Awakens the chest or bell under the crosshair, if (and only if) it is an eligible natural object. */
    private static int object(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockHitResult hit = level.clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(8)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        BlockPos pos = hit.getBlockPos();
        Entity made = ChestTsukumogami.awaken(level, pos, p);
        if (made == null) made = BellTsukumogami.awaken(level, pos);
        if (made == null) {
            c.getSource().sendFailure(Component.translatable("command.quantumdeath.yokai.not_natural"));
            return 0;
        }
        return ok(c, "object", made.getDisplayName());
    }

    private static int bestiary(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean reveal) {
        for (ServerPlayer p : players) {
            if (reveal) Bestiary.reveal(p);
            else Bestiary.reset(p);
        }
        return ok(c, reveal ? "bestiary_reveal" : "bestiary_reset", players.size());
    }

    private static int directorNow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        if (level != c.getSource().getServer().overworld()) {
            c.getSource().sendFailure(Component.translatable("command.quantumdeath.yokai.overworld_only"));
            return 0;
        }
        for (List<ServerPlayer> g : EncounterDirector.groups(level)) {
            if (!g.contains(p)) continue;
            EncounterDirector.Result r = EncounterDirector.attempt(level, g, true, null);
            return ok(c, "director.result", r.name().toLowerCase());
        }
        return 0;
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        var groups = EncounterDirector.groups(level);
        int wild = groups.stream().mapToInt(g -> EncounterDirector.wildNear(level, g)).sum();
        return ok(c, "director.stats", EncounterDirector.attempts, EncounterDirector.spawned, EncounterDirector.signs, EncounterDirector.budgetFull,
                groups.size(), wild, EncounterDirector.paused());
    }

    private static int companions(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        List<Map.Entry<UUID, Bonds.Bond>> list = Bonds.of(c.getSource().getServer(), p.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.yokai.companions", p.getDisplayName(), list.size()), false);
        for (var e : list) {
            Bonds.Bond b = e.getValue();
            c.getSource().sendSuccess(() -> Component.literal(" - ").append(Component.translatable("yokai.quantumdeath." + b.species()))
                    .append(Component.literal(" [" + b.state() + "] " + Math.round(b.health()) + "/" + Math.round(b.max()))).withStyle(ChatFormatting.GRAY), false);
        }
        return list.size();
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Mob> wild = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof YokaiCreature y && !y.yokai().bonded());
        // Chest and bell spirits put their object back (or drop it) when removed this way.
        wild.forEach(Entity::discard);
        return ok(c, "cleared", wild.size());
    }

    private static int calm(CommandContext<CommandSourceStack> c, int seconds) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Mob> near = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof YokaiCreature);
        near.forEach(m -> {
            ((YokaiCreature) m).yokai().calm(seconds * 20);
            m.setTarget(null);
        });
        return ok(c, "calmed", near.size(), seconds);
    }
}
