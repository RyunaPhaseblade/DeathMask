package ryuna.qdeath.test;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.entity.limbo.BileResidue;
import ryuna.qdeath.entity.limbo.BreachTickerEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.StranglerEntity;
import ryuna.qdeath.entity.limbo.TongueSegment;
import ryuna.qdeath.entity.projectile.BileBolus;
import ryuna.qdeath.entity.projectile.RibShard;
import ryuna.qdeath.limbo.Breach;
import ryuna.qdeath.limbo.Grips;
import ryuna.qdeath.limbo.HordePaths;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.limbo.Horde;
import ryuna.qdeath.limbo.Stimuli;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ryuna.qdeath.test.LimboExpansionTest.get;
import static ryuna.qdeath.test.LimboExpansionTest.limbo;
import static ryuna.qdeath.test.LimboExpansionTest.log;
import static ryuna.qdeath.test.LimboExpansionTest.player;

/** The fights of the expansion, on the flat stage of the Limbo: Ticker, tongue, throwers, senses, routes and the horde. */
final class ExpansionCombat {
    private final LimboExpansionTest t;
    private final BlockPos a;
    private final BlockState stone = Blocks.COBBLESTONE.defaultBlockState(), brick = Blocks.STONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();

    ExpansionCombat(LimboExpansionTest t) { this.t = t; this.a = t.stage; }

