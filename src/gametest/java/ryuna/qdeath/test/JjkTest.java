package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.jjk.ClientTechnique;
import ryuna.qdeath.client.jjk.JjkClient;
import ryuna.qdeath.client.jjk.TechniqueScreen;
import ryuna.qdeath.jjk.Freeze;
import ryuna.qdeath.jjk.JjkClasses;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.Ongoing;
import ryuna.qdeath.jjk.TechniqueData;
import ryuna.qdeath.jjk.Techniques;
import ryuna.qdeath.jjk.entity.TechniqueBody;
import ryuna.qdeath.jjk.entity.TechniqueProjectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

import static ryuna.qdeath.test.TestUtil.cmd;
import static ryuna.qdeath.test.TestUtil.settle;

/**
 * The cursed-technique classes, driven through the real client: the book and the screen, every class's techniques
 * through the real key bindings and packets, progression rules, death, a world reload, a dimension change, and a
 * dedicated server with a second (fake) player. Soft checks: every failure is listed at the end.
 * Run alone with {@code -Dquantumdeath.refine=none -Dquantumdeath.skipLimboTest=true -Dquantumdeath.onlyLimbo=true}; skip with {@code -Dquantumdeath.skipJjk=true}.
 */
public class JjkTest implements FabricClientGameTest {
    private static final int Y = 200;
    private final List<String> failures = new ArrayList<>();
    private int passes;
    private ClientGameTestContext ctx;
    private TestSingleplayerContext sp;

    private void soft(boolean ok, String what) {
        if (ok) { passes++; Quantumdeath.LOGGER.info("JJK OK: {}", what); }
        else { failures.add(what); Quantumdeath.LOGGER.error("JJK FAILED: {}", what); }
    }

    private <T> T server(Function<ServerPlayer, T> f) {
        return sp.getServer().computeOnServer(s -> f.apply(s.getPlayerList().getPlayers().getFirst()));
    }

    private void onServer(java.util.function.Consumer<ServerPlayer> f) {
        sp.getServer().runOnServer(s -> f.accept(s.getPlayerList().getPlayers().getFirst()));
    }

