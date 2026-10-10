package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import ryuna.qdeath.hell.HellLayer;

import static ryuna.qdeath.test.TestUtil.*;

/**
 * Screenshot tour (build/run/clientGameTest/screenshots): every layer, every arena, every creature and boss,
 * and VIGIL in each of its forms.
 */
public class HellVisualTour implements FabricClientGameTest {
    static final String[] MOBS = {"hollow", "cinder_knight", "glutton", "gluttling", "sullen", "wrathborn", "heretic", "iron_fury", "penitent", "echo",
            "swarm_eye", "damper", "bifauce", "fake_creeper"};
    static final String[] BOSSES = {"toll_keeper", "gorged_matriarch", "styxmaw", "heresiarch", "vigil_wake"};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (Boolean.getBoolean("quantumdeath.onlyLimbo")) return;
        // Real world generation: the default "consistent settings" are superflat with structures disabled.
        try (TestSingleplayerContext sp = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setSeed("666"); s.setGenerateStructures(true); s.setAllowCommands(true); })
                .create()) {
            settle(ctx, sp, 40);
            hideHud(ctx);
            cmd(sp, "deathmask vigil pause");
            cmd(sp, "difficulty normal");
            cmd(sp, "time set noon");

            lineup(ctx, sp);

            for (HellLayer layer : HellLayer.values()) {
                cmd(sp, "deathmask tp " + layer.id);
                settle(ctx, sp, 80);
                ctx.getInput().lookAt(0, 5);
                ctx.waitTicks(5);
                ctx.takeScreenshot("layer_" + layer.depth + "_" + layer.id + "_shrine");
                ctx.getInput().lookAt(180, 8);
                ctx.waitTicks(5);
                ctx.takeScreenshot("layer_" + layer.depth + "_" + layer.id + "_vista");
                // VIGIL, as it looks in this layer.
                cmd(sp, "deathmask vigil show " + layer.depth);
                ctx.waitTicks(50);
                ctx.takeScreenshot("vigil_form_" + layer.depth);
                cmd(sp, "kill @e[type=quantumdeath:vigil]");
                arena(ctx, sp, layer);
            }
        }
    }

    private void lineup(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        cmd(sp, "fill -40 149 -14 40 149 14 minecraft:polished_blackstone");
        cmd(sp, "fill -40 150 -14 40 170 14 minecraft:air");
        for (int i = 0; i < MOBS.length; i++) {
            int x = -26 + i * 4;
            cmd(sp, "summon quantumdeath:" + MOBS[i] + " " + x + " 150 0 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }
        cmd(sp, "tp @p -14 152 12 180 8");
        settle(ctx, sp, 30);
        ctx.takeScreenshot("lineup_mobs_left");
        cmd(sp, "tp @p 14 152 12 180 8");
        settle(ctx, sp, 20);
        ctx.takeScreenshot("lineup_mobs_right");
        cmd(sp, "kill @e[type=!minecraft:player]");
        for (int i = 0; i < BOSSES.length; i++) {
            cmd(sp, "summon quantumdeath:" + BOSSES[i] + " 0 150 0 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            cmd(sp, "tp @p 0 151 13 180 6");
            settle(ctx, sp, 20);
            ctx.takeScreenshot("boss_model_" + BOSSES[i]);
            cmd(sp, "kill @e[type=!minecraft:player]");
        }
    }

    private void arena(ClientGameTestContext ctx, TestSingleplayerContext sp, HellLayer layer) {
        BlockPos o = arenaOrigin(sp, layer);
        if (o == null) { ryuna.qdeath.Quantumdeath.LOGGER.warn("GAMETEST: no arena found in {}", layer.id); return; }
        boolean ossuary = layer == HellLayer.DIS;
        // Arena centre is 12 north of the altar; the Ossuary sanctum is far underground in the dungeon.
        BlockPos altar = ossuary ? o.offset(0, -14, -82) : o.offset(0, 1, 12);
        BlockPos center = altar.north(12);
        cmd(sp, "execute in quantumdeath:" + layer.id + " run tp @p " + altar.getX() + " " + (altar.getY() + 4) + " " + (altar.getZ() + 6) + " 180 25");
        settle(ctx, sp, 60);
        ctx.takeScreenshot("arena_" + layer.depth + "_" + layer.id);
        if (ossuary) {
            for (int[] room : new int[][]{{0, -12, 0}, {0, -12, -20}, {18, -12, -19}, {0, -12, -39}, {0, -12, -56}, {0, -12, -70}}) {
                cmd(sp, "execute in quantumdeath:dis run tp @p " + (o.getX() + room[0]) + " " + (o.getY() + room[1]) + " " + (o.getZ() + room[2]) + " 180 10");
                settle(ctx, sp, 20);
                ctx.takeScreenshot("ossuary_room_" + room[2]);
            }
            cmd(sp, "execute in quantumdeath:dis run tp @p " + altar.getX() + " " + (altar.getY() + 4) + " " + (altar.getZ() + 6) + " 180 25");
            settle(ctx, sp, 20);
        }
        // Summon through the real altar logic, then capture the entrance and the boss.
        sp.getServer().runOnServer(server -> ryuna.qdeath.entity.boss.BossSummons.summon(layer, server.getLevel(layer.level), altar, null));
        ctx.waitTicks(40);
        ctx.takeScreenshot("boss_" + layer.depth + "_entrance");
        ctx.waitTicks(layer == HellLayer.ZENITH ? 140 : 80);
        ctx.takeScreenshot("boss_" + layer.depth + "_awake");
        cmd(sp, "deathmask boss kill");
        ctx.waitTicks(20);
        // The final form plays a long death; count only the living.
        check(sp.getServer().computeOnServer(server -> server.getLevel(layer.level)
                .getEntitiesOfClass(ryuna.qdeath.entity.boss.HellBoss.class, new net.minecraft.world.phys.AABB(center).inflate(96), e -> e.isAlive()).isEmpty()), "boss of " + layer.id + " summoned and killed");
        ctx.waitTicks(110);
    }
}
