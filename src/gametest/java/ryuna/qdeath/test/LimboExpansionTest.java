package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.limbo.Provenance;
import ryuna.qdeath.registry.ModBlocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static ryuna.qdeath.test.TestUtil.settle;

/**
 * The expansion of the Limbo (docs/ampliacion-limbo-especificacion.md) in a real client: the acceptance matrix of the
 * document (possession, alliance, Ticker, separation of bursts, protections, tongue, throwers, senses, navigation,
 * mounds, plague, regressions, multiplayer on a dedicated server) and the performance comparison with the expansion off.
 * Results are "EXPAND OK/FAILED ..." lines; failures are collected and reported together at the end.
 *
 * <p>-Dquantumdeath.skipExpansion=true skips it; -Dquantumdeath.expandPhases=visual,possession,... runs some phases.
 */
public class LimboExpansionTest implements FabricClientGameTest {
    final List<String> failures = new ArrayList<>();
    ClientGameTestContext ctx;
    TestSingleplayerContext sp;
    BlockPos stage;
    Set<String> phases;

    void soft(boolean ok, String what) {
        if (ok) Quantumdeath.LOGGER.info("EXPAND OK: {}", what);
        else { Quantumdeath.LOGGER.error("EXPAND FAILED: {}", what); failures.add(what); }
    }

    static void log(String what, Object... args) { Quantumdeath.LOGGER.info("EXPAND " + what, args); }

