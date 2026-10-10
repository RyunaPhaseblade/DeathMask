package ryuna.qdeath.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.gui.SkillsScreen;
import ryuna.qdeath.client.yokai.BestiaryScreen;
import ryuna.qdeath.client.yokai.BookTabs;
import ryuna.qdeath.client.yokai.ClientYokai;
import ryuna.qdeath.client.yokai.CompanionScreen;
import ryuna.qdeath.client.jjk.ClientTechnique;
import ryuna.qdeath.client.jjk.TechniqueScreen;
import ryuna.qdeath.entity.FakeCreeperEntity;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.entity.SwarmEyeEntity;
import ryuna.qdeath.entity.yokai.BellTsukumogami;
import ryuna.qdeath.entity.yokai.ChestTsukumogami;
import ryuna.qdeath.entity.yokai.IttanMomenEntity;
import ryuna.qdeath.entity.yokai.KappaEntity;
import ryuna.qdeath.entity.yokai.NurikabeEntity;
import ryuna.qdeath.entity.yokai.OniEntity;
import ryuna.qdeath.entity.yokai.TanukiDecoy;
import ryuna.qdeath.entity.yokai.TanukiEntity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.jjk.Techniques;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.skills.SkillProgress;
import ryuna.qdeath.yokai.AmuletData;
import ryuna.qdeath.yokai.Awakening;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.EncounterDirector;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Provenance;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiCommands;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;
import ryuna.qdeath.yokai.YokaiNet;
import ryuna.qdeath.yokai.YokaiPlayer;
import ryuna.qdeath.yokai.YokaiWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static ryuna.qdeath.test.TestUtil.cmd;
import static ryuna.qdeath.test.TestUtil.settle;

/**
 * The yokai awakening, driven through the real client (docs/yokai-shikigamis-percepcion.md, acceptance criteria):
 * awakening and its advancement, old worlds, grace and spawn restrictions; the book's tabs, the general tree and its
 * migration; the three perception levels per player; every creature's warning and rules; natural provenance of
 * chests and bells; pacts, amulets and duplication; tracking; the mount; a reload; and a dedicated server with two
 * players of different perception and many companions. Soft checks: every failure is listed at the end.
 * Run alone with -Dquantumdeath.skipItems=true -Dquantumdeath.refine=none -Dquantumdeath.skipLimboTest=true
 * -Dquantumdeath.onlyLimbo=true -Dquantumdeath.skipJjk=true; skip with -Dquantumdeath.skipYokai=true.
 * -Dquantumdeath.yokaiPhases=a,b,... runs only some phases (awakening, gui, perception, creatures, objects, pacts,
 * amulets, tracking, mount, reload, server).
 */
public class YokaiTest implements FabricClientGameTest {
    private static final int Y = 200;
    private final List<String> failures = new ArrayList<>();
    private int passes;
    private ClientGameTestContext ctx;
    private TestSingleplayerContext sp;
    private Set<String> phases;

    private void soft(boolean ok, String what) {
        if (ok) { passes++; Quantumdeath.LOGGER.info("YOKAI OK: {}", what); }
        else { failures.add(what); Quantumdeath.LOGGER.error("YOKAI FAILED: {}", what); }
    }

    /** Waits up to {@code max} ticks for the condition; the ticks it took, or -1 (never throws, unlike waitFor). */
    private int until(java.util.function.BooleanSupplier cond, int max) {
        for (int i = 0; i <= max; i++) {
            // Evaluated on the test thread: server checks go through computeOnServer, client checks through computeOnClient.
            if (cond.getAsBoolean()) return i;
            ctx.waitTick();
        }
        return -1;
    }

    /** Waits (up to 3 s) for a screen of that class; -1 if it never opens. */
    private int screen(Class<?> type) { return until(() -> ctx.computeOnClient(mc -> type.isInstance(mc.gui.screen())), 60); }

    private boolean phase(String name) { return phases.isEmpty() || phases.contains(name); }