    private TechniqueData data() { return server(Techniques::data); }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("quantumdeath.skipJjk")) return;
        ctx = context;
        TestWorldSave save;
        try (TestSingleplayerContext single = ctx.worldBuilder()
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL); s.setSeed("4242"); s.setAllowCommands(true); })
                .create()) {
            sp = single;
            save = single.getWorldSave();
            settle(ctx, sp, 40);
            arena();
            choose();
            progression();
            choso();
            uraume();
            jogo();
            naoya();
            todo();
            death();
            onServer(p -> { Techniques.setClass(p, "naoya"); Techniques.setLevel(p, 12); Techniques.unlock(p, "naoya:fps_movement", true); });
            ctx.waitTicks(5);
        }
        // Logout + server restart: everything comes back from disk.
        try (TestSingleplayerContext again = save.open()) {
            sp = again;
            settle(ctx, sp, 40);
            TechniqueData d = data();
            soft(d.classId.equals("naoya") && d.progress().level == 12 && d.unlocked("naoya:fps_movement"), "class, level and skills survive a save and reload");
            soft(d.classes.containsKey("choso") && d.classes.get("choso").level == 30, "progress of the other classes is kept per class");
            soft(ClientTechnique.classId.equals("naoya") && ClientTechnique.level == 12, "client is resynchronised on join");
        }
        multiplayer();
        Quantumdeath.LOGGER.info("JJK SUMMARY: {} passed, {} failed {}", passes, failures.size(), failures);
        if (!failures.isEmpty()) throw new AssertionError("JJK failures: " + failures);
    }

    // ------------------------------------------------------------------------------------------------ setup helpers

    private void arena() {
        cmd(sp, "deathmask vigil pause");
        cmd(sp, "time set noon");
        cmd(sp, "gamerule advance_time false");
        cmd(sp, "gamerule advance_weather false");
        cmd(sp, "gamerule spawn_monsters false");
        cmd(sp, "gamerule immediate_respawn true");
        cmd(sp, "weather clear");
        // A yard and a long runway in the sky (force-loaded, or the fill would silently fail far from the player).
        cmd(sp, "forceload add -32 -32 31 240");
        cmd(sp, "fill -20 " + (Y - 1) + " -20 20 " + (Y - 1) + " 20 minecraft:smooth_stone");
        cmd(sp, "fill -20 " + Y + " -20 20 " + (Y + 7) + " 20 minecraft:air");
        cmd(sp, "fill -4 " + (Y - 1) + " 21 4 " + (Y - 1) + " 230 minecraft:smooth_stone");
        cmd(sp, "fill -4 " + Y + " 21 4 " + (Y + 6) + " 230 minecraft:air");
        home();
        cmd(sp, "effect give @p minecraft:resistance infinite 4 true");
        cmd(sp, "effect give @p minecraft:saturation infinite 0 true");
        settle(ctx, sp, 30);
    }

    private void home() {
        cmd(sp, "tp @p 0.5 " + Y + " 0.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(4);
    }

    private void reset() {
        cmd(sp, "kill @e[type=husk]");
        cmd(sp, "kill @e[type=chicken]");
        cmd(sp, "kill @e[type=arrow]");
        cmd(sp, "kill @e[type=quantumdeath:technique_body]");
        cmd(sp, "kill @e[type=quantumdeath:technique_projectile]");
        cmd(sp, "fill -20 " + Y + " -20 20 " + (Y + 7) + " 20 minecraft:air");
        cmd(sp, "jjk cooldown reset @p");
        cmd(sp, "jjk resource fill @p");
        cmd(sp, "effect clear @p minecraft:slowness");
        onServer(p -> p.setHealth(p.getMaxHealth()));
        home();
    }

    private UUID dummy(Vec3 at, boolean ai) {
        return sp.getServer().computeOnServer(s -> {
            ServerLevel level = s.overworld();
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            h.setPos(at);
            h.setYRot(180);
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
            h.setHealth(400);
            h.setNoAi(!ai);
            h.setPersistenceRequired();
            level.addFreshEntity(h);
            return h.getUUID();
        });
    }

    private Vec3 at(double x, double y, double z) { return new Vec3(x + 0.5, Y + y, z + 0.5); }

    private Entity entity(UUID id) { return sp.getServer().computeOnServer(s -> s.overworld().getEntity(id)); }

    private float health(UUID id) {
        return sp.getServer().computeOnServer(s -> s.overworld().getEntity(id) instanceof LivingEntity l ? l.getHealth() : -1f);
    }

    /** Turns the real client camera toward a point (the server learns the rotation from the client). */
    private void aim(Vec3 target) {
        Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(4);
    }

    private void aimAt(UUID id) { aim(sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).getBoundingBox().getCenter())); }

    private void select(String skill) {
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new JjkNet.Select(skill)));
        ctx.waitTicks(4);
        soft(data().selected.equals(skill) && ClientTechnique.selected.equals(skill), "select " + skill + " (server and HUD)");
    }

    private void tap() {
        ctx.getInput().pressKey(JjkClient.USE);
        ctx.waitTicks(2);
    }

    private void hold(int ticks) {
        ctx.getInput().holdKeyFor(JjkClient.USE, ticks);
        ctx.waitTicks(2);
    }

    private void secondary() {
        ctx.getInput().pressKey(JjkClient.SECONDARY);
        ctx.waitTicks(2);
    }

    private long count(Class<? extends Entity> type, java.util.function.Predicate<Entity> filter) {
        return sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            return (long) p.level().getEntitiesOfClass(type, p.getBoundingBox().inflate(120), filter::test).size();
        });
    }

    private int cursedIce() {
        return sp.getServer().computeOnServer(s -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(-20, Y - 1, -20, 20, Y + 8, 20)) if (s.overworld().getBlockState(q).is(JjkContent.CURSED_ICE)) n++;
            return n;
        });
    }

    private void hideChat() { TestUtil.hideHud(ctx); }

    /** A screenshot of the current class's full tree. */
    private void treeShot(String name) {
        ctx.runOnClient(JjkClient::openTree);
        ctx.waitForScreen(TechniqueScreen.class);
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(4);
        ctx.takeScreenshot(name);
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------------------------------------ GUI and rules

    private void choose() {
        // The book opens the Path of the Void.
        cmd(sp, "give @p quantumdeath:wisdom_of_the_void");
        ctx.waitTicks(5);
        onServer(p -> {
            for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(ryuna.qdeath.moditems.modItems.WISDOM_OF_THE_VOID)) p.getInventory().setSelectedSlot(i);
        });
        ctx.waitTicks(5);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        int waited = ctx.waitForScreen(TechniqueScreen.class);
        soft(waited >= 0, "using the Wisdom of the Void opens the technique screen");
        ctx.waitTicks(5);
        hideChat();
        ctx.takeScreenshot("jjk_choose_class");
        // Click the Choso card, then "Awaken".
        int[] card = ctx.computeOnClient(mc -> ((TechniqueScreen) mc.gui.screen()).cardCenter("choso"));
        click(card[0], card[1], com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        ctx.takeScreenshot("jjk_choose_class_picked");
        int[] confirm = ctx.computeOnClient(mc -> ((TechniqueScreen) mc.gui.screen()).confirmCenter());
        click(confirm[0], confirm[1], com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        TechniqueData d = data();
        soft(d.classId.equals("choso"), "choosing a class in the screen selects it on the server (" + d.classId + ")");
        soft(d.unlocked("choso:blood_manipulation"), "the starting technique is granted");
        soft(ctx.computeOnClient(mc -> mc.gui.screen() instanceof TechniqueScreen t && !t.choosing()), "the screen switches to the skill tree");
        ctx.waitTicks(5);
        ctx.takeScreenshot("jjk_tree_choso_new");
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        ctx.waitTicks(5);
    }

    private void click(int guiX, int guiY, int button) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTicks(1);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(2);
    }

    private void unlockFromClient(String skill) {
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new JjkNet.Unlock(skill)));
        ctx.waitTicks(4);
    }

    private void progression() {
        TechniqueData d = data();
        soft(d.progress().level == 1 && d.progress().tp == 0, "a new class starts at level 1 with no TP");
        unlockFromClient("choso:slicing_exorcism");
        soft(!data().unlocked("choso:slicing_exorcism"), "unlock refused without TP");
        cmd(sp, "jjk tp add @p 1");
        ctx.waitTicks(3);
        unlockFromClient("choso:slicing_exorcism");
        soft(data().unlocked("choso:slicing_exorcism") && data().progress().tp == 0, "unlock with TP spends it");
        cmd(sp, "jjk tp add @p 10");
        ctx.waitTicks(3);
        unlockFromClient("choso:convergence");
        soft(!data().unlocked("choso:convergence"), "tier II refused below level 5");
        unlockFromClient("choso:piercing_blood");
        soft(!data().unlocked("choso:piercing_blood"), "a skill without its prerequisite is refused");
        cmd(sp, "jjk level set @p 5");
        ctx.waitTicks(3);
        soft(data().progress().tp == 10 + Techniques.tpEarned(5), "levels award TP (" + data().progress().tp + ")");
        unlockFromClient("choso:convergence");
        soft(data().unlocked("choso:convergence"), "tier II accepted at level 5 with its prerequisite");
        soft(ClientTechnique.unlocked.contains("choso:convergence"), "unlocks are synchronised to the client");
        // Class XP from a kill with a technique.
        reset();
        int xp0 = data().progress().xp;
        UUID weak = sp.getServer().computeOnServer(s -> {
            Husk h = EntityTypes.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
            h.setPos(at(0, 0, 5));
            h.setNoAi(true);
            h.setHealth(5);
            s.overworld().addFreshEntity(h);
            return h.getUUID();
        });
        aimAt(weak);
        select("choso:slicing_exorcism");
        tap();
        ctx.waitTicks(15);
        soft(entity(weak) == null || !entity(weak).isAlive(), "Slicing Exorcism kills a weak husk");
        soft(data().progress().xp > xp0, "killing with a technique gives class XP (" + xp0 + " -> " + data().progress().xp + ")");
        int lvl = data().progress().level;
        cmd(sp, "jjk xp add @p 100000");
        ctx.waitTicks(3);
        soft(data().progress().level == 30 && ClientTechnique.level == 30, "XP raises the level up to the cap (" + lvl + " -> " + data().progress().level + ")");
        // The tree with mixed states, and a tooltip.
        ctx.runOnClient(JjkClient::openTree);
        ctx.waitForScreen(TechniqueScreen.class);
        ctx.waitTicks(5);
        var piercing = JjkClasses.skill("choso:piercing_blood");
        int[] node = ctx.computeOnClient(mc -> ((TechniqueScreen) mc.gui.screen()).nodeCenter(piercing));
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(node[0] * scale, node[1] * scale);
        ctx.waitTicks(3);
        ctx.takeScreenshot("jjk_tree_choso_tooltip");
        click(node[0], node[1], com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
        soft(data().unlocked("choso:piercing_blood"), "clicking an available node in the screen unlocks it");
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------------------------------------------ Choso

    private void choso() {
        cmd(sp, "jjk max @p choso");
        ctx.waitTicks(4);
        treeShot("jjk_tree_choso");
        reset();
        UUID d = dummy(at(0, 0, 7), false);
        aimAt(d);
        select("choso:slicing_exorcism");
        float b0 = data().resource;
        tap();
        ctx.waitTicks(12);
        soft(health(d) < 400, "Slicing Exorcism damages (" + health(d) + ")");
        soft(data().resource < b0, "Slicing Exorcism costs blood");
        // Convergence: hold, release, stored.
        select("choso:convergence");
        hold(36);
        ctx.waitTicks(3);
        soft(ClientTechnique.indicator("jjk.quantumdeath.ind.convergence") != null, "Convergence stores compressed blood (HUD indicator)");
        select("choso:piercing_blood");
        float h0 = health(d);
        tap();
        ctx.waitTicks(3);
        hideChat();
        ctx.takeScreenshot("jjk_choso_piercing_blood");
        soft(h0 - health(d) >= 10, "Piercing Blood fires at once from stored blood and hits hard (" + (h0 - health(d)) + ")");
        // Supernova against a group.
        reset();
        UUID[] ring = {dummy(at(3, 0, 0), false), dummy(at(-3, 0, 0), false), dummy(at(0, 0, 3), false), dummy(at(0, 0, -3), false)};
        select("choso:supernova");
        hold(25);
        ctx.waitTicks(12);
        ctx.takeScreenshot("jjk_choso_supernova");
        ctx.waitTicks(25);
        int hurt = 0;
        for (UUID u : ring) if (health(u) < 400) hurt++;
        soft(hurt >= 3, "Supernova detonates around Choso and hits the group (" + hurt + "/4)");
        // Flowing Red Scale and its Stack.
        double speed0 = server(p -> p.getAttributeValue(Attributes.MOVEMENT_SPEED));
        select("choso:flowing_red_scale");
        tap();
        double speed1 = server(p -> p.getAttributeValue(Attributes.MOVEMENT_SPEED));
        soft(speed1 > speed0, "Flowing Red Scale raises speed (" + speed0 + " -> " + speed1 + ")");
        cmd(sp, "jjk cooldown reset @p");
        select("choso:flowing_red_scale_stack");
        tap();
        double speed2 = server(p -> p.getAttributeValue(Attributes.MOVEMENT_SPEED));
        soft(speed2 > speed1, "Stack is stronger than Flowing Red Scale (" + speed2 + ")");
        ctx.waitTicks(20);
        tap();
        ctx.waitTicks(2);
        soft(server(p -> p.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)), "ending Stack crashes the body");
        soft(server(p -> p.getAttributeValue(Attributes.MOVEMENT_SPEED)) < speed1, "Stack modifiers are removed when it ends");
        // Spending blood you do not have costs health.
        reset();
        aim(at(0, 1, 10));
        select("choso:slicing_exorcism");
        onServer(p -> Techniques.data(p).resource = 0);
        float hp0 = server(LivingEntity::getHealth);
        tap();
        ctx.waitTicks(3);
        soft(server(LivingEntity::getHealth) < hp0, "blood deficit is paid in health");
        soft(ClientTechnique.state.resource() < 5, "blood is synchronised to the HUD");
        select("choso:blood_manipulation");
        onServer(p -> { Techniques.data(p).resource = 10; p.setHealth(p.getMaxHealth()); });
        tap();
        soft(data().resource >= 40, "Blood Manipulation draws blood from the body (" + data().resource + ")");
    }

    // ------------------------------------------------------------------------------------------------ Uraume

    private void uraume() {
        cmd(sp, "jjk max @p uraume");
        ctx.waitTicks(4);
        treeShot("jjk_tree_uraume");
        reset();
        UUID d = dummy(at(0, 0, 7), false);
        aimAt(d);
        select("uraume:ice_projectile");
        tap();
        ctx.waitTicks(10);
        LivingEntity target = (LivingEntity) entity(d);
        soft(health(d) < 400 && sp.getServer().computeOnServer(s -> Freeze.buildup(target)) > 0, "Ice Projectile damages and freezes");
        UUID close = dummy(at(0, 0, 3), false);
        aimAt(close);
        select("uraume:freeze_touch");
        tap();
        LivingEntity t2 = (LivingEntity) entity(close);
        soft(sp.getServer().computeOnServer(s -> Freeze.buildup(t2)) >= 45, "Freeze Touch builds heavy freezing");
        soft(ClientTechnique.state.indicators().stream().anyMatch(i -> i.key().startsWith("jjk.quantumdeath.ind.freeze.")), "the target's freeze stage shows on the HUD");
        // Frost Calm freezes a target solid.
        reset();
        UUID calm = dummy(at(0, 0, 5), false);
        aimAt(calm);
        select("uraume:frost_calm");
        ctx.getInput().holdKeyFor(JjkClient.USE, 20);
        hideChat();
        ctx.takeScreenshot("jjk_uraume_frost_calm");
        ctx.getInput().holdKeyFor(JjkClient.USE, 24);
        ctx.waitTicks(2);
        soft(sp.getServer().computeOnServer(s -> s.overworld().getEntity(calm) instanceof LivingEntity l && l.hasEffect(JjkContent.IMMOBILIZED)), "Frost Calm immobilizes");
        ctx.takeScreenshot("jjk_uraume_frozen_solid");
        // Ice Wall: only temporary blocks, shattered on demand.
        reset();
        aim(at(0, 0, 8));
        select("uraume:ice_wall");
        tap();
        int ice = cursedIce();
        soft(ice >= 15, "Ice Wall raises cursed ice (" + ice + " blocks)");
        ctx.takeScreenshot("jjk_uraume_ice_wall");
        secondary();
        soft(cursedIce() == 0, "the secondary key shatters the walls");
        // Ice Formation spikes melt by themselves.
        select("uraume:ice_formation");
        aim(at(0, 0, 6));
        tap();
        ctx.waitTicks(8);
        soft(cursedIce() > 0, "Ice Formation places spikes");
        ctx.waitTicks(130);
        soft(cursedIce() == 0, "cursed ice melts on its own (no world clutter)");
        // Icefall.
        reset();
        UUID fall = dummy(at(0, 0, 9), false);
        aimAt(fall);
        select("uraume:icefall");
        tap();
        ctx.waitTicks(6);
        ctx.takeScreenshot("jjk_uraume_icefall");
        ctx.waitTicks(20);
        soft(health(fall) < 400, "Enhanced Icefall crashes down on the target");
        // Immune to her own cold.
        onServer(p -> { Freeze.add(p, 100, null); p.setTicksFrozen(120); });
        ctx.waitTicks(2);
        soft(server(p -> Freeze.stage(p) == 0 && p.getTicksFrozen() == 0), "Uraume never freezes");
    }

    // ------------------------------------------------------------------------------------------------ Jogo

    private void jogo() {
        cmd(sp, "jjk max @p jogo");
        ctx.waitTicks(4);
        treeShot("jjk_tree_jogo");
        reset();
        cmd(sp, "effect clear @p minecraft:resistance");
        float hp0 = server(LivingEntity::getHealth);
        onServer(p -> p.hurtServer((ServerLevel) p.level(), p.damageSources().onFire(), 6));
        onServer(p -> p.hurtServer((ServerLevel) p.level(), p.damageSources().lava(), 6));
        soft(server(LivingEntity::getHealth) >= hp0, "Jogo is immune to fire and lava");
        cmd(sp, "effect give @p minecraft:resistance infinite 4 true");
        UUID d = dummy(at(0, 0, 4), false);
        aimAt(d);
        select("jogo:disaster_flames");
        hold(24);
        soft(health(d) < 400 && entity(d).isOnFire(), "Disaster Flames burns in a cone");
        reset();
        d = dummy(at(0, 0, 8), false);
        aimAt(d);
        select("jogo:fire_blast");
        tap();
        ctx.waitTicks(10);
        soft(health(d) < 400, "Fire Blast hits");
        float h1 = health(d);
        select("jogo:fire_burst");
        tap();
        ctx.waitTicks(2);
        soft(health(d) < h1, "Fire Burst detonates at the target");
        // Ember insects hunt.
        reset();
        UUID prey = dummy(at(6, 0, 10), false);
        aimAt(prey);
        select("jogo:ember_insects");
        tap();
        soft(count(TechniqueProjectile.class, e -> ((TechniqueProjectile) e).kind() == TechniqueProjectile.Kind.EMBER_INSECT) >= 4, "Ember Insects are released");
        ctx.waitTicks(20);
        ctx.takeScreenshot("jjk_jogo_insects");
        ctx.waitTicks(60);
        soft(health(prey) < 400, "the insects seek and explode on their target");
        // Volcanoes, control, eruption.
        reset();
        select("jogo:mini_volcano");
        for (int i = 0; i < 4; i++) {
            aim(at(-6 + i * 4, 0, 6));
            tap();
            cmd(sp, "jjk cooldown reset @p");
        }
        ctx.waitTicks(20);
        soft(count(TechniqueBody.class, e -> ((TechniqueBody) e).kind() == TechniqueBody.Kind.VOLCANO) == 3, "Volcano Control keeps at most three volcanoes");
        UUID foe = dummy(at(0, 0, 14), false);
        ctx.waitTicks(70);
        soft(health(foe) < 400, "volcanoes shell enemies in range");
        hideChat();
        ctx.takeScreenshot("jjk_jogo_volcanoes");
        aimAt(foe);
        float hf = health(foe);
        secondary();
        ctx.waitTicks(40);
        soft(health(foe) < hf, "the salvo (secondary key) fires every volcano at the crosshair");
        select("jogo:volcanic_eruption");
        aim(at(2, 0.5, 6));
        tap();
        ctx.waitTicks(20);
        ctx.takeScreenshot("jjk_jogo_eruption");
        ctx.waitTicks(60);
        soft(count(TechniqueBody.class, e -> true) == 2, "a volcano erupts and is spent");
        // Dimension change discards what the class left behind.
        cmd(sp, "deathmask tp vestibule");
        settle(ctx, sp, 40);
        long left = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntitiesOfClass(TechniqueBody.class, new AABB(-50, Y - 20, -50, 50, Y + 40, 50)).size());
        soft(left == 0, "changing dimension removes the volcanoes");
        soft(data().classId.equals("jogo") && data().progress().level == 30, "class data survives a dimension change");
        cmd(sp, "execute in minecraft:overworld run tp @p 0.5 " + Y + " 0.5");
        settle(ctx, sp, 30);
        // Maximum: Meteor.
        reset();
        cmd(sp, "jjk resource fill @p");
        UUID[] crowd = {dummy(at(0, 0, 16), false), dummy(at(4, 0, 18), false), dummy(at(-4, 0, 18), false)};
        aim(at(0, 0, 17));
        select("jogo:maximum_meteor");
        tap();
        soft(data().energy < 5, "Maximum: Meteor spends all cursed energy (" + data().energy + ")");
        ctx.waitTicks(38);
        hideChat();
        ctx.takeScreenshot("jjk_jogo_meteor_falling");
        soft(count(TechniqueBody.class, e -> ((TechniqueBody) e).kind() == TechniqueBody.Kind.METEOR) == 1, "the meteor appears and falls");
        ctx.waitTicks(33);
        ctx.takeScreenshot("jjk_jogo_meteor_impact");
        int hit = 0;
        for (UUID u : crowd) if (health(u) < 400 - 20) hit++;
        soft(hit == 3, "Maximum: Meteor devastates the area (" + hit + "/3)");
        int cd = server(p -> Techniques.cast(p, JjkClasses.skill("jogo:maximum_meteor")).cooldownLeft("jogo:maximum_meteor"));
        soft(cd > 5000, "Maximum: Meteor has a very long cooldown (" + cd + ")");
        cmd(sp, "jjk resource fill @p");
        tap();
        soft(count(TechniqueBody.class, e -> ((TechniqueBody) e).kind() == TechniqueBody.Kind.METEOR) == 0, "it cannot be cast again during the cooldown");
        cmd(sp, "fill -20 " + (Y - 3) + " -20 20 " + (Y - 1) + " 40 minecraft:smooth_stone");
    }

    // ------------------------------------------------------------------------------------------------ Naoya

    private boolean frozen() { return server(p -> p.hasEffect(JjkContent.IMMOBILIZED)); }

    private void naoya() {
        cmd(sp, "jjk max @p naoya");
        ctx.waitTicks(4);
        treeShot("jjk_tree_naoya");
        reset();
        cmd(sp, "fill -4 " + (Y - 1) + " -20 4 " + (Y - 1) + " 230 minecraft:smooth_stone");
        cmd(sp, "tp @p 0.5 " + Y + " 0.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(6);
        select("naoya:projection_sorcery");
        double z0 = server(p -> p.getZ());
        tap();
        ctx.waitTicks(4);
        hideChat();
        ctx.takeScreenshot("jjk_naoya_projection");
        ctx.waitTicks(10);
        double z1 = server(p -> p.getZ());
        soft(z1 - z0 > 5, "a projection carries Naoya along the plan (" + String.format("%.1f", z1 - z0) + " blocks)");
        soft(!frozen(), "a clear projection does not break the rule");
        // Chain in rhythm.
        for (int i = 0; i < 5; i++) {
            tap();
            ctx.waitTicks(11);
        }
        ctx.waitTicks(1);
        var ind = ClientTechnique.indicator("jjk.quantumdeath.ind.mach");
        if (ind == null) ind = ClientTechnique.indicator("jjk.quantumdeath.ind.momentum");
        soft(ind != null && Float.parseFloat(ind.arg()) >= 2.5f, "chained projections build momentum to Mach (" + (ind == null ? "none" : ind.arg()) + ")");
        soft(!frozen(), "a chain in rhythm never breaks the rule");
        double z2 = server(p -> p.getZ());
        soft(z2 - z1 > 40, "momentum carries far (" + String.format("%.1f", z2 - z1) + " blocks)");
        soft(Math.abs(server(p -> p.getY()) - Y) < 0.6, "the chain stays on the runway (y=" + server(p -> p.getY()) + ")");
        // Over open air a chain sinks instead of flying.
        double yEdge = server(p -> p.getY());
        cmd(sp, "fill -4 " + (Y - 1) + " " + (int) (z2 + 2) + " 4 " + (Y - 1) + " 230 minecraft:air");
        ctx.waitTicks(3);
        for (int i = 0; i < 3; i++) { tap(); ctx.waitTicks(11); }
        ctx.waitTicks(4);
        soft(server(p -> p.getY()) < yEdge - 4, "chained projections over the void sink (no flight): y=" + server(p -> p.getY()));
        cmd(sp, "fill -4 " + (Y - 1) + " -20 4 " + (Y - 1) + " 230 minecraft:smooth_stone");
        cmd(sp, "tp @p 0.5 " + Y + " 60.5 0 0");
        ctx.waitTicks(30);
        ctx.waitTicks(30);
        // Pressing too early breaks the rule.
        tap();
        ctx.waitTicks(2);
        tap();
        ctx.waitTicks(1);
        soft(frozen(), "pressing mid-projection freezes Naoya (24 FPS rule)");
        ctx.waitTicks(40);
        // A plan into a wall breaks the rule.
        Quantumdeath.LOGGER.info("JJK naoya after chains: pos={}", me());
        cmd(sp, "tp @p 0.5 " + Y + " 40.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(10);
        double zw = server(p -> p.getZ());
        cmd(sp, "fill -4 " + Y + " " + (int) (zw + 4) + " 4 " + (Y + 4) + " " + (int) (zw + 4) + " minecraft:stone");
        ctx.waitTicks(3);
        Quantumdeath.LOGGER.info("JJK naoya before wall: pos={} wall z={} {}", me(), (int) (zw + 4), server(ryuna.qdeath.jjk.naoya.Naoya::debug));
        tap();
        boolean hitWall = false;
        for (int i = 0; i < 20 && !hitWall; i++) {
            ctx.waitTicks(1);
            hitWall = frozen();
            if (i % 3 == 0) Quantumdeath.LOGGER.info("JJK naoya wall t{}: z={} {}", i, server(p -> p.getZ()), server(ryuna.qdeath.jjk.naoya.Naoya::debug));
        }
        soft(hitWall, "projecting into terrain freezes Naoya");
        ctx.waitTicks(40);
        cmd(sp, "fill -4 " + Y + " " + (int) (zw + 4) + " 4 " + (Y + 4) + " " + (int) (zw + 4) + " minecraft:air");
        // A sharp chained turn breaks the rule.
        tap();
        ctx.waitTicks(11);
        ctx.getInput().lookAt(100, 0);
        ctx.waitTicks(1);
        tap();
        ctx.waitTicks(2);
        soft(frozen(), "a chained turn sharper than allowed freezes Naoya");
        ctx.waitTicks(40);
        // Frame Touch on a moving husk: it breaks the rule and freezes in a frame.
        reset();
        UUID walker = dummy(at(0, 0, 3), true);
        ctx.waitTicks(25);
        onServer(p -> p.attack(p.level().getEntity(walker)));
        boolean framed = false;
        for (int i = 0; i < 30 && !framed; i++) {
            ctx.waitTicks(1);
            framed = sp.getServer().computeOnServer(s -> s.overworld().getEntity(walker) instanceof LivingEntity l && l.hasEffect(JjkContent.IMMOBILIZED));
        }
        soft(framed, "Frame Touch + Frame Freeze: a husk that moves is frozen in a frame");
        hideChat();
        ctx.takeScreenshot("jjk_naoya_frame_freeze");
        // Momentum Attack: a hit while projecting is stronger than a standing hit.
        reset();
        UUID bag = dummy(at(0, 0, 30), false);
        cmd(sp, "tp @p 0.5 " + Y + " 0.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(30);
        float s0 = health(bag);
        onServer(p -> { p.teleportTo(0.5, Y, 28.5); });
        ctx.waitTicks(25);
        onServer(p -> p.attack(p.level().getEntity(bag)));
        float standing = s0 - health(bag);
        cmd(sp, "tp @p 0.5 " + Y + " 22.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(30);
        tap();
        float s1 = health(bag);
        for (int i = 0; i < 14; i++) {
            ctx.waitTicks(1);
            boolean near = server(p -> p.distanceTo(p.level().getEntity(bag)) < 3.5);
            if (near) { onServer(p -> p.attack(p.level().getEntity(bag))); break; }
        }
        float moving = s1 - health(bag);
        soft(moving > standing, "Momentum Attack: a hit at speed is stronger (" + standing + " -> " + moving + ")");
    }

    // ------------------------------------------------------------------------------------------------ Todo

    private Vec3 pos(UUID id) { return sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).position()); }
    private Vec3 me() { return server(Entity::position); }

    private void todo() {
        cmd(sp, "jjk max @p todo");
        ctx.waitTicks(4);
        treeShot("jjk_tree_todo");
        reset();
        UUID d = dummy(at(0, 0, 8), false);
        aimAt(d);
        select("todo:self_swap");
        Vec3 p0 = me(), d0 = pos(d);
        tap();
        ctx.waitTicks(6);
        soft(me().distanceTo(d0) < 1.0 && pos(d).distanceTo(p0) < 1.0, "Self Swap exchanges places with the target");
        hideChat();
        ctx.takeScreenshot("jjk_todo_swapped");
        // Fake clap: nothing moves.
        cmd(sp, "jjk cooldown reset @p");
        aimAt(d);
        select("todo:fake_clap");
        Vec3 p1 = me(), d1 = pos(d);
        tap();
        ctx.waitTicks(6);
        soft(me().distanceTo(p1) < 0.2 && pos(d).distanceTo(d1) < 0.2, "Fake Clap claps without swapping");
        // Entity Swap: mark A (sneak + use), clap at B.
        reset();
        UUID a = dummy(at(5, 0, 6), false), b = dummy(at(-5, 0, 6), false);
        select("todo:entity_swap");
        aimAt(a);
        ctx.getInput().holdShift();
        ctx.waitTicks(2);
        tap();
        ctx.getInput().releaseShift();
        ctx.waitTicks(3);
        soft(ClientTechnique.indicator("jjk.quantumdeath.ind.mark") != null, "sneak + use marks the first target");
        Vec3 a0 = pos(a), b0 = pos(b), me0 = me();
        cmd(sp, "jjk cooldown reset @p");
        aimAt(b);
        tap();
        ctx.waitTicks(6);
        soft(pos(a).distanceTo(b0) < 1.0 && pos(b).distanceTo(a0) < 1.0 && me().distanceTo(me0) < 0.5, "Entity Swap exchanges two other entities");
        // Projectile swap: an arrow hanging in the air.
        reset();
        UUID arrow = sp.getServer().computeOnServer(s -> {
            Arrow ar = new Arrow(EntityTypes.ARROW, s.overworld());
            ar.setPos(at(0, 2, 9));
            ar.setNoGravity(true);
            ar.setDeltaMovement(0, 0, 0.01);
            s.overworld().addFreshEntity(ar);
            return ar.getUUID();
        });
        aim(pos(arrow));
        select("todo:self_swap");
        Vec3 ar0 = pos(arrow), p0Arrow = me();
        tap();
        ctx.waitTicks(6);
        Vec3 now = me();
        soft(Math.hypot(now.x - ar0.x, now.z - ar0.z) < 1.2 && pos(arrow).distanceTo(p0Arrow) < 1.5, "Projectile Swap moves Todo to a projectile in flight (" + now + " vs " + ar0 + ")");
        // Unsafe destination: a chicken in a pocket too small for a player. The swap is refused.
        reset();
        cmd(sp, "fill -1 " + Y + " 6 1 " + (Y + 2) + " 8 minecraft:glass");
        cmd(sp, "setblock 0 " + Y + " 7 minecraft:air");
        UUID chick = sp.getServer().computeOnServer(s -> {
            var c = EntityTypes.CHICKEN.create(s.overworld(), EntitySpawnReason.COMMAND);
            c.setPos(at(0, 0, 7));
            c.setNoAi(true);
            s.overworld().addFreshEntity(c);
            return c.getUUID();
        });
        cmd(sp, "fill 0 " + Y + " 6 0 " + Y + " 6 minecraft:air");
        aim(pos(chick).add(0, 0.3, 0));
        Vec3 meBefore = me(), cBefore = pos(chick);
        tap();
        ctx.waitTicks(6);
        soft(me().distanceTo(meBefore) < 0.3 && pos(chick).distanceTo(cBefore) < 0.3, "a swap into a space too small is refused (nobody moves)");
        cmd(sp, "fill -1 " + Y + " 6 1 " + (Y + 2) + " 8 minecraft:air");
    }

    // ------------------------------------------------------------------------------------------------ death

    private void death() {
        cmd(sp, "jjk max @p choso");
        reset();
        select("choso:flowing_red_scale");
        tap();
        cmd(sp, "effect clear @p minecraft:resistance");
        cmd(sp, "kill @p");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> { if (mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn(); });
        ctx.waitFor(mc -> mc.player != null && mc.player.isAlive(), 200);
        ctx.waitTicks(2);
        TechniqueData d = data();
        float half = ryuna.qdeath.jjk.JjkBalance.maxEnergy(30) * 0.5f;
        soft(d.energy >= half - 1 && d.energy <= half + 8, "cursed energy refills to half on respawn (" + d.energy + ")");
        ctx.waitTicks(30);
        d = data();
        soft(d.classId.equals("choso") && d.progress().level == 30 && d.unlocked("choso:choso_mastery"), "class and skills survive death");
        soft(server(p -> p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Quantumdeath.id("jjk.frs.speed")) == null), "toggles do not carry over death");
        soft(ClientTechnique.classId.equals("choso"), "the client keeps the class after respawn");
        soft(sp.getServer().computeOnServer(s -> Ongoing.count()) == 0, "no technique effect outlives its owner");
        cmd(sp, "effect give @p minecraft:resistance infinite 4 true");
        cmd(sp, "effect give @p minecraft:saturation infinite 0 true");
        home();
    }

    // ------------------------------------------------------------------------------------------------ dedicated server

    private void multiplayer() {
        Properties props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("level-seed", "999");
        props.setProperty("pvp", "true");
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
            server.runCommand("op Player0");
            try (var connection = server.connect()) {
                connection.waitForChunksRender(200);
                server.runCommand("deathmask vigil pause");
                server.runCommand("gamerule spawn_monsters false");
                server.runCommand("jjk class set Player0 todo");
                ctx.waitTicks(10);
                soft(ClientTechnique.classId.equals("todo"), "dedicated server: class reaches the client");
                server.runCommand("jjk max Player0");
                ctx.waitTicks(10);
                soft(ClientTechnique.unlocked.size() == JjkClasses.get("todo").skills().size(), "dedicated server: unlocks reach the client");
                soft(ClientTechnique.state.energyMax() > 100, "dedicated server: cursed energy synchronised");
                // Two players of the same class casting at once (the second is a server-side fake player).
                server.runCommand("jjk class set Player0 choso");
                server.runCommand("jjk max Player0");
                ctx.waitTicks(5);
                boolean both = server.computeOnServer(s -> {
                    ServerPlayer real = s.getPlayerList().getPlayers().getFirst();
                    ServerLevel level = (ServerLevel) real.level();
                    FakePlayer fake = FakePlayer.get(level);
                    fake.setPos(real.getX() + 3, real.getY(), real.getZ());
                    Techniques.setClass(fake, "choso");
                    Techniques.unlockAll(fake);
                    Techniques.fill(fake);
                    Techniques.select(fake, "choso:slicing_exorcism");
                    Techniques.select(real, "choso:slicing_exorcism");
                    Techniques.input(fake, JjkNet.PRESS);
                    Techniques.input(fake, JjkNet.RELEASE);
                    Techniques.input(real, JjkNet.PRESS);
                    Techniques.input(real, JjkNet.RELEASE);
                    var projectiles = level.getEntitiesOfClass(TechniqueProjectile.class, real.getBoundingBox().inflate(16));
                    boolean ownReal = projectiles.stream().anyMatch(p -> p.getOwner() == real), ownFake = projectiles.stream().anyMatch(p -> p.getOwner() == fake);
                    Techniques.cleanup(fake);
                    return ownReal && ownFake;
                });
                soft(both, "dedicated server: two Choso players cast at the same time, each owning their projectile");
                // A key held while disconnecting.
                ctx.runOnClient(mc -> ClientPlayNetworking.send(new JjkNet.Select("choso:convergence")));
                ctx.waitTicks(3);
                ctx.getInput().holdKey(JjkClient.USE);
                ctx.waitTicks(10);
            }
            ctx.getInput().releaseKey(JjkClient.USE);
            ctx.waitTicks(20);
            try (var again = server.connect()) {
                again.waitForChunksRender(200);
                ctx.waitTicks(10);
                soft(ClientTechnique.classId.equals("choso") && ClientTechnique.level == 30, "dedicated server: progress survives disconnecting mid-charge");
                soft(server.computeOnServer(s -> !Techniques.rt(s.getPlayerList().getPlayers().getFirst()).holding), "dedicated server: no key is left held after reconnecting");
            }
        }
    }
}