    private static String r2(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    private float health() { return t.server(s -> player(s).getHealth()); }
    private Vec3 ppos() { return t.server(s -> player(s).position()); }
    private void heal() { t.run(s -> player(s).setHealth(player(s).getMaxHealth())); t.waitTicks(2); }

    /** A closed room (walls, roof) around {@code c}, inner 3 x 3 x 3; the stage floor is its floor. */
    private void room(BlockPos c, BlockState wall, boolean marked) {
        t.box(c.offset(-2, 0, -2), c.offset(2, 3, 2), wall, marked);
        t.box(c.offset(-1, 0, -1), c.offset(1, 2, 1), air, false);
    }

    private int countMissing(BlockPos from, BlockPos to, BlockState expected) {
        return t.server(s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(from, to)) if (!limbo(s).getBlockState(p).is(expected.getBlock())) n++;
            return n;
        });
    }

    // ================================================================== the Breach Ticker
    void ticker() {
        t.cmd("gamerule mob_griefing true");
        horizontal();
        unknownWall();
        protectedStops();
        vertical();
        diagonal();
        interruption();
        noGriefing();
        adhesion();
        t.creative();
        t.clearStage();
    }

    /** Spawns a Ticker {@code d} blocks east of {@code c} hunting the player inside the room at {@code c}. */
    private UUID tickerAt(BlockPos c, double dx, double dy, double dz) {
        UUID k = t.spawn(HellEntities.BREACH_TICKER, c.getX() + 0.5 + dx, c.getY() + dy, c.getZ() + 0.5 + dz, 90, null);
        t.engage(k);
        return k;
    }

    private void horizontal() {
        t.clearStage();
        BlockPos c = a;
        room(c, stone, true);
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(false);
        // A pig against the outer wall, where the burst opens it: no damage, no push.
        UUID pig = t.spawn(EntityTypes.PIG, c.getX() + 3.5, c.getY(), c.getZ() + 2.5, 0, m -> m.setNoAi(true));
        int total = 5 * 4 * 5 - 3 * 3 * 3;
        UUID k = tickerAt(c, 9, 0, 0);
        int clung = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        Vec3 before = ppos();
        int burst = t.until(s -> get(s, k) == null, 120);
        t.waitTicks(5);
        int opened = countMissing(c.offset(-2, 0, -2), c.offset(2, 3, 2), stone) - 27;
        boolean east = countMissing(c.offset(2, 0, -1), c.offset(2, 2, 1), stone) > 0;
        float hp = health();
        double moved = ppos().distanceTo(before);
        boolean pigOk = t.server(s -> get(s, pig) instanceof Mob m && m.getHealth() == m.getMaxHealth() && m.getDeltaMovement().horizontalDistance() < 0.01);
        log("ticker horizontal: clung after {} ticks, burst {}, {} of {} wall blocks opened, player hp {}, moved {}", clung, burst, opened, total, hp, r2(moved));
        t.soft(clung >= 0 && burst >= 0 && east && opened > 0 && opened <= LimboTuning.TICKER_MAX_BLOCKS,
                "Ticker: clings to the wall a player built and opens a horizontal corridor through it (" + opened + " blocks)");
        t.soft(hp >= 20f && moved < 0.05 && pigOk, "Ticker: the siege burst hurts and pushes nothing (player hp " + hp + ", moved " + r2(moved) + ", pig untouched " + pigOk + ")");
        t.soft(t.server(s -> Breach.opened) > 0, "Ticker: the burst consumed the Ticker (gone after the burst)");
    }

    private void unknownWall() {
        t.clearStage();
        BlockPos c = a;
        room(c, stone, false);
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(true);
        UUID k = tickerAt(c, 9, 0, 0);
        t.waitTicks(300);
        boolean never = t.server(s -> !(get(s, k) instanceof BreachTickerEntity b) || b.mode() != BreachTickerEntity.ON_BARRIER);
        int missing = countMissing(c.offset(-2, 0, -2), c.offset(2, 3, 2), stone) - 27;
        t.soft(never && missing == 0, "Ticker: a wall of unknown provenance (set by command, not by a player) is never opened (" + missing + " missing)");
    }

    /**
     * A shelter of unknown provenance (set by command) with an outer skin a player placed on its east side. Inside the
     * east wall: a chest, a votive lantern and a progression block; behind it, inside the room, a column of marked blocks.
     * The Ticker opens the skin (the only side it may open, wherever it comes from) and the burst stops at the first layer
     * it may not open: the protected blocks and the marked column behind them stay.
     */
    private void protectedStops() {
        t.clearStage();
        BlockPos c = a;
        room(c, brick, false);
        t.set(c.offset(2, 1, 0), Blocks.CHEST.defaultBlockState(), true);
        t.set(c.offset(2, 0, 0), ModBlocks.VOTIVE_LANTERN.defaultBlockState(), true);
        t.set(c.offset(2, 2, 0), ModBlocks.BREACH_ANCHOR.defaultBlockState(), true);
        t.box(c.offset(1, 0, -1), c.offset(1, 1, 1), stone, true);
        t.box(c.offset(3, 0, -2), c.offset(3, 3, 2), stone, true);
        t.tp(c.getX() - 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(true);
        UUID k = tickerAt(c, 0, 0, -9); // north: the straight line meets only unknown brick
        int clung = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        String at = t.server(s -> get(s, k) instanceof BreachTickerEntity b ? b.blockPosition().subtract(c).toShortString() : "none");
        int gone = t.until(s -> get(s, k) == null, 120);
        t.waitTicks(5);
        int outer = countMissing(c.offset(3, 0, -2), c.offset(3, 3, 2), stone);
        int inner = countMissing(c.offset(2, 0, -2), c.offset(2, 3, 2), brick) - 3; // the three protected blocks are not brick
        boolean prot = t.server(s -> limbo(s).getBlockState(c.offset(2, 1, 0)).is(Blocks.CHEST) && limbo(s).getBlockState(c.offset(2, 0, 0)).is(ModBlocks.VOTIVE_LANTERN)
                && limbo(s).getBlockState(c.offset(2, 2, 0)).is(ModBlocks.BREACH_ANCHOR));
        int behind = countMissing(c.offset(1, 0, -1), c.offset(1, 1, 1), stone);
        int shell = countMissing(c.offset(-2, 0, -2), c.offset(2, 3, 2), brick) - 27 - 3; // the inside (air and the column), the protected blocks
        log("ticker protected: clung at {} after {}, burst {}, outer {} opened, inner {}, behind {}, shell {}", at, clung, gone, outer, inner, behind, shell);
        t.soft(clung >= 0 && gone >= 0 && outer > 0, "Ticker: from any side, it finds the face a player built and opens that outer layer (" + outer + ")");
        t.soft(inner == 0 && prot && shell == 0, "Ticker: blocks of unknown provenance, a container, a votive lantern and a progression block are never broken");
        t.soft(behind == 0, "Ticker: an unauthorized solid layer stops the burst: the marked blocks behind it are untouched (" + behind + " missing)");
    }

    /** A bunker whose roof a player built, the player inside, the Ticker on the roof: a corridor straight down. */
    private void vertical() {
        t.clearStage();
        BlockPos c = a;
        t.box(c.offset(-2, 0, -2), c.offset(2, 3, 2), brick, false);
        t.box(c.offset(-1, 0, -1), c.offset(1, 2, 1), air, false);
        t.box(c.offset(-2, 3, -2), c.offset(2, 3, 2), stone, true);
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(true);
        UUID k = tickerAt(c, 1, 4, 0);
        int clung = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        int burst = t.until(s -> get(s, k) == null, 120);
        t.waitTicks(5);
        int roof = countMissing(c.offset(-2, 3, -2), c.offset(2, 3, 2), stone);
        int walls = countMissing(c.offset(-2, 0, -2), c.offset(2, 2, 2), brick) - 27;
        t.soft(clung >= 0 && burst >= 0 && roof > 0 && walls == 0, "Ticker: vertical breach - the roof opens downward, the unknown walls stay (" + roof + " roof blocks)");
    }

    /** The player stands high inside a closed marked building: the Ticker clings low and the corridor rises diagonally. */
    private void diagonal() {
        t.clearStage();
        BlockPos c = a;
        t.box(c.offset(-3, 0, -3), c.offset(3, 7, 3), stone, true);
        t.box(c.offset(-2, 0, -2), c.offset(2, 6, 2), air, false);
        t.box(c.offset(-2, 0, -2), c.offset(0, 3, 2), brick, false); // an inner platform (unknown), the player on it at height 4
        t.tp(c.getX() - 0.5, c.getY() + 4, c.getZ() + 0.5);
        t.survival(true);
        Map<BlockPos, Boolean> before = new HashMap<>();
        UUID k = tickerAt(c, 9, 0, 0);
        t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        t.until(s -> get(s, k) == null, 120);
        t.waitTicks(5);
        List<BlockPos> gone = t.server(s -> {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, 7, 3))) {
                boolean shell = Math.abs(p.getX() - c.getX()) == 3 || Math.abs(p.getZ() - c.getZ()) == 3 || p.getY() - c.getY() == 7;
                if (shell && !limbo(s).getBlockState(p).is(Blocks.COBBLESTONE)) out.add(p.immutable());
            }
            return out;
        });
        int minY = gone.stream().mapToInt(BlockPos::getY).min().orElse(0), maxY = gone.stream().mapToInt(BlockPos::getY).max().orElse(0);
        log("ticker diagonal: {} shell blocks opened, y {}..{}", gone.size(), minY - c.getY(), maxY - c.getY());
        t.soft(!gone.isEmpty() && maxY - minY >= 2, "Ticker: diagonal breach - the corridor rises toward the player standing high (" + gone.size() + " blocks over " + (maxY - minY + 1) + " levels)");
    }

    private void interruption() {
        t.clearStage();
        BlockPos c = a;
        room(c, stone, true);
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(true);
        UUID k = tickerAt(c, 9, 0, 0);
        int clung = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        t.waitTicks(20);
        t.run(s -> { if (get(s, k) instanceof BreachTickerEntity b) b.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 1f); });
        t.waitTicks(2);
        boolean stunned = t.server(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.STUNNED);
        int reclung = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 400);
        int intactWhileStunned = 0;
        t.soft(clung >= 0 && stunned, "Ticker: a hit during the count tears it loose and stuns it");
        t.soft(reclung >= LimboTuning.TICKER_REATTACH - 10, "Ticker: it waits about five seconds before clinging again (" + reclung + " ticks)");
        t.until(s -> get(s, k) == null, 120);
    }

    private void noGriefing() {
        t.clearStage();
        t.cmd("gamerule mob_griefing false");
        BlockPos c = a;
        room(c, stone, true);
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(true);
        UUID k = tickerAt(c, 9, 0, 0);
        t.waitTicks(260);
        int missing = countMissing(c.offset(-2, 0, -2), c.offset(2, 3, 2), stone) - 27;
        t.soft(missing == 0, "Ticker: with mobGriefing off nothing is opened (" + missing + " missing)");
        t.cmd("gamerule mob_griefing true");
    }

    /** On the player: a two-second count, a burst that hurts (up to 10) and breaks nothing; a hit stops it. */
    private void adhesion() {
        t.clearStage();
        BlockPos c = a;
        t.box(c.offset(-2, 0, 3), c.offset(2, 1, 3), stone, true); // marked blocks within the radius: none may break
        t.tp(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        t.survival(false);
        t.ctx.getInput().lookAt(90, 0);
        UUID k = tickerAt(c, 3.5, 0, 0);
        int on = t.until(s -> get(s, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_VICTIM, 200);
        double speed = t.server(s -> player(s).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        boolean free = t.server(s -> !player(s).hasEffect(MobEffects.SLOWNESS));
        int burst = t.until(s -> get(s, k) == null, 80);
        t.waitTicks(3);
        float hp = health();
        int broken = countMissing(c.offset(-2, 0, 3), c.offset(2, 1, 3), stone);
        log("ticker adhesion: on victim after {}, burst after {}, hp {} (speed attribute {})", on, burst, hp, speed);
        t.soft(on >= 0 && burst >= 30, "Ticker: it leaps onto the player and counts about two seconds before the burst (" + burst + " ticks)");
        t.soft(hp < 20f && hp >= 9.5f, "Ticker: the burst on the victim hurts, at most 10 (hp " + hp + ")");
        t.soft(broken == 0, "Ticker: the burst on a victim breaks no block (" + broken + ")");
        t.soft(free && speed > 0.09, "Ticker: the adhesion does not hold the player in place (no slowness, speed " + r2(speed) + ")");
        // Interrupted: a hit while it counts.
        heal();
        UUID k2 = tickerAt(c, 3.5, 0, 0);
        t.until(s -> get(s, k2) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_VICTIM, 200);
        t.waitTicks(10);
        t.run(s -> { if (get(s, k2) instanceof BreachTickerEntity b) player(s).attack(b); });
        t.waitTicks(50);
        float hp2 = health();
        boolean alive = t.server(s -> get(s, k2) instanceof BreachTickerEntity b && b.isAlive());
        t.soft(hp2 >= 19.9f && alive, "Ticker: hitting it while it counts on the victim tears it off before the burst (hp " + hp2 + ")");
        t.run(s -> { if (get(s, k2) != null) get(s, k2).discard(); });
    }

    // ================================================================== the Strangler's tongue
    void tongue() {
        t.clearStage();
        t.survival(false);
        UUID s1 = strangler(11);
        // In flight first, then holding: it never connects instantly.
        int flying = t.until(s -> get(s, s1) instanceof StranglerEntity st && st.tongue() == StranglerEntity.FLYING, 300);
        int holding = t.until(s -> get(s, s1) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 40);
        double d0 = t.server(s -> player(s).distanceTo(get(s, s1)));
        t.waitTicks(40);
        double d1 = t.server(s -> player(s).distanceTo(get(s, s1)));
        t.soft(flying >= 0 && holding > 0, "tongue: announced, then it travels before it connects (" + flying + ", +" + holding + " ticks)");
        t.soft(d0 - d1 > 2.0 && d0 - d1 < 2.5 * 2 + 0.6, "tongue: the victim is dragged toward the Strangler, at most 2.5 blocks a second (" + r2(d0) + " -> " + r2(d1) + ")");
        int ended = t.until(s -> !Grips.held(player(s).getUUID()), 80);
        float hp = health();
        t.soft(ended >= 0 && ended + 40 + holding <= LimboTuning.TONGUE_HOLD + 30, "tongue: the hold ends by itself after at most four seconds");
        t.soft(hp >= 15.9f && hp <= 19f, "tongue: one point a second, at most four (hp " + hp + ")");
        boolean immune = t.server(s -> !Grips.canGrab(player(s)));
        t.waitTicks(LimboTuning.TONGUE_IMMUNITY + 5);
        boolean after = t.server(s -> Grips.canGrab(player(s)));
        t.soft(immune && after, "tongue: the freed victim is immune to tongues for six seconds, then not");
        t.clearStage();
        heal();
        // A shield turned to the throw stops the tongue reaching.
        t.cmd("item replace entity @p weapon.offhand with minecraft:shield");
        t.cmd("item replace entity @p weapon.mainhand with minecraft:air"); // a book in hand would be read instead (and pause the game)
        UUID s2 = strangler(11);
        t.ctx.getInput().lookAt(0, 0);
        t.ctx.getInput().holdKey(o -> o.keyUse);
        int blocked0 = t.server(s -> Grips.blocked);
        t.until(s -> get(s, s2) instanceof StranglerEntity st && st.tongue() == StranglerEntity.FLYING, 300);
        t.waitTicks(25);
        t.ctx.getInput().releaseKey(o -> o.keyUse);
        boolean shielded = t.server(s -> Grips.blocked > blocked0 && !Grips.held(player(s)));
        t.soft(shielded, "tongue: a shield turned toward the throw stops the tongue reaching");
        t.cmd("item replace entity @p weapon.offhand with minecraft:air");
        t.clearStage();
        heal();
        // Cut by the victim: an attack on a length of tongue.
        UUID s3 = strangler(10);
        t.until(s -> get(s, s3) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        boolean cut = t.server(s -> {
            TongueSegment seg = nearestSegment(s);
            if (seg == null) return false;
            player(s).attack(seg);
            return !Grips.held(player(s));
        });
        t.soft(cut, "tongue: the victim cuts the tongue with an attack on it");
        t.clearStage();
        // Cut by an ally (a second player).
        UUID s4 = strangler(10);
        t.until(s -> get(s, s4) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        boolean ally = t.server(s -> {
            TongueSegment seg = nearestSegment(s);
            if (seg == null) return false;
            FakePlayer fp = FakePlayer.get(limbo(s));
            fp.snapTo(seg.getX() + 1, seg.getY(), seg.getZ(), 0, 0);
            fp.attack(seg);
            return !Grips.held(player(s));
        });
        t.soft(ally, "tongue: an ally cuts the tongue holding a friend");
        t.clearStage();
        // A wall comes between: the tongue breaks.
        UUID s5 = strangler(10);
        t.until(s -> get(s, s5) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        t.run(s -> {
            Vec3 mid = player(s).position().add(get(s, s5).position()).scale(0.5);
            BlockPos m = BlockPos.containing(mid);
            for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 3; dy++) limbo(s).setBlockAndUpdate(m.offset(dx, dy, 0), Blocks.STONE.defaultBlockState());
        });
        int walled = t.until(s -> !Grips.held(player(s)), 10);
        t.soft(walled >= 0, "tongue: a solid wall between the Strangler and its victim breaks the tongue (" + walled + " ticks)");
        t.clearStage();
        // Hurting the captor breaks the grip; so does its death.
        UUID s6 = strangler(10);
        t.until(s -> get(s, s6) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        t.run(s -> get(s, s6).hurtServer(limbo(s), limbo(s).damageSources().generic(), 1f));
        boolean hurtBreaks = t.server(s -> !Grips.held(player(s)));
        t.soft(hurtBreaks, "tongue: any damage to the Strangler breaks the grip");
        t.clearStage();
        UUID s7 = strangler(10);
        t.until(s -> get(s, s7) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        t.run(s -> { if (get(s, s7) instanceof StranglerEntity st) st.kill(limbo(s)); });
        t.waitTicks(2);
        boolean deathBreaks = t.server(s -> !Grips.held(player(s)) && player(s).getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                .getModifier(ryuna.qdeath.Quantumdeath.id("tongue_drag")) == null);
        t.soft(deathBreaks, "tongue: the Strangler's death frees the victim and leaves nothing on it");
        t.clearStage();
        // Consecrated light: stepping into it ends the grip.
        UUID s8 = strangler(10);
        t.until(s -> get(s, s8) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 300);
        BlockPos lamp = t.server(s -> player(s).blockPosition().offset(-1, 0, -1));
        t.run(s -> {
            limbo(s).setBlockAndUpdate(lamp, ModBlocks.VOTIVE_LANTERN.defaultBlockState());
            VotiveLantern.consecrate(limbo(s), lamp, limbo(s).getBlockState(lamp), LimboTuning.MAX_FUEL);
        });
        int holy = t.until(s -> !Grips.held(player(s)), 20);
        t.soft(holy >= 0, "tongue: consecrated light breaks the grip (" + holy + " ticks)");
        t.clearStage();
        // One victim, one grip: two Stranglers at once.
        heal();
        UUID s9 = strangler(10), s10 = strangler(-10);
        int one = t.until(s -> Grips.held(player(s)), 300);
        boolean single = true;
        for (int i = 0; i < 20; i++) {
            t.waitTicks(2);
            single &= t.server(s -> limbo(s).getEntitiesOfClass(StranglerEntity.class, new AABB(a).inflate(30), st -> st.tongue() == StranglerEntity.HOLDING).size() <= 1);
        }
        t.soft(one >= 0 && single, "tongue: a victim admits a single grip (two Stranglers never hold it together)");
        t.creative();
        t.clearStage();
    }

    private UUID strangler(double dz) {
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID id = t.spawn(HellEntities.STRANGLER, a.getX() + 0.5, a.getY(), a.getZ() + 0.5 + dz, dz > 0 ? 180 : 0, null);
        t.engage(id);
        return id;
    }

    private static TongueSegment nearestSegment(MinecraftServer s) {
        return limbo(s).getEntitiesOfClass(TongueSegment.class, player(s).getBoundingBox().inflate(20)).stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player(s)))).orElse(null);
    }

    // ================================================================== the throwers
    void shooters() {
        t.clearStage();
        // Direct hit in the open: bile 5 and a short slowness (never stacked), and a puddle that lasts three seconds.
        t.survival(false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID sp = t.spawn(HellEntities.BILE_SPITTER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(sp);
        int hit = t.until(s -> player(s).getHealth() < 20f, 300);
        float hp = health();
        var slow = t.server(s -> player(s).getEffect(MobEffects.SLOWNESS));
        int residue = t.until(s -> !limbo(s).getEntitiesOfClass(BileResidue.class, new AABB(a).inflate(30)).isEmpty(), 20);
        int gone = t.until(s -> limbo(s).getEntitiesOfClass(BileResidue.class, new AABB(a).inflate(30)).isEmpty(), 100);
        t.soft(hit >= 0 && Math.abs(hp - 15f) < 0.6f, "throwers: a bolus of bile hits for 5 (hp " + hp + ")");
        t.soft(slow != null && slow.getAmplifier() == 0 && slow.getDuration() <= LimboTuning.BILE_SLOW, "throwers: and slows (Slowness I, at most two seconds)");
        t.soft(residue >= 0 && gone >= 40, "throwers: the bile leaves a puddle that lasts about three seconds (" + gone + " more ticks)");
        t.clearStage();
        // Rib: 6 on a direct hit.
        heal();
        UUID rb = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 14.5, 180, null);
        t.engage(rb);
        int ribHit = t.until(s -> player(s).getHealth() < 20f, 300);
        float hp2 = health();
        t.soft(ribHit >= 0 && Math.abs(hp2 - 14f) < 0.6f, "throwers: a rib hits for 6 (hp " + hp2 + ")");
        t.clearStage();
        // A shield raised toward the thrower blocks the direct hit (no damage, no slowness).
        heal();
        t.cmd("item replace entity @p weapon.offhand with minecraft:shield");
        t.cmd("item replace entity @p weapon.mainhand with minecraft:air"); // a book in hand would be read instead (and pause the game)
        t.ctx.getInput().lookAt(0, 0);
        t.ctx.getInput().holdKey(o -> o.keyUse);
        UUID rb2 = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(rb2);
        int thrown = t.until(s -> !limbo(s).getEntitiesOfClass(RibShard.class, new AABB(a).inflate(30)).isEmpty(), 300);
        t.waitTicks(30);
        t.ctx.getInput().releaseKey(o -> o.keyUse);
        float hp3 = health();
        t.soft(thrown >= 0 && hp3 >= 19.9f, "throwers: a raised shield blocks the rib (hp " + hp3 + ")");
        t.run(s -> { if (get(s, rb2) != null) get(s, rb2).discard(); });
        t.clearStage();
        UUID sp2 = t.spawn(HellEntities.BILE_SPITTER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(sp2);
        t.ctx.getInput().lookAt(0, -15);
        t.ctx.getInput().holdKey(o -> o.keyUse);
        int lobbed = t.until(s -> !limbo(s).getEntitiesOfClass(BileBolus.class, new AABB(a).inflate(30)).isEmpty(), 300);
        t.waitTicks(40);
        t.ctx.getInput().releaseKey(o -> o.keyUse);
        float hp4 = health();
        boolean noSlow = t.server(s -> player(s).getEffect(MobEffects.SLOWNESS) == null || player(s).getEffect(MobEffects.SLOWNESS).getDuration() < 15);
        t.soft(lobbed >= 0 && hp4 >= 19.9f, "throwers: a raised shield blocks the bolus (hp " + hp4 + ", slowness only from a puddle stepped in: " + noSlow + ")");
        t.cmd("item replace entity @p weapon.offhand with minecraft:air");
        t.clearStage();
        // Cover: behind a wall nothing is thrown at all.
        heal();
        t.box(a.offset(-3, 0, 3), a.offset(3, 3, 3), brick, false);
        UUID rb3 = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(rb3);
        t.waitTicks(160);
        int shots = t.server(s -> limbo(s).getEntitiesOfClass(RibShard.class, new AABB(a).inflate(30)).size());
        float hp5 = health();
        t.soft(hp5 >= 19.9f, "throwers: cover protects (no hit behind a wall; " + shots + " ribs in the air)");
        t.clearStage();
        // Height: the player on a pillar is still reached (the arc rises to it).
        heal();
        t.box(a.offset(-1, 0, -1), a.offset(1, 3, 1), brick, false);
        t.tp(a.getX() + 0.5, a.getY() + 4, a.getZ() + 0.5);
        UUID sp3 = t.spawn(HellEntities.BILE_SPITTER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(sp3);
        int high = t.until(s -> player(s).getHealth() < 20f, 300);
        t.soft(high >= 0, "throwers: a player standing high is reached (" + high + " ticks)");
        t.clearStage();
        // Dodging: stepping aside after the throw makes it miss.
        heal();
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID sp4 = t.spawn(HellEntities.BILE_SPITTER, a.getX() + 0.5, a.getY(), a.getZ() + 14.5, 180, null);
        t.engage(sp4);
        t.until(s -> !limbo(s).getEntitiesOfClass(BileBolus.class, new AABB(a).inflate(30)).isEmpty(), 300);
        t.tp(a.getX() + 4.5, a.getY(), a.getZ() + 0.5);
        t.waitTicks(40);
        float hp6 = health();
        t.soft(hp6 >= 19.9f, "throwers: stepping aside dodges the arc (hp " + hp6 + ")");
        t.clearStage();
        // A dead in the line: no throw while it stands there; the throw comes once it moves.
        heal();
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID wall = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, 180, m -> m.setNoAi(true));
        UUID rb4 = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.run(s -> { if (get(s, rb4) instanceof Mob m) m.setNoAi(false); });
        t.engage(rb4);
        t.waitTicks(120);
        int during = t.server(s -> limbo(s).getEntitiesOfClass(RibShard.class, new AABB(a).inflate(30)).size());
        float hp7 = health();
        t.soft(hp7 >= 19.9f, "throwers: a dead standing in the line of throw cancels the throw (" + during + " ribs thrown)");
        t.clearStage();
        // Keeping distance: too close, the thrower backs away to 6-12 blocks.
        t.survival(true);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID rb5 = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 3.5, 180, null);
        t.engage(rb5);
        t.waitTicks(100);
        double dist = t.server(s -> get(s, rb5).distanceTo(player(s)));
        t.soft(dist >= 5.0, "throwers: a thrower too close backs off to throwing distance (" + r2(dist) + " blocks)");
        t.clearStage();
        // Out of sight: the thrower goes to look rather than throwing blind.
        t.box(a.offset(-4, 0, 4), a.offset(4, 4, 4), brick, false);
        UUID rb6 = t.spawn(HellEntities.RIB_THROWER, a.getX() + 0.5, a.getY(), a.getZ() + 12.5, 180, null);
        t.engage(rb6);
        Vec3 from = t.server(s -> get(s, rb6).position());
        t.waitTicks(120);
        Vec3 to = t.server(s -> get(s, rb6).position());
        int blind = t.server(s -> limbo(s).getEntitiesOfClass(RibShard.class, new AABB(a).inflate(30)).size());
        t.soft(to.distanceTo(from) > 2 && blind == 0, "throwers: out of sight it moves to look, without throwing blind (moved " + r2(to.distanceTo(from)) + ")");
        t.creative();
        t.clearStage();
    }

    // ================================================================== senses
    void senses() {
        t.clearStage();
        t.survival(true);
        // The player shut in a room; outside its south wall, two listeners each side of half the radius.
        t.box(a.offset(-3, 0, -3), a.offset(3, 4, 3), brick, false);
        t.box(a.offset(-2, 0, -2), a.offset(2, 3, 2), air, false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        t.ctx.getInput().lookAt(180, 0);
        UUID near = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, 0, null); // 7 blocks from the mined block: within 16 / 2
        UUID far = t.spawn(HellEntities.HOLLOW, a.getX() + 3.5, a.getY(), a.getZ() + 10.5, 0, null);
        t.run(s -> { for (UUID id : List.of(near, far)) if (get(s, id) instanceof LimboDead d) d.setResting(true); });
        // Mining (obsidian: by hand it never breaks, so this is mining in progress only).
        t.set(a.offset(0, 1, -1), Blocks.OBSIDIAN.defaultBlockState(), false);
        t.ctx.getInput().lookAt(180, 20);
        int pub0 = t.server(s -> Stimuli.published);
        t.ctx.getInput().holdKey(o -> o.keyAttack);
        t.waitTicks(40);
        t.ctx.getInput().releaseKey(o -> o.keyAttack);
        int pub = t.server(s -> Stimuli.published) - pub0;
        boolean nearSearch = t.server(s -> get(s, near) instanceof LimboDead d && d.mind() == Mind.SEARCH && d.quarry() == null && d.lastStimulus() == Stimuli.Kind.MINING);
        boolean farQuiet = t.server(s -> get(s, far) instanceof LimboDead d && d.lastStimulus() == null);
        t.soft(pub >= 2 && pub <= 6, "senses: mining is heard at most once each half second (" + pub + " in 2 s)");
        t.soft(nearSearch, "senses: a dead behind a wall within half the radius hears the mining and searches, without being given the player");
        t.soft(farQuiet, "senses: a solid wall halves the distance a sound carries (the farther one hears nothing)");
        t.waitTicks(100);
        boolean noChase = t.server(s -> get(s, near) instanceof LimboDead d && d.quarry() == null && d.mind() != Mind.PURSUIT);
        t.soft(noChase, "senses: the noise starts a search, not a chase (no sight of the player through the wall)");
        t.clearStage();
        // Opening a chest (the real client), heard within 12.
        t.set(a.offset(0, 0, -2), Blocks.CHEST.defaultBlockState(), false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID c1 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 8.5, 0, null);
        UUID c2 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() - 16.5, 180, null);
        t.run(s -> { for (UUID id : List.of(c1, c2)) if (get(s, id) instanceof LimboDead d) d.setResting(true); });
        t.cmd("item replace entity @p weapon.mainhand with minecraft:air");
        t.ctx.getInput().lookAt(a.offset(0, 0, -2));
        t.waitTicks(5);
        t.ctx.getInput().pressKey(o -> o.keyUse);
        t.waitTicks(10);
        t.ctx.runOnClient(mc -> mc.player.closeContainer());
        boolean heard = t.server(s -> get(s, c1) instanceof LimboDead d && d.lastStimulus() == Stimuli.Kind.CONTAINER);
        boolean notFar = t.server(s -> get(s, c2) instanceof LimboDead d && d.lastStimulus() != Stimuli.Kind.CONTAINER);
        t.soft(heard && notFar, "senses: opening a chest is heard within 12 blocks (and not at 17)");
        t.clearStage();
        // Eating (hungry, with bread): heard within 8.
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        t.cmd("effect clear @p minecraft:saturation");
        t.run(s -> player(s).getFoodData().setFoodLevel(6));
        t.cmd("item replace entity @p weapon.mainhand with minecraft:bread 4");
        UUID e1 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() - 6.5, 180, null);
        UUID e2 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 10.5, 0, null);
        t.run(s -> { for (UUID id : List.of(e1, e2)) if (get(s, id) instanceof LimboDead d) { d.setResting(true); d.setYRot(d.getYRot() + 180); } });
        t.ctx.getInput().lookAt(90, 0);
        t.ctx.getInput().holdKey(o -> o.keyUse);
        t.waitTicks(40);
        t.ctx.getInput().releaseKey(o -> o.keyUse);
        boolean ate = t.server(s -> get(s, e1) instanceof LimboDead d && d.lastStimulus() == Stimuli.Kind.EAT);
        boolean far8 = t.server(s -> get(s, e2) instanceof LimboDead d && d.lastStimulus() != Stimuli.Kind.EAT);
        t.soft(ate && far8, "senses: eating is heard within 8 blocks (and not at 10)");
        t.cmd("effect give @p minecraft:saturation infinite 0 true");
        t.clearStage();
        // An unattended torch in view: the dead go to look, find nobody, give up, and ignore it for a while.
        t.creative();
        t.set(a.offset(0, 0, 10), Blocks.TORCH.defaultBlockState(), false);
        UUID l1 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, null);
        t.tp(a.getX() + 0.5, a.getY() + 12, a.getZ() - 22.5); // far enough not to be seen, near enough for it to think
        int drawn = t.until(s -> get(s, l1) instanceof LimboDead d && d.lastStimulus() == Stimuli.Kind.LIGHT, 200);
        int reached = t.until(s -> get(s, l1) instanceof LimboDead d && d.position().distanceTo(Vec3.atCenterOf(a.offset(0, 0, 10))) < 3.5, 300);
        boolean chased = t.server(s -> get(s, l1) instanceof LimboDead d && (d.mind() == Mind.PURSUIT || d.quarry() != null));
        t.soft(drawn >= 0 && reached >= 0 && !chased, "senses: a lit torch in view draws a dead to look; an empty light starts no chase");
        t.waitTicks(260);
        boolean ignores = t.server(s -> get(s, l1) instanceof LimboDead d && d.mind() != Mind.SEARCH);
        t.soft(ignores, "senses: once inspected, the empty light is ignored for a while");
        t.clearStage();
        // A votive lantern draws nobody; a torch out of sight draws nobody.
        t.set(a.offset(0, 0, 10), ModBlocks.VOTIVE_LANTERN.defaultBlockState(), false);
        t.set(a.offset(8, 0, -10), Blocks.TORCH.defaultBlockState(), false);
        t.box(a.offset(5, 0, -8), a.offset(11, 3, -8), brick, false);
        UUID l2 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, null);
        t.waitTicks(200);
        boolean none = t.server(s -> get(s, l2) instanceof LimboDead d && d.lastStimulus() != Stimuli.Kind.LIGHT);
        t.soft(none, "senses: votive lanterns and lights out of sight draw nobody");
        t.clearStage();
    }

    // ================================================================== routes
    void navigation() {
        t.cmd("gamerule mob_griefing true");
        t.survival(true);
        // A tunnel the player sealed but for a glass pane: the dead break the pane (after a visible preparation) and come in.
        t.clearStage();
        t.box(a.offset(-2, 0, -6), a.offset(2, 3, 6), brick, false);
        t.box(a.offset(-1, 0, -5), a.offset(1, 2, 5), air, false);
        t.set(a.offset(0, 0, 6), Blocks.GLASS.defaultBlockState(), false);
        t.set(a.offset(0, 1, 6), Blocks.GLASS.defaultBlockState(), false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID g = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 10.5, 180, null);
        t.engage(g);
        int broke = t.until(s -> !limbo(s).getBlockState(a.offset(0, 0, 6)).is(Blocks.GLASS) || !limbo(s).getBlockState(a.offset(0, 1, 6)).is(Blocks.GLASS), 400);
        int inside = t.until(s -> get(s, g) instanceof LimboDead d && d.getZ() < a.getZ() + 5.5, 200);
        t.soft(broke >= LimboTuning.WEAK_PREPARE && inside >= 0, "routes: a sealed tunnel's glass is broken after a preparation and the dead come in (" + broke + " ticks)");
        t.clearStage();
        // A hit during the preparation cancels it.
        t.box(a.offset(-2, 0, -6), a.offset(2, 3, 6), brick, false);
        t.box(a.offset(-1, 0, -5), a.offset(1, 2, 5), air, false);
        t.set(a.offset(0, 0, 6), Blocks.GLASS.defaultBlockState(), false);
        t.set(a.offset(0, 1, 6), Blocks.GLASS.defaultBlockState(), false);
        UUID g2 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 8.5, 180, null);
        t.engage(g2);
        int cracking = t.until(s -> limbo(s).getBlockState(a.offset(0, 0, 6)).is(Blocks.GLASS) && get(s, g2) instanceof LimboDead d && d.animId() == LimboDead.ANIM_SIEGE, 400);
        t.run(s -> get(s, g2).hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 1f));
        t.waitTicks(20);
        boolean intact = t.server(s -> limbo(s).getBlockState(a.offset(0, 0, 6)).is(Blocks.GLASS) && limbo(s).getBlockState(a.offset(0, 1, 6)).is(Blocks.GLASS));
        t.soft(cracking >= 0 && intact, "routes: damage during the preparation cancels the break");
        t.clearStage();
        // Without mobGriefing the glass holds.
        t.cmd("gamerule mob_griefing false");
        t.box(a.offset(-2, 0, -6), a.offset(2, 3, 6), brick, false);
        t.box(a.offset(-1, 0, -5), a.offset(1, 2, 5), air, false);
        t.set(a.offset(0, 0, 6), Blocks.GLASS.defaultBlockState(), false);
        t.set(a.offset(0, 1, 6), Blocks.GLASS.defaultBlockState(), false);
        UUID g3 = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 9.5, 180, null);
        t.engage(g3);
        t.waitTicks(160);
        boolean held = t.server(s -> limbo(s).getBlockState(a.offset(0, 0, 6)).is(Blocks.GLASS) && limbo(s).getBlockState(a.offset(0, 1, 6)).is(Blocks.GLASS));
        t.soft(held, "routes: with mobGriefing off no weak block is broken");
        t.cmd("gamerule mob_griefing true");
        t.clearStage();
        // A ladder and a vine up a pillar.
        climbable(true);
        climbable(false);
        // A tower with no ladder and only two dead: one on the other's shoulders is not enough for four blocks.
        t.clearStage();
        t.box(a.offset(-1, 0, -1), a.offset(1, 3, 1), brick, false);
        t.tp(a.getX() + 0.5, a.getY() + 4, a.getZ() + 0.5);
        List<UUID> few = new ArrayList<>();
        for (int i = 0; i < 2; i++) { UUID id = t.spawn(HellEntities.HOLLOW, a.getX() + 4.5 + i, a.getY(), a.getZ() + 0.5, 90, null); t.engage(id); few.add(id); }
        t.waitTicks(200);
        double maxY = few.stream().mapToDouble(id -> t.server(s -> get(s, id) != null ? get(s, id).getY() : 0)).max().orElse(0);
        t.soft(maxY < a.getY() + 3.9, "routes: the common dead are given no free climbing of walls (max height " + r2(maxY - a.getY()) + ")");
        t.clearStage();
        // A route changed under a pursuit: the shared route through a closed corridor is dropped and they go round.
        t.box(a.offset(-8, 0, 2), a.offset(8, 3, 2), brick, false);
        t.set(a.offset(-6, 0, 2), air, false);
        t.set(a.offset(-6, 1, 2), air, false);
        t.set(a.offset(6, 0, 2), air, false);
        t.set(a.offset(6, 1, 2), air, false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        List<UUID> pack = new ArrayList<>();
        for (int i = 0; i < 4; i++) { UUID id = t.spawn(HellEntities.HOLLOW, a.getX() - 3.5 + i * 2, a.getY(), a.getZ() + 9.5, 180, null); t.engage(id); pack.add(id); }
        t.waitTicks(25);
        long inv0 = HordePaths.invalidated;
        // Close the western gap the routes go through.
        t.set(a.offset(-6, 0, 2), brick, false);
        t.set(a.offset(-6, 1, 2), brick, false);
        int arrived = t.until(s -> pack.stream().anyMatch(id -> get(s, id) != null && get(s, id).distanceTo(player(s)) < 2.5), 400);
        log("routes: shared routes invalidated by the change: {}", String.valueOf(HordePaths.invalidated - inv0));
        t.soft(arrived >= 0, "routes: after a block change closes their way, they reach the player by the other opening (" + arrived + " ticks)");
        // Bodies of different builds never share a route.
        boolean apart = t.server(s -> {
            LimboDead hollow = HellEntities.HOLLOW.create(limbo(s), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            LimboDead crawler = HellEntities.CRAWLER.create(limbo(s), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            Mob spider = EntityTypes.SPIDER.create(limbo(s), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            return profile(hollow) != profile(crawler) && profile(hollow) != profile(spider) && profile(crawler) != profile(spider);
        });
        t.soft(apart, "routes: a Crawler, a man and a climbing spider host never share a route");
        t.creative();
        t.clearStage();
    }

    private static int profile(Mob m) {
        try {
            var f = HordePaths.class.getDeclaredMethod("profile", Mob.class);
            f.setAccessible(true);
            return (int) f.invoke(null, m);
        } catch (Exception e) { return -1; }
    }

    private void climbable(boolean ladder) {
        t.clearStage();
        t.box(a.offset(-1, 0, -1), a.offset(1, 4, 1), brick, false);
        for (int y = 0; y <= 4; y++) {
            BlockState s = ladder ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST)
                    : Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true);
            t.set(a.offset(2, y, 0), s, false);
        }
        t.tp(a.getX() + 0.5, a.getY() + 5, a.getZ() + 0.5);
        UUID id = t.spawn(HellEntities.HOLLOW, a.getX() + 7.5, a.getY(), a.getZ() + 0.5, 90, null);
        t.engage(id);
        int up = t.until(s -> get(s, id) instanceof LimboDead d && d.getY() >= a.getY() + 4.9, 600);
        t.soft(up >= 0, "routes: a dead climbs a " + (ladder ? "ladder" : "vine") + " up to the player (" + up + " ticks)");
    }

    // ================================================================== the horde's instincts (Horde)
    void horde() {
        t.cmd("gamerule mob_griefing true");
        t.survival(true);
        for (int h : new int[]{3, 6, 9}) pile(h);
        sharedPile();
        openGround();
        overhang();
        consecratedFoot();
        fire();
        dig();
        t.creative();
        t.clearStage();
    }

    private List<UUID> crowd(int n, double r0) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double ang = i * 2 * Math.PI / n, r = r0 + (i % 3);
            UUID id = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5 + Math.cos(ang) * r, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * r, 0, null);
            t.engage(id);
            out.add(id);
        }
        return out;
    }

    private void tower(int h) {
        t.clearStage();
        t.box(a.offset(-1, 0, -1), a.offset(1, h - 1, 1), brick, false);
        t.tp(a.getX() + 0.5, a.getY() + h, a.getZ() + 0.5);
    }

    /** The first body of {@code ids} found inside a block, described, or "". */
    private String inside(List<UUID> ids) {
        return t.server(s -> {
            for (int i = 0; i < ids.size(); i++) if (get(s, ids.get(i)) instanceof LimboDead d
                    && limbo(s).getBlockCollisions(d, d.getBoundingBox().deflate(0.05)).iterator().hasNext())
                return "#" + i + " role " + d.moundRole() + " mind " + d.debugState() + " at " + d.position().subtract(Vec3.atLowerCornerOf(a));
            return "";
        });
    }

    private void pile(int h) {
        tower(h);
        int piles0 = Horde.piles, over0 = Horde.overTop, lifts0 = Horde.lifts;
        List<UUID> crowd = crowd(Horde.crowdFor(h), 6);
        boolean clean = true;
        double highest = 0;
        int formed = -1, top = -1, limit = 500 + h * 100;
        for (int k = 0; k < limit; k += 5) {
            String in = inside(crowd);
            if (!in.isEmpty() && clean) { log("pile {}: a body is inside a block after {} ticks: {}", h, k, in); clean = false; }
            highest = Math.max(highest, t.server(s -> crowd.stream().mapToDouble(id -> get(s, id) != null ? get(s, id).getY() : 0).max().orElse(0)) - a.getY());
            if (formed < 0 && Horde.piles > piles0) formed = k;
            boolean up = t.server(s -> crowd.stream().anyMatch(id -> get(s, id) instanceof LimboDead d && d.getY() >= a.getY() + h - 0.05
                    && d.getX() > a.getX() - 1 && d.getX() < a.getX() + 2 && d.getZ() > a.getZ() - 1 && d.getZ() < a.getZ() + 2));
            if (up) { top = k; break; }
            t.waitTicks(5);
        }
        if (top < 0) log("pile {}: stalled - {}", h, t.server(s -> {
            StringBuilder b = new StringBuilder();
            for (UUID id : crowd) if (get(s, id) instanceof LimboDead d)
                b.append("[role ").append(d.moundRole()).append(" y ").append(r2(d.getY() - a.getY())).append(" foot ")
                        .append(r2(Math.sqrt(Horde.horizontalSqr(d.position(), Vec3.atBottomCenterOf(a))))).append(' ').append(d.debugState()).append("] ");
            return b.toString();
        }));
        log("pile {}: {} dead, pile announced after {}, highest body {}, first over the top after {} ticks ({} climbs, {} over)", h, crowd.size(), formed,
                r2(highest), top, Horde.lifts - lifts0, Horde.overTop - over0);
        t.soft(top >= 0, "horde: height " + h + " - the dead climb each other and get onto the tower (" + top + " ticks)");
        t.soft(clean, "horde: height " + h + " - no body ever ends up inside a block");
        t.clearStage();
    }

    /**
     * The dead that cannot reach a quarry above a wall share one pile: two dead on opposite sides of a tower are sent to
     * the same foot (the crowds above usually climb before anyone is stuck long enough to ask).
     */
    private void sharedPile() {
        tower(6);
        UUID east = t.spawn(HellEntities.HOLLOW, a.getX() + 6.5, a.getY(), a.getZ() + 0.5, 90, null);
        UUID west = t.spawn(HellEntities.HOLLOW, a.getX() - 5.5, a.getY(), a.getZ() + 0.5, 270, null);
        int piles0 = Horde.piles;
        String piles = t.server(s -> {
            if (!(get(s, east) instanceof LimboDead e) || !(get(s, west) instanceof LimboDead w)) return "missing";
            Horde.Ledge first = Horde.pile(limbo(s), e, player(s)), second = Horde.pile(limbo(s), w, player(s));
            return first != null && first.equals(second) ? "" : first + " / " + second;
        });
        t.soft(piles.isEmpty() && Horde.piles == piles0 + 1, "horde: dead on opposite sides of a tower share one pile (" + piles + ")");
        t.clearStage();
    }

    /** On open ground the ones behind scramble over the ones ahead; they still reach the player. */
    private void openGround() {
        t.clearStage();
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        int lifts0 = Horde.lifts;
        List<UUID> pack = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID id = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5 + (i % 4) * 0.6 - 0.9, a.getY(), a.getZ() + 12.5 + (i / 4) * 0.6, 180, null);
            t.engage(id);
            pack.add(id);
        }
        boolean clean = true;
        int reached = -1;
        for (int k = 0; k < 200; k += 5) {
            clean &= inside(pack).isEmpty();
            if (t.server(s -> pack.stream().anyMatch(id -> get(s, id) != null && get(s, id).distanceTo(player(s)) < 2.5))) { reached = k; break; }
            t.waitTicks(5);
        }
        t.waitTicks(60);
        clean &= inside(pack).isEmpty();
        log("open ground: {} climbs, reached after {}", Horde.lifts - lifts0, reached);
        t.soft(Horde.lifts > lifts0, "horde: on open ground the dead behind climb over the ones ahead (" + (Horde.lifts - lifts0) + ")");
        t.soft(reached >= 0 && clean, "horde: on open ground they still reach the player, never inside a block (" + reached + " ticks)");
        t.clearStage();
    }

    private void overhang() {
        t.clearStage();
        // A block of six (wall and platform) with a ledge jutting out over the foot of every face at height three: no pile
        // can stand anywhere around it, not even on the sides the crowd can walk round to.
        t.box(a.offset(-3, 0, 2), a.offset(3, 5, 2), brick, false);
        t.box(a.offset(-3, 0, 3), a.offset(3, 5, 9), brick, false);
        t.box(a.offset(-4, 3, 1), a.offset(4, 3, 10), brick, false);
        t.tp(a.getX() + 0.5, a.getY() + 6, a.getZ() + 5.5);
        List<UUID> crowd = new ArrayList<>();
        for (int i = 0; i < Horde.crowdFor(6); i++) {
            UUID id = t.spawn(HellEntities.HOLLOW, a.getX() - 4.5 + (i % 10), a.getY(), a.getZ() - 3.5 - (i / 10), 0, null);
            crowd.add(id);
        }
        // From the ground no pile can stand under it (the column over the foot is roofed by the ledge).
        boolean ledge = t.server(s -> crowd.stream().allMatch(id -> get(s, id) instanceof LimboDead d && Horde.findLedge(limbo(s), d, player(s)) == null));
        for (UUID id : crowd) t.engage(id);
        boolean clean = true;
        for (int k = 0; k < 300; k += 10) { clean &= inside(crowd).isEmpty(); t.waitTicks(10); }
        boolean onTop = t.server(s -> crowd.stream().anyMatch(id -> get(s, id) != null && get(s, id).getY() >= a.getY() + 5.9));
        log("overhang: anyone on top (piled up its outer face, then on the ledge) {}", onTop);
        // No pile under it and no body through it; climbing its outer face and then from the ledge is what anyone could do.
        t.soft(ledge && clean, "horde: an overhang over the foot of the wall: no pile under it, no body through it");
        t.clearStage();
    }

    private void consecratedFoot() {
        tower(4);
        BlockPos lamp = a.offset(4, 0, 4);
        t.run(s -> {
            limbo(s).setBlockAndUpdate(lamp, ModBlocks.VOTIVE_LANTERN.defaultBlockState());
            VotiveLantern.consecrate(limbo(s), lamp, limbo(s).getBlockState(lamp), LimboTuning.MAX_FUEL);
        });
        int piles0 = Horde.piles;
        crowd(Horde.crowdFor(4), 10);
        t.waitTicks(300);
        t.soft(Horde.piles == piles0, "horde: consecrated light at the foot of the wall prevents the pile");
        t.clearStage();
    }

    /** One burning dead in a packed group: the fire spreads by touch and the burning ones run faster. */
    private void fire() {
        t.clearStage();
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        int ign0 = Horde.ignitions;
        List<UUID> pack = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID id = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5 + (i % 4) * 0.55 - 0.8, a.getY(), a.getZ() + 14.5 + (i / 4) * 0.55, 180, null);
            pack.add(id);
        }
        double base = t.server(s -> get(s, pack.getFirst()) instanceof LimboDead d ? d.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) : 0.0);
        t.run(s -> {
            for (UUID id : pack) if (get(s, id) instanceof LimboDead d) d.engage(player(s));
            if (get(s, pack.getFirst()) instanceof LimboDead d) d.igniteForSeconds(8);
        });
        int spread = t.until(s -> pack.stream().filter(id -> get(s, id) != null && get(s, id).isOnFire()).count() >= 3, 80);
        String fast = t.server(s -> {
            StringBuilder b = new StringBuilder();
            for (UUID id : pack) if (get(s, id) instanceof LimboDead d && d.isOnFire())
                b.append(r2(d.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / base)).append(' ');
            return b.toString();
        });
        boolean faster = t.server(s -> pack.stream().allMatch(id -> !(get(s, id) instanceof LimboDead d) || !d.isOnFire()
                || (d.hasEffect(MobEffects.SPEED) && d.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) >= base * 1.5)));
        log("fire: spread to 3 after {} ticks, {} ignitions, burning speed x {}", spread, Horde.ignitions - ign0, fast);
        t.soft(spread >= 0, "horde: fire spreads from a burning dead to the ones it touches (" + spread + " ticks)");
        t.soft(faster && !fast.isEmpty(), "horde: the burning dead run faster (Speed, x" + fast.trim() + ")");
        t.clearStage();
    }

    /**
     * Digging the dead up: a hunting dead on soft ground claws at it for ten seconds and a common dead comes out beside it,
     * hunting too (and never digging itself); a blow during the digging stops it and nobody comes out.
     */
    private void dig() {
        t.clearStage();
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        t.box(a.offset(6, -1, -2), a.offset(10, -1, 2), ModBlocks.CINDER_SOIL.defaultBlockState(), false);
        AABB area = new AABB(a).inflate(20, 6, 20);
        int risen0 = Horde.risen;
        UUID digger = t.spawn(HellEntities.HOLLOW, a.getX() + 8.5, a.getY(), a.getZ() + 0.5, 90, null);
        t.run(s -> { if (get(s, digger) instanceof LimboDead d) { d.engage(player(s)); d.startDig(); } });
        t.waitTicks(LimboTuning.DIG_TICKS / 2);
        boolean stayed = t.server(s -> get(s, digger) instanceof LimboDead d && d.digging() && d.distanceTo(player(s)) > 6);
        int out = t.until(s -> Horde.risen > risen0, LimboTuning.DIG_TICKS);
        t.waitTicks(40);
        String risen = t.server(s -> {
            StringBuilder b = new StringBuilder();
            for (LimboDead d : limbo(s).getEntitiesOfClass(LimboDead.class, area, LimboDead::risen))
                b.append(d.mind()).append(" at ").append(r2(get(s, digger) != null ? d.distanceTo(get(s, digger)) : -1)).append(' ');
            return b.toString();
        });
        boolean hunting = t.server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, area, d -> d.risen() && d.mind() == Mind.PURSUIT).size() == 1);
        log("dig: digging after {} ticks {}, dug up after {} more, risen: {}", LimboTuning.DIG_TICKS / 2, stayed, out, risen);
        t.soft(stayed, "horde: the digging dead stays clawing at the ground, away from the player");
        t.soft(out >= 0 && hunting, "horde: after the digging a dead claws its way out beside it and hunts (" + risen.trim() + ")");
        // A blow interrupts it: nobody comes out.
        t.clearStage();
        t.box(a.offset(6, -1, -2), a.offset(10, -1, 2), ModBlocks.CINDER_SOIL.defaultBlockState(), false);
        int risen1 = Horde.risen;
        UUID second = t.spawn(HellEntities.HOLLOW, a.getX() + 8.5, a.getY(), a.getZ() + 0.5, 90, null);
        t.run(s -> { if (get(s, second) instanceof LimboDead d) { d.engage(player(s)); d.startDig(); } });
        t.waitTicks(60);
        t.run(s -> { if (get(s, second) instanceof LimboDead d) d.hurtServer(limbo(s), limbo(s).damageSources().playerAttack(player(s)), 1f); });
        boolean stopped = t.server(s -> get(s, second) instanceof LimboDead d && !d.digging());
        t.waitTicks(LimboTuning.DIG_TICKS);
        int dead = t.server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, area).size());
        t.soft(stopped && Horde.risen == risen1 && dead == 1, "horde: a blow stops the digging and nobody comes out (" + dead + " dead)");
        t.clearStage();
    }
}
