package ryuna.qdeath.limbo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.hell.HellMob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the dead can perceive: which creatures count as alive, how loud they are, and who just arrived
 * (a short grace so nobody is ambushed stepping out of a portal). Player speed is sampled here because the
 * server does not keep a reliable velocity for players.
 */
public final class LimboSenses {
    private LimboSenses() {}

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> SPEED = new HashMap<>();
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();

    /**
     * The living: players, villagers and animals. The dead never hunt each other, nor creatures of Hell, nor the fireproof
     * beasts of the lava, nor a host held by a Mourning Parasite (for as long as the bond lasts it is one of the horde).
     */
    public static boolean isLife(LivingEntity e) {
        if (!e.isAlive() || e instanceof HellMob) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (Possession.isPossessed(e)) return false;
        return e instanceof AbstractVillager || (e instanceof Animal && !e.fireImmune());
    }

    /** Movement noise radius: sprinting is loud, walking audible, crouching or standing still nearly silent. */
    public static double noiseRadius(LivingEntity e) {
        double speed = e instanceof Player ? SPEED.getOrDefault(e.getUUID(), 0.0) : e.getDeltaMovement().horizontalDistance();
        if (speed < 0.02) return LimboTuning.NOISE_CROUCH * 0.6;
        if (e.isCrouching() || e.isShiftKeyDown()) return LimboTuning.NOISE_CROUCH;
        return e.isSprinting() || speed > 0.2 ? LimboTuning.NOISE_SPRINT : LimboTuning.NOISE_WALK;
    }

    /** True while a creature is visibly running away (the Runaway reacts to fleeing prey). */
    public static boolean fleeing(LivingEntity e) {
        return e.isSprinting() || (e instanceof Player ? SPEED.getOrDefault(e.getUUID(), 0.0) : e.getDeltaMovement().horizontalDistance()) > 0.2;
    }

    public static void grantGrace(ServerPlayer player, int ticks) {
        GRACE_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    public static boolean inGrace(LivingEntity e) {
        Long until = GRACE_UNTIL.get(e.getUUID());
        return until != null && e.level().getGameTime() < until;
    }

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % LimboTuning.SCAN_INTERVAL == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Vec3 last = LAST_POS.put(p.getUUID(), p.position());
                SPEED.put(p.getUUID(), last == null ? 0.0 : last.subtract(p.position()).horizontalDistance() / LimboTuning.SCAN_INTERVAL);
            }
        }
        if (t % LimboTuning.TRAIL_STEP == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (isLife(p)) BreathClues.breathe(p);
        }
    }

    public static void forget(UUID player) {
        LAST_POS.remove(player);
        SPEED.remove(player);
        GRACE_UNTIL.remove(player);
        BreathClues.forget(player);
    }

    public static void clear() {
        LAST_POS.clear();
        SPEED.clear();
        GRACE_UNTIL.clear();
        BreathClues.clear();
    }
}
