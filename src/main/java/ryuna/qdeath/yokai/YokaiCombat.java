package ryuna.qdeath.yokai;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Explosion;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Damage rules. Before complete sight, nothing attributable to a player reaches a yokai: melee, projectiles,
 * techniques, explosions, pets or companions. Companions never hurt their owner. The Retreat node reacts to yokai
 * damage taken.
 */
public final class YokaiCombat {
    private YokaiCombat() {}

    private static final Map<UUID, Long> RETREAT_READY = new HashMap<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(YokaiCombat::allow);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken <= 0) return;
            if (entity instanceof YokaiCreature y) {
                y.yokai().markCombat();
                Entity attacker = source.getEntity();
                if (attacker instanceof YokaiCreature other) other.yokai().markCombat();
            }
            if (entity instanceof ServerPlayer player && source.getEntity() instanceof YokaiCreature y) {
                y.yokai().markCombat();
                retreat(player);
            }
            if (source.getEntity() instanceof YokaiCreature y) y.yokai().markCombat();
        });
    }

    private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof YokaiCreature y) {
            ServerPlayer player = Perception.attributable(source);
            if (!Perception.mayHurt(player, entity)) return false;
            // A shikigami is never hurt by its own owner (or the owner's pets), so it never turns on them.
            if (player != null && y.yokai().isOwner(player)) return false;
            // A companion never hurts its own owner's other companions.
            if (y.yokai().bonded() && source.getEntity() instanceof YokaiCreature other && other.yokai().bonded()
                    && y.yokai().owner().equals(other.yokai().owner())) return false;
        }
        if (entity instanceof ServerPlayer victim && source.getEntity() instanceof YokaiCreature y && y.yokai().isOwner(victim)) return false;
        return true;
    }

    private static final net.minecraft.resources.Identifier RETREAT = ryuna.qdeath.Quantumdeath.id("retreat");
    private static final Map<UUID, Long> RETREAT_END = new HashMap<>();

    /** Retreat node: +10% speed for 4 s after a yokai hurts you, at most every 20 s. */
    private static void retreat(ServerPlayer player) {
        if (!SkillManager.has(player, SkillId.SPIRIT_III)) return;
        long now = player.level().getGameTime();
        if (now < RETREAT_READY.getOrDefault(player.getUUID(), 0L)) return;
        RETREAT_READY.put(player.getUUID(), now + YokaiBalance.Perception.RETREAT_COOLDOWN);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(RETREAT);
        speed.addTransientModifier(new AttributeModifier(RETREAT, YokaiBalance.Perception.RETREAT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        RETREAT_END.put(player.getUUID(), now + YokaiBalance.Perception.RETREAT_TICKS);
        player.level().playSound(null, player.blockPosition(), ryuna.qdeath.registry.ModSounds.YOKAI_RETREAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    public static void tick(MinecraftServer server) {
        if (RETREAT_END.isEmpty()) return;
        long now = server.overworld().getGameTime();
        RETREAT_END.entrySet().removeIf(e -> {
            if (now < e.getValue()) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.getAttribute(Attributes.MOVEMENT_SPEED) != null) p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(RETREAT);
            return true;
        });
    }

    /** Projectiles of a player (or of their pets) pass through yokai the player cannot fully see. */
    public static boolean projectileMayHit(Entity projectile, Entity target, Entity owner) {
        if (!(target instanceof YokaiCreature)) return true;
        ServerPlayer p = owner instanceof ServerPlayer sp ? sp : owner instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer sp ? sp : null;
        return Perception.mayHurt(p, target);
    }

    /** Explosions set off by a player without sight do not push yokai either. */
    public static boolean ignoresExplosion(Entity yokai, Explosion explosion) {
        Entity source = explosion.getIndirectSourceEntity();
        ServerPlayer p = source instanceof ServerPlayer sp ? sp : source instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer sp ? sp : null;
        return !Perception.mayHurt(p, yokai);
    }

    public static void forget(UUID player) { RETREAT_READY.remove(player); }
}
