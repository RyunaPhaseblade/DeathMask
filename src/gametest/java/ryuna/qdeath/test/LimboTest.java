package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.entity.boss.HellBoss;
import ryuna.qdeath.entity.limbo.CrierEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.WalkingOssuaryEntity;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellProgress;
import ryuna.qdeath.limbo.BreathClues;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboCommands;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.limbo.plague.PlagueDirector;
import ryuna.qdeath.limbo.plague.PlagueState;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static ryuna.qdeath.test.TestUtil.cmd;
import static ryuna.qdeath.test.TestUtil.settle;

/**
 * The Limbo: screenshots of the arrival, the four biomes, the structures, the bestiary and every announced pose, and
 * functional checks of the redesign's acceptance scenarios. Failures are collected and reported together at the end.
 */
public class LimboTest implements FabricClientGameTest {
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext sp;
    private ClientGameTestContext ctx;
    private BlockPos arena;

    private void soft(boolean ok, String what) {
        if (ok) Quantumdeath.LOGGER.info("LIMBO TEST OK: {}", what);
        else { Quantumdeath.LOGGER.error("LIMBO TEST FAILED: {}", what); failures.add(what); }
    }

    private <T> T server(Function<net.minecraft.server.MinecraftServer, T> f) { return sp.getServer().computeOnServer(f::apply); }
    private static ServerPlayer player(net.minecraft.server.MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }
    private static ServerLevel limbo(net.minecraft.server.MinecraftServer s) { return s.getLevel(HellLayer.VESTIBULE.level); }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("quantumdeath.skipLimboTest")) return;
        ryuna.qdeath.limbo.LimboTuning.digging = false; // a random dig would stop a measured runner
        ctx = context;
        try (TestSingleplayerContext single = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setSeed("666"); s.setGenerateStructures(true); s.setAllowCommands(true); })
                .create()) {
            sp = single;
            settle(ctx, sp, 40);
            cmd(sp, "deathmask vigil pause");
            cmd(sp, "deathmask limbo processions off");
            cmd(sp, "difficulty normal");
            ctx.getInput().pressKey(o -> o.keyToggleGui);
            boolean quick = Boolean.getBoolean("quantumdeath.limboQuick"); // functional checks only
            if (!quick) { arrivalAndBiomes(); structures(); }
            else { cmd(sp, "deathmask tp vestibule"); settle(ctx, sp, 100); }
            prepareArena();
            if (!quick) bestiary();
            survival();
            states();
            crier();
            ossuary();
            siege();
            consecration();
            lure();
            tollKeeper();
            cmd(sp, "gamerule spawn_monsters true");
            plague();
        }
        if (!failures.isEmpty()) throw new AssertionError("Limbo test failures: " + failures);
    }

    // ------------------------------------------------------------------ visual tour
    private void arrivalAndBiomes() {
        cmd(sp, "deathmask tp vestibule");
        settle(ctx, sp, 100);
        cmd(sp, "deathmask limbo intro");
        ctx.waitTicks(40);
        ctx.getInput().lookAt(0, 4);
        ctx.waitTicks(5);
        ctx.takeScreenshot("limbo_arrival_north");
        ctx.getInput().lookAt(180, 4);
        ctx.waitTicks(5);
        ctx.takeScreenshot("limbo_arrival_south");
        for (String b : LimboCommands.BIOMES) {
            cmd(sp, "deathmask limbo biome " + b);
            settle(ctx, sp, 80);
            boolean in = server(s -> limbo(s).getBiome(player(s).blockPosition()).is(ResourceKey.create(Registries.BIOME, Quantumdeath.id(b))));
            soft(in, "biome " + b + " generates and can be reached");
            cmd(sp, "tp @p ~ ~6 ~");
            ctx.waitTicks(10);
            for (int yaw : new int[]{30, 210}) {
                ctx.getInput().lookAt(yaw, 12);
                ctx.waitTicks(10);
                ctx.takeScreenshot("biome_" + b + "_" + yaw);
            }
        }
    }

    private void structures() {
        for (String s : LimboCommands.STRUCTURES) {
            BlockPos before = server(x -> player(x).blockPosition());
            cmd(sp, "deathmask limbo structure " + s);
            settle(ctx, sp, 80);
            BlockPos after = server(x -> player(x).blockPosition());
            soft(!after.equals(before), "structure " + s + " is generated and located");
            cmd(sp, "tp @p ~ ~10 ~");
            ctx.waitTicks(5);
            ctx.getInput().lookAt(0, 45);
            ctx.waitTicks(15);
            ctx.takeScreenshot("structure_" + s);
        }
    }

    private static final String[] DEAD = {"hollow", "runaway", "crier", "crawler", "sundered", "walking_ossuary", "mourning_hound", "wake_bearer"};

    /** The bestiary on the flat test ground (no night vision: the Limbo's own light). */
    private void bestiary() {
        BlockPos p = arena.north(4);
        String y = String.valueOf(p.getY());
        cmd(sp, "kill @e[type=!minecraft:player,distance=..200]");
        for (int i = 0; i < DEAD.length; i++) {
            int x = p.getX() - 17 + i * 5;
            cmd(sp, "summon quantumdeath:" + DEAD[i] + " " + x + " " + y + " " + p.getZ() + " {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }
        cmd(sp, "tp @p " + (p.getX() - 8) + " " + (p.getY() + 1) + " " + (p.getZ() + 9) + " 180 10");
        settle(ctx, sp, 30);
        ctx.takeScreenshot("bestiary_left");
        cmd(sp, "tp @p " + (p.getX() + 9) + " " + (p.getY() + 1) + " " + (p.getZ() + 9) + " 180 10");
        settle(ctx, sp, 20);
        ctx.takeScreenshot("bestiary_right");
        cmd(sp, "kill @e[type=!minecraft:player]");
        // Every announced pose, one creature at a time, seen from the front and from the side.
        int[][] poses = {{0, LimboDead.ANIM_ATTACK, 11}, {0, LimboDead.ANIM_CALL, 10}, {0, LimboDead.ANIM_RECOGNIZE, 8}, {1, LimboDead.ANIM_SPECIAL, 8},
                {2, LimboDead.ANIM_CALL, 40}, {2, LimboDead.ANIM_CALL, 47}, {3, LimboDead.ANIM_ATTACK, 10}, {4, LimboDead.ANIM_ATTACK, 14},
                {5, LimboDead.ANIM_ATTACK, 15}, {5, LimboDead.ANIM_SPECIAL, 10}, {6, LimboDead.ANIM_ATTACK, 9}, {7, LimboDead.ANIM_ATTACK, 20},
                {7, LimboDead.ANIM_SPECIAL, 25}, {0, LimboDead.ANIM_EMERGE, 12}};
        for (int[] pose : poses) {
            String id = DEAD[pose[0]];
            cmd(sp, "summon quantumdeath:" + id + " " + p.getX() + " " + y + " " + p.getZ() + " {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            cmd(sp, "tp @p " + (p.getX() + 2.5) + " " + p.getY() + " " + (p.getZ() + 3.5) + " 145 8");
            ctx.waitTicks(10);
            int anim = pose[1], length = anim == LimboDead.ANIM_CALL && pose[0] == 2 ? 50 : 30;
            sp.getServer().runOnServer(s -> limbo(s).getEntitiesOfClass(LimboDead.class, new AABB(p).inflate(4)).forEach(d -> d.playAnim(anim, length)));
            ctx.waitTicks(pose[2]);
            ctx.takeScreenshot("pose_" + id + "_" + anim + "_" + pose[2]);
            sp.getServer().runOnServer(s -> limbo(s).getEntitiesOfClass(LimboDead.class, new AABB(p).inflate(6)).forEach(Entity::discard));
            ctx.waitTicks(5);
        }
    }

    // ------------------------------------------------------------------ functional checks
    /** A flat, lit test ground in the Limbo, away from structures; the player in survival, unharmable. */
    private void prepareArena() {
        arena = server(s -> player(s).blockPosition().offset(60, 0, 0));
        BlockPos a = arena;
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + a.getX() + " " + (a.getY() + 20) + " " + a.getZ());
        ctx.waitTicks(20);
        cmd(sp, "fill " + (a.getX() - 22) + " " + (a.getY() - 1) + " " + (a.getZ() - 22) + " " + (a.getX() + 22) + " " + (a.getY() - 1) + " " + (a.getZ() + 22) + " quantumdeath:ashen_bricks");
        cmd(sp, "fill " + (a.getX() - 22) + " " + a.getY() + " " + (a.getZ() - 22) + " " + (a.getX() + 22) + " " + (a.getY() + 7) + " " + (a.getZ() + 22) + " minecraft:air");
        reset();
        settle(ctx, sp, 40);
    }

    private void survival() {
        cmd(sp, "gamerule spawn_monsters false"); // only the dead each check places
        reset();
        cmd(sp, "gamemode survival");
        cmd(sp, "effect give @p minecraft:resistance infinite 4 true");
        cmd(sp, "effect give @p minecraft:saturation infinite 0 true");
        settle(ctx, sp, 20);
    }

    private void reset() {
        BlockPos a = arena;
        cmd(sp, "kill @e[type=!minecraft:player,distance=..200]");
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + a.getX() + " " + a.getY() + " " + a.getZ() + " 0 0");
        ctx.waitTicks(10);
    }

    private UUID spawn(EntityType<? extends LimboDead> type, BlockPos at, float yaw) {
        return server(s -> {
            LimboDead d = type.create(limbo(s), EntitySpawnReason.COMMAND);
            d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
            d.setYHeadRot(yaw);
            d.setYBodyRot(yaw);
            d.finalizeSpawn(limbo(s), limbo(s).getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
            d.setPersistenceRequired();
            limbo(s).addFreshEntity(d);
            return d.getUUID();
        });
    }

    private LimboDead dead(net.minecraft.server.MinecraftServer s, UUID id) {
        Entity e = limbo(s).getEntity(id);
        return e instanceof LimboDead d ? d : null;
    }

    private Mind mind(UUID id) { return server(s -> { LimboDead d = dead(s, id); return d == null ? null : d.mind(); }); }

    /** Rest -> recognition -> pursuit while the player is in view; search -> dispersal -> rest once it is gone. */
    private void states() {
        reset();
        BlockPos a = arena;
        UUID h = spawn(HellEntities.HOLLOW, a.south(9), 180f);
        Set<Mind> seen = EnumSet.noneOf(Mind.class);
        for (int i = 0; i < 100 && !seen.contains(Mind.PURSUIT); i++) { ctx.waitTicks(2); Mind m = mind(h); if (m != null) seen.add(m); }
        soft(seen.contains(Mind.RECOGNITION) && seen.contains(Mind.PURSUIT), "a Hollow that sees a living player recognizes, then pursues " + seen);
        ctx.takeScreenshot("state_pursuit");
        // Crouching quiets steps but never hides from sight.
        boolean quiet = server(s -> { ServerPlayer p = player(s); p.setShiftKeyDown(true); double r = LimboSenses.noiseRadius(p); p.setShiftKeyDown(false); return r < LimboTuning.NOISE_WALK; });
        soft(quiet, "crouching reduces movement noise");
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + (a.getX() - 20) + " " + a.getY() + " " + (a.getZ() - 20));
        cmd(sp, "setblock " + (a.getX() - 19) + " " + a.getY() + " " + (a.getZ() - 20) + " minecraft:stone");
        cmd(sp, "fill " + (a.getX() - 19) + " " + a.getY() + " " + (a.getZ() - 21) + " " + (a.getX() - 19) + " " + (a.getY() + 2) + " " + (a.getZ() - 18) + " minecraft:stone");
        cmd(sp, "fill " + (a.getX() - 21) + " " + a.getY() + " " + (a.getZ() - 19) + " " + (a.getX() - 18) + " " + (a.getY() + 2) + " " + (a.getZ() - 19) + " minecraft:stone");
        seen.clear();
        List<Mind> order = new ArrayList<>();
        for (int i = 0; i < 300 && !(seen.contains(Mind.DISPERSAL) && order.getLast() == Mind.REST); i++) {
            ctx.waitTicks(4);
            Mind m = mind(h);
            if (m != null && (order.isEmpty() || order.getLast() != m)) order.add(m);
            if (m != null) seen.add(m);
        }
        soft(seen.contains(Mind.SEARCH) && seen.contains(Mind.DISPERSAL) && !order.isEmpty() && order.getLast() == Mind.REST,
                "losing the target leads to search, dispersal and back to rest " + order);
        cmd(sp, "fill " + (a.getX() - 21) + " " + a.getY() + " " + (a.getZ() - 21) + " " + (a.getX() - 18) + " " + (a.getY() + 2) + " " + (a.getZ() - 18) + " minecraft:air");
    }

    /** A Crier's completed call sends nearby dead to search where it saw life, without handing them the target; a hit during the inhale loses it. */
    private void crier() {
        for (boolean interrupt : new boolean[]{false, true}) {
            reset();
            BlockPos a = arena;
            List<UUID> hollows = new ArrayList<>();
            for (int i = -1; i <= 1; i++) hollows.add(spawn(HellEntities.HOLLOW, a.offset(i * 4, 0, 20), 0f));
            UUID c = spawn(HellEntities.CRIER, a.south(8), 180f);
            boolean calling = false;
            for (int i = 0; i < 80 && !calling; i++) { ctx.waitTicks(2); calling = mind(c) == Mind.CALL; }
            soft(calling, "the Crier starts its call (interrupt=" + interrupt + ")");
            if (!interrupt) {
                ctx.waitTicks(30);
                ctx.takeScreenshot("crier_inhale");
                ctx.waitTicks(40);
                long searching = hollows.stream().filter(id -> mind(id) == Mind.SEARCH || mind(id) == Mind.RECOGNITION || mind(id) == Mind.PURSUIT).count();
                boolean noTarget = server(s -> hollows.stream().map(id -> dead(s, id)).allMatch(d -> d == null || d.mind() != Mind.SEARCH || d.quarry() == null));
                soft(searching >= 2, "after the scream nearby Hollows investigate (" + searching + "/3)");
                soft(noTarget, "searching Hollows are not given the target");
            } else {
                ctx.waitTicks(10);
                sp.getServer().runOnServer(s -> { LimboDead cr = dead(s, c); cr.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 1f); });
                ctx.waitTicks(60);
                long searching = hollows.stream().filter(id -> mind(id) == Mind.SEARCH).count();
                soft(searching == 0 && mind(c) != Mind.CALL, "a hit during the inhale interrupts the call (" + searching + " searching)");
            }
        }
    }

    /** The Walking Ossuary carries a finite contingent: it sheds at most that many, and the count survives a reload. */
    private void ossuary() {
        reset();
        BlockPos a = arena;
        UUID o = spawn(HellEntities.WALKING_OSSUARY, a.south(8), 180f);
        int start = server(s -> ((WalkingOssuaryEntity) dead(s, o)).occupants());
        // Wound it past all three thresholds (70%, 40%, 15%) without killing it.
        for (int hit = 0; hit < 11; hit++) {
            sp.getServer().runOnServer(s -> { LimboDead d = dead(s, o); if (d != null) { d.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 6f); } });
            ctx.waitTicks(45);
            if (hit == 3) ctx.takeScreenshot("ossuary_shedding");
        }
        ctx.waitTicks(60);
        int left = server(s -> { LimboDead d = dead(s, o); return d instanceof WalkingOssuaryEntity w ? w.occupants() : -1; });
        long shed = server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, new AABB(a).inflate(30), d -> !(d instanceof WalkingOssuaryEntity)).size());
        soft(start > 0 && left == 0 && shed == start, "the Ossuary shed exactly its contingent (" + start + " -> " + left + ", " + shed + " out)");
        // Reload: go far away, let the area unload, come back.
        cmd(sp, "kill @e[type=!minecraft:player,type=!quantumdeath:walking_ossuary,distance=..200]");
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + (a.getX() + 600) + " " + (a.getY() + 30) + " " + a.getZ());
        ctx.waitTicks(300);
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + a.getX() + " " + a.getY() + " " + a.getZ());
        settle(ctx, sp, 60);
        int after = server(s -> { LimboDead d = dead(s, o); return d instanceof WalkingOssuaryEntity w ? w.occupants() : -1; });
        soft(after == 0, "after unloading and reloading, the Ossuary's spent contingent stays spent (" + after + ")");
    }

    /** Only authorized defences fall, only with mobGriefing, and stone around them stays. */
    private void siege() {
        for (boolean griefing : new boolean[]{true, false}) {
            reset();
            BlockPos a = arena;
            cmd(sp, "gamerule mob_griefing " + griefing);
            cmd(sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() + 1) + " " + (a.getY() + 2) + " " + (a.getZ() + 1) + " minecraft:cobblestone hollow");
            cmd(sp, "setblock " + a.getX() + " " + a.getY() + " " + (a.getZ() + 1) + " minecraft:oak_door[facing=south,half=lower]");
            cmd(sp, "setblock " + a.getX() + " " + (a.getY() + 1) + " " + (a.getZ() + 1) + " minecraft:oak_door[facing=south,half=upper]");
            cmd(sp, "execute in quantumdeath:vestibule run tp @p " + (a.getX() + 0.5) + " " + a.getY() + " " + (a.getZ() + 0.5) + " 180 0");
            BlockPos door = a.south();
            UUID o = spawn(HellEntities.WALKING_OSSUARY, a.south(6), 180f);
            sp.getServer().runOnServer(s -> dead(s, o).engage(player(s)));
            boolean broken = false;
            for (int i = 0; i < 90 && !broken; i++) {
                ctx.waitTicks(5);
                broken = server(s -> !limbo(s).getBlockState(door).is(Blocks.OAK_DOOR));
                if (i == 14 && griefing) ctx.takeScreenshot("siege_preparing");
            }
            boolean stone = server(s -> limbo(s).getBlockState(a.east()).is(Blocks.COBBLESTONE) && limbo(s).getBlockState(a.west()).is(Blocks.COBBLESTONE));
            if (griefing) soft(broken && stone, "with mobGriefing the Ossuary batters down a wooden door, never the stone");
            else soft(!broken && stone, "without mobGriefing no defence is broken");
            cmd(sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() + 1) + " " + (a.getY() + 2) + " " + (a.getZ() + 1) + " minecraft:air");
        }
        cmd(sp, "gamerule mob_griefing true");
    }

    /** Consecration keeps the common dead out while fuel lasts; VIGIL's sanctuary is the lantern itself, fuel or not. */
    private void consecration() {
        reset();
        BlockPos a = arena;
        BlockPos lantern = a.east(3);
        cmd(sp, "setblock " + lantern.getX() + " " + lantern.getY() + " " + lantern.getZ() + " quantumdeath:votive_lantern");
        cmd(sp, "deathmask limbo consecrate");
        ctx.waitTicks(5);
        boolean holy = server(s -> Consecration.at(limbo(s), Vec3.atCenterOf(a)) != null && limbo(s).getBlockState(lantern).getValue(VotiveLantern.FUEL) == LimboTuning.MAX_FUEL);
        soft(holy, "funeral wax consecrates the votive lantern");
        UUID h = spawn(HellEntities.HOLLOW, a.east(5).south(2), 180f);
        ctx.waitTicks(20);
        ctx.takeScreenshot("consecrated_lantern");
        ctx.waitTicks(140);
        double d = server(s -> { LimboDead x = dead(s, h); return x == null ? -1 : Math.sqrt(x.distanceToSqr(Vec3.atBottomCenterOf(lantern))); });
        soft(d > VotiveLantern.SANCTUARY_RADIUS - 0.5, "a Hollow inside consecrated light walks out of it (distance " + d + ")");
        soft(server(s -> dead(s, h) != null), "the dead are driven out, not destroyed");
        boolean burnt = server(s -> {
            ServerLevel l = limbo(s);
            l.setBlockAndUpdate(lantern, l.getBlockState(lantern).setValue(VotiveLantern.FUEL, 1));
            l.getBlockState(lantern).tick(l, lantern, l.getRandom());
            return Consecration.at(l, Vec3.atCenterOf(a)) == null && VigilEntity.inSanctuary(l, a);
        });
        soft(burnt, "when the wax is spent the extra protection ends, the VIGIL sanctuary stays");
        cmd(sp, "setblock " + lantern.getX() + " " + lantern.getY() + " " + lantern.getZ() + " minecraft:air");
    }

    /** A Hound searching is drawn off by a Breath Lure. */
    private void lure() {
        reset();
        BlockPos a = arena;
        UUID hound = spawn(HellEntities.MOURNING_HOUND, a.south(10), 0f);
        Vec3 lurePos = Vec3.atBottomCenterOf(a.south(10).east(14));
        sp.getServer().runOnServer(s -> {
            dead(s, hound).hearAlert(Vec3.atBottomCenterOf(a.south(10).west(12)));
            BreathClues.addLure(limbo(s), lurePos);
        });
        cmd(sp, "execute in quantumdeath:vestibule run tp @p " + a.getX() + " " + (a.getY() + 30) + " " + (a.getZ() - 30));
        ctx.waitTicks(200);
        double d = server(s -> { LimboDead x = dead(s, hound); return x == null ? 99 : Math.sqrt(x.distanceToSqr(lurePos)); });
        soft(d < 4, "a searching Hound follows a Breath Lure (distance " + d + ")");
    }

    /** The Toll-Keeper calls a finite, announced group, still grants descent, and its fall breaks the first seal. */
    private void tollKeeper() {
        reset();
        cmd(sp, "deathmask progress reset");
        cmd(sp, "deathmask boss summon vestibule");
        int summoned = 0;
        for (int i = 0; i < 60 && summoned == 0; i++) {
            ctx.waitTicks(10);
            summoned = server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(48)).size());
        }
        ctx.waitTicks(20);
        ctx.takeScreenshot("toll_keeper_call");
        int count = server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(48)).size());
        soft(count > 0 && count <= 5, "the Toll-Keeper calls a finite group of the unpaid (" + count + ")");
        cmd(sp, "deathmask boss kill");
        ctx.waitTicks(100);
        int depth = server(s -> HellProgress.of(player(s)).depth());
        soft(depth == 2, "the Toll-Keeper still unlocks the descent (depth " + depth + ")");
        soft(server(s -> PlagueDirector.state(s).sealBroken()), "its defeat breaks the first seal");
        boolean noBoss = server(s -> limbo(s).getEntitiesOfClass(HellBoss.class, player(s).getBoundingBox().inflate(96), e -> e.isAlive()).isEmpty());
        soft(noBoss, "the boss is gone and can be summoned again");
    }

    /** A plague focus: waves come out of the anchor, nothing advances while nobody is there, the purge stops new waves. */
    private void plague() {
        cmd(sp, "kill @e[type=!minecraft:player,distance=..200]");
        // Dry land: a breach only opens on natural ground, and this seed's spawn is in the sea.
        BlockPos land = server(s -> {
            ServerLevel ow = s.overworld();
            var found = ow.findClosestBiome3d(h -> h.is(net.minecraft.world.level.biome.Biomes.PLAINS) || h.is(net.minecraft.world.level.biome.Biomes.FOREST)
                    || h.is(net.minecraft.world.level.biome.Biomes.SAVANNA) || h.is(net.minecraft.world.level.biome.Biomes.TAIGA), BlockPos.ZERO.atY(64), 6400, 32, 64);
            if (found == null) return null;
            BlockPos at = found.getFirst();
            ow.getChunk(at.getX() >> 4, at.getZ() >> 4);
            return ow.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        });
        soft(land != null, "found dry land for the plague check");
        if (land == null) return;
        cmd(sp, "execute in minecraft:overworld run tp @p " + land.getX() + " " + land.getY() + " " + land.getZ());
        settle(ctx, sp, 60);
        cmd(sp, "deathmask plague trigger minor now");
        ctx.waitTicks(10);
        PlagueState.Focus f = server(s -> PlagueDirector.state(s).foci().stream().filter(PlagueState.Focus::open).findFirst().orElse(null));
        soft(f != null, "a plague focus opens with its breach anchor");
        if (f == null) return;
        BlockPos anchor = f.anchor();
        boolean placed = server(s -> s.overworld().getBlockState(anchor).is(ModBlocks.BREACH_ANCHOR));
        soft(placed, "the anchor stands on natural ground");
        int out = 0;
        for (int i = 0; i < 20 && out == 0; i++) {
            ctx.waitTicks(10);
            out = server(s -> s.overworld().getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(12)).size());
        }
        soft(out > 0 && out <= LimboTuning.MINOR_RESERVE, "a first, budgeted wave climbs out of the breach (" + out + ")");
        cmd(sp, "execute in minecraft:overworld run tp @p " + (anchor.getX() + 6) + " " + (anchor.getY() + 4) + " " + (anchor.getZ() + 6) + " facing " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
        ctx.waitTicks(30);
        ctx.takeScreenshot("plague_breach_wave");
        // Nobody near: nothing advances.
        PlagueState.Focus before = server(s -> PlagueDirector.state(s).foci().stream().filter(x -> x.id() == f.id()).findFirst().orElseThrow());
        cmd(sp, "execute in minecraft:overworld run tp @p " + (anchor.getX() + 400) + " 150 " + anchor.getZ());
        ctx.waitTicks(400);
        PlagueState.Focus away = server(s -> PlagueDirector.state(s).foci().stream().filter(x -> x.id() == f.id()).findFirst().orElseThrow());
        soft(away.equals(before), "no waves accumulate while nobody is near (" + before + " / " + away + ")");
        // The purge: stay beside the anchor, looking at it.
        cmd(sp, "execute in minecraft:overworld run tp @p " + (anchor.getX() + 2.5) + " " + (anchor.getY() + 0) + " " + (anchor.getZ() + 0.5) + " facing " + (anchor.getX() + 0.5) + " " + (anchor.getY() + 0.5) + " " + (anchor.getZ() + 0.5));
        settle(ctx, sp, 40);
        ctx.getInput().lookAt(anchor); // the client owns the camera: look at the anchor as a player would
        ctx.waitTicks(5);
        Set<UUID> existing = new HashSet<>(server(s -> s.overworld().getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(64)).stream().map(Entity::getUUID).toList()));
        sp.getServer().runOnServer(s -> PlagueDirector.startPurge(s.overworld(), player(s), anchor));
        ctx.waitTicks(40);
        ctx.takeScreenshot("plague_purge");
        ctx.waitTicks(LimboTuning.PURGE_TICKS);
        boolean purged = server(s -> !s.overworld().getBlockState(anchor).is(ModBlocks.BREACH_ANCHOR)
                && PlagueDirector.state(s).foci().stream().anyMatch(x -> x.id() == f.id() && x.stage() == PlagueState.CONTAINED));
        soft(purged, "holding the rite purges the anchor and contains the focus");
        ctx.waitTicks(LimboTuning.PLAGUE_WAVE_INTERVAL + 60);
        boolean noNew = server(s -> existing.containsAll(s.overworld().getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(64)).stream().map(Entity::getUUID).toList()));
        soft(noNew, "a purged breach releases no new waves");
        BlockState at = server(s -> s.overworld().getBlockState(anchor));
        soft(!at.is(ModBlocks.BREACH_ANCHOR), "a purged anchor does not come back");
    }
}
