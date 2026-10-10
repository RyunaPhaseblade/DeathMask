package ryuna.qdeath.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import ryuna.qdeath.entity.limbo.BreachTickerEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.MourningParasiteEntity;
import ryuna.qdeath.entity.limbo.StranglerEntity;
import ryuna.qdeath.limbo.Horde;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.registry.HellEntities;

import java.util.List;
import java.util.UUID;

import static ryuna.qdeath.test.LimboExpansionTest.get;
import static ryuna.qdeath.test.LimboExpansionTest.limbo;

/**
 * Screenshots of the expansion in the Limbo's own light: every new creature with its skins, every announced signal
 * (the parasite's crouch and its tendrils, the Ticker's pulse, the Strangler's throat and its tongue in flight and
 * holding, the Spitter's open chest, the Rib Thrower's reach), the thrown pieces and the bile, a possessed host, a
 * mound of bodies. The screenshots are for review; the checks only make sure each scene happened.
 */
final class ExpansionVisuals {
    private final LimboExpansionTest t;

    ExpansionVisuals(LimboExpansionTest t) { this.t = t; }

    private void shot(String name) { t.ctx.takeScreenshot("exp_" + name); }

    void run() {
        bestiary();
        signals();
        tongue();
        throwers();
        ticker();
        possession();
        mound();
        dig();
    }

    private UUID frozen(EntityType<? extends LimboDead> type, double x, double y, double z, float yaw, int skin) {
        return t.spawn(type, x, y, z, yaw, d -> { d.setNoAi(true); if (skin >= 0) d.setSkin(skin); });
    }

    void bestiary() {
        BlockPos a = t.stage;
        double y = a.getY();
        for (EntityType<? extends LimboDead> type : List.of(HellEntities.STRANGLER, HellEntities.BILE_SPITTER, HellEntities.RIB_THROWER)) {
            for (int i = 0; i < 3; i++) frozen(type, a.getX() - 2.5 + i * 2.5, y, a.getZ(), 180 - 25 + i * 25, i);
            frozen(HellEntities.HOLLOW, a.getX() + 5, y, a.getZ(), 200, 3); // scale reference
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
            t.view(a.getX() + 0.5, y + 1.2, a.getZ() - 4.6, 0, 8);
            shot("bestiary_" + id);
            t.view(a.getX() + 5.5, y + 1.2, a.getZ() - 2.5, 60, 8);
            shot("bestiary_" + id + "_side");
            t.clearStage();
        }
        for (EntityType<? extends LimboDead> type : List.of(HellEntities.MOURNING_PARASITE, HellEntities.BREACH_TICKER)) {
            for (int i = 0; i < 3; i++) frozen(type, a.getX() - 1.5 + i * 1.5, y, a.getZ(), 180 - 30 + i * 30, -1);
            frozen(HellEntities.HOLLOW, a.getX() + 3.5, y, a.getZ(), 200, 3);
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
            t.view(a.getX() + 0.5, y + 0.8, a.getZ() - 3.0, 0, 20);
            shot("bestiary_" + id);
            t.view(a.getX() + 3.0, y + 1.2, a.getZ() - 1.5, 70, 25);
            shot("bestiary_" + id + "_side");
            t.clearStage();
        }
    }

    /** The announced poses, frozen at their most readable frame. */
    void signals() {
        BlockPos a = t.stage;
        double y = a.getY();
        record Pose(EntityType<? extends LimboDead> type, int anim, int len, int at, String name) {}
        for (Pose p : List.of(new Pose(HellEntities.STRANGLER, LimboDead.ANIM_SPECIAL, 24, 16, "strangler_throat"),
                new Pose(HellEntities.STRANGLER, LimboDead.ANIM_CALL, 80, 20, "strangler_hauling"),
                new Pose(HellEntities.BILE_SPITTER, LimboDead.ANIM_SPECIAL, 26, 17, "spitter_open"),
                new Pose(HellEntities.RIB_THROWER, LimboDead.ANIM_SPECIAL, 24, 8, "ribs_reach"),
                new Pose(HellEntities.RIB_THROWER, LimboDead.ANIM_SPECIAL, 24, 16, "ribs_draw"),
                new Pose(HellEntities.MOURNING_PARASITE, LimboDead.ANIM_SPECIAL, 12, 6, "parasite_crouch"),
                new Pose(HellEntities.BREACH_TICKER, LimboDead.ANIM_CALL, 60, 50, "ticker_pulse"))) {
            UUID id = frozen(p.type, a.getX() + 0.5, y, a.getZ() + 0.5, -40, 0);
            boolean small = p.type == HellEntities.MOURNING_PARASITE || p.type == HellEntities.BREACH_TICKER;
            t.view(a.getX() + (small ? 2.0 : 3.0), y + (small ? 0.9 : 1.1), a.getZ() + (small ? 2.4 : 3.6), 145, small ? 20 : 6);
            t.run(s -> { if (get(s, id) instanceof LimboDead d) d.playAnim(p.anim, p.len); });
            t.waitTicks(p.at);
            shot("pose_" + p.name);
            t.clearStage();
        }
    }

