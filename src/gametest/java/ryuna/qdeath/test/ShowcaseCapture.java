package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ryuna.qdeath.client.jjk.JjkClient;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.registry.BestiaryTab;
import ryuna.qdeath.registry.HellTab;

import static ryuna.qdeath.test.TestUtil.*;

/**
 * Frames for the visual showcase (docs/SHOWCASE.es.md). Off unless -Dquantumdeath.showcase=true; run it alone with
 * -Dquantumdeath.skipItems=true -Dquantumdeath.refine=none -Dquantumdeath.skipLimboTest=true -Dquantumdeath.onlyLimbo=true
 * -Dquantumdeath.skipJjk=true -Dquantumdeath.skipYokai=true. Frames land in build/run/clientGameTest/screenshots as
 * gif_<name>_NNN.png; tools/make_gifs.py turns each group into a GIF.
 * -Dquantumdeath.showcaseOnly=a,b limits it to those scenes.
 */
public class ShowcaseCapture implements FabricClientGameTest {
    static final int FRAMES = 20, STEP = 3;
    static final String[] MOBS = {"hollow", "stalker", "crier", "foreman", "lanky", "bloater", "breaker", "runt", "runaway", "crawler", "sundered",
            "walking_ossuary", "mourning_hound", "wake_bearer", "cinder_knight", "glutton", "gluttling", "sullen", "wrathborn", "heretic",
            "iron_fury", "penitent", "echo", "swarm_eye", "damper", "bifauce", "fake_creeper"};
    static final String[] BOSSES = {"toll_keeper", "gorged_matriarch", "styxmaw", "heresiarch", "vigil_wake"};
    static final int Y = 150;

    ClientGameTestContext ctx;
    TestSingleplayerContext sp;
    String only;

    boolean wants(String scene) { return only.isEmpty() || ("," + only + ",").contains("," + scene + ","); }

