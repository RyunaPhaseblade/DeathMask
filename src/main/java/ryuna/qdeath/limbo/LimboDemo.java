package ryuna.qdeath.limbo;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.registry.HellEntities;

/**
 * Showcase and test tools of the expansion (operator): {@code /deathmask limbo provenance mark <from> <to>} and
 * {@code /deathmask limbo demo <possession|breach|tongue|bile|ribs|mound [height]|dig|fire>}. The demos build only what they show
 * (a marked wall, a tower) in front of the player, from the blocks of the world; nothing else of the terrain is touched.
 */
public final class LimboDemo {
    private LimboDemo() {}

    public static LiteralArgumentBuilder<CommandSourceStack> provenance() {
        return Commands.literal("provenance").then(Commands.literal("mark")
                .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(LimboDemo::mark))));
    }

    /** Marks an old build as placed by players. Only loaded chunks are touched; nothing is loaded for it. */
    private static int mark(CommandContext<CommandSourceStack> c) {
        BlockPos a = BlockPosArgument.getBlockPos(c, "from"), b = BlockPosArgument.getBlockPos(c, "to");
        long volume = (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (volume > 65536) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.provenance.too_big", volume, 65536)); return 0; }
        int[] r = Provenance.markArea(c.getSource().getLevel(), a, b);
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.provenance.marked", r[0], r[1]), true);
        return r[0];
    }

    public static LiteralArgumentBuilder<CommandSourceStack> demo() {
        var root = Commands.literal("demo");
        root.then(Commands.literal("possession").executes(LimboDemo::possession));
        root.then(Commands.literal("breach").executes(LimboDemo::breach));
        root.then(Commands.literal("tongue").executes(c -> single(c, HellEntities.STRANGLER, 11)));
        root.then(Commands.literal("bile").executes(c -> single(c, HellEntities.BILE_SPITTER, 14)));
        root.then(Commands.literal("ribs").executes(c -> single(c, HellEntities.RIB_THROWER, 16)));
        root.then(Commands.literal("ticker").executes(c -> single(c, HellEntities.BREACH_TICKER, 8)));
        root.then(Commands.literal("mound").executes(c -> mound(c, 6))
                .then(Commands.argument("height", IntegerArgumentType.integer(2, LimboTuning.PILE_MAX_HEIGHT)).executes(c -> mound(c, IntegerArgumentType.getInteger(c, "height")))));
        root.then(Commands.literal("dig").executes(LimboDemo::dig));
        root.then(Commands.literal("fire").executes(LimboDemo::fire));
        return root;
    }

    private static Direction facing(ServerPlayer p) { return p.getDirection(); }

    private static @Nullable <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw) {
        T m = type.create(level, EntitySpawnReason.COMMAND);
        if (m == null) return null;
        m.snapTo(x, y, z, yaw, 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), EntitySpawnReason.COMMAND, null);
        level.addFreshEntity(m);
        return m;
    }

    private static @Nullable BlockPos ahead(ServerLevel level, ServerPlayer p, int distance) {
        Direction f = facing(p);
        return Processions.ground(level, p.getBlockX() + f.getStepX() * distance, p.getBlockZ() + f.getStepZ() * distance, p.getBlockY(), 6);
    }

    /** One creature of the expansion in front of the player, hunting it. */
    private static int single(CommandContext<CommandSourceStack> c, EntityType<? extends LimboDead> type, int distance) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos at = ahead(level, p, distance);
        if (at == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.demo.no_ground")); return 0; }
        LimboDead d = spawn(level, type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180);
        if (d != null && LimboSenses.isLife(p)) d.engage(p);
        return 1;
    }

    /** A pig, a villager and a spider ahead, and a Mourning Parasite behind them. */
    private static int possession(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        Direction f = facing(p), side = f.getClockWise();
        int i = 0;
        for (EntityType<? extends Mob> t : java.util.List.<EntityType<? extends Mob>>of(net.minecraft.world.entity.EntityTypes.PIG, net.minecraft.world.entity.EntityTypes.VILLAGER, net.minecraft.world.entity.EntityTypes.SPIDER)) {
            BlockPos g = Processions.ground(level, p.getBlockX() + f.getStepX() * 7 + side.getStepX() * (i - 1) * 3, p.getBlockZ() + f.getStepZ() * 7 + side.getStepZ() * (i - 1) * 3, p.getBlockY(), 6);
            if (g != null) spawn(level, t, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, p.getYRot());
            i++;
        }
        BlockPos g = ahead(level, p, 13);
        if (g == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.demo.no_ground")); return 0; }
        spawn(level, HellEntities.MOURNING_PARASITE, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, p.getYRot() + 180);
        return 1;
    }

    /**
     * A shelter of cobblestone marked as placed by players (5 x 4 x 5, roofed) around the player, and a Breach Ticker ten
     * blocks ahead that hunts it: it cannot reach the player, so (with mobGriefing on) it opens a corridor through the wall.
     */
    private static int breach(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos base = p.blockPosition();
        BlockState stone = Blocks.COBBLESTONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 0; y < 4; y++) {
            BlockPos q = base.offset(dx, y, dz);
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 3) {
                level.setBlockAndUpdate(q, stone);
                Provenance.mark(level, q);
            } else level.setBlockAndUpdate(q, air);
        }
        BlockPos g = ahead(level, p, 10);
        if (g != null) {
            LimboDead t = spawn(level, HellEntities.BREACH_TICKER, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, p.getYRot() + 180);
            if (t != null && LimboSenses.isLife(p)) t.engage(p);
        }
        return 1;
    }

    /**
     * A tower of the given height for the player to stand on (3 x 3 of cobblestone, marked), and enough common dead around
     * it, hunting, to pile up against it.
     */
    private static int mound(CommandContext<CommandSourceStack> c, int height) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos foot = p.blockPosition();
        BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 0; y < height; y++) {
            BlockPos q = foot.offset(dx, y, dz);
            level.setBlockAndUpdate(q, stone);
            Provenance.mark(level, q);
        }
        p.teleportTo(foot.getX() + 0.5, foot.getY() + height, foot.getZ() + 0.5);
        int n = Horde.crowdFor(height);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n, r = 7 + (i % 3);
            BlockPos g = Processions.ground(level, (int) Math.floor(foot.getX() + 0.5 + Math.cos(a) * r), (int) Math.floor(foot.getZ() + 0.5 + Math.sin(a) * r), foot.getY(), 4);
            if (g == null) continue;
            LimboDead d = spawn(level, HellEntities.HOLLOW, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0);
            if (d != null && LimboSenses.isLife(p)) d.engage(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.quantumdeath.demo.mound", height, n), true);
        return n;
    }

    /**
     * A common dead ten blocks ahead, hunting the player, that at once starts digging a fellow out of the ground. Where
     * the ground under it is not soft, a patch of cinder soil is laid for it.
     */
    private static int dig(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos g = ahead(level, p, 10);
        if (g == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.demo.no_ground")); return 0; }
        if (!level.getBlockState(g.below()).is(Horde.DIG_GROUND))
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(g.offset(dx, -1, dz), ryuna.qdeath.registry.ModBlocks.CINDER_SOIL.defaultBlockState());
        LimboDead d = spawn(level, HellEntities.HOLLOW, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, p.getYRot() + 180);
        if (d == null) return 0;
        if (LimboSenses.isLife(p)) d.engage(p);
        d.startDig();
        return 1;
    }

    /** Eight common dead packed together eight blocks ahead, hunting the player; the first of them is burning. */
    private static int fire(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos g = ahead(level, p, 8);
        if (g == null) { c.getSource().sendFailure(Component.translatable("command.quantumdeath.demo.no_ground")); return 0; }
        Direction side = facing(p).getClockWise();
        for (int i = 0; i < 8; i++) {
            double along = (i % 4 - 1.5) * 0.7, back = (i / 4) * 0.7;
            LimboDead d = spawn(level, HellEntities.HOLLOW, g.getX() + 0.5 + side.getStepX() * along + facing(p).getStepX() * back, g.getY(),
                    g.getZ() + 0.5 + side.getStepZ() * along + facing(p).getStepZ() * back, p.getYRot() + 180);
            if (d == null) continue;
            if (LimboSenses.isLife(p)) d.engage(p);
            if (i == 0) d.igniteForSeconds(8);
        }
        return 8;
    }

    /** /deathmask limbo stats, the expansion's counters. */
    public static Component stats(ServerLevel level, Vec3 at) {
        return Component.literal("expansion " + (LimboTuning.expansion ? "on" : "off") + ": possessed hosts " + Possession.hostsNear(level, at, 128)
                + " (" + Possession.possessions + " taken, " + Possession.releases + " freed); tongues " + Grips.grabs + " grabs, " + Grips.blocked
                + " blocked by shields; breaches " + Breach.breaches + " (" + Breach.opened + " blocks); horde " + Horde.lifts + " climbs, "
                + Horde.piles + " piles, " + Horde.overTop + " over the top, " + Horde.ignitions + " set alight, " + Horde.digs + " digs, " + Horde.risen + " dug up; stimuli "
                + Stimuli.published + " published, " + Stimuli.heard + " heard; routes " + HordePaths.invalidated + " invalidated, " + Routes.probes + " probes");
    }
}
