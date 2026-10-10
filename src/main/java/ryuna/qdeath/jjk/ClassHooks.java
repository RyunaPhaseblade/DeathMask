package ryuna.qdeath.jjk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Class-wide behaviour that is not bound to one key press: passives, resource rules, HUD indicators, cleanup. */
public interface ClassHooks {
    /** Every server tick while this class is selected. */
    default void tick(Cast c) {}
    default void indicators(Cast c, List<JjkNet.Indicator> out) {}
    /** Owner logged out, died, changed dimension or class: discard whatever the class left in the world. */
    default void cleanup(ServerPlayer player) {}
    default float resourceMax(TechniqueData data) { return 0; }
    /** Multiplier on damage this class's player deals to {@code target} (any source the player is credited with). */
    default float outgoingMultiplier(Cast c, LivingEntity target, DamageSource source, float amount) { return 1f; }
    /** After this class's player damaged {@code target}. */
    default void afterHit(Cast c, LivingEntity target, DamageSource source, float taken, boolean technique) {}
    /** Called when the class is first selected or the player respawns, to fill the resource. */
    default void fill(TechniqueData data, float fraction) { data.resource = resourceMax(data) * fraction; }
}
