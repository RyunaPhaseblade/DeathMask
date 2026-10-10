package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.block.PuzzleBlocks;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.entity.vigil.VigilDirector;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellProgress;
import ryuna.qdeath.registry.ModBlocks;

import java.util.Properties;

import static ryuna.qdeath.test.TestUtil.*;

/** Functional checks: portals, gates, progression, death, VIGIL, the Ossuary puzzle and a dedicated server. */
public class HellGameplayTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (Boolean.getBoolean("quantumdeath.onlyLimbo")) return;
        try (TestSingleplayerContext sp = ctx.worldBuilder()
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL); s.setSeed("777"); s.setAllowCommands(true); })
                .create()) {
            settle(ctx, sp, 40);
            cmd(sp, "deathmask vigil pause");
            cmd(sp, "effect give @a minecraft:resistance infinite 4 true");
            cmd(sp, "effect give @a minecraft:fire_resistance infinite 0 true");
            cmd(sp, "effect give @a minecraft:saturation infinite 0 true");
            portals(ctx, sp);
            gates(ctx, sp);
            bossCredit(ctx, sp);
            death(ctx, sp);
            vigil(ctx, sp);
            puzzle(ctx, sp);
        }
        multiplayer(ctx);
    }

    private static ResourceKey<Level> dim(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().level().dimension());
    }

    private void portals(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        // A 4x5 obsidian frame at (0, 100, 0) in the Overworld, lit with fire.
        cmd(sp, "fill -2 99 -3 3 99 3 minecraft:stone");
        cmd(sp, "fill 0 100 0 3 104 0 minecraft:obsidian");
        cmd(sp, "fill 1 101 0 2 103 0 minecraft:air");
        cmd(sp, "setblock 1 101 0 minecraft:fire");
        ctx.waitTicks(5);
        check(sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(1, 102, 0)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)), "Overworld portal lights");
        cmd(sp, "tp @p 1.5 101 0.5");
        ctx.waitTicks(120);
        settle(ctx, sp, 40);
        check(dim(sp) == HellLayer.VESTIBULE.level, "Nether portal leads to the Ashen Vestibule");
        ctx.takeScreenshot("test_portal_arrival_vestibule");
        // Back through the exit portal that was built on arrival.
        BlockPos exit = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-4, -3, -4), p.blockPosition().offset(4, 3, 4)))
                if (p.level().getBlockState(q).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) return q.immutable();
            return null;
        });
        check(exit != null, "an exit portal exists in the Vestibule");
        cmd(sp, "tp @p " + (exit.getX() + 4) + " " + exit.getY() + " " + exit.getZ());
        ctx.waitTicks(30);
        cmd(sp, "tp @p " + (exit.getX() + 0.5) + " " + exit.getY() + " " + (exit.getZ() + 0.5));
        ctx.waitTicks(120);
        check(dim(sp) == Level.OVERWORLD, "portals in Hell lead back to the Overworld");
    }

    private void gates(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "deathmask progress reset");
        cmd(sp, "deathmask tp vestibule");
        settle(ctx, sp, 60);
        BlockPos gate = gatePos(sp, true);
        check(gate != null, "arrival shrine has a descent gate");
        walkInto(ctx, sp, gate);
        check(dim(sp) == HellLayer.VESTIBULE.level, "the descent gate is sealed before the Toll-Keeper falls");
        ctx.takeScreenshot("test_gate_sealed");
        // Step out, or the gate carries the player down by itself once descent is granted.
        cmd(sp, "tp @p " + (gate.getX() + 0.5) + " " + gate.getY() + " " + (gate.getZ() - 1.5));
        cmd(sp, "deathmask progress set 2");
        ctx.waitTicks(80);
        walkInto(ctx, sp, gate);
        settle(ctx, sp, 40);
        check(dim(sp) == HellLayer.GORGE.level, "the gate opens once descent is earned");
        ctx.takeScreenshot("test_gate_descended_gorge");
        BlockPos up = gatePos(sp, false);
        String where = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().toString());
        check(up != null, "the arrival shrine in the Gorge has an ascent gate (player at " + where + ")");
        ctx.waitTicks(100);
        walkInto(ctx, sp, up);
        check(dim(sp) == HellLayer.VESTIBULE.level, "the ascent gate leads back up");
    }

    private void bossCredit(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "deathmask progress reset");
        cmd(sp, "gamemode creative");
        cmd(sp, "deathmask boss summon vestibule");
        ctx.waitTicks(100);
        check(sp.getServer().computeOnServer(s -> !s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(HellBoss.class,
                s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(40)).isEmpty()), "the Toll-Keeper can be summoned");
        ctx.takeScreenshot("test_boss_summoned");
        cmd(sp, "deathmask boss kill");
        ctx.waitTicks(80);
        int depth = sp.getServer().computeOnServer(s -> HellProgress.of(s.getPlayerList().getPlayers().getFirst()).depth());
        check(depth == 2, "killing the Toll-Keeper grants descent to the Gorge (depth=" + depth + ")");
        // Re-summonable: a second fight is always possible.
        cmd(sp, "deathmask boss summon vestibule");
        ctx.waitTicks(20);
        check(sp.getServer().computeOnServer(s -> !s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(HellBoss.class,
                s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(40)).isEmpty()), "the boss can be fought again");
        cmd(sp, "deathmask boss kill");
        ctx.waitTicks(80);
        cmd(sp, "gamemode survival");
    }

    private void death(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "deathmask progress set 3");
        cmd(sp, "deathmask tp gorge");
        settle(ctx, sp, 40);
        BlockPos deathAt = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
        cmd(sp, "effect clear @a minecraft:resistance");
        cmd(sp, "kill @p");
        ctx.waitTicks(20);
        ctx.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
        ctx.takeScreenshot("test_death_screen");
        ctx.clickScreenButton("deathScreen.respawn");
        ctx.waitTicks(40);
        check(dim(sp) == Level.OVERWORLD, "dying in Hell respawns you in the Overworld");
        int depth = sp.getServer().computeOnServer(s -> HellProgress.of(s.getPlayerList().getPlayers().getFirst()).depth());
        check(depth == 3, "descent progress survives death");
        boolean remains = sp.getServer().computeOnServer(s -> {
            ServerLevel g = s.getLevel(HellLayer.GORGE.level);
            for (BlockPos q : BlockPos.betweenClosed(deathAt.offset(-2, -2, -2), deathAt.offset(2, 2, 2))) if (g.getBlockState(q).is(ModBlocks.PILGRIM_REMAINS)) return true;
            return false;
        });
        check(remains, "the pilgrim's remains are left where they died");
        cmd(sp, "effect give @a minecraft:resistance infinite 4 true");
    }

    private void vigil(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "deathmask tp vestibule");
        settle(ctx, sp, 60);
        cmd(sp, "fill ~-30 ~-1 ~-30 ~30 ~-1 ~30 quantumdeath:ashen_bricks replace minecraft:air");
        for (VigilDirector.Event e : new VigilDirector.Event[]{VigilDirector.Event.SOUND, VigilDirector.Event.TRACES, VigilDirector.Event.REMAINS})
            check(sp.getServer().computeOnServer(s -> VigilDirector.trigger(s.getPlayerList().getPlayers().getFirst(), e, HellLayer.VESTIBULE)), "VIGIL event " + e + " fires");
        ctx.waitTicks(20);
        ctx.takeScreenshot("test_vigil_traces");
        // Remove the arrival shrine's lantern so the pursuit can start.
        cmd(sp, "fill ~-14 ~-6 ~-14 ~14 ~6 ~14 minecraft:air replace quantumdeath:votive_lantern");
        cmd(sp, "deathmask vigil pursuit");
        ctx.waitTicks(150);
        boolean hunting = sp.getServer().computeOnServer(s -> s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(VigilEntity.class,
                new AABB(s.getPlayerList().getPlayers().getFirst().blockPosition()).inflate(80), v -> v.mode() == VigilEntity.Mode.PURSUE).size() == 1);
        check(hunting, "a pursuit spawns VIGIL hunting the player");
        ctx.takeScreenshot("test_vigil_pursuit_dread");
        // Sanctuary: light a votive lantern at the player's feet; VIGIL stops at its edge and leaves.
        cmd(sp, "setblock ~1 ~ ~ quantumdeath:votive_lantern");
        ctx.waitTicks(150);
        boolean gone = sp.getServer().computeOnServer(s -> s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(VigilEntity.class,
                new AABB(s.getPlayerList().getPlayers().getFirst().blockPosition()).inflate(80), v -> v.mode() != VigilEntity.Mode.FADE).isEmpty());
        check(gone, "VIGIL gives up at the edge of a votive lantern's light");
        cmd(sp, "setblock ~1 ~ ~ minecraft:air");
        // Mourning Bell banishes a pursuer.
        cmd(sp, "deathmask vigil pursuit");
        ctx.waitTicks(150);
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(VigilEntity.class, p.getBoundingBox().inflate(80)).forEach(v -> v.banish(p));
        });
        ctx.waitTicks(40);
        check(sp.getServer().computeOnServer(s -> s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(VigilEntity.class,
                s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(80)).isEmpty()), "a mourning bell banishes VIGIL");
        // Sighting stays inside the fog.
        cmd(sp, "deathmask vigil sighting");
        ctx.waitTicks(30);
        ctx.takeScreenshot("test_vigil_sighting");
    }

    private void puzzle(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "deathmask tp dis");
        settle(ctx, sp, 40);
        boolean opened = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            ServerLevel level = (ServerLevel) p.level();
            BlockPos base = p.blockPosition().offset(0, 0, 4);
            BlockPos door = base.offset(0, 0, 6);
            for (int n = 0; n < 4; n++) level.setBlockAndUpdate(base.offset(n * 2 - 3, 0, 0), ModBlocks.CHOIR_BELL.defaultBlockState().setValue(PuzzleBlocks.ChoirBell.NOTE, n));
            level.setBlockAndUpdate(door, ModBlocks.SEALED_DOOR.defaultBlockState());
            for (int note : PuzzleBlocks.ChoirBell.ORDER) {
                BlockPos bell = base.offset(note * 2 - 3, 0, 0);
                level.getBlockState(bell).useWithoutItem(level, p, new BlockHitResult(Vec3.atCenterOf(bell), Direction.UP, bell, false));
            }
            return level.getBlockState(door).isAir();
        });
        check(opened, "ringing Deceit, Gluttony, Wrath, Pride opens the Sealed Door");
    }

    private BlockPos gatePos(TestSingleplayerContext sp, boolean descend) {
        return sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            var block = descend ? ModBlocks.DESCENT_GATE : ModBlocks.ASCENT_GATE;
            for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-8, -4, -8), p.blockPosition().offset(8, 4, 8)))
                if (p.level().getBlockState(q).is(block) && !p.level().getBlockState(q.below()).is(block)) return q.immutable();
            return null;
        });
    }

    private void walkInto(ClientGameTestContext ctx, TestSingleplayerContext sp, BlockPos gate) {
        String dimension = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().identifier().toString());
        cmd(sp, "execute in " + dimension + " run tp @p " + (gate.getX() + 0.5) + " " + gate.getY() + " " + (gate.getZ() + 0.5));
        ctx.waitTicks(30);
    }

    private void multiplayer(ClientGameTestContext ctx) {
        Properties props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("level-seed", "888");
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
            server.runCommand("op Player0");
            try (var connection = server.connect()) {
                connection.waitForChunksRender(200);
                server.runCommand("deathmask vigil pause");
                server.runCommand("deathmask progress set 4 Player0");
                server.runCommand("deathmask tp dis Player0");
                ctx.waitTicks(60);
                connection.waitForChunksRender(200);
                ctx.takeScreenshot("test_multiplayer_dis");
                ResourceKey<Level> d = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension());
                check(d == HellLayer.DIS.level, "dedicated server: player reaches Dis");
                server.runCommand("execute as Player0 at Player0 run deathmask boss summon dis");
                ctx.waitTicks(120);
                ctx.takeScreenshot("test_multiplayer_boss");
                server.runCommand("execute as Player0 at Player0 run deathmask boss kill");
                ctx.waitTicks(80);
                int depth = server.computeOnServer(s -> HellProgress.of(s.getPlayerList().getPlayers().getFirst()).depth());
                check(depth == 5, "dedicated server: Heresiarch credit opens the Zenith (depth=" + depth + ")");
            }
        }
    }
}