    <T> T server(Function<MinecraftServer, T> f) { return sp.getServer().computeOnServer(f::apply); }
    void run(Consumer<MinecraftServer> f) { sp.getServer().runOnServer(f::accept); }
    static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }
    static ServerLevel limbo(MinecraftServer s) { return s.getLevel(HellLayer.VESTIBULE.level); }
    void cmd(String c) { TestUtil.cmd(sp, c); }
    void waitTicks(int n) { ctx.waitTicks(n); }

    boolean on(String phase) { return phases.isEmpty() || phases.contains(phase); }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("quantumdeath.skipExpansion")) return;
        ryuna.qdeath.limbo.LimboTuning.digging = false; // a random dig would stop a measured runner (the horde phase digs on purpose)
        ctx = context;
        String only = System.getProperty("quantumdeath.expandPhases", "");
        phases = only.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(only.split(",")));
        // The window runs in the background: losing focus must not open the pause menu (it stops the integrated server).
        ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
        sp = ctx.worldBuilder()
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setAllowCommands(true); })
                .create();
        try {
            settle(ctx, sp, 40);
            cmd("deathmask vigil pause");
            cmd("deathmask limbo processions off");
            cmd("difficulty normal");
            cmd("gamerule spawn_monsters false");
            cmd("gamerule spawn_mobs false");
            cmd("gamerule advance_time false");
            cmd("time set noon");
            ctx.getInput().pressKey(o -> o.keyToggleGui);
            ExpansionChecks checks = new ExpansionChecks(this);
            if (on("possession")) checks.possession();
            if (on("plague")) checks.plague();
            if (on("composition")) checks.composition();
            if (on("regression")) checks.regression();
            cmd("deathmask tp vestibule");
            settle(ctx, sp, 100);
            buildStage();
            if (on("visual")) new ExpansionVisuals(this).run();
            if (on("alliance")) checks.alliance();
            if (on("provenance")) checks.provenance();
            if (on("ticker")) checks.ticker();
            if (on("tongue")) checks.tongue();
            if (on("shooters")) checks.shooters();
            if (on("senses")) checks.senses();
            if (on("navigation")) checks.navigation();
            if (on("horde")) checks.horde();
            if (on("demo")) checks.demo();
            if (on("natural")) checks.natural();
            if (on("persistence")) checks.persistence();
            if (on("perf")) checks.performance();
        } finally {
            sp.close();
        }
        if (on("dedicated")) new ExpansionChecks(this).dedicated();
        if (!failures.isEmpty()) throw new AssertionError("Expansion failures (" + failures.size() + "): " + failures);
    }

    // ------------------------------------------------------------------ the stage
    /** A flat floor of ash bricks in the Limbo, 49 x 49, with 20 blocks of air above it. */
    void buildStage() {
        stage = server(s -> player(s).blockPosition());
        flatten(stage, 24, 20);
        cmd("tp @p " + (stage.getX() + 0.5) + " " + stage.getY() + " " + (stage.getZ() + 0.5));
        settle(ctx, sp, 20);
    }

    /** Restores the stage around {@code c}: floor, air, no creatures (players stay). */
    void flatten(BlockPos c, int r, int h) {
        run(s -> {
            ServerLevel l = limbo(s);
            for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++)
                for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) l.getChunk(cx, cz);
            l.getEntitiesOfClass(Entity.class, new AABB(c).inflate(r + 16, 40, r + 16), e -> !(e instanceof ServerPlayer)).forEach(Entity::discard);
            BlockState floor = ModBlocks.ASHEN_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                for (int y = -3; y < 0; y++) l.setBlock(p.set(c.getX() + x, c.getY() + y, c.getZ() + z), floor, 2);
                for (int y = 0; y <= h; y++) l.setBlock(p.set(c.getX() + x, c.getY() + y, c.getZ() + z), air, 2);
            }
        });
        waitTicks(3);
    }

    void clearStage() { flatten(stage, 24, 20); }

    // ------------------------------------------------------------------ creatures
    /** Saves, closes and reopens this same world (a real restart of the integrated server). */
    void restart() { restart(60); }

    void restart(int settleTicks) {
        var save = sp.getWorldSave();
        sp.close();
        sp = save.open();
        settle(ctx, sp, settleTicks);
    }

    <T extends Entity> UUID spawn(EntityType<T> type, double x, double y, double z, float yaw, Consumer<T> setup) {
        return spawnIn(LimboExpansionTest::limbo, type, x, y, z, yaw, setup);
    }

    <T extends Entity> UUID spawnIn(Function<MinecraftServer, ServerLevel> where, EntityType<T> type, double x, double y, double z, float yaw, Consumer<T> setup) {
        return server(s -> {
            ServerLevel l = where.apply(s);
            T e = type.create(l, EntitySpawnReason.COMMAND);
            e.snapTo(x, y, z, yaw, 0);
            if (e instanceof Mob m) {
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
                m.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), EntitySpawnReason.COMMAND, null);
                m.setPersistenceRequired();
            }
            if (setup != null) setup.accept(e);
            l.addFreshEntity(e);
            return e.getUUID();
        });
    }

    UUID spawnAt(EntityType<? extends Entity> type, BlockPos at, float yaw) { return spawn(type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, null); }

    static Entity get(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) { Entity e = l.getEntity(id); if (e != null) return e; }
        return null;
    }

    /** A dead that hunts the player (the player must be alive: survival). */
    void engage(UUID dead) { run(s -> { if (get(s, dead) instanceof LimboDead d) d.engage(player(s)); }); }

    /** Waits up to {@code timeout} ticks for {@code done}; returns the ticks it took, or -1. */
    int until(Predicate<MinecraftServer> done, int timeout) {
        for (int t = 0; t < timeout; t += 2) {
            if (server(done::test)) return t;
            waitTicks(2);
        }
        return -1;
    }

    void survival(boolean invulnerable) {
        cmd("gamemode survival");
        cmd("effect clear @p");
        cmd("effect give @p minecraft:saturation infinite 0 true");
        if (invulnerable) cmd("effect give @p minecraft:resistance infinite 255 true");
        run(s -> player(s).setHealth(20f));
        waitTicks(3);
    }

    void creative() {
        cmd("gamemode creative");
        cmd("effect clear @p");
        waitTicks(2);
    }

    void tp(double x, double y, double z) {
        cmd("tp @p " + x + " " + y + " " + z);
        waitTicks(3);
    }

    void view(double x, double y, double z, float yaw, float pitch) {
        tp(x, y, z);
        ctx.getInput().lookAt(yaw, pitch);
        waitTicks(6);
    }

    /** Sets a block in the Limbo, optionally marked as placed by a player. */
    void set(BlockPos p, BlockState state, boolean marked) {
        run(s -> {
            limbo(s).setBlockAndUpdate(p, state);
            if (marked) Provenance.mark(limbo(s), p);
        });
    }

    /** Fills a box in the Limbo (inclusive), optionally marked. */
    void box(BlockPos a, BlockPos b, BlockState state, boolean marked) {
        run(s -> {
            ServerLevel l = limbo(s);
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                l.setBlockAndUpdate(p, state);
                if (marked) Provenance.mark(l, p);
            }
        });
    }

    BlockState block(BlockPos p) { return server(s -> limbo(s).getBlockState(p)); }

    static Level levelOf(MinecraftServer s, String which) { return which.equals("limbo") ? limbo(s) : s.overworld(); }
}
