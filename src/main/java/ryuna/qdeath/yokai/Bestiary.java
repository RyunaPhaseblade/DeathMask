package ryuna.qdeath.yokai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The discovered bestiary: players first collect clues (sounds, prints, lights going out), then see a shadow with
 * partial sight, and only identify a creature (name, model, habits) after a clear look with complete sight.
 */
public final class Bestiary {
    private Bestiary() {}

    private static void add(ServerPlayer player, Species s, int bits) {
        YokaiPlayer data = YokaiPlayer.of(player);
        if ((data.entry(s) & bits) == bits) return;
        YokaiPlayer.set(player, data.withBits(s, bits));
        YokaiNet.syncSelf(player);
    }

    /** {@code player} witnessed one of the species' signs. */
    public static void clue(ServerPlayer player, Species s, String clue) {
        int i = s.clues.indexOf(clue);
        if (i >= 0 && !player.isSpectator()) add(player, s, 1 << i);
    }

    /** Every player within {@code radius} of {@code at} witnesses the sign (sounds, prints, a light going out). */
    public static void clueNear(ServerLevel level, Vec3 at, double radius, Species s, String clue) {
        for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(at) <= radius * radius) clue(p, s, clue);
    }

    public static void shade(ServerPlayer player, Species s) { add(player, s, YokaiPlayer.SHADE); }

    public static void identify(ServerPlayer player, Species s) { add(player, s, YokaiPlayer.IDENTIFIED | YokaiPlayer.SHADE); }

    /** Called every second by each yokai: nearby players with a clear look learn what they can see. */
    public static void observe(Mob mob, Species s) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        double r = YokaiBalance.Perception.IDENTIFY_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(mob) > r * r || !p.hasLineOfSight(mob)) continue;
            int view = Perception.view(p, mob);
            if (view >= 2) identify(p, s);
            else if (view == 1) shade(p, s);
        }
    }

    public static void reveal(ServerPlayer player) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        for (Species s : Species.values()) m.put(s.id, YokaiPlayer.IDENTIFIED | YokaiPlayer.SHADE | ((1 << s.clues.size()) - 1));
        YokaiPlayer.set(player, YokaiPlayer.of(player).withBestiary(m));
        YokaiNet.syncSelf(player);
    }

    public static void reset(ServerPlayer player) {
        YokaiPlayer.set(player, YokaiPlayer.of(player).withBestiary(java.util.Map.of()));
        YokaiNet.syncSelf(player);
    }
}