    /** A Strangler throws its tongue at a villager: the tongue in flight, then holding and dragging. */
    void tongue() {
        BlockPos a = t.stage;
        double y = a.getY();
        UUID v = t.spawn(EntityTypes.VILLAGER, a.getX() + 0.5, y, a.getZ() + 10.5, 180, m -> m.setNoAi(true));
        UUID s = t.spawn(HellEntities.STRANGLER, a.getX() + 0.5, y, a.getZ() + 0.5, 0, null);
        t.view(a.getX() + 7.5, y + 2.5, a.getZ() + 5.5, 90, 12);
        t.run(srv -> { if (get(srv, s) instanceof LimboDead d && get(srv, v) instanceof Mob m) d.engage(m); });
        int flying = t.until(srv -> get(srv, s) instanceof StranglerEntity st && st.tongue() == StranglerEntity.FLYING, 200);
        t.waitTicks(2);
        shot("tongue_flying");
        int holding = t.until(srv -> get(srv, s) instanceof StranglerEntity st && st.tongue() == StranglerEntity.HOLDING, 60);
        t.waitTicks(15);
        shot("tongue_holding");
        t.soft(flying >= 0 && holding >= 0, "visual: the tongue is seen in flight and then holding (" + flying + ", " + holding + ")");
        t.clearStage();
    }

    void throwers() {
        BlockPos a = t.stage;
        double y = a.getY();
        for (EntityType<? extends LimboDead> type : List.of(HellEntities.BILE_SPITTER, HellEntities.RIB_THROWER)) {
            UUID v = t.spawn(EntityTypes.VILLAGER, a.getX() + 0.5, y, a.getZ() + 12.5, 180, m -> m.setNoAi(true));
            UUID s = t.spawn(type, a.getX() + 0.5, y, a.getZ() + 0.5, 0, null);
            t.view(a.getX() + 8.5, y + 2.5, a.getZ() + 6.5, 90, 10);
            t.run(srv -> { if (get(srv, s) instanceof LimboDead d && get(srv, v) instanceof Mob m) d.engage(m); });
            EntityType<?> shotType = type == HellEntities.BILE_SPITTER ? HellEntities.BILE_BOLUS : HellEntities.RIB_SHARD;
            int thrown = t.until(srv -> !limbo(srv).getEntities(shotType, new net.minecraft.world.phys.AABB(a).inflate(30), e -> e.tickCount >= 3).isEmpty(), 200);
            shot("thrown_" + BuiltInRegistries.ENTITY_TYPE.getKey(shotType).getPath());
            t.soft(thrown >= 0, "visual: " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + " throws at a villager in view");
            if (type == HellEntities.BILE_SPITTER) {
                int puddle = t.until(srv -> !limbo(srv).getEntities(HellEntities.BILE_RESIDUE, new net.minecraft.world.phys.AABB(a).inflate(30), e -> true).isEmpty(), 60);
                t.view(a.getX() + 3.5, y + 2.0, a.getZ() + 10.5, 120, 40);
                shot("bile_residue");
                t.soft(puddle >= 0, "visual: the bolus leaves a puddle of bile");
            }
            t.clearStage();
        }
    }

