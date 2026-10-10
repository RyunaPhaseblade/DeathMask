package ryuna.qdeath.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.limbo.BloaterCorpse;
import ryuna.qdeath.entity.limbo.ForemanEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.StalkerEntity;
import ryuna.qdeath.limbo.HordePaths;
import ryuna.qdeath.limbo.LimboDirector;
import ryuna.qdeath.limbo.LimboStats;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.registry.HellEntities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ryuna.qdeath.test.LimboRefinementTest.limbo;
import static ryuna.qdeath.test.LimboRefinementTest.log;
import static ryuna.qdeath.test.LimboRefinementTest.player;
import static ryuna.qdeath.test.TestUtil.cmd;
import static ryuna.qdeath.test.TestUtil.settle;

/**
 * Functional, balance and performance measurements of the refinement, each logged as "REFINE ..." with its numbers.
 * Speeds are measured on a flat lane with the real client input (W, W+sprint) for the player and real pursuit for the
 * dead, under equal conditions.
 */
final class RefinementChecks {
    private final LimboRefinementTest t;
    private BlockPos lane;
    private double sprint, walk;

    RefinementChecks(LimboRefinementTest t) { this.t = t; }

    void run() {
        String only = System.getProperty("quantumdeath.refineOnly", "");
        survival(true);
        if (!only.isEmpty()) {
            for (String c : only.split(",")) switch (c) {
                case "speed" -> speeds();
                case "horde" -> hordes();
                case "director" -> director();
                case "difficulty" -> difficulty();
                case "crawler" -> crawler();
                default -> {}
            }
            return;
        }
        speeds();
        hordes();
        stalker();
        foreman();
        lanky();
        bloater();
        breaker();
        runtAndRunaway();
        crawler();
        difficulty();
        director();
    }

    // ------------------------------------------------------------------ helpers
    private void survival(boolean invulnerable) {
        cmd(t.sp, "gamemode survival");
        cmd(t.sp, "effect clear @p");
        cmd(t.sp, "effect give @p minecraft:saturation infinite 0 true");
        if (invulnerable) cmd(t.sp, "effect give @p minecraft:resistance infinite 255 true");
        cmd(t.sp, "clear @p");
        t.ctx.waitTicks(5);
    }

