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
import net.minecraft.world.phys.AABB;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.limbo.LimboCommands;
import ryuna.qdeath.registry.HellEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static ryuna.qdeath.test.TestUtil.cmd;
import static ryuna.qdeath.test.TestUtil.settle;

/**
 * The refinement of the Limbo (docs/refinamiento-limbo-hardcore.md), in a real client: screenshots of the terrain from the
 * ground and from above in each biome, the human dead in the Limbo's own light (every skin, every specialist, every
 * announced pose), and the functional and performance measurements of the acceptance criteria. Results are logged as
 * "REFINE ..." lines and failures are collected and reported together.
 *
 * <p>-Dquantumdeath.refine=visual|func|all (default all); -Dquantumdeath.refineSeed=N (default 666).
 */
public class LimboRefinementTest implements FabricClientGameTest {
    final List<String> failures = new ArrayList<>();
    TestSingleplayerContext sp;
    ClientGameTestContext ctx;
    BlockPos stage;

    void soft(boolean ok, String what) {
        if (ok) Quantumdeath.LOGGER.info("REFINE OK: {}", what);
        else { Quantumdeath.LOGGER.error("REFINE FAILED: {}", what); failures.add(what); }
    }

    static void log(String what, Object... args) { Quantumdeath.LOGGER.info("REFINE " + what, args); }