    /** FRAMES screenshots, STEP ticks apart: the raw material of one GIF. */
    void burst(String name) {
        for (int i = 0; i < FRAMES; i++) {
            ctx.takeScreenshot(String.format("gif_%s_%03d", name, i));
            ctx.waitTicks(STEP);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("quantumdeath.showcase")) return;
        ctx = context;
        only = System.getProperty("quantumdeath.showcaseOnly", "");
        try (TestSingleplayerContext single = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setSeed("666"); s.setGenerateStructures(true); s.setAllowCommands(true); })
                .create()) {
            sp = single;
            settle(ctx, sp, 40);
            hideHud(ctx);
            for (String c : new String[]{"deathmask vigil pause", "difficulty normal", "time set noon", "gamerule advance_time false",
                    "gamerule send_command_feedback false", "gamerule spawn_monsters false"})
                cmd(sp, c);
            ctx.getInput().resizeWindow(1280, 720);
            if (wants("tabs")) tabs();
            cmd(sp, "title @a clear");
            ctx.getInput().pressKey(o -> o.keyToggleGui);   // no HUD, chat, titles or hand in the frames
            if (wants("layers")) layers();
            if (wants("mobs")) mobs();
            if (wants("bosses")) bosses();
            if (wants("yokai")) yokai();
            if (wants("jjk")) jjk();
            if (wants("expansion")) expansion();
        }
    }

    static final String[] EXPANSION = {"mourning_parasite", "breach_ticker", "strangler", "bile_spitter", "rib_thrower"};

    /**
     * The expansion of the Limbo (docs/ampliacion-limbo-implementacion.md): each new creature on the stage, then each mechanic
     * live: a possession, a breach, a tongue, a bolus, a mound. In the Limbo, so the expansion's behaviours are on.
     */
    private void expansion() {
        cmd(sp, "deathmask tp vestibule");
        settle(ctx, sp, 80);
        cmd(sp, "deathmask limbo processions off");
        cmd(sp, "gamerule mob_griefing true");
        stageHere();
        // Everything is placed from the stage's centre in absolute coordinates: the camera is moved between scenes and a
        // relative way back would carry any fall of the player into the next scene.
        net.minecraft.core.BlockPos c = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
        // The camera hovers where it is put (creative flight): it does not sink to the floor during a long capture.
        ctx.runOnClient(mc -> { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); });
        java.util.function.BiFunction<double[], String, String> at = (o, rest) -> String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f%s",
                c.getX() + 0.5 + o[0], c.getY() + o[1], c.getZ() + 0.5 + o[2], rest);
        java.util.function.BiFunction<int[], String, String> blk = (o, rest) -> (c.getX() + o[0]) + " " + (c.getY() + o[1]) + " " + (c.getZ() + o[2]) + rest;
        java.util.function.Consumer<String> camera = where -> { cmd(sp, "tp @p " + where); settle(ctx, sp, 5); };
        String kill = "kill @e[type=!minecraft:player,distance=..60]";
        for (String mob : EXPANSION) {
            cmd(sp, "summon quantumdeath:" + mob + " " + at.apply(new double[]{0, 0, 0}, " {PersistenceRequired:1b,NoAI:1b,Rotation:[0f,0f]}"));
            boolean small = mob.equals("mourning_parasite") || mob.equals("breach_ticker");
            camera.accept(at.apply(new double[]{0, 0, small ? 3.2 : 4.2}, " 180 " + (small ? 22 : 8)));
            ctx.getInput().lookAt(180, small ? 22 : 8);
            ctx.waitTicks(15);
            burst("mob_" + mob);
            cmd(sp, kill);
        }
        // A pig taken by a parasite, seen from the side (the parasite starts right behind it).
        cmd(sp, "summon minecraft:pig " + at.apply(new double[]{0, 0, 5}, " {PersistenceRequired:1b}"));
        cmd(sp, "summon quantumdeath:mourning_parasite " + at.apply(new double[]{0, 0, 2}, " {PersistenceRequired:1b}"));
        camera.accept(at.apply(new double[]{10, 2.5, 4}, " 90 15"));
        ctx.getInput().lookAt(90, 15);
        ctx.waitTicks(20);
        burst("exp_possession");
        ctx.waitTicks(40);
        burst("exp_possessed");
        cmd(sp, kill);
        // The tongue, the bolus and the rib thrown at villagers in front, seen from the side.
        for (String[] scene : new String[][]{{"strangler", "exp_tongue", "11"}, {"bile_spitter", "exp_bile", "13"}, {"rib_thrower", "exp_ribs", "15"}}) {
            double far = Double.parseDouble(scene[2]);
            cmd(sp, "summon minecraft:villager " + at.apply(new double[]{0, 0, far}, " {PersistenceRequired:1b,NoAI:1b,Rotation:[180f,0f]}"));
            cmd(sp, "summon quantumdeath:" + scene[0] + " " + at.apply(new double[]{0, 0, 0}, " {PersistenceRequired:1b,Rotation:[0f,0f]}"));
            camera.accept(at.apply(new double[]{9, 2, far / 2}, " 90 10"));
            ctx.getInput().lookAt(90, 10);
            ctx.waitTicks(20);
            burst(scene[1]);
            burst(scene[1] + "_2");
            cmd(sp, kill);
        }
        // A Breach Ticker against a hut built by a player (marked), a villager inside.
        cmd(sp, "kill @e[type=minecraft:item,distance=..60]"); // what the creatures of the portraits dropped
        cmd(sp, "fill " + blk.apply(new int[]{-2, 0, 6}, " ") + blk.apply(new int[]{2, 3, 10}, " minecraft:cobblestone"));
        cmd(sp, "fill " + blk.apply(new int[]{-1, 0, 7}, " ") + blk.apply(new int[]{1, 2, 9}, " minecraft:air"));
        cmd(sp, "deathmask limbo provenance mark " + blk.apply(new int[]{-2, 0, 6}, " ") + blk.apply(new int[]{2, 3, 10}, ""));
        cmd(sp, "summon minecraft:villager " + at.apply(new double[]{0, 0, 8}, " {PersistenceRequired:1b,NoAI:1b}"));
        cmd(sp, "summon quantumdeath:breach_ticker " + at.apply(new double[]{0, 0, 0}, " {PersistenceRequired:1b}"));
        // The hut is sealed: the Ticker is told where the villager it hunts is (as a pursuit that lost sight of it would know).
        sp.getServer().runOnServer(s -> {
            var level = s.getLevel(HellLayer.VESTIBULE.level);
            var box = new net.minecraft.world.phys.AABB(c).inflate(16);
            // The living one in the hut (the villagers of the scenes before may still be in their death animation).
            var hut = net.minecraft.world.phys.Vec3.atCenterOf(c.offset(0, 0, 8));
            var villager = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, box, net.minecraft.world.entity.Entity::isAlive).stream()
                    .min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(hut))).orElse(null);
            if (villager != null) for (var ticker : level.getEntitiesOfClass(ryuna.qdeath.entity.limbo.BreachTickerEntity.class, box)) ticker.engage(villager);
        });
        // Seen from behind the Ticker, facing the wall between it and the villager.
        camera.accept(at.apply(new double[]{1.5, 2.5, -5}, " 0 15"));
        ctx.getInput().lookAt(0, 15);
        // Filmed from the moment it clings: the count (3 s), the burst and the open corridor.
        for (int k = 0; k < 60; k++) {
            boolean clinging = sp.getServer().computeOnServer(s -> s.getLevel(HellLayer.VESTIBULE.level).getEntitiesOfClass(ryuna.qdeath.entity.limbo.BreachTickerEntity.class,
                    new net.minecraft.world.phys.AABB(c).inflate(16), b -> b.mode() == ryuna.qdeath.entity.limbo.BreachTickerEntity.ON_BARRIER).size() > 0);
            if (clinging) break;
            ctx.waitTicks(10);
        }
        for (int i = 0; i < 4; i++) burst("exp_breach" + (i == 0 ? "" : "_" + i));
        cmd(sp, kill);
        cmd(sp, "fill " + blk.apply(new int[]{-2, 0, 6}, " ") + blk.apply(new int[]{2, 3, 10}, " minecraft:air"));
        // A mound against a tower of six with a villager on top.
        cmd(sp, "fill " + blk.apply(new int[]{-1, 0, -1}, " ") + blk.apply(new int[]{1, 5, 1}, " minecraft:stone_bricks"));
        cmd(sp, "summon minecraft:villager " + at.apply(new double[]{0, 6, 0}, " {PersistenceRequired:1b,NoAI:1b}"));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            cmd(sp, "summon quantumdeath:hollow " + at.apply(new double[]{Math.cos(a) * 7, 0, Math.sin(a) * 7}, " {PersistenceRequired:1b}"));
        }
        camera.accept(at.apply(new double[]{12, 4, 0}, " 90 12"));
        ctx.getInput().lookAt(90, 12);
        ctx.waitTicks(20);
        for (int i = 0; i < 6; i++) { burst("exp_mound" + (i == 0 ? "" : "_" + i)); ctx.waitTicks(30); }
        cmd(sp, kill);
        camera.accept(at.apply(new double[]{0, 0, 0}, ""));
    }

    /** A flat stage of ash bricks where the player stands (in the Limbo). */
    private void stageHere() {
        cmd(sp, "forceload add ~-40 ~-40 ~40 ~40");
        cmd(sp, "fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 quantumdeath:ashen_bricks");
        cmd(sp, "fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air");
        settle(ctx, sp, 20);
    }

    /** Creative tabs of the mod, opened in the real creative screen. */
    private void tabs() {
        for (var key : java.util.List.of(HellTab.KEY, BestiaryTab.KEY, ryuna.qdeath.moditems.modItems.CUSTOM_CREATIVE_TAB_KEY)) {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(key);
            ctx.runOnClient(mc -> {
                mc.setScreenAndShow(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
            });
            ctx.waitForScreen(CreativeModeInventoryScreen.class);
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                try {
                    var scr = mc.gui.screen();
                    var c = CreativeModeInventoryScreen.class;
                    var getPage = c.getDeclaredMethod("getPage", CreativeModeTab.class);
                    var toPage = c.getDeclaredMethod("switchToPage", int.class);
                    var select = c.getDeclaredMethod("selectTab", CreativeModeTab.class);
                    getPage.setAccessible(true); toPage.setAccessible(true); select.setAccessible(true);
                    toPage.invoke(scr, getPage.invoke(scr, tab));
                    select.invoke(scr, tab);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            ctx.getInput().setCursorPos(2, 2);
            ctx.waitTicks(6);
            ctx.takeScreenshot("tab_" + key.identifier().getPath());
            ctx.runOnClient(mc -> mc.setScreenAndShow(null));
            ctx.waitTicks(3);
        }
    }

    /** A still of every layer's shrine and of its landscape, and a slow pan across it. */
    private void layers() {
        for (HellLayer layer : HellLayer.values()) {
            cmd(sp, "deathmask tp " + layer.id);
            settle(ctx, sp, 80);
            ctx.getInput().lookAt(0, 5);
            ctx.waitTicks(5);
            ctx.takeScreenshot("layer_" + layer.depth + "_" + layer.id);
            for (int i = 0; i < FRAMES; i++) {
                ctx.getInput().lookAt(i * 6 - 60, 6);
                ctx.waitTicks(2);
                ctx.takeScreenshot(String.format("gif_pan_%d_%03d", layer.depth, i));
            }
        }
    }

    /** Each creature awake on a stone stage in front of the player. */
    private void mobs() {
        stage();
        for (String mob : MOBS) {
            cmd(sp, "summon quantumdeath:" + mob + " 0 " + Y + " -3 {PersistenceRequired:1b,Rotation:[0f,0f]}");
            cmd(sp, "tp @p 0 " + Y + " 3 180 8");
            ctx.getInput().lookAt(180, 8);
            ctx.waitTicks(15);
            burst("mob_" + mob);
            cmd(sp, "kill @e[type=!minecraft:player]");
        }
    }

    /** Each boss with its AI on, fighting nobody (the player is creative and stays still). */
    private void bosses() {
        stage();
        for (String boss : BOSSES) {
            cmd(sp, "summon quantumdeath:" + boss + " 0 " + Y + " -5 {PersistenceRequired:1b,Rotation:[0f,0f]}");
            cmd(sp, "tp @p 0 " + Y + " 9 180 10");
            ctx.getInput().lookAt(180, 10);
            ctx.waitTicks(20);
            burst("boss_" + boss);
            cmd(sp, "kill @e[type=!minecraft:player]");
        }
    }

    static final String[] YOKAI = {"kappa", "oni", "chest_tsukumogami", "tanuki", "tengu", "kodama", "nurikabe", "ittan_momen", "bell_tsukumogami"};

    /** Each yokai awake, with the player seeing it fully (perception 2). */
    private void yokai() {
        stage();
        cmd(sp, "yokai awaken");
        cmd(sp, "yokai perception 2");
        for (String y : YOKAI) {
            cmd(sp, "summon quantumdeath:" + y + " 0 " + Y + " -4 {PersistenceRequired:1b,Rotation:[0f,0f]}");
            cmd(sp, "tp @p 0 " + Y + " 4 180 8");
            ctx.getInput().lookAt(180, 8);
            ctx.waitTicks(25);
            burst("yokai_" + y);
            cmd(sp, "kill @e[type=!minecraft:player]");
        }
    }

    // class, skill, ticks to hold the key (0 = tap), golems around the target (1 = one in front, 4 = a ring)
    static final Object[][] TECHNIQUES = {
            {"choso", "slicing_exorcism", 0, 1}, {"choso", "supernova", 25, 4}, {"choso", "flowing_red_scale", 0, 1},
            {"uraume", "ice_projectile", 0, 1}, {"uraume", "ice_wall", 0, 1}, {"uraume", "icefall", 0, 4},
            {"jogo", "disaster_flames", 24, 1}, {"jogo", "fire_blast", 0, 1}, {"jogo", "mini_volcano", 0, 1}, {"jogo", "maximum_meteor", 0, 1},
            {"todo", "fake_clap", 0, 1}};

    /** Each technique cast at training golems, from the real keybind. */
    private void jjk() {
        stage();
        cmd(sp, "gamemode survival");
        cmd(sp, "effect give @p minecraft:resistance infinite 4 true");
        cmd(sp, "effect give @p minecraft:saturation infinite 0 true");
        String current = "";
        for (Object[] t : TECHNIQUES) {
            String cls = (String) t[0], skill = (String) t[1];
            int hold = (int) t[2], golems = (int) t[3];
            if (!cls.equals(current)) { cmd(sp, "jjk max @p " + cls); current = cls; ctx.waitTicks(10); }
            cmd(sp, "jjk cooldown reset @p");
            cmd(sp, "jjk resource fill @p");
            cmd(sp, "kill @e[type=!minecraft:player]");
            cmd(sp, "tp @p 0 " + Y + " 0 180 8");
            ctx.getInput().lookAt(180, 8);
            String nbt = " {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}";
            if (golems == 1) cmd(sp, "summon minecraft:iron_golem 0 " + Y + " -5" + nbt);
            else for (int[] o : new int[][]{{3, -2}, {-3, -2}, {0, -5}, {0, 3}}) cmd(sp, "summon minecraft:iron_golem " + o[0] + " " + Y + " " + o[1] + nbt);
            ctx.waitTicks(10);
            String id = cls + ":" + skill;
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new JjkNet.Select(id)));
            ctx.waitTicks(5);
            if (hold > 0) ctx.getInput().holdKeyFor(JjkClient.USE, hold);
            else ctx.getInput().pressKey(JjkClient.USE);
            burst("jjk_" + cls + "_" + skill);
        }
        cmd(sp, "kill @e[type=!minecraft:player]");
    }

    private void stage() {
        cmd(sp, "tp @p 0 " + (Y + 1) + " 0");
        cmd(sp, "forceload add -40 -40 40 40");
        cmd(sp, "fill -30 " + (Y - 1) + " -30 30 " + (Y - 1) + " 30 minecraft:polished_blackstone");
        cmd(sp, "fill -30 " + Y + " -30 30 " + (Y + 20) + " 30 minecraft:air");
        settle(ctx, sp, 20);
    }
}
