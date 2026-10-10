package ryuna.qdeath.limbo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellTravel;
import ryuna.qdeath.limbo.plague.PlagueDirector;
import ryuna.qdeath.limbo.plague.PlagueState;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** /deathmask limbo ... and /deathmask plague ...: test and filming commands for the Limbo and its seepages. */
public final class LimboCommands {
    private LimboCommands() {}

    public static final List<String> BIOMES = List.of("ashen_wastes", "weeping_fields", "cinder_deltas", "remembrance_gardens");
    public static final List<String> STRUCTURES = List.of("ferry_landing", "pilgrim_camp", "mourner_shrine", "funeral_village", "procession_pit", "limbo_ossuary");

    public static LiteralArgumentBuilder<CommandSourceStack> limbo() {
        var root = Commands.literal("limbo");
        var procession = Commands.literal("procession").executes(c -> procession(c, null));
        for (Processions.Kind k : Processions.Kind.values()) procession.then(Commands.literal(k.name().toLowerCase()).executes(c -> procession(c, k)));
        root.then(procession);
        root.then(Commands.literal("intro").executes(c -> { LimboDirector.firstContact(c.getSource().getPlayerOrException()); return 1; }));
        root.then(Commands.literal("debug").executes(c -> {
            LimboDirector.debug = !LimboDirector.debug;
            for (ServerLevel l : c.getSource().getServer().getAllLevels())
                for (LimboDead d : l.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LimboDead.class), d -> true)) d.refreshDebugName();
            c.getSource().sendSuccess(() -> Component.literal("Limbo debug " + (LimboDirector.debug ? "on" : "off")), true);
            return 1;
        }));
        root.then(Commands.literal("processions")
                .then(Commands.literal("on").executes(c -> { LimboDirector.processions = true; return 1; }))
                .then(Commands.literal("off").executes(c -> { LimboDirector.processions = false; return 1; })));
        var biome = Commands.literal("biome");
        for (String b : BIOMES) biome.then(Commands.literal(b).executes(c -> biome(c, b)));
        root.then(biome);
        var structure = Commands.literal("structure");
        for (String s : STRUCTURES) structure.then(Commands.literal(s).executes(c -> structure(c, s)));
        root.then(structure);
        root.then(Commands.literal("state").executes(LimboCommands::state));
        root.then(Commands.literal("consecrate").executes(LimboCommands::consecrate));
        root.then(Commands.literal("graves").executes(c -> {
            int n = LimboDirector.stirGraves(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 16);
            c.getSource().sendSuccess(() -> Component.literal(n + " graves stir"), true);
            return n;
        }));
        root.then(Commands.literal("kit").executes(c -> kit(c.getSource().getPlayerOrException())));
        // Hordes for tests and filming: N dead (any species, common by default) in a ring around you, all hunting you.
        var horde = Commands.literal("horde");
        var count = Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 300)).executes(c -> horde(c, "hollow"));
        for (String id : DEAD_IDS) count.then(Commands.literal(id).executes(c -> horde(c, id)));
        root.then(horde.then(count));
        root.then(Commands.literal("pack").executes(c -> { LimboDirector.hurry(c.getSource().getPlayerOrException(), false); return 1; }));
        root.then(Commands.literal("procession_now").executes(c -> { LimboDirector.hurry(c.getSource().getPlayerOrException(), true); return 1; }));
        root.then(Commands.literal("stats").executes(LimboCommands::stats));
        // The expansion: provenance of old builds, and demonstrations of each new mechanic.
        root.then(LimboDemo.provenance());
        root.then(LimboDemo.demo());
        return root;
    }

    public static final List<String> DEAD_IDS = List.of("hollow", "stalker", "crier", "foreman", "lanky", "bloater", "breaker", "runt", "runaway", "crawler",
            "sundered", "walking_ossuary", "mourning_hound", "wake_bearer", "mourning_parasite", "breach_ticker", "strangler", "bile_spitter", "rib_thrower");

    private static int horde(CommandContext<CommandSourceStack> c, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count");
        var type = level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).getValue(Quantumdeath.id(id));
        int placed = 0;
        for (int i = 0; i < n * 3 && placed < n; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2, d = 20 + level.getRandom().nextDouble() * 20;
            BlockPos g = Processions.ground(level, (int) (p.getX() + Math.cos(a) * d), (int) (p.getZ() + Math.sin(a) * d), p.getBlockY(), 12);
            if (g == null || !(type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND) instanceof LimboDead dead)) continue;
            dead.snapTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
            dead.finalizeSpawn(level, level.getCurrentDifficultyAt(g), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
            level.addFreshEntity(dead);
            if (ryuna.qdeath.limbo.LimboSenses.isLife(p)) dead.engage(p);
            placed++;
        }
        int done = placed;
        c.getSource().sendSuccess(() -> Component.literal(done + " " + id + " hunting you"), true);
        return placed;
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Map<String, Integer> byType = new java.util.TreeMap<>();
        List<LimboDead> dead = LimboDirector.deadNear(level, c.getSource().getPosition(), 128);
        for (LimboDead d : dead) byType.merge(net.minecraft.world.entity.EntityType.getKey(d.getType()).getPath(), 1, Integer::sum);
        long hunting = dead.stream().filter(d -> d.mind() == Mind.PURSUIT).count();
        c.getSource().sendSuccess(() -> Component.literal(dead.size() + " dead within 128 (" + hunting + " in pursuit): " + byType), false);
        c.getSource().sendSuccess(() -> Component.literal("director: " + LimboDirector.packsSpawned + " packs, " + LimboDirector.processionsSpawned
                + " processions, " + LimboDirector.bodiesSpawned + " bodies; routes: " + HordePaths.searches + " searched, " + HordePaths.shared + " shared"), false);
        c.getSource().sendSuccess(() -> LimboDemo.stats(level, c.getSource().getPosition()), false);
        return dead.size();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> plague() {
        var root = Commands.literal("plague");
        var trigger = Commands.literal("trigger");
        for (String size : new String[]{"minor", "major"}) {
            boolean major = size.equals("major");
            trigger.then(Commands.literal(size).executes(c -> trigger(c, major, false))
                    .then(Commands.literal("now").executes(c -> trigger(c, major, true))));
        }
        root.then(trigger);
        root.then(Commands.literal("list").executes(LimboCommands::list));
        root.then(Commands.literal("clear").executes(c -> {
            int n = PlagueDirector.clear(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.literal(n + " foci cleared"), true);
            return n;
        }));
        root.then(Commands.literal("seal")
                .then(Commands.literal("broken").executes(c -> { PlagueDirector.breakFirstSeal(c.getSource().getServer()); return 1; }))
                .then(Commands.literal("intact").executes(c -> {
                    var server = c.getSource().getServer();
                    server.overworld().setAttached(PlagueState.TYPE, PlagueDirector.state(server).withSealBroken(false));
                    return 1;
                })));
        root.then(Commands.literal("portal").executes(c -> { PlagueDirector.portalCrossed(c.getSource().getServer()); return 1; }));
        return root;
    }

    private static int procession(CommandContext<CommandSourceStack> c, Processions.Kind kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos at = Processions.findSpot(level, p, 18, 28, true);
        if (at == null) at = Processions.ground(level, p.getBlockX() + 20, p.getBlockZ(), p.getBlockY(), 12);
        if (at == null) { c.getSource().sendFailure(Component.literal("No ground for a procession here")); return 0; }
        List<LimboDead> list = Processions.spawn(level, at, kind != null ? kind : Processions.kindAt(level, at));
        c.getSource().sendSuccess(() -> Component.literal("Procession of " + list.size() + " at " + list.getFirst().blockPosition().toShortString()), true);
        return list.size();
    }

    private static int biome(CommandContext<CommandSourceStack> c, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level().getServer().getLevel(HellLayer.VESTIBULE.level);
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Quantumdeath.id(id));
        BlockPos from = p.level() == level ? p.blockPosition() : BlockPos.containing(p.getX() / 8, 64, p.getZ() / 8);
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), from.atY(56), 3200, 32, 32);
        if (found == null) { c.getSource().sendFailure(Component.literal("Biome not found: " + id)); return 0; }
        go(p, level, found.getFirst());
        return 1;
    }

    private static int structure(CommandContext<CommandSourceStack> c, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.level().getServer().getLevel(HellLayer.VESTIBULE.level);
        var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Quantumdeath.id(id)));
        if (holder.isEmpty()) { c.getSource().sendFailure(Component.literal("Unknown structure " + id)); return 0; }
        BlockPos from = p.level() == level ? p.blockPosition() : BlockPos.containing(p.getX() / 8, 64, p.getZ() / 8);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
        if (found == null) { c.getSource().sendFailure(Component.literal("Structure not found: " + id)); return 0; }
        go(p, level, found.getFirst());
        return 1;
    }

    private static void go(ServerPlayer p, ServerLevel level, BlockPos near) {
        BlockPos safe = HellTravel.findSafe(level, HellLayer.VESTIBULE, near);
        p.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(safe), Vec3.ZERO, p.getYRot(), 0f, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        p.sendSystemMessage(Component.literal("-> " + safe.toShortString()).withStyle(ChatFormatting.GRAY));
    }

    private static int state(CommandContext<CommandSourceStack> c) {
        Map<Mind, Integer> counts = new EnumMap<>(Mind.class);
        List<LimboDead> dead = LimboDirector.deadNear(c.getSource().getLevel(), c.getSource().getPosition(), 64);
        for (LimboDead d : dead) counts.merge(d.mind(), 1, Integer::sum);
        c.getSource().sendSuccess(() -> Component.literal(dead.size() + " dead within 64: " + counts), false);
        return dead.size();
    }

    private static int consecrate(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos at = HellTravel.findBlock(level, BlockPos.containing(c.getSource().getPosition()), Set.of(ModBlocks.VOTIVE_LANTERN), 10);
        if (at == null) { c.getSource().sendFailure(Component.literal("No votive lantern within 10 blocks")); return 0; }
        BlockState s = level.getBlockState(at);
        VotiveLantern.consecrate(level, at, s, ryuna.qdeath.limbo.LimboTuning.MAX_FUEL);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> c, boolean major, boolean now) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var f = PlagueDirector.forceOpen(p, major, now);
        if (f == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.plague.no_site")); return 0; }
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.plague.opened", major ? "major" : "minor", f.anchor().toShortString()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        PlagueState s = PlagueDirector.state(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("portal=" + s.portalOpened() + " seal_broken=" + s.sealBroken()), false);
        String[] stages = {"warning", "active", "exhausted", "contained"};
        for (PlagueState.Focus f : s.foci())
            c.getSource().sendSuccess(() -> Component.literal("#" + f.id() + " " + (f.major() ? "major" : "minor") + " " + stages[f.stage()] + " at " + f.anchor().toShortString()
                    + " waves_left=" + f.wavesLeft() + " reserve=" + f.reserve()), false);
        return s.foci().size();
    }

    private static int kit(ServerPlayer p) {
        List<Item> items = List.of(HellItems.FUNERAL_WAX, HellItems.CONSECRATED_WAX, HellItems.BREATH_LURE, HellItems.MOURNING_BELL, HellItems.PILGRIMS_COMPASS,
                HellItems.FERRYMAN_TOLL, HellItems.ASHEN_OBOL, HellItems.SOUL_ASH);
        for (Item i : items) p.addItem(new ItemStack(i, i == HellItems.MOURNING_BELL || i == HellItems.PILGRIMS_COMPASS || i == HellItems.FERRYMAN_TOLL ? 1 : 8));
        for (var b : List.of(ModBlocks.VOTIVE_LANTERN, ModBlocks.FUNERAL_BARRICADE, ModBlocks.LIMBO_GRAVE, ModBlocks.DEEP_ASH, ModBlocks.UNSTABLE_BONE))
            p.addItem(new ItemStack(b, 8));
        return items.size();
    }
}