    /** A Ticker clinging to a wall a player built, pulsing, and the wall after the burst. */
    void ticker() {
        BlockPos a = t.stage;
        double y = a.getY();
        // A sealed hut of cobblestone marked as placed by players, a villager inside.
        BlockPos h = a.offset(0, 0, 8);
        t.box(h.offset(-2, 0, -2), h.offset(2, 3, 2), Blocks.COBBLESTONE.defaultBlockState(), true);
        t.box(h.offset(-1, 0, -1), h.offset(1, 2, 1), Blocks.AIR.defaultBlockState(), false);
        UUID v = t.spawn(EntityTypes.VILLAGER, h.getX() + 0.5, y, h.getZ() + 0.5, 0, m -> m.setNoAi(true));
        UUID k = t.spawn(HellEntities.BREACH_TICKER, a.getX() + 0.5, y, a.getZ() + 0.5, 0, null);
        t.run(srv -> { if (get(srv, k) instanceof LimboDead d && get(srv, v) instanceof Mob m) d.engage(m); });
        t.view(a.getX() + 5.5, y + 2.0, a.getZ() + 3.0, 60, 15);
        int clung = t.until(srv -> get(srv, k) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER, 300);
        StringBuilder modes = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            t.waitTicks(10);
            String m = t.server(srv -> get(srv, k) instanceof BreachTickerEntity b ? b.mode() + "@" + b.blockPosition().subtract(a).toShortString() : "gone");
            modes.append(m).append(' ');
        }
        shot("ticker_clinging");
        int gone = t.until(srv -> get(srv, k) == null, 80);
        t.waitTicks(10);
        shot("ticker_breach_after");
        int opened = t.server(srv -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(h.offset(-2, 0, -2), h.offset(2, 3, 2)))
                if (Math.max(Math.abs(p.getX() - h.getX()), Math.abs(p.getZ() - h.getZ())) == 2 || p.getY() - h.getY() == 3)
                    if (!limbo(srv).getBlockState(p).is(Blocks.COBBLESTONE)) n++;
            return n;
        });
        LimboExpansionTest.log("visual ticker: clung after {}, then {}, burst after {}, {} blocks of the hut opened", clung, modes.toString().trim(), gone, opened);
        t.soft(clung >= 0 && gone >= 0 && opened > 0, "visual: the Ticker clings to the hut and bursts it open (" + opened + " blocks)");
        t.clearStage();
    }

    /** A Mourning Parasite takes a villager: the leap, the tendrils, the possessed host. */
    void possession() {
        BlockPos a = t.stage;
        double y = a.getY();
        // (A host must have its own AI: the parasite drives it; creatures frozen with NoAI are not taken.)
        UUID v = t.spawn(EntityTypes.VILLAGER, a.getX() + 0.5, y, a.getZ() + 6.5, 180, null);
        UUID p = t.spawn(HellEntities.MOURNING_PARASITE, a.getX() + 0.5, y, a.getZ() + 0.5, 0, null);
        t.view(a.getX() + 4.5, y + 2.0, a.getZ() + 5.5, 90, 15);
        int fixing = t.until(srv -> get(srv, p) instanceof MourningParasiteEntity m && m.phase() == MourningParasiteEntity.FIXING, 300);
        t.waitTicks(8);
        shot("parasite_fixing");
        int taken = t.until(srv -> get(srv, v) != null && Possession.isPossessed(get(srv, v)), 80);
        t.waitTicks(30);
        t.view(a.getX() + 2.5, y + 1.8, a.getZ() + 4.5, 120, 15);
        shot("possessed_villager");
        t.soft(fixing >= 0 && taken >= 0, "visual: the parasite latches and possesses (" + fixing + ", " + taken + ")");
        t.clearStage();
    }

    /** A mound of bodies against a tower of six with a villager on top. */
    void mound() {
        BlockPos a = t.stage;
        double y = a.getY();
        t.box(a.offset(-1, 0, -1), a.offset(1, 5, 1), Blocks.STONE_BRICKS.defaultBlockState(), false);
        UUID v = t.spawn(EntityTypes.VILLAGER, a.getX() + 0.5, y + 6, a.getZ() + 0.5, 0, m -> m.setNoAi(true));
        int n = Horde.crowdFor(6), piles0 = Horde.piles;
        for (int i = 0; i < n; i++) {
            double ang = i * 2 * Math.PI / n, r = 6 + (i % 3);
            UUID d = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5 + Math.cos(ang) * r, y, a.getZ() + 0.5 + Math.sin(ang) * r, 0, null);
            t.run(srv -> { if (get(srv, d) instanceof LimboDead dd && get(srv, v) instanceof Mob m) dd.engage(m); });
        }
        t.view(a.getX() + 12.5, y + 4, a.getZ() + 0.5, 90, 12);
        int formed = t.until(srv -> Horde.piles > piles0, 400);
        t.waitTicks(5);
        shot("mound_gathering");
        t.waitTicks(15);
        shot("mound_rising");
        t.waitTicks(20);
        shot("mound_climbing");
        t.soft(formed >= 0, "visual: the dead gather into a mound against the tower");
        t.clearStage();
    }

    /** A dead clawing at a patch of cinder soil, and the one it digs up coming out of the ground. */
    void dig() {
        BlockPos a = t.stage;
        double y = a.getY();
        t.box(a.offset(-2, -1, -2), a.offset(2, -1, 2), ryuna.qdeath.registry.ModBlocks.CINDER_SOIL.defaultBlockState(), false);
        UUID v = t.spawn(EntityTypes.VILLAGER, a.getX() + 0.5, y, a.getZ() + 9.5, 0, m -> m.setNoAi(true));
        UUID d = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, y, a.getZ() + 0.5, 0, null);
        t.run(srv -> { if (get(srv, d) instanceof LimboDead dd && get(srv, v) instanceof Mob m) { dd.engage(m); dd.startDig(); } });
        t.view(a.getX() + 4.5, y + 2.2, a.getZ() + 0.5, 90, 25);
        t.waitTicks(23);
        shot("horde_digging");
        int risen0 = Horde.risen;
        int out = t.until(srv -> Horde.risen > risen0, LimboTuning.DIG_TICKS);
        t.waitTicks(12);
        shot("horde_dug_up");
        t.soft(out >= 0, "visual: a dead digs another up out of the ground");
        t.clearStage();
    }
}