    private <T> T server(Function<ServerPlayer, T> f) { return sp.getServer().computeOnServer(s -> f.apply(s.getPlayerList().getPlayers().getFirst())); }
    private void onServer(Consumer<ServerPlayer> f) { sp.getServer().runOnServer(s -> f.accept(s.getPlayerList().getPlayers().getFirst())); }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("quantumdeath.skipYokai")) return;
        ctx = context;
        phases = new HashSet<>();
        String only = System.getProperty("quantumdeath.yokaiPhases", "");
        if (!only.isBlank()) for (String p : only.split(",")) phases.add(p.trim());
        // Shorter waits for the rituals and searches; every other number is the shipped default.
        YokaiBalance.Kappa.PACT_SPACING = 40;
        YokaiBalance.Bell.WARN_COOLDOWN = 30;
        YokaiBalance.SwarmEye.TRACK_SEARCH = 80;
        TestWorldSave save;
        try (TestSingleplayerContext single = ctx.worldBuilder()
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL); s.setSeed("2626"); s.setAllowCommands(true); })
                .create()) {
            sp = single;
            save = single.getWorldSave();
            settle(ctx, sp, 40);
            arena();
            if (phase("awakening")) awakening();
            else cmd(sp, "yokai awaken @p");
            ctx.waitTicks(5);
            cmd(sp, "yokai director pause");
            cmd(sp, "yokai grace skip");
            if (phase("gui")) gui();
            if (phase("perception")) perception();
            if (phase("creatures")) creatures();
            if (phase("swarmeye")) swarmEye();
            if (phase("objects")) objects();
            if (phase("pacts")) pacts();
            if (phase("amulets")) amulets();
            if (phase("tracking")) tracking();
            if (phase("mount")) mount();
            if (phase("reload")) beforeReload();
        }
        if (phase("reload")) {
            try (TestSingleplayerContext again = save.open()) {
                sp = again;
                settle(ctx, sp, 40);
                afterReload();
            }
        }
        if (phase("server")) dedicated();
        Quantumdeath.LOGGER.info("YOKAI SUMMARY: {} passed, {} failed {}", passes, failures.size(), failures);
        if (!failures.isEmpty()) throw new AssertionError("Yokai failures: " + failures);
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
        cmd(sp, "forceload add -48 -48 47 47");
        cmd(sp, "fill -24 " + (Y - 1) + " -24 24 " + (Y - 1) + " 24 minecraft:smooth_stone");
        cmd(sp, "fill -24 " + Y + " -24 24 " + (Y + 8) + " 24 minecraft:air");
        home();
        cmd(sp, "effect give @p minecraft:saturation infinite 0 true");
        settle(ctx, sp, 30);
    }

    private void home() {
        cmd(sp, "tp @p 0.5 " + Y + " 0.5 0 0");
        ctx.getInput().lookAt(0, 0);
        ctx.waitTicks(4);
    }

    /** Every companion stays in a far corner of the arena. */
    private void parkCompanions() {
        onServer(p -> p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof YokaiCreature y && y.yokai().bonded()).forEach(m -> {
            ((YokaiCreature) m).yokai().order(YokaiCore.Order.STAY);
            m.teleportTo(-18.5, Y, 18.5);
        }));
        ctx.waitTicks(3);
    }

    private void clearArena() {
        cmd(sp, "yokai clear");
        cmd(sp, "kill @e[type=quantumdeath:swarm_arm]");
        cmd(sp, "kill @e[type=item]");
        cmd(sp, "kill @e[type=quantumdeath:tanuki_decoy]");
        cmd(sp, "fill -24 " + Y + " -24 24 " + (Y + 8) + " 24 minecraft:air");
        onServer(p -> {
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.getInventory().clearContent();
        });
        home();
    }

    private Vec3 at(double x, double y, double z) { return new Vec3(x + 0.5, Y + y, z + 0.5); }

    private void perceive(int level) {
        onServer(p -> {
            YokaiPlayer.set(p, YokaiPlayer.of(p).withOverride(level));
            YokaiNet.syncSelf(p);
        });
        ctx.waitTicks(4);
    }

    private void demo(boolean on) {
        cmd(sp, "yokai demo " + (on ? "on" : "off"));
        ctx.waitTicks(4);
    }

    private UUID spawn(Species s, Vec3 at) {
        return sp.getServer().computeOnServer(srv -> {
            Mob m = EncounterDirector.spawnAt(srv.overworld(), s, at, EntitySpawnReason.COMMAND);
            if (m == null) return null;
            m.setPersistenceRequired();
            return m.getUUID();
        });
    }

    private @Nullable Entity entity(UUID id) { return id == null ? null : sp.getServer().computeOnServer(s -> s.overworld().getEntity(id)); }

    private float health(UUID id) { return sp.getServer().computeOnServer(s -> s.overworld().getEntity(id) instanceof LivingEntity l ? l.getHealth() : -1f); }

    private float playerHealth() { return server(LivingEntity::getHealth); }

    private void heal() { onServer(p -> p.setHealth(p.getMaxHealth())); }

    private <T> T with(UUID id, Function<Entity, T> f) { return sp.getServer().computeOnServer(s -> f.apply(s.overworld().getEntity(id))); }

    private void aim(Vec3 target) {
        Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(4);
    }

    /** Looks at the entity, first stepping within interaction reach (3 blocks) when it is farther. */
    private void aimAt(UUID id) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            Entity e = s.overworld().getEntity(id);
            if (e == null || p.distanceTo(e) <= 2.6) return;
            Vec3 d = p.position().subtract(e.position()).multiply(1, 0, 1);
            d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : d.normalize();
            double y = e.isNoGravity() || e.isInWater() ? p.getY() : e.getY();
            p.teleportTo(e.getX() + d.x * 2.2, y, e.getZ() + d.z * 2.2);
        });
        ctx.waitTicks(3);
        aim(sp.getServer().computeOnServer(s -> s.overworld().getEntity(id).getBoundingBox().getCenter()));
    }

    private void click(int guiX, int guiY, int button) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTicks(1);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(3);
    }

    private void hand(ItemStack stack) {
        onServer(p -> {
            p.getInventory().setSelectedSlot(0);
            p.getInventory().setItem(0, stack);
        });
        ctx.waitTicks(3);
    }

    private void use() {
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(4);
    }

    private void attack() {
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
    }

    private void hideChat() { TestUtil.hideHud(ctx); }

    private static boolean titleShown(Minecraft mc) {
        try {
            var f = net.minecraft.client.gui.Hud.class.getDeclaredField("title");
            f.setAccessible(true);
            return f.get(mc.gui.hud) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private boolean hasAdvancement(ServerPlayer p, Identifier id) {
        AdvancementHolder h = p.level().getServer().getAdvancements().get(id);
        return h != null && p.getAdvancements().getOrStartProgress(h).isDone();
    }

    // ------------------------------------------------------------------------------------------------ awakening

    private void awakening() {
        soft(!sp.getServer().computeOnServer(Awakening::awake), "a new world starts asleep");
        // Before the awakening nothing natural brings the four original creatures to the Overworld.
        boolean noLegacySpawns = sp.getServer().computeOnServer(s -> {
            ServerLevel level = s.overworld();
            for (BlockPos pos : List.of(new BlockPos(0, 64, 0), new BlockPos(800, 64, -600), new BlockPos(-1500, 64, 900))) {
                for (var e : level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.MONSTER, pos).unwrap())
                    if (e.value().type() == ModEntities.FAKE_CREEPER || e.value().type() == ModEntities.BIFAUCE || e.value().type() == ModEntities.DAMPER || e.value().type() == ModEntities.SWARM_EYE)
                        return false;
            }
            return true;
        });
        soft(noLegacySpawns, "the original four creatures are no longer in the Overworld biome spawn tables");
        soft(sp.getServer().computeOnServer(s -> EncounterDirector.attempt(s.overworld(), EncounterDirector.groups(s.overworld()).getFirst(), true, null))
                == EncounterDirector.Result.ASLEEP, "the encounter director does nothing before the awakening");
        // Fire without a portal does not count.
        cmd(sp, "setblock 6 " + (Y - 1) + " 6 minecraft:netherrack");
        cmd(sp, "tp @p 6.5 " + Y + " 3.5 0 0");
        ctx.waitTicks(3);
        hand(new ItemStack(Items.FLINT_AND_STEEL));
        aim(new Vec3(6.5, Y, 6.5));
        use();
        ctx.waitTicks(5);
        soft(sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(6, Y, 6)).is(Blocks.FIRE)), "flint and steel lit a fire on netherrack");
        soft(!sp.getServer().computeOnServer(Awakening::awake), "lighting a fire without forming a portal does not wake the world");
        // A real obsidian frame, lit by the player.
        cmd(sp, "fill -1 " + Y + " 10 2 " + (Y + 4) + " 10 minecraft:obsidian");
        cmd(sp, "fill 0 " + (Y + 1) + " 10 1 " + (Y + 3) + " 10 minecraft:air");
        cmd(sp, "tp @p 0.5 " + Y + " 7.5 0 0");
        ctx.waitTicks(3);
        aim(new Vec3(0.5, Y + 1.0, 10.5));
        hideChat();
        use();
        ctx.waitTicks(10);
        soft(sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, Y + 1, 10)).is(Blocks.NETHER_PORTAL)), "the obsidian frame formed a portal");
        soft(sp.getServer().computeOnServer(Awakening::awake), "the first portal formed in the Overworld wakes the world");
        soft(server(p -> hasAdvancement(p, Awakening.ADVANCEMENT)), "the responsible player receives \"What have you done?\" as an advancement");
        soft(server(p -> YokaiWorld.of(p.level().getServer()).awardedTo().map(u -> u.equals(p.getUUID())).orElse(false)), "the advancement delivery is recorded");
        soft(!ctx.computeOnClient(YokaiTest::titleShown), "no title is shown for the awakening");
        // It counts down from the portal on (about a second has passed since then).
        int grace = server(p -> YokaiWorld.of(p.level().getServer()).graceLeft());
        soft(grace <= YokaiBalance.World.GRACE_TICKS && grace > YokaiBalance.World.GRACE_TICKS - 100, "the grace period starts at 10 minutes (" + grace + " ticks left)");
        soft(ClientYokai.awakened && ClientYokai.noteUnread, "the book receives a note (client knows the world is awake)");
        ctx.waitTicks(30);
        ctx.takeScreenshot("yokai_awakening_toast");
        // Through the portal: the Limbo, without the vanilla Nether advancement.
        cmd(sp, "tp @p 0.5 " + (Y + 1) + " 10.5");
        int t = until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == HellLayer.VESTIBULE.level), 400);
        soft(t >= 0, "the portal still leads to the Limbo");
        soft(!server(p -> hasAdvancement(p, Identifier.withDefaultNamespace("story/enter_the_nether"))), "entering the Limbo does not also grant \"We Need to Go Deeper\"");
        cmd(sp, "execute in minecraft:overworld run tp @p 0.5 " + Y + " 0.5");
        until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == Level.OVERWORLD), 200);
        settle(ctx, sp, 20);
        // The grace period only counts Overworld activity, and only harmless encounters happen during it.
        int g0 = server(p -> YokaiWorld.of(p.level().getServer()).graceLeft());
        ctx.waitTicks(60);
        int g1 = server(p -> YokaiWorld.of(p.level().getServer()).graceLeft());
        soft(g1 < g0 && g0 - g1 <= 100, "the grace counts down while someone is in the Overworld (" + g0 + " -> " + g1 + ")");
        boolean harmless = sp.getServer().computeOnServer(s -> {
            ServerLevel level = s.overworld();
            var group = EncounterDirector.groups(level).getFirst();
            for (int i = 0; i < 25; i++) EncounterDirector.attempt(level, group, true, null);
            return level.getEntitiesOfClass(Mob.class, group.getFirst().getBoundingBox().inflate(80), m -> m instanceof YokaiCreature y && y.species().offensive()).isEmpty();
        });
        soft(harmless, "during the grace period the director places no dangerous yokai");
        long wild = sp.getServer().computeOnServer(s -> (long) EncounterDirector.wildNear(s.overworld(), EncounterDirector.groups(s.overworld()).getFirst()));
        soft(wild <= YokaiBalance.Director.MAX_WILD, "the director never exceeds four wild yokai near a group (" + wild + ")");
        cmd(sp, "yokai clear");
        // No identifiable cause: the world wakes anyway and the advancement waits for the first traveller.
        cmd(sp, "yokai sleep");
        cmd(sp, "fill -6 " + Y + " 10 -3 " + (Y + 4) + " 10 minecraft:obsidian");
        cmd(sp, "fill -5 " + (Y + 1) + " 10 -4 " + (Y + 3) + " 10 minecraft:air");
        cmd(sp, "setblock -5 " + (Y + 1) + " 10 minecraft:fire");
        ctx.waitTicks(5);
        soft(sp.getServer().computeOnServer(Awakening::awake) && sp.getServer().computeOnServer(s -> YokaiWorld.of(s).pendingAdvancement()),
                "a portal lit without a responsible player wakes the world and leaves the advancement pending");
        cmd(sp, "tp @p -4.5 " + (Y + 1) + " 10.5");
        until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == HellLayer.VESTIBULE.level), 400);
        soft(!sp.getServer().computeOnServer(s -> YokaiWorld.of(s).pendingAdvancement()), "the first player through a portal receives the pending advancement");
        cmd(sp, "execute in minecraft:overworld run tp @p 0.5 " + Y + " 0.5");
        until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == Level.OVERWORLD), 200);
        settle(ctx, sp, 10);
        // An older world: already asleep, an existing portal is crossed.
        cmd(sp, "yokai sleep");
        ctx.waitTicks(3);
        cmd(sp, "tp @p 0.5 " + (Y + 1) + " 10.5");
        until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == HellLayer.VESTIBULE.level), 400);
        soft(sp.getServer().computeOnServer(Awakening::awake), "in an older world, crossing an existing portal wakes the world");
        soft(server(p -> hasAdvancement(p, Awakening.ADVANCEMENT)), "earlier advancements are kept");
        cmd(sp, "execute in minecraft:overworld run tp @p 0.5 " + Y + " 0.5");
        until(() -> ctx.computeOnClient(mc -> mc.player != null && mc.player.level().dimension() == Level.OVERWORLD), 200);
        settle(ctx, sp, 20);
        cmd(sp, "fill -6 " + Y + " 10 2 " + (Y + 4) + " 10 minecraft:air");
        home();
    }

    // ------------------------------------------------------------------------------------------------ book, skills and migration

    private void gui() {
        clearArena();
        // Migration of the original ordinal format (nine nodes) to stable ids.
        boolean migrated = sp.getServer().computeOnServer(s -> {
            CompoundTag old = new CompoundTag();
            old.putInt("points", 3);
            old.put("unlocked", new IntArrayTag(new int[]{0, 3, 6, 7}));
            SkillProgress p = new SkillProgress();
            p.readFrom(TagValueInput.create(ProblemReporter.DISCARDING, s.registryAccess(), old));
            boolean ok = p.points() == 3 && p.isUnlocked(SkillId.HEALTH_I) && p.isUnlocked(SkillId.SPEED_I) && p.isUnlocked(SkillId.STRENGTH_I)
                    && p.isUnlocked(SkillId.STRENGTH_II) && p.unlocked().size() == 4 && !p.isUnlocked(SkillId.PERCEPTION_I);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, s.registryAccess());
            p.writeTo(out);
            SkillProgress back = new SkillProgress();
            back.readFrom(TagValueInput.create(ProblemReporter.DISCARDING, s.registryAccess(), out.buildResult()));
            return ok && back.unlocked().equals(p.unlocked()) && back.points() == 3;
        });
        soft(migrated, "old ordinal unlocks migrate to stable ids, new nodes start locked, points kept");
        // Points: vanilla levels give general points; technique points are untouched.
        int tpBefore = server(p -> Techniques.data(p).progress().tp);
        cmd(sp, "xp add @p 6 levels");
        ctx.waitTicks(6);
        int points = server(p -> SkillManager.progress(p).points());
        soft(points >= 6, "vanilla levels give general points (" + points + ")");
        // The book opens on the techniques tab; the General Skills tab shows the tree.
        hand(new ItemStack(ryuna.qdeath.moditems.modItems.WISDOM_OF_THE_VOID));
        use();
        soft(screen(TechniqueScreen.class) >= 0, "the Wisdom of the Void opens the book");
        int width = ctx.computeOnClient(mc -> mc.gui.screen().width);
        int[] tab = BookTabs.center(width, BookTabs.Tab.GENERAL);
        click(tab[0], tab[1], InputConstants.MOUSE_BUTTON_LEFT);
        soft(screen(SkillsScreen.class) >= 0, "the General Skills tab opens the general tree");
        hideChat();
        ctx.waitTicks(5);
        ctx.takeScreenshot("yokai_general_tree");
        // Hover and click Omen, then Cursed Sight.
        int[] n1 = ctx.computeOnClient(mc -> { SkillsScreen s = (SkillsScreen) mc.gui.screen(); return new int[]{s.nodeX(SkillId.PERCEPTION_I), s.nodeY(SkillId.PERCEPTION_I)}; });
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(n1[0] * scale, n1[1] * scale);
        ctx.waitTicks(4);
        ctx.takeScreenshot("yokai_general_tooltip");
        click(n1[0], n1[1], InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(6);
        soft(server(p -> SkillManager.progress(p).isUnlocked(SkillId.PERCEPTION_I)), "clicking Omen learns it (server-validated)");
        soft(ClientYokai.perception == 1, "learning Omen gives partial perception");
        int[] n2 = ctx.computeOnClient(mc -> { SkillsScreen s = (SkillsScreen) mc.gui.screen(); return new int[]{s.nodeX(SkillId.PERCEPTION_II), s.nodeY(SkillId.PERCEPTION_II)}; });
        // A drag that starts and ends on the node pans, never learns.
        ctx.getInput().setCursorPos(n2[0] * scale, n2[1] * scale);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(1);
        ctx.getInput().moveCursor(30 * scale, 10 * scale);
        ctx.waitTicks(2);
        ctx.getInput().moveCursor(-30 * scale, -10 * scale);
        ctx.waitTicks(1);
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        soft(!server(p -> SkillManager.progress(p).isUnlocked(SkillId.PERCEPTION_II)), "dragging over a node does not learn it");
        n2 = ctx.computeOnClient(mc -> { SkillsScreen s = (SkillsScreen) mc.gui.screen(); return new int[]{s.nodeX(SkillId.PERCEPTION_II), s.nodeY(SkillId.PERCEPTION_II)}; });
        click(n2[0], n2[1], InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(6);
        soft(server(p -> SkillManager.progress(p).isUnlocked(SkillId.PERCEPTION_II)) && ClientYokai.perception == 2, "clicking Cursed Sight gives complete perception");
        int spent = server(p -> points - SkillManager.progress(p).points());
        soft(spent == YokaiBalance.Skills.TIER_1_COST + YokaiBalance.Skills.TIER_2_COST, "Omen and Cursed Sight cost 1 and 2 general points (" + spent + ")");
        soft(server(p -> Techniques.data(p).progress().tp) == tpBefore, "general unlocks never spend technique points");
        // Pacts needs Cursed Sight: try Orders before Pacts (refused), then a locked node through the packet.
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new ryuna.qdeath.skills.SkillPackets.UnlockRequest(SkillId.BOND_II.id)));
        ctx.waitTicks(4);
        soft(!server(p -> SkillManager.progress(p).isUnlocked(SkillId.BOND_II)), "a node without its prerequisite is refused by the server");
        // Zoom with the wheel.
        ctx.getInput().scroll(3);
        ctx.waitTicks(3);
        ctx.takeScreenshot("yokai_general_zoomed");
        // Bestiary tab.
        tab = BookTabs.center(width, BookTabs.Tab.BESTIARY);
        click(tab[0], tab[1], InputConstants.MOUSE_BUTTON_LEFT);
        soft(screen(BestiaryScreen.class) >= 0, "the Bestiary tab opens the bestiary");
        ctx.waitTicks(5);
        ctx.takeScreenshot("yokai_bestiary_note");
        soft(!ClientYokai.noteUnread && !server(p -> YokaiPlayer.of(p).noteUnread()), "opening the note marks it as read");
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        ctx.waitTicks(3);
        // Spanish, at another GUI scale.
        ctx.runOnClient(mc -> {
            mc.getLanguageManager().setSelected("es_mx");
            mc.options.guiScale().set(2);
            mc.reloadResourcePacks();
        });
        ctx.waitTicks(80);
        ctx.runOnClient(mc -> mc.resizeGui());
        cmd(sp, "yokai bestiary reveal");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.setScreenAndShow(new SkillsScreen()));
        ctx.waitTicks(8);
        ctx.takeScreenshot("yokai_general_tree_es");
        ctx.runOnClient(mc -> mc.setScreenAndShow(new BestiaryScreen()));
        screen(BestiaryScreen.class);
        int[] oni = ctx.computeOnClient(mc -> ((BestiaryScreen) mc.gui.screen()).entryCenter("oni"));
        click(oni[0], oni[1], InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(8);
        ctx.takeScreenshot("yokai_bestiary_oni_es");
        soft(ctx.computeOnClient(mc -> net.minecraft.client.resources.language.I18n.get("yokai.quantumdeath.book.tab.bestiary")).equals("Bestiario"), "the book is translated to Mexican Spanish");
        ctx.runOnClient(mc -> {
            mc.getLanguageManager().setSelected("en_us");
            mc.options.guiScale().set(0);
            mc.reloadResourcePacks();
        });
        ctx.waitTicks(80);
        ctx.runOnClient(mc -> { mc.resizeGui(); mc.setScreenAndShow(null); });
        cmd(sp, "yokai bestiary reset");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------------------------------------ perception

    private void perception() {
        clearArena();
        UUID oni = spawn(Species.ONI, at(0, 0, 3));
        onServer(p -> ((Mob) p.level().getEntity(oni)).setNoAi(true));
        float h0 = health(oni);
        // Level 0: nothing drawn, nothing to aim at, nothing hurts it.
        perceive(0);
        aimAt(oni);
        boolean hidden = ctx.computeOnClient(mc -> {
            Entity e = mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(10), x -> x.getUUID().equals(oni)).getFirst();
            return ClientYokai.view(e) == 0 && !e.isPickable() && (mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY);
        });
        soft(hidden, "perception 0: the model is not drawn and the crosshair cannot target it");
        hideChat();
        ctx.takeScreenshot("yokai_perception_0");
        hand(new ItemStack(Items.IRON_SWORD));
        attack();
        soft(health(oni) == h0, "perception 0: a player cannot hurt a yokai");
        // A player's arrow passes through it; a technique cannot reach it either.
        boolean arrow = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            Entity o = s.overworld().getEntity(oni);
            var a = new net.minecraft.world.entity.projectile.arrow.Arrow(s.overworld(), p, new ItemStack(Items.ARROW), null);
            return !ryuna.qdeath.yokai.YokaiCombat.projectileMayHit(a, o, p) && !ryuna.qdeath.jjk.JjkCombat.canHit(p, o);
        });
        soft(arrow, "perception 0: projectiles and techniques of the player ignore the yokai");
        boolean tnt = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            LivingEntity o = (LivingEntity) s.overworld().getEntity(oni);
            float before = o.getHealth();
            o.hurtServer(s.overworld(), s.overworld().damageSources().explosion(null, p), 10);
            return o.getHealth() == before;
        });
        soft(tnt, "perception 0: an explosion caused by the player does not hurt the yokai");
        // Level 1: a shadow, no model, still no damage.
        perceive(1);
        ctx.waitTicks(20);
        soft(ctx.computeOnClient(mc -> ClientYokai.view(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(10), x -> x.getUUID().equals(oni)).getFirst()) == 1),
                "perception 1: the yokai is an omen for this player");
        ctx.takeScreenshot("yokai_perception_1");
        attack();
        soft(health(oni) == h0, "perception 1: still cannot hurt it");
        // Level 2: the creature itself.
        perceive(2);
        aimAt(oni);
        ctx.waitTicks(10);
        ctx.takeScreenshot("yokai_perception_2");
        attack();
        soft(health(oni) < h0, "perception 2: the player can fight it (" + h0 + " -> " + health(oni) + ")");
        soft(server(p -> (YokaiPlayer.of(p).entry(Species.ONI) & YokaiPlayer.IDENTIFIED) != 0), "a clear look with complete sight identifies it in the bestiary");
        // The 10% partial-perception pursuit: once per encounter, per player.
        double rate = sp.getServer().computeOnServer(s -> {
            ServerLevel level = s.overworld();
            OniEntity o = (OniEntity) level.getEntity(oni);
            o.yokai().calm(0);
            int chase = 0, stable = 0, n = 400;
            for (int i = 0; i < n; i++) {
                FakePlayer f = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("yokai-test-" + i).getBytes()), "fake" + i));
                YokaiPlayer.set(f, YokaiPlayer.of(f).withOverride(1));
                f.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                boolean first = o.yokai().detect(f).chase;
                if (first) chase++;
                if (o.yokai().detect(f).chase == first) stable++;
                if (o.yokai().mayPursue(f) != first) stable--;
            }
            return stable == n ? chase / (double) n : -1;
        });
        soft(rate >= 0.04 && rate <= 0.18, "partial perception: about 10% of encounters start a pursuit, decided once per encounter (" + rate + ")");
        cmd(sp, "yokai clear");
        perceive(-1);
    }

    // ------------------------------------------------------------------------------------------------ creatures

    /**
     * Swarm Eye: it appears in flight, keeps drifting about and bobs between one and two blocks over the floor; then
     * frozen close-ups of the eye (front, side, back) and of its shot, a 3D small eye flying pupil first.
     */
    private void swarmEye() {
        clearArena();
        perceive(2);
        // A wider floor, and the player far away (beyond its 20-block sight) so it wanders instead of stopping to watch.
        cmd(sp, "fill -46 " + (Y - 1) + " -46 46 " + (Y - 1) + " 46 minecraft:smooth_stone");
        cmd(sp, "tp @p -40.5 " + Y + " -40.5");
        ctx.waitTicks(5);
        UUID eye = spawn(Species.SWARM_EYE, at(20, 0, 20));
        double h0 = with(eye, Entity::getY) - Y;
        soft(h0 >= 0.95 && h0 <= 2.05, "Swarm Eye: it appears in flight (" + String.format("%.2f", h0) + " blocks over the floor)");
        double lo = 9, hi = -9, travel = 0;
        int samples = 0;
        boolean landed = false;
        Vec3 last = with(eye, Entity::position);
        for (int i = 0; i < 60; i++) {
            ctx.waitTicks(5);
            Vec3 now = with(eye, Entity::position);
            travel += now.subtract(last).horizontalDistance();
            last = now;
            landed |= with(eye, Entity::onGround);
            if (Math.abs(now.x) > 45 || Math.abs(now.z) > 45) continue;
            lo = Math.min(lo, now.y - Y);
            hi = Math.max(hi, now.y - Y);
            samples++;
        }
        soft(samples > 40 && lo >= 0.9 && hi <= 2.1 && hi - lo >= 0.6,
                "Swarm Eye: it bobs between one and two blocks over the floor (" + String.format("%.2f..%.2f", lo, hi) + ", " + samples + " samples)");
        soft(travel >= 4 && !landed, "Swarm Eye: it keeps flying about (" + String.format("%.1f", travel) + " blocks in 15 s, landed " + landed + ")");
        cmd(sp, "yokai clear");
        cmd(sp, "fill -46 " + (Y - 1) + " -46 46 " + (Y - 1) + " 46 minecraft:air");
        cmd(sp, "fill -24 " + (Y - 1) + " -24 24 " + (Y - 1) + " 24 minecraft:smooth_stone");
        home();

        // Close-ups without the HUD. Watching the player it holds its place; frozen, the camera walks around it.
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        UUID close = spawn(Species.SWARM_EYE, at(0, 0, 3));
        until(() -> with(close, e -> ((SwarmEyeEntity) e).gaze() >= 1), 60);
        ctx.waitTicks(20);
        cmd(sp, "tick freeze");
        ctx.waitTicks(3);
        Vec3 c = with(close, e -> e.getBoundingBox().getCenter());
        aim(c);
        ctx.takeScreenshot("yokai_swarm_eye_front");
        String[] views = {"side", "back"};
        Vec3[] from = {c.add(2.6, -1.4, 0), c.add(0, -1.4, 2.6)};
        for (int i = 0; i < views.length; i++) {
            cmd(sp, String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f", from[i].x, from[i].y, from[i].z));
            ctx.waitTicks(4);
            aim(c);
            ctx.takeScreenshot("yokai_swarm_eye_" + views[i]);
        }
        cmd(sp, "yokai clear");
        home();
        // Its shot, frozen in flight: one crossing the view (tails behind), one coming straight at the camera. They crawl for
        // a few ticks first: a projectile younger than 2 ticks is never drawn this close to the camera.
        cmd(sp, "tick unfreeze");
        Vec3 eyePos = ctx.computeOnClient(mc -> mc.player.getEyePosition());
        onServer(p -> {
            for (Vec3[] s : new Vec3[][]{{eyePos.add(0.35, -0.15, 1.3), new Vec3(1, 0.2, 0)}, {eyePos.add(-0.45, 0.05, 1.6), new Vec3(0, 0, -1)}}) {
                var shot = new ryuna.qdeath.entity.SwarmArmProjectile(ModEntities.SWARM_ARM, p.level());
                shot.setPos(s[0].x, s[0].y - 0.175, s[0].z);
                shot.shoot(s[1].x, s[1].y, s[1].z, 0.01f, 0f);
                p.level().addFreshEntity(shot);
            }
        });
        ctx.waitTicks(4);
        cmd(sp, "tick freeze");
        aim(eyePos.add(0, -0.05, 1.4));
        ctx.takeScreenshot("yokai_swarm_eye_shot");
        cmd(sp, "tick unfreeze");
        cmd(sp, "kill @e[type=quantumdeath:swarm_arm]");
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
    }

    private void creatures() {
        clearArena();
        demo(true);
        // False Creeper: a three-second hiss, then a burst that respects walls, never breaks blocks.
        cmd(sp, "time set midnight");
        UUID fc = spawn(Species.FALSE_CREEPER, at(0, 1, 2));
        onServer(p -> ((YokaiCreature) p.level().getEntity(fc)).yokai().calm(400));
        heal();
        float hp0 = playerHealth();
        onServer(p -> ((FakeCreeperEntity) p.level().getEntity(fc)).ignite(true));
        ctx.waitTicks(20);
        soft(with(fc, e -> e instanceof FakeCreeperEntity f && f.fuseProgress(0) >= 0), "False Creeper: it hisses before bursting");
        soft(playerHealth() == hp0, "False Creeper: no damage during the warning");
        int stone = countArena(Blocks.SMOOTH_STONE);
        // Read the damage the tick it lands (saturation would heal it back within a second).
        until(() -> entity(fc) == null || !entity(fc).isAlive(), 100);
        float dmg = hp0 - playerHealth();
        soft(entity(fc) == null || !entity(fc).isAlive(), "False Creeper: it bursts after the hiss");
        soft(dmg > 0 && dmg <= 6.01f, "False Creeper: the burst deals at most 6 damage (" + dmg + ")");
        soft(countArena(Blocks.SMOOTH_STONE) == stone && countArena(Blocks.FIRE) == 0, "False Creeper: no block broken, no fire");
        // Behind a solid wall: no damage.
        heal();
        cmd(sp, "fill -1 " + Y + " 1 1 " + (Y + 2) + " 1 minecraft:stone");
        UUID fc2 = spawn(Species.FALSE_CREEPER, at(0, 0, 2.6));
        onServer(p -> ((FakeCreeperEntity) p.level().getEntity(fc2)).ignite(true));
        ctx.waitTicks(80);
        soft(playerHealth() == server(LivingEntity::getMaxHealth), "False Creeper: a solid wall between stops the burst");
        cmd(sp, "fill -1 " + Y + " 1 1 " + (Y + 2) + " 1 minecraft:air");
        cmd(sp, "time set noon");
        // Damper: a light in either hand, regardless of perception; a creak first; gives up when the light is put away.
        heal();
        perceive(0);
        onServer(p -> p.getInventory().setItem(20, new ItemStack(Items.TORCH, 4)));
        UUID damper = spawn(Species.DAMPER, at(0, 0, 4));
        ctx.waitTicks(40);
        soft(with(damper, e -> ((Mob) e).getTarget() == null), "Damper: a torch kept in the inventory does not provoke it");
        onServer(p -> p.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, new ItemStack(Items.TORCH)));
        int seen = until(() -> with(damper, e -> e instanceof ryuna.qdeath.entity.DamperEntity d && d.pose() == 1), 60);
        soft(seen >= 0, "Damper: a light in the off hand provokes it, even unseen, and it creaks first");
        float hpD = playerHealth();
        ctx.waitTicks(25);
        soft(playerHealth() == hpD, "Damper: no blow during the two-second creak");
        ctx.waitTicks(70);
        soft(playerHealth() < hpD, "Damper: then it strikes (" + hpD + " -> " + playerHealth() + ")");
        onServer(p -> p.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, ItemStack.EMPTY));
        ctx.waitTicks(130);
        soft(with(damper, e -> ((Mob) e).getTarget() == null), "Damper: putting the light away makes it give up after 5 s");
        // mobGriefing: off keeps the torches, on lets it put one out.
        cmd(sp, "gamerule mob_griefing false");
        cmd(sp, "setblock 3 " + Y + " 5 minecraft:torch");
        ctx.waitTicks(260);
        soft(sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(3, Y, 5)).is(Blocks.TORCH)), "Damper: with mobGriefing off the torch stays");
        cmd(sp, "tp " + damper + " 0.5 " + Y + " 4.5"); // it may have wandered beyond its 12-block light search
        cmd(sp, "gamerule mob_griefing true");
        int out = until(() -> !sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(3, Y, 5)).is(Blocks.TORCH)), 600);
        soft(out >= 0, "Damper: with mobGriefing on it puts out a reachable torch");
        // Behind a wall with a light: no line of sight, no provocation.
        cmd(sp, "yokai clear");
        heal();
        cmd(sp, "fill -12 " + Y + " 2 12 " + (Y + 3) + " 2 minecraft:stone"); // wide enough that it cannot just walk around it
        UUID damper2 = spawn(Species.DAMPER, at(0, 0, 5));
        onServer(p -> p.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, new ItemStack(Items.LANTERN)));
        ctx.waitTicks(40);
        soft(with(damper2, e -> e instanceof ryuna.qdeath.entity.DamperEntity d && d.getTarget() == null), "Damper: a wall between keeps the light unseen");
        onServer(p -> p.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, ItemStack.EMPTY));
        cmd(sp, "fill -12 " + Y + " 2 12 " + (Y + 3) + " 2 minecraft:air");
        cmd(sp, "yokai clear");
        // Oni: a two-second club windup, an 8-damage slam, and it never leaves its territory for someone who has not hurt it.
        perceive(2);
        heal();
        UUID oni = spawn(Species.ONI, at(0, 0, 4));
        int wind = until(() -> with(oni, e -> e instanceof OniEntity o && o.preparing()), 100);
        soft(wind >= 0, "Oni: it raises its club (visible windup)");
        float hpO = playerHealth();
        ctx.waitTicks(20);
        soft(playerHealth() == hpO, "Oni: no damage while the club is raised");
        ctx.waitTicks(40);
        soft(hpO - playerHealth() >= 7.9f || playerHealth() < hpO, "Oni: the slam lands (" + hpO + " -> " + playerHealth() + ")");
        ctx.takeScreenshot("yokai_oni");
        cmd(sp, "tp @p 0.5 " + Y + " -20.5");
        ctx.waitTicks(80);
        soft(with(oni, e -> ((Mob) e).getTarget() == null), "Oni: it does not follow out of its territory");
        cmd(sp, "yokai clear");
        home();
        // Nurikabe: a wall nobody sees; striking its base moves it aside without hurting it.
        perceive(0);
        UUID nuri = spawn(Species.NURIKABE, at(0, 0, 3));
        Vec3 n0 = with(nuri, Entity::position);
        float nh = health(nuri);
        cmd(sp, "tp @p 0.5 " + Y + " 0.5 0 0");
        ctx.getInput().holdKeyFor(o -> o.keyUp, 30);
        soft(server(p -> p.getZ()) < n0.z - 1.0, "Nurikabe: an invisible wall blocks the way");
        aim(n0.add(0, 0.4, 0));
        attack();
        ctx.waitTicks(20);
        soft(with(nuri, e -> e.position().distanceTo(n0)) > 1.0 && health(nuri) == nh, "Nurikabe: striking its base (unseen) moves it aside, unhurt");
        cmd(sp, "yokai clear");
        // Ittan-momen: rustle for two seconds, then a wrap (3 damage and slowness).
        perceive(2);
        heal();
        home();
        UUID ittan = spawn(Species.ITTAN_MOMEN, at(0, 1, 3));
        int ready = until(() -> with(ittan, e -> e instanceof IttanMomenEntity i && i.preparing()), 200);
        soft(ready >= 0, "Ittan-momen: a rustling windup before the wrap");
        float hpI = playerHealth();
        int wrapped = until(() -> server(p -> p.hasEffect(MobEffects.SLOWNESS)), 80);
        soft(wrapped >= 0 && playerHealth() < hpI, "Ittan-momen: the wrap hurts and slows (" + hpI + " -> " + playerHealth() + ")");
        cmd(sp, "yokai clear");
        // Kappa: provoked by torn water plants even unseen; bubbles first; the hold; three thrown sea pickles free you.
        pool();
        perceive(0);
        heal();
        UUID kappa = spawn(Species.KAPPA, at(0, -2, 18));
        onServer(p -> ((KappaEntity) p.level().getEntity(kappa)).setAnchor(new BlockPos(0, Y - 2, 18)));
        cmd(sp, "setblock 2 " + (Y - 2) + " 15 minecraft:seagrass");
        cmd(sp, "tp @p 2.5 " + Y + " 12.5");
        ctx.waitTicks(10);
        aim(new Vec3(2.5, Y - 1.5, 15.5));
        attack();
        ctx.waitTicks(5);
        soft(server(p -> ((KappaEntity) p.level().getEntity(kappa)).provoked(p.getUUID())), "Kappa: tearing up water plants within 6 blocks provokes it, even unseen");
        cmd(sp, "tp @p 0.5 " + (Y - 2) + " 16.5");
        int held = until(() -> with(kappa, e -> ((KappaEntity) e).holding()), 200);
        soft(held >= 0, "Kappa: a provoked victim in its water is held");
        cmd(sp, "tp @p 0.5 " + (Y - 2) + " 31.5");
        ctx.waitTicks(30);
        double dist = server(p -> Vec3.atBottomCenterOf(new BlockPos(0, Y - 2, 18)).distanceTo(new Vec3(p.getX(), Y - 2, p.getZ())));
        soft(dist <= YokaiBalance.Kappa.HOLD_RADIUS + 2.5, "Kappa: the victim is kept within 8 blocks of its spot (" + dist + ")");
        soft(server(p -> p.getY()) > Y - 4.5, "Kappa: it never drags the victim to the bottom");
        for (int i = 0; i < 3; i++) {
            onServer(p -> {
                ItemEntity it = p.drop(new ItemStack(Items.SEA_PICKLE), true, net.minecraft.util.Prediction.SERVER_ONLY);
                if (it != null) it.setPos(p.level().getEntity(kappa).position().add(0, 1, 0));
            });
            ctx.waitTicks(25);
        }
        soft(!with(kappa, e -> ((KappaEntity) e).holding()), "Kappa: three sea pickles thrown by a player who cannot see it end the hold");
        // Provoked again: defeating it ends the hold too.
        onServer(p -> ((KappaEntity) p.level().getEntity(kappa)).hurtServer((ServerLevel) p.level(), p.damageSources().playerAttack(p), 1));
        perceive(2);
        onServer(p -> ((KappaEntity) p.level().getEntity(kappa)).hurtServer((ServerLevel) p.level(), p.damageSources().playerAttack(p), 1));
        cmd(sp, "tp @p 0.5 " + (Y - 2) + " 17.5");
        until(() -> with(kappa, e -> ((KappaEntity) e).holding()), 200);
        cmd(sp, "kill " + kappa);
        ctx.waitTicks(5);
        soft(entity(kappa) == null || !entity(kappa).isAlive(), "Kappa: defeated, the hold ends with it");
        drain();
        home();
        // Swarm Eye: watching for 3 s, then a shriek; hurt, it defends with small eyes.
        heal();
        UUID eye = spawn(Species.SWARM_EYE, at(0, 1, 2.5));
        int shriek = until(() -> with(eye, e -> e instanceof SwarmEyeEntity s && s.gaze() == 2), 140);
        soft(shriek >= 0, "Swarm Eye: it observes for three seconds and shrieks");
        aimAt(eye);
        hand(new ItemStack(Items.WOODEN_SWORD));
        attack();
        int shot = until(() -> count(ryuna.qdeath.entity.SwarmArmProjectile.class) > 0, 120);
        soft(shot >= 0, "Swarm Eye: hurt, it defends itself with small eyes");
        ctx.takeScreenshot("yokai_swarm_eye");
        cmd(sp, "yokai clear");
        cmd(sp, "kill @e[type=quantumdeath:swarm_arm]");
        // Bifauce: both mouths open for 1.5 s before a straight charge.
        heal();
        home();
        UUID bif = spawn(Species.BIFAUCE, at(0, 0, 9));
        int open = until(() -> with(bif, e -> e instanceof ryuna.qdeath.entity.BifauceEntity b && b.pose() == 1), 200);
        soft(open >= 0, "Bifauce: both mouths open before the charge");
        float hpB = playerHealth();
        ctx.waitTicks(15);
        soft(playerHealth() == hpB, "Bifauce: no damage while the mouths open");
        ctx.takeScreenshot("yokai_bifauce");
        cmd(sp, "yokai clear");
        // Unseen, the Bifauce only deceives: familiar sounds from nowhere, recorded as clues.
        perceive(0);
        UUID bif2 = spawn(Species.BIFAUCE, at(0, 0, 10));
        int mimic = until(() -> server(p -> (YokaiPlayer.of(p).entry(Species.BIFAUCE) & 3) != 0), 400);
        soft(mimic >= 0, "Bifauce: unseen, it mimics everyday sounds (a clue in the book)");
        soft(with(bif2, e -> ((Mob) e).getTarget() == null), "Bifauce: unseen, it never attacks");
        cmd(sp, "yokai clear");
        // Tanuki: decoys vanish when investigated, real items untouched.
        cmd(sp, "kill @e[type=item]"); // the torch the Damper put out
        perceive(2);
        UUID tan = spawn(Species.TANUKI, at(6, 0, 6));
        int decoy = until(() -> count(TanukiDecoy.class) > 0, 1400);
        soft(decoy >= 0, "Tanuki: it leaves an illusory decoy");
        if (decoy >= 0) {
            Vec3 d = sp.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(TanukiDecoy.class, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(40)).getFirst().position());
            cmd(sp, "tp @p " + d.x + " " + d.y + " " + d.z);
            ctx.waitTicks(10);
            boolean gone = sp.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(TanukiDecoy.class, new net.minecraft.world.phys.AABB(d, d).inflate(1.5)).isEmpty());
            long items = count(ItemEntity.class);
            soft(gone && items == 0, "Tanuki: investigated, the decoy vanishes and leaves nothing real (gone " + gone + ", items " + items + ")");
        }
        cmd(sp, "yokai clear");
        home();
        // Kodama: a sapling blesses its shelter: hostile yokai notice people 25% later there.
        cmd(sp, "fill 3 " + Y + " 4 3 " + (Y + 4) + " 4 minecraft:oak_log");
        cmd(sp, "fill 1 " + (Y + 5) + " 2 5 " + (Y + 6) + " 6 minecraft:oak_leaves[persistent=false]");
        UUID kod = spawn(Species.KODAMA, at(2, 0, 2));
        ctx.waitTicks(110);
        aimAt(kod);
        hand(new ItemStack(Items.OAK_SAPLING));
        use();
        boolean shelter = sp.getServer().computeOnServer(s -> YokaiGoals.detectionFactor(s.overworld(), new Vec3(3.5, Y, 4.5)) < 0.8);
        soft(shelter, "Kodama: given a sapling, its shelter reduces hostile detection by 25%");
        cmd(sp, "yokai clear");
        cmd(sp, "fill -5 " + Y + " -5 6 " + (Y + 8) + " 8 minecraft:air");
        // Bell: it warns of something hostile within 24 blocks.
        UUID bell = spawn(Species.BELL, at(0, 0, 4));
        cmd(sp, "summon minecraft:zombie 8 " + Y + " 8 {NoAI:1b,PersistenceRequired:1b}");
        int warned = until(() -> with(bell, e -> e instanceof BellTsukumogami b && b.animId() == BellTsukumogami.ANIM_WARN), 200);
        soft(warned >= 0, "Bell: it rings a warning when something hostile is near");
        cmd(sp, "kill @e[type=zombie]");
        cmd(sp, "yokai clear");
        demo(false);
        perceive(-1);
    }

    private int countArena(net.minecraft.world.level.block.Block block) {
        return sp.getServer().computeOnServer(s -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(-24, Y - 1, -24, 24, Y + 8, 24)) if (s.overworld().getBlockState(q).is(block)) n++;
            return n;
        });
    }

    private long count(Class<? extends Entity> type) {
        return sp.getServer().computeOnServer(s -> (long) s.overworld().getEntitiesOfClass(type, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(60)).size());
    }

    private void pool() {
        cmd(sp, "fill -6 " + (Y - 4) + " 12 6 " + (Y - 1) + " 35 minecraft:stone");
        cmd(sp, "fill -5 " + (Y - 3) + " 13 5 " + (Y - 1) + " 34 minecraft:water");
    }

    private void drain() {
        cmd(sp, "fill -6 " + (Y - 4) + " 12 6 " + (Y - 2) + " 35 minecraft:air");
        cmd(sp, "fill -6 " + (Y - 1) + " 12 6 " + (Y - 1) + " 24 minecraft:smooth_stone");
        cmd(sp, "fill -6 " + (Y - 1) + " 25 6 " + (Y - 1) + " 35 minecraft:air");
    }

    // ------------------------------------------------------------------------------------------------ natural objects

    private void objects() {
        clearArena();
        // A chest placed by a player (no provenance) never wakes, even with a loot table.
        cmd(sp, "setblock 3 " + Y + " 3 minecraft:chest");
        cmd(sp, "data merge block 3 " + Y + " 3 {LootTable:\"minecraft:chests/simple_dungeon\"}");
        boolean placed = sp.getServer().computeOnServer(s -> ChestTsukumogami.awaken(s.overworld(), new BlockPos(3, Y, 3), null) == null);
        soft(placed, "a player-placed chest is never transformed");
        // The same chest marked as generated (what world generation does) wakes with its contents.
        onServer(p -> Provenance.markGenerated(p.level().getChunkAt(new BlockPos(3, Y, 3))));
        UUID chest = sp.getServer().computeOnServer(s -> {
            ChestTsukumogami c = ChestTsukumogami.awaken(s.overworld(), new BlockPos(3, Y, 3), null);
            return c == null ? null : c.getUUID();
        });
        soft(chest != null && sp.getServer().computeOnServer(s -> s.overworld().getBlockState(new BlockPos(3, Y, 3)).isAir()), "a natural, untouched chest wakes into a tsukumogami");
        List<ItemStack> carried = chest == null ? List.of() : with(chest, e -> ((ChestTsukumogami) e).contents());
        soft(!carried.isEmpty() && count(ItemEntity.class) == 0, "it carries the chest's contents and drops nothing (" + carried.size() + " stacks)");
        // Killed, it puts the chest back in its place with the same items, and nothing extra.
        cmd(sp, "kill " + chest);
        ctx.waitTicks(30);
        List<ItemStack> back = sp.getServer().computeOnServer(s -> s.overworld().getBlockEntity(new BlockPos(3, Y, 3)) instanceof ChestBlockEntity cb
                ? java.util.stream.IntStream.range(0, cb.getContainerSize()).mapToObj(cb::getItem).filter(x -> !x.isEmpty()).map(ItemStack::copy).toList() : List.of());
        soft(same(carried, back) && count(ItemEntity.class) == 0, "on death the chest returns to its place with exactly its items, no duplicate");
        // Opened (loot rolled) chests are no longer eligible; neither is a rebuilt or a double chest.
        cmd(sp, "setblock 3 " + Y + " 3 minecraft:air");
        cmd(sp, "setblock 3 " + Y + " 3 minecraft:chest");
        cmd(sp, "data merge block 3 " + Y + " 3 {LootTable:\"minecraft:chests/simple_dungeon\"}");
        soft(sp.getServer().computeOnServer(s -> ChestTsukumogami.awaken(s.overworld(), new BlockPos(3, Y, 3), null) == null), "a rebuilt chest in the same place has no provenance");
        cmd(sp, "setblock 6 " + Y + " 3 minecraft:chest[type=right,facing=north]");
        cmd(sp, "setblock 7 " + Y + " 3 minecraft:chest[type=left,facing=north]");
        cmd(sp, "data merge block 6 " + Y + " 3 {LootTable:\"minecraft:chests/simple_dungeon\"}");
        onServer(p -> Provenance.markGenerated(p.level().getChunkAt(new BlockPos(6, Y, 3))));
        soft(sp.getServer().computeOnServer(s -> ChestTsukumogami.awaken(s.overworld(), new BlockPos(6, Y, 3), null) == null), "double chests are excluded");
        cmd(sp, "fill 3 " + Y + " 3 7 " + Y + " 3 minecraft:air");
        // The generation hook on a real village runs in the dedicated-server phase: this world is flat, without structures.
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        List<ItemStack> rest = new ArrayList<>(b);
        for (ItemStack x : a) {
            ItemStack m = rest.stream().filter(y -> ItemStack.isSameItemSameComponents(x, y) && x.getCount() == y.getCount()).findFirst().orElse(null);
            if (m == null) return false;
            rest.remove(m);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ pacts

    private void pacts() {
        clearArena();
        perceive(2);
        // Without Pacts: nothing happens, nothing is consumed.
        UUID eye = spawn(Species.SWARM_EYE, at(0, 1.5, 4));
        onServer(p -> { var r = ((YokaiCreature) p.level().getEntity(eye)).yokai().ritual(p); r.stage = 1; r.time = p.level().getGameTime(); });
        hand(new ItemStack(Items.SPIDER_EYE, 2));
        aimAt(eye);
        use();
        soft(server(p -> p.getMainHandItem().getCount()) == 2 && !with(eye, e -> ((YokaiCreature) e).yokai().bonded()), "without Pacts, no pact and the offering is kept");
        // With Pacts but no amulet: still nothing consumed.
        onServer(p -> { SkillManager.progress(p).grant(SkillId.BOND_III); SkillManager.sync(p); });
        ctx.waitTicks(3);
        use();
        soft(server(p -> p.getMainHandItem().getCount()) == 2 && !with(eye, e -> ((YokaiCreature) e).yokai().bonded()), "without a free amulet, no pact and the offering is kept");
        // With an amulet: the gaze ritual (5 s) and a spider eye.
        onServer(p -> p.getInventory().setItem(8, new ItemStack(YokaiContent.AMULET)));
        onServer(p -> ((YokaiCreature) p.level().getEntity(eye)).yokai().resetRitual(p));
        aimAt(eye);
        // Watching, it sways in the air: keep it in sight as a player would.
        for (int i = 0; i < YokaiBalance.SwarmEye.PACT_GAZE / 4 + 5; i++) aim(with(eye, e -> e.getBoundingBox().getCenter()));
        use();
        soft(with(eye, e -> ((YokaiCreature) e).yokai().bonded()), "Swarm Eye: holding its gaze, then a spider eye, seals the pact");
        soft(server(p -> p.getMainHandItem().getCount()) == 1, "the final offering is consumed only on success");
        soft(server(p -> Bonds.data(p.getInventory().getItem(8)).state().equals(AmuletData.DEPLOYED)), "the amulet is bound during the pact");
        // Chest: four planks, then an iron ingot.
        onServer(p -> p.getInventory().setItem(7, new ItemStack(YokaiContent.AMULET)));
        UUID chest = spawn(Species.CHEST, at(-3, 0, 4));
        onServer(p -> ((ChestTsukumogami) p.level().getEntity(chest)).fillDemo());
        aimAt(chest);
        hand(new ItemStack(Items.IRON_INGOT));
        use();
        soft(!with(chest, e -> ((YokaiCreature) e).yokai().bonded()) && server(p -> p.getMainHandItem().getCount()) == 1, "Chest: an ingot before the repair is refused and kept");
        hand(new ItemStack(Items.OAK_PLANKS, 4));
        aimAt(chest);
        use();
        hand(new ItemStack(Items.IRON_INGOT));
        aimAt(chest);
        use();
        soft(with(chest, e -> ((YokaiCreature) e).yokai().bonded()), "Chest: repaired with four planks and given an ingot, it bonds");
        // Its storage opens for its owner.
        hand(ItemStack.EMPTY);
        ctx.getInput().holdShift();
        ctx.waitTicks(3);
        aimAt(chest);
        use();
        ctx.getInput().releaseShift();
        soft(until(() -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen), 40) >= 0, "Chest: the owner opens its 27 slots");
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        ctx.waitTicks(3);
        // Kappa: three visits with a sea pickle, a minute apart (shortened here).
        pool();
        onServer(p -> p.getInventory().setItem(6, new ItemStack(YokaiContent.AMULET)));
        UUID kappa = spawn(Species.KAPPA, at(0, -2, 18));
        cmd(sp, "tp @p 0.5 " + Y + " 13.5");
        ctx.waitTicks(5);
        for (int v = 0; v < 3; v++) {
            hand(new ItemStack(Items.SEA_PICKLE, 2));
            aimAt(kappa);
            use();
            if (v == 0) {
                use();
                soft(server(p -> p.getMainHandItem().getCount()) == 1, "Kappa: a visit too soon is refused without consuming");
            }
            ctx.waitTicks(YokaiBalance.Kappa.PACT_SPACING + 5);
        }
        soft(with(kappa, e -> ((YokaiCreature) e).yokai().bonded()), "Kappa: three spaced visits with sea pickles seal the pact");
        drain();
        home();
        // Ittan-momen: pinned by a snowball, tied with a ritual thread.
        onServer(p -> p.getInventory().setItem(5, new ItemStack(YokaiContent.AMULET)));
        UUID ittan = spawn(Species.ITTAN_MOMEN, at(0, 1, 4));
        onServer(p -> ((YokaiCreature) p.level().getEntity(ittan)).yokai().calm(1200));
        hand(new ItemStack(YokaiContent.RITUAL_THREAD));
        aimAt(ittan);
        use();
        soft(!with(ittan, e -> ((YokaiCreature) e).yokai().bonded()), "Ittan-momen: a thread without the snowball is refused");
        onServer(p -> {
            Entity it = p.level().getEntity(ittan);
            Snowball sb = new Snowball(p.level(), p, new ItemStack(Items.SNOWBALL));
            sb.setPos(it.position().add(0, 1.5, 0));
            sb.shoot(0, -1, 0, 0.6f, 0);
            p.level().addFreshEntity(sb);
        });
        int pinned = until(() -> with(ittan, e -> e instanceof IttanMomenEntity i && i.stunnedClient()), 40);
        soft(pinned >= 0, "Ittan-momen: a snowball pins it");
        ctx.waitTicks(10); // pinned, it drops to the ground: aim once it has landed
        aimAt(ittan);
        use();
        soft(with(ittan, e -> ((YokaiCreature) e).yokai().bonded()), "Ittan-momen: a ritual thread tied in time seals the pact");
        // Tanuki: three decoys, then an apple.
        onServer(p -> p.getInventory().setItem(4, new ItemStack(YokaiContent.AMULET)));
        UUID tan = spawn(Species.TANUKI, at(5, 0, -4));
        onServer(p -> { var r = ((YokaiCreature) p.level().getEntity(tan)).yokai().ritual(p); r.count = 2; });
        hand(new ItemStack(Items.APPLE));
        aimAt(tan);
        use();
        soft(!with(tan, e -> ((YokaiCreature) e).yokai().bonded()), "Tanuki: an apple before finding three decoys is refused");
        onServer(p -> { var r = ((YokaiCreature) p.level().getEntity(tan)).yokai().ritual(p); r.count = 3; });
        aimAt(tan);
        use();
        soft(with(tan, e -> ((YokaiCreature) e).yokai().bonded()), "Tanuki: three decoys found and an apple seal the pact");
        // Tengu: the route of three high points, then a feather.
        onServer(p -> p.getInventory().setItem(3, new ItemStack(YokaiContent.AMULET)));
        UUID tengu = spawn(Species.TENGU, at(-6, 0, -6));
        hand(ItemStack.EMPTY);
        aimAt(tengu);
        use();
        List<BlockPos> route = sp.getServer().computeOnServer(s -> {
            try {
                var f = ryuna.qdeath.entity.yokai.TenguEntity.class.getDeclaredField("routes");
                f.setAccessible(true);
                var map = (java.util.Map<?, ?>) f.get(s.overworld().getEntity(tengu));
                Object r = map.values().iterator().next();
                var pf = r.getClass().getDeclaredField("points");
                pf.setAccessible(true);
                @SuppressWarnings("unchecked") List<BlockPos> pts = (List<BlockPos>) pf.get(r);
                return new ArrayList<>(pts);
            } catch (Exception e) {
                return List.of();
            }
        });
        soft(route.size() == 3, "Tengu: asked with an empty hand, it marks a route of three points");
        for (BlockPos q : route) {
            cmd(sp, "tp @p " + (q.getX() + 0.5) + " " + q.getY() + " " + (q.getZ() + 0.5));
            ctx.waitTicks(6);
        }
        hand(new ItemStack(Items.FEATHER));
        cmd(sp, "tp @p -3.5 " + Y + " -3.5");
        ctx.waitTicks(5);
        aimAt(tengu);
        use();
        soft(with(tengu, e -> ((YokaiCreature) e).yokai().bonded()), "Tengu: the route completed and a feather seal the pact");
        // Bell: three warnings attended, then a copper ingot.
        onServer(p -> p.getInventory().setItem(2, new ItemStack(YokaiContent.AMULET)));
        parkCompanions(); // the companions following the player would stand in the way of the click
        home();
        UUID bell = spawn(Species.BELL, at(0, 0, -3));
        cmd(sp, "summon minecraft:zombie 0 " + Y + " -12 {NoAI:1b,PersistenceRequired:1b}");
        ctx.waitTicks(4 * (YokaiBalance.Bell.WARN_COOLDOWN + 20));
        hand(new ItemStack(Items.COPPER_INGOT));
        aimAt(bell);
        use();
        soft(with(bell, e -> ((YokaiCreature) e).yokai().bonded()), "Bell: three warnings attended and a copper ingot seal the pact (warnings attended: "
                + server(p -> p.level().getEntity(bell) instanceof YokaiCreature y && !y.yokai().bonded() ? y.yokai().ritual(p).count : -1) + ")");
        cmd(sp, "kill @e[type=zombie]");
        onServer(p -> p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof YokaiCreature y && y.yokai().bonded())
                .forEach(m -> ((YokaiCreature) m).yokai().order(YokaiCore.Order.FOLLOW)));
        // Orders: the companion screen; follow and stay; defend and passive need Orders.
        hand(ItemStack.EMPTY);
        aimAt(tan);
        use();
        soft(screen(CompanionScreen.class) >= 0, "right-clicking one's companion opens its orders");
        ctx.waitTicks(4);
        ctx.takeScreenshot("yokai_companion_screen");
        // Several companions crowd the player: check the one whose screen actually opened.
        int opened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CompanionScreen c ? c.entity() : -1);
        ctx.clickScreenButton("yokai.quantumdeath.companion.stay");
        ctx.waitTicks(4);
        soft(sp.getServer().computeOnServer(s -> s.overworld().getEntity(opened) instanceof YokaiCreature y && y.yokai().order() == YokaiCore.Order.STAY),
                "the Stay order reaches the companion");
        ctx.runOnClient(mc -> mc.setScreenAndShow(null));
        int tanId = with(tan, Entity::getId);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(tanId, "defend", "")));
        ctx.waitTicks(4);
        soft(with(tan, e -> ((YokaiCreature) e).yokai().order() == YokaiCore.Order.DEFEND), "with Orders, Defend is accepted");
        // Non-pactable creatures cannot be bound, and companions never attack their owner.
        soft(sp.getServer().computeOnServer(s -> { var o = EncounterDirector.spawnAt(s.overworld(), Species.ONI, at(8, 0, 8), EntitySpawnReason.COMMAND);
            boolean ok = !Bonds.pact(s.getPlayerList().getPlayers().getFirst(), o); o.discard(); return ok; }), "an Oni cannot be bound");
        cmd(sp, "yokai clear");
    }

    // ------------------------------------------------------------------------------------------------ amulets

    private void amulets() {
        clearArena();
        perceive(2);
        onServer(p -> { p.getInventory().clearContent(); SkillManager.progress(p).grant(SkillId.BOND_III); SkillManager.sync(p); });
        UUID chest = spawn(Species.CHEST, at(0, 0, 3));
        onServer(p -> {
            ChestTsukumogami c = (ChestTsukumogami) p.level().getEntity(chest);
            c.fillDemo();
            YokaiCommands.forcePact(p, c);
            c.setHealth(11);
            c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 2400, 0));
        });
        int slot = server(p -> { for (int i = 0; i < 36; i++) if (Bonds.data(p.getInventory().getItem(i)).state().equals(AmuletData.DEPLOYED)) return i; return -1; });
        soft(slot >= 0, "a demonstration pact binds an amulet");
        onServer(p -> { var a = p.getInventory().getItem(slot); p.getInventory().setItem(slot, ItemStack.EMPTY); p.getInventory().setItem(0, a); p.getInventory().setSelectedSlot(0); });
        // Refused in combat and when too far.
        onServer(p -> ((YokaiCreature) p.level().getEntity(chest)).yokai().markCombat());
        aimAt(chest);
        use();
        soft(entity(chest) != null && entity(chest).isAlive(), "storing is refused during combat");
        ctx.waitTicks(YokaiBalance.Companion.COMBAT_TICKS + 5);
        cmd(sp, "tp " + chest + " 0.5 " + Y + " 12.5");
        ctx.waitTicks(3);
        soft(sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            Bonds.useOn(p, p.getMainHandItem(), (Mob) s.overworld().getEntity(chest));
            return s.overworld().getEntity(chest) != null;
        }), "storing is refused beyond 6 blocks");
        cmd(sp, "tp " + chest + " 0.5 " + Y + " 3.5");
        ctx.waitTicks(3);
        // Stored: the entity is gone and the amulet holds it, health, items and effects included.
        aimAt(chest);
        float before = health(chest); // regeneration keeps healing it until it is stored
        use();
        soft(entity(chest) == null, "the companion goes into the amulet");
        ItemStack stored = server(p -> p.getMainHandItem().copy());
        soft(Bonds.data(stored).state().equals(AmuletData.STORED), "the amulet shows it as stored");
        // A copy of the stored amulet (a duplication attempt).
        onServer(p -> p.getInventory().setItem(1, stored.copy()));
        // Summon on a block face: back, unchanged.
        aim(new Vec3(0.5, Y, 4.5));
        use();
        UUID back = sp.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(ChestTsukumogami.class, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(10))
                .stream().findFirst().map(Entity::getUUID).orElse(null));
        soft(chest.equals(back), "summoned, it is the same companion (same identity)");
        soft(back != null && Math.abs(health(back) - before) <= 1 && with(back, e -> ((LivingEntity) e).hasEffect(MobEffects.REGENERATION)) && !with(back, e -> ((ChestTsukumogami) e).contents()).isEmpty(),
                "health, effects and inventory are preserved through the amulet");
        // The copy cannot summon a second one, and becomes void.
        onServer(p -> p.getInventory().setSelectedSlot(1));
        ctx.waitTicks(3);
        aim(new Vec3(-2.5, Y, 4.5));
        use();
        long chests = count(ChestTsukumogami.class);
        soft(chests == 1, "a copied amulet cannot summon a duplicate (" + chests + ")");
        // Amulets check the registry once a second (server ticks: under load slower than these client ticks).
        soft(until(() -> server(p -> Bonds.data(p.getInventory().getItem(1)).state().equals(AmuletData.LOST)), 100) >= 0, "the outdated copy shows the bond as lost");
        // Two summon requests in the same tick: at most one instance.
        onServer(p -> { p.getInventory().setSelectedSlot(0); });
        ctx.waitTicks(2);
        aimAt(back);
        use();
        ItemStack again = server(p -> p.getMainHandItem().copy());
        boolean single = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            ItemStack a = again.copy(), b = again.copy();
            boolean r1 = Bonds.summon(p, a, new BlockPos(0, Y - 1, 6), net.minecraft.core.Direction.UP);
            boolean r2 = Bonds.summon(p, b, new BlockPos(2, Y - 1, 6), net.minecraft.core.Direction.UP);
            return r1 != r2 && s.overworld().getEntitiesOfClass(ChestTsukumogami.class, p.getBoundingBox().inflate(10)).size() == 1;
        });
        soft(single, "simultaneous summons from two copies produce a single instance");
        cmd(sp, "kill @e[type=quantumdeath:chest_tsukumogami]");
        ctx.waitTicks(25);
        // Invalid place: inside stone it is refused and nothing is lost.
        UUID ittan = spawn(Species.ITTAN_MOMEN, at(0, 1, 3));
        onServer(p -> { YokaiCommands.forcePact(p, (Mob) p.level().getEntity(ittan)); });
        int ia = server(p -> { for (int i = 0; i < 36; i++) if (Bonds.data(p.getInventory().getItem(i)).state().equals(AmuletData.DEPLOYED) && Bonds.data(p.getInventory().getItem(i)).species().equals("ittan_momen")) return i; return -1; });
        onServer(p -> { var a = p.getInventory().getItem(ia); p.getInventory().setItem(ia, ItemStack.EMPTY); p.getInventory().setItem(0, a); p.getInventory().setSelectedSlot(0); });
        aimAt(ittan);
        use();
        soft(entity(ittan) == null, "the Ittan-momen is stored");
        cmd(sp, "fill -1 " + Y + " 5 1 " + (Y + 3) + " 7 minecraft:stone");
        boolean refused = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            return !Bonds.summon(p, p.getMainHandItem(), new BlockPos(0, Y + 1, 6), net.minecraft.core.Direction.UP) && Bonds.data(p.getMainHandItem()).state().equals(AmuletData.STORED);
        });
        soft(refused, "a summon into a solid place is refused and the companion stays safe in the amulet");
        cmd(sp, "fill -1 " + Y + " 5 1 " + (Y + 3) + " 7 minecraft:air");
        // Destroying a stored amulet loses the companion for good.
        UUID bond = server(p -> Bonds.data(p.getMainHandItem()).bond().orElse(null));
        onServer(p -> {
            ItemStack a = p.getMainHandItem().copy();
            p.getInventory().setItem(0, ItemStack.EMPTY);
            ItemEntity it = new ItemEntity(p.level(), p.getX(), p.getY() + 2, p.getZ() + 2, a);
            p.level().addFreshEntity(it);
            it.discard();
        });
        ctx.waitTicks(3);
        soft(sp.getServer().computeOnServer(s -> Bonds.get(s, bond).state().equals(Bonds.LOST)), "destroying a stored amulet loses that companion permanently");
        // Death is permanent: the amulet empties and shows the lost bond, reusable for a new pact.
        UUID tan = spawn(Species.TANUKI, at(3, 0, 3));
        onServer(p -> { p.getInventory().clearContent(); YokaiCommands.forcePact(p, (Mob) p.level().getEntity(tan)); });
        cmd(sp, "kill " + tan);
        ctx.waitTicks(30);
        soft(server(p -> { for (ItemStack s : p.getInventory()) if (Bonds.data(s).state().equals(AmuletData.LOST)) return true; return false; }),
                "when a companion dies its amulet shows \"bond lost\"");
        UUID kod2 = spawn(Species.TANUKI, at(-3, 0, 3));
        soft(sp.getServer().computeOnServer(s -> YokaiCommands.forcePact(s.getPlayerList().getPlayers().getFirst(), (Mob) s.overworld().getEntity(kod2)))
                && server(p -> { int n = 0; for (ItemStack s : p.getInventory()) if (s.is(YokaiContent.AMULET)) n++; return n; }) == 1,
                "the emptied amulet is reused for a new pact");
        // A lost (dropped) deployed amulet does not kill the companion; a replacement rebinds it and voids the old one.
        ItemStack oldAmulet = server(p -> { for (ItemStack s : p.getInventory()) if (s.is(YokaiContent.AMULET)) return s.copy(); return ItemStack.EMPTY; });
        onServer(p -> p.getInventory().clearContent());
        hand(new ItemStack(YokaiContent.AMULET));
        aimAt(kod2);
        use();
        soft(entity(kod2) != null && server(p -> Bonds.data(p.getMainHandItem()).state().equals(AmuletData.DEPLOYED)), "a replacement amulet rebinds the living companion");
        onServer(p -> p.getInventory().setItem(5, oldAmulet.copy()));
        soft(until(() -> server(p -> Bonds.data(p.getInventory().getItem(5)).state().equals(AmuletData.LOST)), 100) >= 0, "the replaced amulet is void");
        // Resonance: the panel and the low-health warning.
        onServer(p -> ((LivingEntity) p.level().getEntity(kod2)).setHealth(2));
        soft(until(() -> !ClientYokai.companions.isEmpty(), 100) >= 0, "Resonance: the client knows its companions");
        hideChat();
        ctx.takeScreenshot("yokai_resonance_hud");
        cmd(sp, "kill @e[type=quantumdeath:tanuki]");
        cmd(sp, "yokai clear");
    }

    // ------------------------------------------------------------------------------------------------ tracking

    /** Puts a fake player in (or takes it out of) the server's player list: a second "connected" player for tracking. */
    @SuppressWarnings("unchecked")
    private static void listed(net.minecraft.server.MinecraftServer s, ServerPlayer p, boolean in) {
        try {
            var pl = net.minecraft.server.players.PlayerList.class;
            var players = pl.getDeclaredField("players");
            var byUuid = pl.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            byUuid.setAccessible(true);
            List<ServerPlayer> list = (List<ServerPlayer>) players.get(s.getPlayerList());
            java.util.Map<UUID, ServerPlayer> map = (java.util.Map<UUID, ServerPlayer>) byUuid.get(s.getPlayerList());
            if (in) { list.add(p); map.put(p.getUUID(), p); }
            else { list.remove(p); map.remove(p.getUUID()); }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private String eyeStatus(UUID eye) { return with(eye, e -> e instanceof SwarmEyeEntity s ? s.trackStatus() : "gone"); }

    private void tracking() {
        clearArena();
        perceive(2);
        onServer(p -> { SkillManager.progress(p).grant(SkillId.BOND_III); SkillManager.sync(p); });
        UUID eye = spawn(Species.SWARM_EYE, at(0, 1.5, 3));
        onServer(p -> YokaiCommands.forcePact(p, (Mob) p.level().getEntity(eye)));
        int forced0 = sp.getServer().computeOnServer(s -> s.overworld().getForceLoadedChunks().size());
        FakePlayer target = sp.getServer().computeOnServer(s -> {
            FakePlayer f = FakePlayer.get(s.overworld(), new GameProfile(UUID.nameUUIDFromBytes("yokai-target".getBytes()), "Target"));
            f.snapTo(6.5, Y, 8.5, 0, 0);
            s.overworld().addFreshEntity(f);
            listed(s, f, true);
            return f;
        });
        ctx.waitTicks(5);
        int eyeId = with(eye, Entity::getId);
        // Out of reach (40 blocks): refused.
        onServer(p -> target.snapTo(40.5, Y, 8.5, 0, 0));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "track", "Target")));
        ctx.waitTicks(5);
        soft(!with(eye, e -> ((SwarmEyeEntity) e).tracking()), "tracking refuses a target beyond 32 blocks of the eye");
        onServer(p -> target.snapTo(6.5, Y, 8.5, 0, 0));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "track", "Target")));
        soft(until(() -> with(eye, e -> ((SwarmEyeEntity) e).tracking()) && eyeStatus(eye).equals("observing"), 100) >= 0, "a connected player in view within 32 blocks can be tracked");
        soft(ClientYokai.tracking.active() && ClientYokai.tracking.x() == 6 && ClientYokai.tracking.z() == 8, "the owner sees the observed coordinates");
        hideChat();
        ctx.takeScreenshot("yokai_tracking_hud");
        soft(server(p -> { for (ItemStack s : p.getInventory()) if (s.is(YokaiContent.AMULET)) { Bonds.useOn(p, s, (Mob) p.level().getEntity(eye)); break; } return p.level().getEntity(eye) != null; }),
                "a tracking eye cannot be stored");
        // Behind a wall: it searches the last place; the target's new position is never reported.
        cmd(sp, "fill 3 " + Y + " 14 12 " + (Y + 6) + " 14 minecraft:stone");
        onServer(p -> target.snapTo(8.5, Y, 20.5, 0, 0));
        ctx.waitTicks(45);
        soft(eyeStatus(eye).equals("searching"), "losing sight, the eye searches (" + eyeStatus(eye) + ")");
        soft(ClientYokai.tracking.z() == 8, "while it cannot see the target, only the last observed position is reported (" + ClientYokai.tracking.z() + ")");
        ctx.waitTicks(YokaiBalance.SwarmEye.TRACK_SEARCH + 40);
        soft(eyeStatus(eye).equals("lost"), "after the search the trail is lost (" + eyeStatus(eye) + ")");
        cmd(sp, "fill 3 " + Y + " 14 12 " + (Y + 6) + " 14 minecraft:air");
        // Disconnection freezes the last observation.
        onServer(p -> target.snapTo(6.5, Y, 8.5, 0, 0));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "untrack", "")));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "track", "Target")));
        ctx.waitTicks(25);
        sp.getServer().runOnServer(s -> listed(s, target, false));
        until(() -> eyeStatus(eye).equals("offline"), 100);
        soft(eyeStatus(eye).equals("offline"), "a target that disconnects freezes the observation (" + eyeStatus(eye) + ")");
        // A change of dimension freezes it too, and the eye never follows.
        sp.getServer().runOnServer(s -> listed(s, target, true));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "untrack", "")));
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "track", "Target")));
        ctx.waitTicks(25);
        boolean moved = sp.getServer().computeOnServer(s -> {
            try {
                ServerLevel limbo = s.getLevel(HellLayer.VESTIBULE.level);
                target.teleport(new net.minecraft.world.level.portal.TeleportTransition(limbo, new Vec3(0.5, 100, 0.5), Vec3.ZERO, 0, 0,
                        java.util.Set.of(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                return target.level() == limbo;
            } catch (Exception e) {
                Quantumdeath.LOGGER.warn("fake player dimension change failed", e);
                return false;
            }
        });
        ctx.waitTicks(25);
        soft(moved && eyeStatus(eye).equals("dimension"), "a target that changes dimension freezes the observation (" + eyeStatus(eye) + ")");
        soft(with(eye, e -> e.level().dimension() == Level.OVERWORLD), "the eye never follows across dimensions");
        // A search where the last place is no longer loaded freezes rather than loading it.
        boolean unloaded = sp.getServer().computeOnServer(s -> {
            try {
                SwarmEyeEntity se = (SwarmEyeEntity) s.overworld().getEntity(eye);
                var last = SwarmEyeEntity.class.getDeclaredField("trackLast");
                var status = SwarmEyeEntity.class.getDeclaredField("trackStatus");
                var until = SwarmEyeEntity.class.getDeclaredField("searchUntil");
                last.setAccessible(true);
                status.setAccessible(true);
                until.setAccessible(true);
                listed(s, target, false);
                target.teleport(new net.minecraft.world.level.portal.TeleportTransition(s.overworld(), new Vec3(6.5, Y, 8.5), Vec3.ZERO, 0, 0,
                        java.util.Set.of(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                listed(s, target, true);
                last.set(se, new Vec3(30000.5, 70, 30000.5));
                status.set(se, "searching");
                until.set(se, s.overworld().getGameTime() + 400);
                return true;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        });
        ctx.waitTicks(25);
        soft(unloaded && eyeStatus(eye).equals("unloaded"), "searching an unloaded place freezes the observation (" + eyeStatus(eye) + ")");
        soft(sp.getServer().computeOnServer(s -> s.overworld().getForceLoadedChunks().size()) == forced0
                && !sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().hasChunk(30000 >> 4, 30000 >> 4)), "tracking never loads or forces chunks");
        sp.getServer().runOnServer(s -> { listed(s, target, false); target.discard(); });
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(eyeId, "untrack", "")));
        ctx.waitTicks(3);
        cmd(sp, "kill @e[type=quantumdeath:swarm_eye]");
        cmd(sp, "yokai clear");
    }

    // ------------------------------------------------------------------------------------------------ mount

    private void mount() {
        clearArena();
        perceive(2);
        onServer(p -> { SkillManager.progress(p).grant(SkillId.BOND_III); SkillManager.sync(p); });
        cmd(sp, "fill -3 " + (Y + 20) + " -3 3 " + (Y + 20) + " 3 minecraft:stone");
        cmd(sp, "tp @p 0.5 " + (Y + 21) + " 0.5 0 0");
        settle(ctx, sp, 10);
        UUID ittan = spawn(Species.ITTAN_MOMEN, at(0, 21, 1));
        onServer(p -> YokaiCommands.forcePact(p, (Mob) p.level().getEntity(ittan)));
        int id = with(ittan, Entity::getId);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new YokaiNet.Order(id, "ride", "")));
        int riding = until(() -> ctx.computeOnClient(mc -> mc.player.getVehicle() != null), 40);
        soft(riding >= 0, "the owner mounts the Ittan-momen");
        // Off the edge, forward: it glides, never climbs, never too fast.
        ctx.getInput().holdKey(o -> o.keyUp);
        double maxUp = -1, maxH = 0, minDown = 1;
        double lastY = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0 : mc.player.getVehicle().getY());
        for (int i = 0; i < 80; i++) {
            ctx.waitTick();
            double[] v = ctx.computeOnClient(mc -> {
                Entity veh = mc.player.getVehicle();
                if (veh == null) return new double[]{0, 0, 0, 0};
                Vec3 d = veh.getDeltaMovement();
                return new double[]{veh.getY(), Math.sqrt(d.x * d.x + d.z * d.z), veh.onGround() ? 1 : 0, 1};
            });
            if (v[3] == 0) break;
            if (v[2] == 0 && i > 10) {
                maxUp = Math.max(maxUp, v[0] - lastY);
                minDown = Math.min(minDown, lastY - v[0]);
                maxH = Math.max(maxH, v[1]);
            }
            lastY = v[0];
        }
        ctx.getInput().releaseKey(o -> o.keyUp);
        soft(maxUp <= 1e-3, "gliding never climbs (" + maxUp + ")");
        soft(maxH <= YokaiBalance.IttanMomen.MAX_HORIZONTAL / 20.0 + 0.02, "gliding never exceeds 6 blocks per second (" + maxH * 20 + ")");
        soft(minDown >= YokaiBalance.IttanMomen.MIN_DESCENT / 20.0 - 0.006, "airborne it always sinks at least 0.5 blocks per second (" + minDown * 20 + ")");
        ctx.takeScreenshot("yokai_mount");
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(5);
        cmd(sp, "fill -3 " + (Y + 20) + " -3 3 " + (Y + 20) + " 3 minecraft:air");
        cmd(sp, "yokai clear");
        cmd(sp, "kill @e[type=quantumdeath:ittan_momen]");
        home();
    }

    // ------------------------------------------------------------------------------------------------ reload

    private UUID reloadDeployed, reloadBond;

    private void beforeReload() {
        clearArena();
        perceive(2);
        reloadDeployed = spawn(Species.TANUKI, at(2, 0, 2));
        onServer(p -> YokaiCommands.forcePact(p, (Mob) p.level().getEntity(reloadDeployed)));
        UUID kappa = spawn(Species.KAPPA, at(-2, 0, 2));
        onServer(p -> YokaiCommands.forcePact(p, (Mob) p.level().getEntity(kappa)));
        reloadBond = with(kappa, e -> ((YokaiCreature) e).yokai().bondId());
        onServer(p -> {
            for (ItemStack s : p.getInventory())
                if (Bonds.data(s).bond().map(b -> b.equals(reloadBond)).orElse(false)) Bonds.useOn(p, s, (Mob) p.level().getEntity(kappa));
        });
        ctx.waitTicks(5);
        soft(entity(kappa) == null, "before the restart one companion is stored");
    }

    private void afterReload() {
        soft(sp.getServer().computeOnServer(Awakening::awake), "the awakening survives a restart");
        soft(entity(reloadDeployed) != null && with(reloadDeployed, e -> ((YokaiCreature) e).yokai().bonded()), "a deployed companion is still bonded after a restart");
        soft(sp.getServer().computeOnServer(s -> Bonds.get(s, reloadBond).state().equals(Bonds.STORED)), "a stored companion is still stored after a restart");
        soft(server(p -> SkillManager.progress(p).isUnlocked(SkillId.PERCEPTION_II) || !phase("gui")), "learned perception survives a restart");
        boolean summoned = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            for (ItemStack st : p.getInventory())
                if (Bonds.data(st).bond().map(b -> b.equals(reloadBond)).orElse(false))
                    return Bonds.summon(p, st, new BlockPos(0, Y - 1, 3), net.minecraft.core.Direction.UP);
            return false;
        });
        soft(summoned, "after a restart the stored companion is summoned from its amulet");
    }

    // ------------------------------------------------------------------------------------------------ dedicated server

    private void dedicated() {
        Properties props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("level-seed", "777");
        props.setProperty("pvp", "true");
        // A normal world with structures (the default test world is flat, without villages); still no natural mobs.
        var builder = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> {
            c.setSeed("777");
            c.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
            c.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
            c.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
        });
        try (TestDedicatedServerContext server = builder.createServer(props)) {
            server.runCommand("op Player0");
            try (var connection = server.connect()) {
                connection.waitForChunksRender(200);
                server.runCommand("deathmask vigil pause");
                server.runCommand("gamerule spawn_monsters false");
                server.runCommand("yokai awaken Player0");
                server.runCommand("yokai director pause");
                server.runCommand("yokai grace skip");
                server.runCommand("forceload add -16 -16 16 16");
                server.runCommand("fill -12 150 -12 12 150 12 minecraft:stone");
                server.runCommand("fill -12 151 -12 12 166 12 minecraft:air"); // a normal world: clear any hill or tree
                server.runCommand("tp Player0 0.5 151 0.5");
                ctx.waitTicks(20);
                // Two players, different perception, the same Oni.
                server.runCommand("yokai perception 2 Player0");
                boolean pair = server.computeOnServer(s -> {
                    ServerLevel level = s.overworld();
                    ServerPlayer real = s.getPlayerList().getPlayers().getFirst();
                    FakePlayer blind = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes("blind".getBytes()), "Blind"));
                    blind.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    blind.setPos(3.5, 151, 0.5);
                    YokaiPlayer.set(blind, YokaiPlayer.of(blind).withOverride(0));
                    Mob oni = EncounterDirector.spawnAt(level, Species.ONI, new Vec3(0.5, 151, 5.5), EntitySpawnReason.COMMAND);
                    YokaiCore c = ((YokaiCreature) oni).yokai();
                    c.detect(real);
                    c.detect(blind);
                    boolean rules = c.mayPursue(real) && !c.mayPursue(blind) && Perception.view(real, oni) == 2 && Perception.view(blind, oni) == 0;
                    float h = oni.getHealth();
                    oni.hurtServer(level, level.damageSources().playerAttack(blind), 5);
                    boolean blindMiss = oni.getHealth() == h;
                    oni.hurtServer(level, level.damageSources().playerAttack(real), 5);
                    boolean realHit = oni.getHealth() < h;
                    // A shrieking eye passes the position on, but never turns a neutral kappa against anyone.
                    Mob kappa = EncounterDirector.spawnAt(level, Species.KAPPA, new Vec3(-3.5, 151, 3.5), EntitySpawnReason.COMMAND);
                    boolean neutral = kappa.getTarget() == null;
                    oni.discard();
                    kappa.discard();
                    return rules && blindMiss && realHit && neutral;
                });
                soft(pair, "dedicated server: per-player perception decides targeting and damage for the same creature");
                ctx.waitTicks(10);
                soft(ClientYokai.perception == 2, "dedicated server: perception reaches the client");
                // Many companions: no hidden cap, no forced chunks.
                int forced = server.computeOnServer(s -> s.overworld().getForceLoadedChunks().size());
                int made = server.computeOnServer(s -> {
                    ServerLevel level = s.overworld();
                    ServerPlayer real = s.getPlayerList().getPlayers().getFirst();
                    int n = 0;
                    for (int i = 0; i < 48; i++) {
                        Mob m = EncounterDirector.spawnAt(level, i % 2 == 0 ? Species.TANUKI : Species.KAPPA, new Vec3(-10.5 + (i % 8) * 3, 151, -10.5 + (i / 8) * 3), EntitySpawnReason.COMMAND);
                        if (m != null && YokaiCommands.forcePact(real, m)) n++;
                        // 48 amulets do not fit in an inventory: the player keeps the deployed ones in a chest.
                        for (int k = 0; k < real.getInventory().getContainerSize(); k++)
                            if (Bonds.data(real.getInventory().getItem(k)).state().equals(AmuletData.DEPLOYED)) real.getInventory().setItem(k, ItemStack.EMPTY);
                    }
                    return n;
                });
                ctx.waitTicks(100);
                int alive = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(-30, 140, -30, 30, 170, 30),
                        m -> m instanceof YokaiCreature y && y.yokai().bonded()).size());
                soft(made == 48 && alive == 48, "dedicated server: 48 companions bonded and active, no cap (" + made + ", " + alive + ")");
                soft(server.computeOnServer(s -> s.overworld().getForceLoadedChunks().size()) == forced, "dedicated server: companions force no chunk loading");
                double mspt = server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1_000_000.0);
                soft(mspt < 50, "dedicated server: the tick stays under 50 ms with 48 companions (" + String.format("%.1f", mspt) + " ms)");
                ctx.waitTicks(10);
                soft(ClientYokai.companions.size() == 48, "dedicated server: the owner's client lists every companion (" + ClientYokai.companions.size() + ")");
            }
            // The owner disconnected: companions rest (no target, no movement orders).
            ctx.waitTicks(20);
            boolean resting = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(-30, 140, -30, 30, 170, 30),
                    m -> m instanceof YokaiCreature y && y.yokai().bonded() && m.getTarget() != null).isEmpty());
            soft(resting, "dedicated server: with the owner away, companions rest");
            villageBells(server);
        }
    }

    /** World generation marks a real village's bell as natural; it wakes and goes back to its place, never copied. */
    private void villageBells(TestDedicatedServerContext server) {
        BlockPos village = server.computeOnServer(s -> s.overworld().findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, BlockPos.ZERO, 100, false));
        soft(village != null, "dedicated server: a village exists in the normal world");
        if (village == null) return;
        int cx = village.getX() >> 4, cz = village.getZ() >> 4;
        server.runCommand("forceload add " + ((cx - 3) << 4) + " " + ((cz - 3) << 4) + " " + ((cx + 3) << 4) + " " + ((cz + 3) << 4));
        BlockPos bell = null;
        for (int t = 0; t < 90 && bell == null; t++) {
            ctx.waitTicks(20);
            bell = server.computeOnServer(s -> {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    var ch = s.overworld().getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) if (be instanceof BellBlockEntity && Provenance.natural(be)) return be.getBlockPos();
                }
                return null;
            });
        }
        soft(bell != null, "dedicated server: world generation marks a village bell as natural");
        if (bell != null) {
            BlockPos at = bell;
            boolean woke = server.computeOnServer(s -> BellTsukumogami.awaken(s.overworld(), at) != null && s.overworld().getBlockState(at).isAir());
            soft(woke, "dedicated server: a natural bell wakes into a tsukumogami");
            // What /yokai clear does (the console has no player to centre it on): removed, the spirit puts its bell back.
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(BellTsukumogami.class, new net.minecraft.world.phys.AABB(at).inflate(32)).forEach(Entity::discard));
            ctx.waitTicks(5);
            // Villagers toss food around: only a bell item would be a copy.
            soft(server.computeOnServer(s -> s.overworld().getBlockState(at).is(Blocks.BELL)), "dedicated server: the bell goes back to its place");
            soft(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(16),
                    it -> it.getItem().is(Items.BELL)).isEmpty()), "dedicated server: the bell is never copied");
        }
        server.runCommand("forceload remove all");
    }
}
