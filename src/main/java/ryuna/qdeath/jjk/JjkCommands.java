package ryuna.qdeath.jjk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.util.Collection;
import java.util.List;

/** {@code /jjk}: operator tools for testing the cursed-technique classes without grinding. */
public final class JjkCommands {
    private JjkCommands() {}

    private static final SuggestionProvider<CommandSourceStack> CLASSES = (c, b) -> SharedSuggestionProvider.suggest(JjkClasses.all().stream().map(t -> t.id), b);
    private static final SuggestionProvider<CommandSourceStack> SKILLS = (c, b) -> {
        b.suggest("all");
        return SharedSuggestionProvider.suggest(JjkClasses.all().stream().flatMap(t -> t.skills().stream()).map(SkillDef::id), b);
    };

    private interface Op { int run(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException; }

    private static int each(CommandContext<CommandSourceStack> c, Op op) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        int n = 0;
        for (ServerPlayer p : players) n += op.run(c, p);
        return n;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(key, args), true);
    }

    private static boolean needsClass(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        if (Techniques.cls(p) != null) return true;
        c.getSource().sendFailure(Component.translatable("jjk.quantumdeath.cmd.no_class", p.getName()));
        return false;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var targets = (java.util.function.Supplier<com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?>>) () -> Commands.argument("targets", EntityArgument.players());
        d.register(Commands.literal("jjk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("class")
                        .then(Commands.literal("set").then(targets.get().then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                .executes(c -> each(c, (ctx, p) -> {
                                    String id = StringArgumentType.getString(ctx, "class");
                                    if (!Techniques.setClass(p, id)) { ctx.getSource().sendFailure(Component.translatable("jjk.quantumdeath.cmd.unknown_class", id)); return 0; }
                                    ok(ctx, "jjk.quantumdeath.cmd.class_set", p.getName(), Component.translatable(JjkClasses.get(id).nameKey()));
                                    return 1;
                                })))))
                        .then(Commands.literal("clear").then(targets.get().executes(c -> each(c, (ctx, p) -> {
                            Techniques.clearClass(p);
                            ok(ctx, "jjk.quantumdeath.cmd.class_cleared", p.getName());
                            return 1;
                        })))))
                .then(Commands.literal("level").then(Commands.literal("set").then(targets.get().then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                        .executes(c -> each(c, (ctx, p) -> {
                            if (!needsClass(ctx, p)) return 0;
                            Techniques.setLevel(p, IntegerArgumentType.getInteger(ctx, "level"));
                            ok(ctx, "jjk.quantumdeath.cmd.level_set", p.getName(), Techniques.data(p).progress().level);
                            return 1;
                        }))))))
                .then(Commands.literal("xp").then(Commands.literal("add").then(targets.get().then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(c -> each(c, (ctx, p) -> {
                            if (!needsClass(ctx, p)) return 0;
                            Techniques.addXp(p, IntegerArgumentType.getInteger(ctx, "amount"), false);
                            ok(ctx, "jjk.quantumdeath.cmd.xp_added", IntegerArgumentType.getInteger(ctx, "amount"), p.getName());
                            return 1;
                        }))))))
                .then(Commands.literal("tp").then(Commands.literal("add").then(targets.get().then(Commands.argument("amount", IntegerArgumentType.integer(-1000, 1000))
                        .executes(c -> each(c, (ctx, p) -> {
                            if (!needsClass(ctx, p)) return 0;
                            var prog = Techniques.data(p).progress();
                            prog.tp = Math.max(0, prog.tp + IntegerArgumentType.getInteger(ctx, "amount"));
                            Techniques.syncProgress(p);
                            ok(ctx, "jjk.quantumdeath.cmd.tp_added", IntegerArgumentType.getInteger(ctx, "amount"), p.getName(), prog.tp);
                            return 1;
                        }))))))
                .then(Commands.literal("skill")
                        .then(Commands.literal("unlock").then(targets.get().then(Commands.argument("skill", IdentifierArgument.id()).suggests(SKILLS)
                                .executes(c -> each(c, (ctx, p) -> {
                                    if (!needsClass(ctx, p)) return 0;
                                    String id = IdentifierArgument.getId(ctx, "skill").toString();
                                    if (id.equals("minecraft:all")) {
                                        Techniques.unlockAll(p);
                                        ok(ctx, "jjk.quantumdeath.cmd.unlocked_all", p.getName());
                                        return 1;
                                    }
                                    var r = Techniques.unlock(p, id, true);
                                    if (r == Techniques.UnlockResult.OK || r == Techniques.UnlockResult.ALREADY) {
                                        ok(ctx, "jjk.quantumdeath.cmd.unlocked", Component.translatable(JjkClasses.skill(id).key()), p.getName());
                                        return 1;
                                    }
                                    ctx.getSource().sendFailure(Component.translatable("jjk.quantumdeath.cmd.unknown_skill", id));
                                    return 0;
                                })))))
                        .then(Commands.literal("reset").then(targets.get().executes(c -> each(c, (ctx, p) -> {
                            if (!needsClass(ctx, p)) return 0;
                            Techniques.resetSkills(p);
                            ok(ctx, "jjk.quantumdeath.cmd.skills_reset", p.getName());
                            return 1;
                        })))))
                .then(Commands.literal("resource").then(Commands.literal("fill").then(targets.get().executes(c -> each(c, (ctx, p) -> {
                    if (!needsClass(ctx, p)) return 0;
                    Techniques.fill(p);
                    ok(ctx, "jjk.quantumdeath.cmd.filled", p.getName());
                    return 1;
                })))))
                .then(Commands.literal("cooldown").then(Commands.literal("reset").then(targets.get().executes(c -> each(c, (ctx, p) -> {
                    Techniques.data(p).cooldowns.clear();
                    Techniques.syncState(p, true);
                    ok(ctx, "jjk.quantumdeath.cmd.cooldowns_reset", p.getName());
                    return 1;
                })))))
                // One step to try a whole class: select it, max level, every skill, full pools, no cooldowns.
                .then(Commands.literal("max").then(targets.get()
                        .executes(c -> each(c, (ctx, p) -> max(ctx, p, null)))
                        .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                .executes(c -> each(c, (ctx, p) -> max(ctx, p, StringArgumentType.getString(ctx, "class")))))))
                .then(Commands.literal("info").executes(c -> info(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player()).executes(c -> info(c, EntityArgument.getPlayer(c, "target")))))
                // Training dummies: slow husks with a lot of health that never despawn.
                .then(Commands.literal("dummy").executes(c -> dummies(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10)).executes(c -> dummies(c, IntegerArgumentType.getInteger(c, "count"))))));
    }

    private static int max(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String classId) {
        if (classId != null && !Techniques.setClass(p, classId)) {
            ctx.getSource().sendFailure(Component.translatable("jjk.quantumdeath.cmd.unknown_class", classId));
            return 0;
        }
        if (!needsClass(ctx, p)) return 0;
        Techniques.setLevel(p, JjkBalance.Progression.MAX_LEVEL);
        Techniques.unlockAll(p);
        Techniques.data(p).cooldowns.clear();
        Techniques.fill(p);
        ok(ctx, "jjk.quantumdeath.cmd.maxed", p.getName(), Component.translatable(Techniques.cls(p).nameKey()));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        TechniqueData d = Techniques.data(p);
        TechniqueClass cls = Techniques.cls(p);
        if (cls == null) { c.getSource().sendSuccess(() -> Component.translatable("jjk.quantumdeath.cmd.no_class", p.getName()), false); return 0; }
        var prog = d.progress();
        List<String> skills = cls.skills().stream().filter(s -> prog.unlocked.contains(s.id())).map(SkillDef::id).toList();
        c.getSource().sendSuccess(() -> Component.translatable("jjk.quantumdeath.cmd.info", p.getName(), Component.translatable(cls.nameKey()), prog.level,
                prog.xp, JjkBalance.xpToNext(prog.level), prog.tp, Math.round(d.energy), Math.round(d.resource)).withStyle(ChatFormatting.GRAY), false);
        c.getSource().sendSuccess(() -> Component.literal(String.join(", ", skills)).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int dummies(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var level = p.level();
        var look = p.getLookAngle();
        for (int i = 0; i < count; i++) {
            Husk h = net.minecraft.world.entity.EntityTypes.HUSK.create((net.minecraft.server.level.ServerLevel) level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            double side = (i - (count - 1) / 2.0) * 2.2;
            h.setPos(p.getX() + look.x * 6 - look.z * side, p.getY(), p.getZ() + look.z * 6 + look.x * side);
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
            h.setHealth(400);
            h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.12);
            h.setPersistenceRequired();
            h.setCustomName(Component.translatable("jjk.quantumdeath.dummy"));
            level.addFreshEntity(h);
        }
        ok(c, "jjk.quantumdeath.cmd.dummies", count);
        return count;
    }
}
