package ryuna.qdeath.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.mixin.ServerPlayerGameModeAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Noises of the living, confirmed by the server: mining, breaking and placing blocks, opening a chest or barrel, eating.
 * A stimulus carries a position, a kind, a loudness and a moment; the dead that hear it go to look (a clue, never the
 * target: a chase still needs sight, touch or a wound). A solid wall between halves the distance a sound carries.
 * Only the dead of the Limbo and of the plague listen.
 */
public final class Stimuli {
    private Stimuli() {}

    public enum Kind {
        MINING(LimboTuning.STIMULUS_MINING), BREAK(LimboTuning.STIMULUS_BREAK), PLACE(LimboTuning.STIMULUS_PLACE),
        CONTAINER(LimboTuning.STIMULUS_CONTAINER), EAT(LimboTuning.STIMULUS_EAT), LIGHT(LimboTuning.LIGHT_RANGE);
        public final double radius;
        Kind(double radius) { this.radius = radius; }
    }

    /** player -> next tick each repeating activity (mining, eating) may be heard again. */
    private static final Map<UUID, long[]> NEXT = new HashMap<>();
    private static long budgetTick = -1;
    private static int raysLeft;
    /** Counters for /deathmask limbo stats and the tests. */
    public static int published, heard;

    /** Shared per-tick budget of wall checks; when it runs out, the remaining listeners simply do not hear this one. */
    static boolean ray(ServerLevel level) {
        long t = level.getGameTime();
        if (t != budgetTick) { budgetTick = t; raysLeft = LimboTuning.BUDGET_STIMULUS_RAYS; }
        return raysLeft-- > 0;
    }

    public static void publish(ServerLevel level, Vec3 at, Kind kind) {
        if (!LimboTuning.expansion) return;
        published++;
        double r = kind.radius;
        BlockPos source = BlockPos.containing(at);
        for (LimboDead d : level.getEntitiesOfClass(LimboDead.class, new AABB(at, at).inflate(r), d -> d.isAlive() && d.expanded())) {
            double dist = d.getEyePosition().distanceTo(at);
            if (dist > r) continue;
            if (dist > r / 2) {
                if (!ray(level)) continue;
                BlockHitResult hit = level.clip(new ClipContext(d.getEyePosition(), at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d));
                if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(source)) continue; // muffled by the wall
            }
            heard++;
            d.hearStimulus(at, kind);
        }
    }

    /** Publishes a repeating activity of a player at most once per STIMULUS_REPEAT ticks. */
    static void repeating(ServerPlayer p, Vec3 at, Kind kind) {
        long now = p.level().getGameTime();
        long[] next = NEXT.computeIfAbsent(p.getUUID(), k -> new long[Kind.values().length]);
        if (now < next[kind.ordinal()]) return;
        next[kind.ordinal()] = now + LimboTuning.STIMULUS_REPEAT;
        publish((ServerLevel) p.level(), at, kind);
    }

    /** Mining in progress and eating are polled: the server knows both, nothing has to be trusted from the client. */
    public static void tick(MinecraftServer server) {
        if (!LimboTuning.expansion || server.getTickCount() % LimboTuning.STIMULUS_REPEAT != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!LimboSenses.isLife(p)) continue;
            ServerPlayerGameModeAccessor mode = (ServerPlayerGameModeAccessor) p.gameMode;
            if (mode.quantumdeath$isDestroyingBlock()) repeating(p, Vec3.atCenterOf(mode.quantumdeath$destroyPos()), Kind.MINING);
            if (p.isUsingItem() && p.getUseItem().getUseAnimation() == ItemUseAnimation.EAT) repeating(p, p.getEyePosition(), Kind.EAT);
        }
    }

    public static void forget(UUID player) { NEXT.remove(player); }

    public static void clear() { NEXT.clear(); published = 0; heard = 0; }
}
