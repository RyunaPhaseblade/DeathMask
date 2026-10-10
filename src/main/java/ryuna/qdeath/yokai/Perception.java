package ryuna.qdeath.yokai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

/**
 * Cursed perception, per player: 0 no perception (only signs), 1 omen (shadows and particles), 2 cursed sight (the
 * model, combat and pacts). Derived from the general skills; operators may set a demonstration override that never
 * touches the skill tree. Every decision is made per creature-player pair.
 */
public final class Perception {
    private Perception() {}

    /** The player's own level (skills, or the demonstration override). */
    public static int level(ServerPlayer player) {
        int override = YokaiPlayer.of(player).perceptionOverride();
        if (override >= 0) return Math.min(2, override);
        if (SkillManager.has(player, SkillId.PERCEPTION_II)) return 2;
        if (SkillManager.has(player, SkillId.PERCEPTION_I)) return 1;
        return 0;
    }

    /** The four original creatures keep their Hell role there: always visible, old danger rules, no perception progression. */
    public static boolean exempt(Entity yokai) {
        return yokai instanceof YokaiCreature y && y.species().legacy && HellLayer.of(yokai.level()) != null;
    }

    /** What {@code player} perceives of {@code yokai}. Spectators see everything. */
    public static int view(ServerPlayer player, Entity yokai) {
        if (exempt(yokai) || player.isSpectator()) return 2;
        return level(player);
    }

    public static boolean canInteract(ServerPlayer player, Entity yokai) { return view(player, yokai) >= 2; }

    /** Creative and spectator players are never the target of offensive behaviour. */
    public static boolean targetable(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** The player a damage source is attributable to: the attacker itself, or the owner of a pet, companion or projectile. */
    public static @Nullable ServerPlayer attributable(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof ServerPlayer p) return p;
        if (e instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer p) return p;
        if (e instanceof YokaiCreature y && y.yokai().ownerPlayer() != null) return y.yokai().ownerPlayer();
        return null;
    }

    /** Whether the player may hurt this yokai (directly or through anything attributable to them). */
    public static boolean mayHurt(@Nullable ServerPlayer player, Entity yokai) {
        return player == null || view(player, yokai) >= 2;
    }
}