    <T> T server(Function<MinecraftServer, T> f) { return sp.getServer().computeOnServer(f::apply); }
    static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }
    static ServerLevel limbo(MinecraftServer s) { return s.getLevel(HellLayer.VESTIBULE.level); }

    @Override
    public void runTest(ClientGameTestContext context) {
        String mode = System.getProperty("quantumdeath.refine", "all");
        if (mode.equals("none")) return;
        ryuna.qdeath.limbo.LimboTuning.digging = false; // a random dig would stop a measured runner
        ctx = context;
        String seed = System.getProperty("quantumdeath.refineSeed", "666");
        try (TestSingleplayerContext single = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setSeed(seed); s.setGenerateStructures(true); s.setAllowCommands(true); })
                .create()) {
            sp = single;
            settle(ctx, sp, 40);
            cmd(sp, "deathmask vigil pause");
            cmd(sp, "deathmask limbo processions off");
            cmd(sp, "difficulty normal");
            cmd(sp, "gamerule spawn_monsters false");
            ctx.getInput().pressKey(o -> o.keyToggleGui);
            cmd(sp, "deathmask tp vestibule");
            settle(ctx, sp, 100);
            if (mode.equals("visual") || mode.equals("all")) {
                terrain();
                buildStage();
                bestiary();
                hordeShots();
            }
            if (mode.equals("func") || mode.equals("all")) {
                if (stage == null) buildStage();
                new RefinementChecks(this).run();
            }
        }
        if (!failures.isEmpty()) throw new AssertionError("Limbo refinement failures: " + failures);
    }

    // ------------------------------------------------------------------ terrain
    void terrain() {
        for (String b : LimboCommands.BIOMES) {
            cmd(sp, "deathmask limbo biome " + b);
            settle(ctx, sp, 100);
            for (int k = 0; k < 2; k++) {
                // A vantage point: inside a large open chamber, a dozen blocks above its floor, facing its longest view.
                double[] v = server(s -> vista(limbo(s), player(s).blockPosition()));
                if (v == null) { log("no vista found in {}", b); continue; }
                cmd(sp, "tp @p " + v[0] + " " + v[1] + " " + v[2]);
                settle(ctx, sp, 40);
                ctx.getInput().lookAt((float) v[3], 12);
                ctx.waitTicks(10);
                ctx.takeScreenshot("terrain_" + b + "_vista" + k);
                cmd(sp, "tp @p " + v[0] + " " + (v[4] + 0.1) + " " + v[2]);
                settle(ctx, sp, 20);
                ctx.getInput().lookAt((float) v[3] + 30, 2);
                ctx.waitTicks(10);
                ctx.takeScreenshot("terrain_" + b + "_ground" + k);
                cmd(sp, "tp @p " + (v[0] + 70) + " " + v[1] + " " + (v[2] + 40));
                settle(ctx, sp, 30);
            }
        }
    }

    /** {x, y, z, yaw, floorY} of a high point in open air near {@code near} with the longest horizontal view, or null. */
    static double[] vista(ServerLevel level, BlockPos near) {
        double[] best = null;
        double bestLen = 0;
        var rnd = level.getRandom();
        for (int i = 0; i < 40; i++) {
            int x = near.getX() + rnd.nextInt(81) - 40, z = near.getZ() + rnd.nextInt(81) - 40;
            level.getChunk(x >> 4, z >> 4);
            for (int y = near.getY() + 30; y > 30; y--) {
                BlockPos f = new BlockPos(x, y, z);
                if (!level.getBlockState(f.below()).isSolid() || !level.getBlockState(f).isAir()) continue;
                int room = 0;
                while (room < 60 && level.getBlockState(f.above(room)).isAir()) room++;
                if (room < 30) break;
                double eyeY = y + Math.min(14, room * 0.35);
                for (int a = 0; a < 360; a += 30) {
                    double dx = -Math.sin(Math.toRadians(a)), dz = Math.cos(Math.toRadians(a));
                    int len = 0;
                    while (len < 160 && level.getBlockState(BlockPos.containing(x + dx * len, eyeY, z + dz * len)).isAir()) len += 2;
                    if (len > bestLen) { bestLen = len; best = new double[]{x + 0.5, eyeY, z + 0.5, a, y}; }
                }
                break;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ bestiary
    /** A flat stage of ash in an open chamber (the Limbo's own light, no night vision). */
    void buildStage() {
        stage = server(s -> player(s).blockPosition());
        BlockPos a = stage;
        // Stone under a single layer of ash: ash falls, and over a cave a floor of ash alone sinks into holes the dead fall
        // into (and walk under walls through), which changes from one generated world to the next.
        cmd(sp, "fill " + (a.getX() - 24) + " " + (a.getY() - 5) + " " + (a.getZ() - 24) + " " + (a.getX() + 24) + " " + (a.getY() - 2) + " " + (a.getZ() + 24) + " minecraft:stone");
        cmd(sp, "fill " + (a.getX() - 24) + " " + (a.getY() - 1) + " " + (a.getZ() - 24) + " " + (a.getX() + 24) + " " + (a.getY() - 1) + " " + (a.getZ() + 24) + " quantumdeath:ash");
        cmd(sp, "fill " + (a.getX() - 24) + " " + a.getY() + " " + (a.getZ() - 24) + " " + (a.getX() + 24) + " " + (a.getY() + 9) + " " + (a.getZ() + 24) + " minecraft:air");
        cmd(sp, "kill @e[type=!minecraft:player,distance=..200]");
        settle(ctx, sp, 20);
    }

    UUID place(EntityType<? extends LimboDead> type, double x, double y, double z, float yaw, int skin, boolean noAi) {
        return server(s -> {
            LimboDead d = type.create(limbo(s), EntitySpawnReason.COMMAND);
            d.snapTo(x, y, z, yaw, 0);
            d.setYHeadRot(yaw);
            d.setYBodyRot(yaw);
            d.finalizeSpawn(limbo(s), limbo(s).getCurrentDifficultyAt(BlockPos.containing(x, y, z)), EntitySpawnReason.COMMAND, null);
            if (skin >= 0) d.setSkin(skin);
            d.setPersistenceRequired();
            d.setNoAi(noAi);
            limbo(s).addFreshEntity(d);
            return d.getUUID();
        });
    }

    void clearStage() {
        sp.getServer().runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(stage).inflate(40), e -> !(e instanceof ServerPlayer)).forEach(Entity::discard));
        ctx.waitTicks(3);
    }

    void view(double x, double y, double z, float yaw, float pitch) {
        cmd(sp, "tp @p " + x + " " + y + " " + z);
        ctx.waitTicks(4);
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(10);
    }

    static final List<EntityType<? extends LimboDead>> SPECIALISTS = List.of(HellEntities.STALKER, HellEntities.CRIER, HellEntities.FOREMAN,
            HellEntities.LANKY, HellEntities.BLOATER, HellEntities.BREAKER, HellEntities.RUNT, HellEntities.RUNAWAY, HellEntities.CRAWLER, HellEntities.SUNDERED);

    /** A real horde in motion (AI on), converging on the player from every side: how a crowd reads in the Limbo's light. */
    void hordeShots() {
        BlockPos a = stage;
        clearStage();
        cmd(sp, "gamemode survival");
        cmd(sp, "effect give @p minecraft:resistance infinite 255 true");
        cmd(sp, "tp @p " + (a.getX() + 0.5) + " " + a.getY() + " " + (a.getZ() + 0.5));
        ctx.waitTicks(5);
        for (int i = 0; i < 44; i++) {
            double ang = i * 2 * Math.PI / 44, r = 18 + (i % 4) * 2.5;
            EntityType<? extends LimboDead> t = i % 9 == 4 ? SPECIALISTS.get((i / 9) % SPECIALISTS.size()) : HellEntities.HOLLOW;
            UUID id = place(t, a.getX() + 0.5 + Math.cos(ang) * r, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * r, 0, -1, false);
            sp.getServer().runOnServer(s -> { if (limbo(s).getEntity(id) instanceof LimboDead d) d.engage(player(s)); });
        }
        ctx.getInput().lookAt(0, 6);
        ctx.waitTicks(30);
        ctx.takeScreenshot("horde_incoming_a");
        ctx.getInput().lookAt(180, 6);
        ctx.waitTicks(12);
        ctx.takeScreenshot("horde_incoming_b");
        ctx.waitTicks(25);
        ctx.getInput().lookAt(90, 10);
        ctx.waitTicks(3);
        ctx.takeScreenshot("horde_close");
        cmd(sp, "tp @p " + (a.getX() + 0.5) + " " + (a.getY() + 7) + " " + (a.getZ() - 6.5));
        ctx.getInput().lookAt(0, 45);
        ctx.waitTicks(10);
        ctx.takeScreenshot("horde_from_above");
        clearStage();
        cmd(sp, "gamemode creative");
        cmd(sp, "effect clear @p");
        ctx.waitTicks(5);
    }

    void bestiary() {
        BlockPos a = stage;
        double y = a.getY();
        // Every common skin, in two rows.
        for (int i = 0; i < 17; i++) place(HellEntities.HOLLOW, a.getX() - 12 + (i % 9) * 3, y, a.getZ() + (i < 9 ? 0 : 4), 180, i, true);
        view(a.getX() + 0.5, y + 1.2, a.getZ() - 9, 0, 8);
        ctx.takeScreenshot("bestiary_common_all");
        view(a.getX() - 6, y + 1.2, a.getZ() - 4, 10, 6);
        ctx.takeScreenshot("bestiary_common_close");
        clearStage();
        // Each specialist with its three skins.
        for (EntityType<? extends LimboDead> type : SPECIALISTS) {
            for (int i = 0; i < 3; i++) place(type, a.getX() - 2.5 + i * 2.5, y, a.getZ(), 180 - 25 + i * 25, i, true);
            place(HellEntities.HOLLOW, a.getX() + 5, y, a.getZ(), 200, 3, true); // scale reference
            view(a.getX() + 0.5, y + 1.2, a.getZ() - 4.6, 0, 8);
            ctx.takeScreenshot("bestiary_" + EntityType.getKey(type).getPath());
            view(a.getX() + 5.5, y + 1.2, a.getZ() - 2.5, 60, 8);
            ctx.takeScreenshot("bestiary_" + EntityType.getKey(type).getPath() + "_side");
            clearStage();
        }
        // A mixed crowd, as a horde would read at a distance.
        for (int i = 0; i < 26; i++) {
            EntityType<? extends LimboDead> t = i % 7 == 3 ? SPECIALISTS.get(i % SPECIALISTS.size()) : HellEntities.HOLLOW;
            place(t, a.getX() - 8 + (i % 6) * 3 + (i / 6) % 2, y, a.getZ() + (i / 6) * 2.5, 180, -1, true);
        }
        view(a.getX() + 0.5, y + 2.5, a.getZ() - 14, 0, 10);
        ctx.takeScreenshot("bestiary_crowd");
        clearStage();
        // Announced poses.
        int[][] poses = {{LimboDead.ANIM_ATTACK, 5}, {LimboDead.ANIM_ATTACK, 9}, {LimboDead.ANIM_SPECIAL, 6}, {LimboDead.ANIM_CALL, 30}, {LimboDead.ANIM_STAGGER, 5}};
        List<EntityType<? extends LimboDead>> all = new ArrayList<>(SPECIALISTS);
        all.addFirst(HellEntities.HOLLOW);
        for (EntityType<? extends LimboDead> type : all) {
            for (int[] pose : poses) {
                if (pose[0] == LimboDead.ANIM_CALL && type != HellEntities.CRIER && type != HellEntities.HOLLOW) continue;
                UUID id = place(type, a.getX() + 0.5, y, a.getZ() + 0.5, -40, 0, true);
                view(a.getX() + 3.0, y + 0.9, a.getZ() + 3.6, 145, 6);
                int anim = pose[0], len = anim == LimboDead.ANIM_CALL ? 46 : 22;
                sp.getServer().runOnServer(s -> { if (limbo(s).getEntity(id) instanceof LimboDead d) d.playAnim(anim, len); });
                ctx.waitTicks(pose[1]);
                ctx.takeScreenshot("pose_" + EntityType.getKey(type).getPath() + "_" + anim + "_" + pose[1]);
                clearStage();
            }
        }
    }
}