    private void clearAll() {
        t.sp.getServer().runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(t.stage).inflate(220),
                e -> !(e instanceof ServerPlayer)).forEach(Entity::discard));
        t.ctx.waitTicks(3);
    }

    private void tp(double x, double y, double z) {
        cmd(t.sp, "tp @p " + x + " " + y + " " + z);
        t.ctx.waitTicks(3);
    }

    private UUID spawn(EntityType<? extends LimboDead> type, double x, double y, double z, boolean engage) {
        UUID id = t.place(type, x, y, z, 0, -1, false);
        if (engage) t.sp.getServer().runOnServer(s -> { if (limbo(s).getEntity(id) instanceof LimboDead d) d.engage(player(s)); });
        return id;
    }

    private LimboDead dead(MinecraftServer s, UUID id) {
        return limbo(s).getEntity(id) instanceof LimboDead d ? d : null;
    }

    private double[] tickStats() {
        long[] times = t.server(MinecraftServer::getTickTimesNanos).clone();
        double[] ms = Arrays.stream(times).filter(v -> v > 0).mapToDouble(v -> v / 1e6).sorted().toArray();
        if (ms.length == 0) return new double[]{0, 0, 0};
        double avg = Arrays.stream(ms).average().orElse(0);
        return new double[]{avg, ms[(int) Math.min(ms.length - 1, Math.floor(ms.length * 0.95))], ms[ms.length - 1]};
    }

    private int fps() { return t.ctx.computeOnClient(mc -> mc.getFps()); }

    // ------------------------------------------------------------------ speed
    private void speeds() {
        BlockPos a = t.stage;
        lane = a.offset(-70, 0, 30);
        BlockPos l = lane;
        // A straight flat lane of bricks, 150 blocks, open above.
        for (int x = -2; x <= 150; x += 30) {
            cmd(t.sp, "fill " + (l.getX() + x) + " " + (l.getY() - 3) + " " + (l.getZ() - 3) + " " + (l.getX() + Math.min(150, x + 29)) + " " + (l.getY() - 1) + " " + (l.getZ() + 3) + " quantumdeath:ashen_bricks");
            cmd(t.sp, "fill " + (l.getX() + x) + " " + l.getY() + " " + (l.getZ() - 3) + " " + (l.getX() + Math.min(150, x + 29)) + " " + (l.getY() + 5) + " " + (l.getZ() + 3) + " minecraft:air");
        }
        settle(t.ctx, t.sp, 20);
        walk = measurePlayer(false);
        sprint = measurePlayer(true);
        log("speed: player walk {} b/t ({} m/s), sprint {} b/t ({} m/s)", r3(walk), r3(walk * 20), r3(sprint), r3(sprint * 20));
        t.soft(Math.abs(sprint - LimboTuning.PLAYER_SPRINT) < 0.02, "measured player sprint matches the tuning reference (" + r3(sprint) + " vs " + LimboTuning.PLAYER_SPRINT + ")");
        Object[][] species = {{HellEntities.HOLLOW, 0.94, 0.97}, {HellEntities.STALKER, 0.96, 0.99}, {HellEntities.CRIER, 0.70, 0.80},
                {HellEntities.FOREMAN, 0.80, 0.90}, {HellEntities.LANKY, 0.85, 0.95}, {HellEntities.BLOATER, 0.75, 0.85}, {HellEntities.BREAKER, 0.55, 0.70},
                {HellEntities.RUNT, 1.03, 1.10}, {HellEntities.CRAWLER, 0.75, 0.90}};
        for (Object[] sp : species) {
            @SuppressWarnings("unchecked") EntityType<? extends LimboDead> type = (EntityType<? extends LimboDead>) sp[0];
            double v = measureDead(type);
            double ratio = v / sprint;
            String name = EntityType.getKey(type).getPath();
            log("speed: {} {} b/t = {} of sprint (walk = {})", name, r3(v), r3(ratio), r3(walk / sprint));
            t.soft(ratio >= (double) sp[1] - 0.005 && ratio <= (double) sp[2] + 0.005, name + " chases at " + r3(ratio) + " of the measured sprint (target " + sp[1] + "-" + sp[2] + ")");
            if (type == HellEntities.HOLLOW) t.soft(v > walk * 1.15, "the common dead outruns a walking player (" + r3(v) + " vs " + r3(walk) + ")");
        }
        chaseOutcome();
    }

    private double measurePlayer(boolean sprinting) {
        BlockPos l = lane;
        tp(l.getX() + 2.5, l.getY(), l.getZ() + 0.5);
        t.ctx.getInput().lookAt(-90, 0);
        t.ctx.waitTicks(10);
        t.ctx.getInput().holdKey(o -> o.keyUp);
        if (sprinting) t.ctx.getInput().holdKey(o -> o.keySprint);
        t.ctx.waitTicks(20);
        double[] a = t.server(s -> new double[]{player(s).getX(), limbo(s).getGameTime()});
        t.ctx.waitTicks(60);
        double[] b = t.server(s -> new double[]{player(s).getX(), limbo(s).getGameTime()});
        t.ctx.getInput().releaseKey(o -> o.keyUp);
        if (sprinting) t.ctx.getInput().releaseKey(o -> o.keySprint);
        t.ctx.waitTicks(10);
        return (b[0] - a[0]) / Math.max(1, b[1] - a[1]);
    }

    /** Steady pursuit speed: the creature starts at one end, its quarry stands still 62 blocks further (within simulation range). */
    private double measureDead(EntityType<? extends LimboDead> type) {
        clearAll();
        BlockPos l = lane;
        tp(l.getX() + 64.5, l.getY(), l.getZ() + 0.5);
        settle(t.ctx, t.sp, 5);
        UUID id = spawn(type, l.getX() + 2.5, l.getY(), l.getZ() + 0.5, true);
        t.ctx.waitTicks(30);
        double[] a = t.server(s -> dead(s, id) == null ? null : new double[]{dead(s, id).getX(), limbo(s).getGameTime()});
        t.ctx.waitTicks(50);
        double[] b = t.server(s -> dead(s, id) == null ? null : new double[]{dead(s, id).getX(), limbo(s).getGameTime()});
        clearAll();
        if (a == null || b == null || b[1] <= a[1]) return 0;
        return (b[0] - a[0]) / (b[1] - a[1]);
    }

    /** Walking away from a common dead fails; sprinting in a straight line gains ground slowly. */
    private void chaseOutcome() {
        for (boolean sprinting : new boolean[]{false, true}) {
            clearAll();
            BlockPos l = lane;
            tp(l.getX() + 14.5, l.getY(), l.getZ() + 0.5);
            UUID id = spawn(HellEntities.HOLLOW, l.getX() + 4.5, l.getY(), l.getZ() + 0.5, true);
            t.ctx.getInput().lookAt(-90, 0);
            t.ctx.waitTicks(25);
            double d0 = t.server(s -> dead(s, id).distanceTo(player(s)));
            t.ctx.getInput().holdKey(o -> o.keyUp);
            if (sprinting) t.ctx.getInput().holdKey(o -> o.keySprint);
            double min = d0;
            StringBuilder trace = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                t.ctx.waitTicks(10);
                double[] st = t.server(s -> new double[]{dead(s, id) == null ? 99 : dead(s, id).distanceTo(player(s)), player(s).getX(), limbo(s).getGameTime(),
                        dead(s, id) == null ? 0 : dead(s, id).getX(), player(s).isSprinting() ? 1 : 0, player(s).hurtTime});
                min = Math.min(min, st[0]);
                trace.append(String.format(java.util.Locale.ROOT, " [t%d p%.1f d%.1f s%d h%d]", (long) st[2] % 10000, st[1], st[3], (int) st[4], (int) st[5]));
            }
            log("chase trace:{}", trace);
            double d1 = t.server(s -> dead(s, id) == null ? 99 : dead(s, id).distanceTo(player(s)));
            double moved = t.server(s -> player(s).getX()) - (lane.getX() + 14.5);
            log("chase: the player moved {} blocks along the lane", r3(moved));
            t.ctx.getInput().releaseKey(o -> o.keyUp);
            if (sprinting) t.ctx.getInput().releaseKey(o -> o.keySprint);
            log("chase: {} away from a common dead for 8 s: distance {} -> {} (closest {}), strikes landed {}", sprinting ? "sprinting" : "walking",
                    r3(d0), r3(d1), r3(min), LimboStats.strikes);
            if (sprinting) t.soft(d1 > d0, "sprinting in a straight line slowly gains distance (" + r3(d0) + " -> " + r3(d1) + ")");
            else t.soft(min < 2.6, "walking away does not escape: the dead closes to striking distance (closest " + r3(min) + ")");
            LimboStats.reset();
        }
        clearAll();
    }

    // ------------------------------------------------------------------ hordes
    private void hordes() {
        for (int n : new int[]{20, 50, 100, 150}) {
            clearAll();
            LimboStats.reset();
            BlockPos a = t.stage;
            tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
            settle(t.ctx, t.sp, 10);
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n, r = 12 + (i % 5) * 2;
                ids.add(spawn(HellEntities.HOLLOW, a.getX() + 0.5 + Math.cos(ang) * r, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * r, true));
            }
            t.ctx.waitTicks(100);
            long pursuing = t.server(s -> ids.stream().map(id -> dead(s, id)).filter(d -> d != null && (d.mind() == Mind.PURSUIT || d.mind().alert())).count());
            // Dead that lost sight of the quarry behind terrain search toward where they saw it: they are coming, not waiting.
            long following = t.server(s -> ids.stream().map(id -> dead(s, id)).filter(d -> d == null || d.mind().alert() || d.mind() == Mind.SEARCH).count());
            String minds = t.server(s -> { java.util.Map<String, Integer> m = new java.util.TreeMap<>();
                for (UUID id : ids) { LimboDead d = dead(s, id); m.merge(d == null ? "gone" : d.mind().name(), 1, Integer::sum); } return m.toString(); });
            log("horde {}: states after 5 s {}", n, minds);
            String searching = t.server(s -> { StringBuilder b = new StringBuilder(); int k = 0;
                for (UUID id : ids) { LimboDead d = dead(s, id); if (d != null && d.mind() == Mind.SEARCH && k++ < 6) b.append(" {").append(d.debugState()).append("}"); } return b.toString(); });
            log("horde {}: sample of searching dead:{}", n, searching);
            int fpsSum = 0;
            double worstP95 = 0, avgSum = 0, peak = 0;
            // The quarry moves around a small circle so the crowd keeps reaching it from new sides.
            for (int k = 0; k < 15; k++) {
                double ang = k * 0.9;
                tp(a.getX() + 0.5 + Math.cos(ang) * 5, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * 5);
                t.ctx.waitTicks(37);
                double[] ts = tickStats();
                avgSum += ts[0];
                worstP95 = Math.max(worstP95, ts[1]);
                peak = Math.max(peak, ts[2]);
                fpsSum += fps();
            }
            int strikers = t.server(s -> LimboStats.STRIKERS.getOrDefault(player(s).getUUID(), Set.of()).size());
            long waiting = t.server(s -> ids.stream().map(id -> dead(s, id)).filter(d -> d != null && d.mind() == Mind.REST).count());
            log("horde {}: in pursuit after 5 s {} / {}; distinct dead that struck the same player in 28 s: {} (total strikes {}); idle {}; "
                    + "server tick avg {} ms, p95 {} ms, peak {} ms; client {} fps; routes searched {}, shared {}",
                    n, pursuing, n, strikers, LimboStats.strikes, waiting, r3(avgSum / 15), r3(worstP95), r3(peak), fpsSum / 15, HordePaths.searches, HordePaths.shared);
            t.soft(following == n && waiting == 0, "horde of " + n + ": every dead hunts the player, none waits (" + pursuing + " in pursuit, "
                    + (following - pursuing) + " searching toward it after losing sight, " + waiting + " idle)");
            if (n >= 50) t.soft(strikers > 10, "horde of " + n + ": far more than ten dead strike the same player (" + strikers + "), no quota");
            if (n == 20) t.soft(strikers >= 12, "horde of 20: most of them reach and strike the same player (" + strikers + ")");
            if (n <= 100) t.soft(worstP95 < 50, "horde of " + n + ": p95 server tick under 50 ms (" + r3(worstP95) + ")");
        }
        clearAll();
    }

    // ------------------------------------------------------------------ specialists
    private void stalker() {
        clearAll();
        BlockPos a = t.stage;
        tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        t.ctx.getInput().lookAt(0, 0); // facing +Z
        UUID id = spawn(HellEntities.STALKER, a.getX() + 0.5, a.getY(), a.getZ() + 10.5, true);
        int behindTicks = 0, firstBehind = -1;
        for (int i = 0; i < 60; i++) {
            t.ctx.waitTicks(4);
            boolean behind = t.server(s -> { LimboDead d = dead(s, id); return d instanceof StalkerEntity st && st.behind(player(s)) && st.distanceTo(player(s)) < 4; });
            if (behind) { behindTicks++; if (firstBehind < 0) firstBehind = i * 4; }
        }
        t.soft(firstBehind >= 0, "the Stalker walks round to the back of a victim facing it (behind after " + firstBehind + " ticks)");
        // Counterplay: turn to face it every half second; it must keep repositioning.
        int repositions = 0;
        boolean was = true;
        LimboStats.reset();
        for (int i = 0; i < 40; i++) {
            float yaw = t.server(s -> { LimboDead d = dead(s, id); if (d == null) return 0f;
                Vec3 to = d.position().subtract(player(s).position()); return (float) Math.toDegrees(Math.atan2(-to.x, to.z)); });
            t.ctx.getInput().lookAt(yaw, 0);
            t.ctx.waitTicks(10);
            boolean behind = t.server(s -> { LimboDead d = dead(s, id); return d instanceof StalkerEntity st && st.behind(player(s)); });
            if (!behind && was) repositions++;
            was = behind;
        }
        log("stalker: reached the back after {} ticks, {} ticks behind in 240; facing it each half-second forced it to start over {} times, strikes {}",
                firstBehind, behindTicks * 4, repositions, LimboStats.strikes);
        clearAll();
    }

    private void foreman() {
        clearAll();
        BlockPos a = t.stage;
        tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID f = spawn(HellEntities.FOREMAN, a.getX() + 0.5, a.getY(), a.getZ() + 14.5, true);
        List<UUID> group = new ArrayList<>();
        for (int i = 0; i < 9; i++) group.add(spawn(HellEntities.HOLLOW, a.getX() - 4.5 + i, a.getY(), a.getZ() + 18.5, true));
        int routed = 0;
        boolean signalled = false;
        for (int i = 0; i < 60 && routed < 2; i++) {
            t.ctx.waitTicks(4);
            signalled |= t.server(s -> dead(s, f) != null && dead(s, f).animId() == LimboDead.ANIM_SPECIAL);
            routed = (int) (long) t.server(s -> group.stream().map(id -> dead(s, id)).filter(d -> d != null && d.approach() != null).count());
            if (i == 10) t.ctx.takeScreenshot("refine_foreman_signal");
        }
        t.soft(signalled && routed >= 2, "the Foreman signals and sends some of the pack round to the sides (" + routed + " routed)");
        // Kill it: the routes expire, the pack keeps hunting.
        t.sp.getServer().runOnServer(s -> { LimboDead d = dead(s, f); if (d != null) d.kill(limbo(s)); });
        t.ctx.waitTicks(140);
        long hunting = t.server(s -> group.stream().map(id -> dead(s, id)).filter(d -> d != null && d.mind() == Mind.PURSUIT).count());
        long routes = t.server(s -> group.stream().map(id -> dead(s, id)).filter(d -> d != null && d.approach() != null).count());
        t.soft(hunting >= 7 && routes == 0, "without the Foreman the routes lapse but the pack keeps hunting (" + hunting + " hunting, " + routes + " routed)");
        clearAll();
    }

    private void lanky() {
        clearAll();
        LimboStats.reset();
        BlockPos a = t.stage;
        tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        // A front row of dead between the player and the Lanky: it reaches over them.
        for (int i = -1; i <= 1; i++) t.place(HellEntities.HOLLOW, a.getX() + 0.5 + i, a.getY(), a.getZ() + 2.0, 180, 0, true);
        UUID l = spawn(HellEntities.LANKY, a.getX() + 0.5, a.getY(), a.getZ() + 3.6, true);
        t.ctx.waitTicks(80);
        int over = t.server(s -> LimboStats.STRIKERS.getOrDefault(player(s).getUUID(), Set.of()).size());
        t.soft(over >= 1, "the Lanky strikes over the front row of a crowd");
        clearAll();
        LimboStats.reset();
        // The player sealed in a stone cell (walls and roof): the Lanky stands right outside and never strikes through.
        cmd(t.sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() + 1) + " " + (a.getY() + 3) + " " + (a.getZ() + 1) + " minecraft:stone hollow");
        tp(a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5);
        spawn(HellEntities.LANKY, a.getX() + 0.5, a.getY(), a.getZ() + 3.0, true);
        t.ctx.waitTicks(120);
        int through = LimboStats.strikes;
        t.soft(through == 0, "the Lanky never strikes through a wall (" + through + " strikes)");
        cmd(t.sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() + 1) + " " + (a.getY() + 3) + " " + (a.getZ() + 1) + " minecraft:air");
        clearAll();
    }

    private void bloater() {
        clearAll();
        LimboStats.reset();
        BlockPos a = t.stage;
        tp(a.getX() + 12.5, a.getY(), a.getZ() + 12.5);
        double bx = a.getX() + 0.5, bz = a.getZ() + 0.5;
        UUID open = t.place(HellEntities.HOLLOW, bx + 2.2, a.getY(), bz, 0, 0, true);
        UUID covered = t.place(HellEntities.HOLLOW, bx - 2.2, a.getY(), bz, 0, 0, true);
        cmd(t.sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() - 1) + " " + (a.getY() + 2) + " " + (a.getZ() + 1) + " minecraft:stone");
        UUID b = t.place(HellEntities.BLOATER, bx, a.getY(), bz, 0, 0, true);
        t.ctx.waitTicks(5);
        t.sp.getServer().runOnServer(s -> { LimboDead d = dead(s, b); d.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 500f); });
        t.ctx.waitTicks(30);
        boolean corpse = t.server(s -> !limbo(s).getEntitiesOfClass(BloaterCorpse.class, new AABB(a).inflate(5)).isEmpty());
        t.soft(corpse, "a dying Putrescent leaves a corpse, not a creature");
        cmd(t.sp, "tp @p " + (bx + 4) + " " + (a.getY() + 1) + " " + (bz + 4) + " facing " + bx + " " + a.getY() + " " + bz);
        t.ctx.getInput().lookAt(BlockPos.containing(bx, a.getY(), bz));
        t.ctx.waitTicks(15);
        t.ctx.takeScreenshot("refine_bloater_corpse_swelling");
        t.ctx.waitTicks(60);
        float hOpen = t.server(s -> dead(s, open) == null ? 0f : dead(s, open).getHealth());
        float hCover = t.server(s -> dead(s, covered) == null ? 0f : dead(s, covered).getHealth());
        log("bloater: corpse burst {} time(s); dead in the open left at {} HP, dead behind a wall at {} HP (of 24)", LimboStats.bursts, hOpen, hCover);
        t.soft(LimboStats.bursts == 1, "the corpse bursts exactly once (" + LimboStats.bursts + ")");
        t.soft(hOpen < hCover, "the burst hurts the dead too, and cover reduces it (" + hOpen + " in the open vs " + hCover + " covered)");
        cmd(t.sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() - 1) + " " + (a.getX() - 1) + " " + (a.getY() + 2) + " " + (a.getZ() + 1) + " minecraft:air");
        // Saved mid-fuse: unload the area and come back; the count resumes (never restarts) and it bursts once.
        clearAll();
        LimboStats.reset();
        UUID b2 = t.place(HellEntities.BLOATER, bx, a.getY(), bz, 0, 1, true);
        t.ctx.waitTicks(5);
        t.sp.getServer().runOnServer(s -> { LimboDead d = dead(s, b2); d.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 500f); });
        t.ctx.waitTicks(20);
        int fuseBefore = t.server(s -> limbo(s).getEntitiesOfClass(BloaterCorpse.class, new AABB(a).inflate(5)).stream().mapToInt(BloaterCorpse::fuse).findFirst().orElse(-1));
        tp(a.getX() + 900.5, a.getY() + 40, a.getZ());
        t.ctx.waitTicks(260);
        int burstAway = LimboStats.bursts;
        tp(a.getX() + 12.5, a.getY(), a.getZ() + 12.5);
        settle(t.ctx, t.sp, 10);
        int fuseAfter = t.server(s -> limbo(s).getEntitiesOfClass(BloaterCorpse.class, new AABB(a).inflate(5)).stream().mapToInt(BloaterCorpse::fuse).findFirst().orElse(-1));
        t.ctx.waitTicks(120);
        log("bloater reload: fuse {} before unloading, {} after reloading; bursts while away {}, after return {}", fuseBefore, fuseAfter, burstAway, LimboStats.bursts);
        t.soft(fuseBefore > 0 && fuseAfter > 0 && fuseAfter <= fuseBefore && burstAway == 0, "the fuse is saved with the corpse and resumes after a reload");
        t.soft(LimboStats.bursts == 1, "after the reload it bursts exactly once (" + LimboStats.bursts + ")");
        t.ctx.waitTicks(LimboTuning.CORPSE_RESIDUE + 20);
        boolean gone = t.server(s -> limbo(s).getEntitiesOfClass(BloaterCorpse.class, new AABB(a).inflate(5)).isEmpty());
        t.soft(gone, "the burst residue clears itself");
        clearAll();
    }

    private void breaker() {
        clearAll();
        LimboStats.reset();
        BlockPos a = t.stage;
        tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        cmd(t.sp, "item replace entity @p weapon.mainhand with minecraft:shield");
        t.ctx.getInput().lookAt(0, 0);
        UUID b = spawn(HellEntities.BREAKER, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, true);
        t.ctx.getInput().holdKey(o -> o.keyUse);
        boolean windup = false, disabled = false;
        for (int i = 0; i < 50 && !disabled; i++) {
            t.ctx.waitTicks(4);
            windup |= t.server(s -> dead(s, b) != null && dead(s, b).animId() == LimboDead.ANIM_ATTACK);
            disabled = t.server(s -> player(s).getCooldowns().isOnCooldown(player(s).getMainHandItem()));
            if (windup && i % 6 == 0) t.ctx.takeScreenshot("refine_breaker_windup_" + i);
        }
        t.ctx.getInput().releaseKey(o -> o.keyUse);
        t.soft(windup && disabled, "the Breaker's prepared blow is announced and knocks a raised shield out of use");
        cmd(t.sp, "clear @p");
        clearAll();
    }

    private void runtAndRunaway() {
        for (EntityType<? extends LimboDead> type : List.of(HellEntities.RUNT, HellEntities.RUNAWAY)) {
            clearAll();
            BlockPos a = t.stage;
            tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
            UUID id = spawn(type, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, true);
            Set<Integer> anims = new HashSet<>();
            double fastest = 0;
            Vec3 last = null;
            boolean specialThenStagger = false;
            boolean special = false;
            long lastTime = 0;
            for (int i = 0; i < 200; i++) {
                t.ctx.waitTicks(1);
                Object[] st = t.server(s -> { LimboDead d = dead(s, id); return d == null ? null : new Object[]{d.animId(), d.position(), limbo(s).getGameTime()}; });
                if (st == null) break;
                int anim = (int) st[0];
                anims.add(anim);
                if (anim == LimboDead.ANIM_SPECIAL) special = true;
                if (special && anim == LimboDead.ANIM_STAGGER) specialThenStagger = true;
                Vec3 p = (Vec3) st[1];
                long time = (long) st[2];
                if (last != null && time > lastTime) fastest = Math.max(fastest, p.subtract(last).horizontalDistance() / (time - lastTime));
                last = p;
                lastTime = time;
                if (i % 50 == 0) tp(a.getX() + 0.5 + (i % 100 == 0 ? 6 : -6), a.getY(), a.getZ() + 0.5);
            }
            String name = EntityType.getKey(type).getPath();
            log("{}: announced its special (pause/compression) {}, recovered after it {}, fastest tick {} b/t ({} of sprint)", name, special, specialThenStagger,
                    r3(fastest), r3(fastest / sprint));
            t.soft(special && specialThenStagger, name + " announces its leap/run and must recover after it");
            if (type == HellEntities.RUNAWAY) t.soft(fastest > sprint * 1.1, "the Runaway's burst outruns a sprint (" + r3(fastest / sprint) + ")");
        }
        clearAll();
    }

    private void crawler() {
        clearAll();
        BlockPos a = t.stage;
        // A wall across the stage with a gap one block high at the floor. The player's side is closed all round (side and
        // back walls): in the Limbo the dead look for another way in, and the gap must be the only one.
        cmd(t.sp, "fill " + (a.getX() - 20) + " " + a.getY() + " " + (a.getZ() + 4) + " " + (a.getX() + 20) + " " + (a.getY() + 4) + " " + (a.getZ() + 4) + " minecraft:stone");
        for (int x : new int[]{-20, 20})
            cmd(t.sp, "fill " + (a.getX() + x) + " " + a.getY() + " " + (a.getZ() - 16) + " " + (a.getX() + x) + " " + (a.getY() + 4) + " " + (a.getZ() + 4) + " minecraft:stone");
        cmd(t.sp, "fill " + (a.getX() - 20) + " " + a.getY() + " " + (a.getZ() - 16) + " " + (a.getX() + 20) + " " + (a.getY() + 4) + " " + (a.getZ() - 16) + " minecraft:stone");
        cmd(t.sp, "fill " + (a.getX() - 1) + " " + a.getY() + " " + (a.getZ() + 4) + " " + (a.getX() + 1) + " " + a.getY() + " " + (a.getZ() + 4) + " minecraft:air");
        tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID c = spawn(HellEntities.CRAWLER, a.getX() + 0.5, a.getY(), a.getZ() + 9.5, true);
        UUID h = spawn(HellEntities.HOLLOW, a.getX() + 3.5, a.getY(), a.getZ() + 9.5, true);
        StringBuilder trace = new StringBuilder();
        for (int k = 0; k < 10; k++) {
            t.ctx.waitTicks(20);
            String at = t.server(s -> dead(s, h) == null ? "gone" : dead(s, h).position().subtract(Vec3.atLowerCornerOf(a)).toString() + " " + dead(s, h).debugState());
            trace.append('[').append(at).append("] ");
        }
        int walls = t.server(s -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(a.offset(-20, 0, -16), a.offset(20, 4, 4)))
                if (limbo(s).getBlockState(q).is(net.minecraft.world.level.block.Blocks.STONE)) n++;
            return n;
        });
        log("crawler trace: walls {} stone; hollow {}", walls, trace);
        double dc = t.server(s -> dead(s, c) == null ? 99 : dead(s, c).distanceTo(player(s)));
        double dh = t.server(s -> dead(s, h) == null ? 99 : dead(s, h).distanceTo(player(s)));
        log("crawler: through a one-block gap the Crawler got to {} blocks, a common dead stayed at {}", r3(dc), r3(dh));
        t.soft(dc < 3 && dh > 4, "the Crawler uses a low gap a standing dead cannot");
        // The closing walls go again (the wall with the gap stays, as before, for the checks that follow).
        for (int x : new int[]{-20, 20})
            cmd(t.sp, "fill " + (a.getX() + x) + " " + a.getY() + " " + (a.getZ() - 16) + " " + (a.getX() + x) + " " + (a.getY() + 4) + " " + (a.getZ() + 3) + " minecraft:air");
        cmd(t.sp, "fill " + (a.getX() - 20) + " " + a.getY() + " " + (a.getZ() - 16) + " " + (a.getX() + 20) + " " + (a.getY() + 4) + " " + (a.getZ() - 16) + " minecraft:air");
        cmd(t.sp, "fill " + (a.getX() - 20) + " " + a.getY() + " " + (a.getZ() + 4) + " " + (a.getX() + 20) + " " + (a.getY() + 4) + " " + (a.getZ() + 4) + " minecraft:air");
        clearAll();
    }

    // ------------------------------------------------------------------ difficulty
    private void difficulty() {
        String[][] kits = {
                {"iron", "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:iron_sword"},
                {"diamond", "minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots", "minecraft:diamond_sword"},
                {"netherite", "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots", "minecraft:netherite_sword"}};
        BlockPos a = t.stage;
        for (String[] kit : kits) {
            // One common dead, fought blow for blow (full-strength swings): what it costs.
            clearAll();
            survival(false);
            cmd(t.sp, "effect clear @p");
            cmd(t.sp, "effect give @p minecraft:instant_health 1 4 true");
            equip(kit);
            tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
            UUID one = spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 4.5, true);
            int ticks = 0;
            while (ticks < 400 && t.server(s -> dead(s, one) != null && dead(s, one).isAlive())) {
                t.ctx.waitTicks(13);
                ticks += 13;
                t.sp.getServer().runOnServer(s -> { LimboDead d = dead(s, one); ServerPlayer p = player(s);
                    if (d != null && d.distanceTo(p) < 3.6) { p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.getEyePosition()); p.attack(d); } });
            }
            float afterOne = t.server(s -> player(s).getHealth());
            int duel = ticks;
            // A pack of twelve, standing still trading nothing: how long the gear holds.
            clearAll();
            cmd(t.sp, "effect give @p minecraft:instant_health 1 4 true");
            t.ctx.waitTicks(5);
            // (A totem in the unused off hand only matters if twelve blows land between two looks: a death here would leave
            // the player on the death screen and spoil every check after this one.)
            cmd(t.sp, "item replace entity @p weapon.offhand with minecraft:totem_of_undying");
            for (int i = 0; i < 12; i++) spawn(HellEntities.HOLLOW, a.getX() + 0.5 + Math.cos(i * 0.52) * 9, a.getY(), a.getZ() + 0.5 + Math.sin(i * 0.52) * 9, true);
            int alive = 0;
            while (alive < 900 && t.server(s -> player(s).isAlive() && player(s).getHealth() > 2f)) { t.ctx.waitTicks(2); alive += 2; }
            clearAll(); // the pack goes at once: the measure is taken
            log("difficulty {}: one common dead fought in melee for {} s costs {} HP (player left at {}); a pack of 12 brings an unmoving player to 1 heart in {} s (ordinary food, no potions)",
                    kit[0], r3(duel / 20.0), r3(20 - afterOne), r3(afterOne), r3(alive / 20.0));
            if (kit[0].equals("iron")) {
                t.soft(afterOne > 8, "with entry gear a lone common dead can be beaten (" + r3(afterOne) + " HP left)");
                t.soft(alive < 30 * 20, "with entry gear a pack forces you to move: standing still lasts " + r3(alive / 20.0) + " s");
            }
            cmd(t.sp, "effect give @p minecraft:instant_health 1 4 true");
            survival(true);
        }
        clearAll();
    }

    private void equip(String[] kit) {
        cmd(t.sp, "item replace entity @p armor.head with " + kit[1]);
        cmd(t.sp, "item replace entity @p armor.chest with " + kit[2]);
        cmd(t.sp, "item replace entity @p armor.legs with " + kit[3]);
        cmd(t.sp, "item replace entity @p armor.feet with " + kit[4]);
        cmd(t.sp, "item replace entity @p weapon.mainhand with " + kit[5]);
        cmd(t.sp, "item replace entity @p weapon.offhand with minecraft:shield");
        t.ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ the director over two minutes
    private void director() {
        clearAll();
        survival(true);
        cmd(t.sp, "gamerule spawn_monsters true");
        cmd(t.sp, "deathmask limbo processions on");
        int packs0 = LimboDirector.packsSpawned, procs0 = LimboDirector.processionsSpawned, bodies0 = LimboDirector.bodiesSpawned;
        // Walk at a walker's pace through the chambers (one step a second along open ground), sampling what is around.
        BlockPos start = t.server(s -> player(s).blockPosition());
        t.sp.getServer().runOnServer(s -> LimboDirector.hurry(player(s), false));
        List<String> samples = new ArrayList<>();
        int maxNear = 0, groupsSeen = 0;
        double heading = 0;
        for (int sec = 0; sec < 150; sec++) {
            double h = heading;
            BlockPos next = t.server(s -> stepOn(limbo(s), player(s).blockPosition(), h));
            if (next == null) { heading += 1.3; next = t.server(s -> player(s).blockPosition()); }
            tp(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
            t.ctx.waitTicks(17);
            if (sec % 10 == 9) {
                int[] g = t.server(s -> groups(limbo(s), player(s).position()));
                maxNear = Math.max(maxNear, g[0]);
                groupsSeen += g[1];
                samples.add("t=" + (sec + 1) + "s near=" + g[0] + " groups=" + g[1] + " hunting=" + g[2]);
                if (sec == 59 || sec == 119) t.ctx.takeScreenshot("refine_director_" + sec);
            }
        }
        log("director over 150 s of walking: {} packs, {} processions, {} bodies placed; most dead within 64 blocks at once {}; failures: spot {}, path {}, arrival {}, cap {}; samples {}",
                LimboDirector.packsSpawned - packs0, LimboDirector.processionsSpawned - procs0, LimboDirector.bodiesSpawned - bodies0, maxNear,
                LimboDirector.failSpot, LimboDirector.failPath, LimboDirector.failArrival, LimboDirector.deferredCap, samples);
        t.soft(LimboDirector.packsSpawned - packs0 >= 2, "packs keep coming during a walk (" + (LimboDirector.packsSpawned - packs0) + " in 150 s)");
        cmd(t.sp, "deathmask limbo processions off");
        cmd(t.sp, "gamerule spawn_monsters false");
        clearAll();
        tp(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
    }

    /** Next standable spot ~4 blocks along {@code heading}, following the ground up or down a little. */
    private static BlockPos stepOn(ServerLevel level, BlockPos from, double heading) {
        for (int turn = 0; turn < 8; turn++) {
            double a = heading + turn * 0.8 * (turn % 2 == 0 ? 1 : -1);
            int x = (int) Math.round(from.getX() + Math.cos(a) * 4), z = (int) Math.round(from.getZ() + Math.sin(a) * 4);
            BlockPos g = ryuna.qdeath.limbo.Processions.ground(level, x, z, from.getY(), 3);
            if (g != null) return g;
        }
        return null;
    }

    /** {dead within 64, groups (clusters of 3+ within 8 blocks), dead hunting}. */
    private static int[] groups(ServerLevel level, Vec3 c) {
        List<LimboDead> dead = level.getEntitiesOfClass(LimboDead.class, new AABB(c, c).inflate(64));
        List<LimboDead> left = new ArrayList<>(dead);
        int groups = 0;
        while (!left.isEmpty()) {
            LimboDead seed = left.removeFirst();
            List<LimboDead> cluster = new ArrayList<>(List.of(seed));
            for (int i = 0; i < cluster.size(); i++) {
                LimboDead d = cluster.get(i);
                left.removeIf(o -> { if (o.distanceTo(d) < 8) { cluster.add(o); return true; } return false; });
            }
            if (cluster.size() >= 3) groups++;
        }
        int hunting = (int) dead.stream().filter(d -> d.mind().alert()).count();
        return new int[]{dead.size(), groups, hunting};
    }

    private static String r3(double v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }
}
