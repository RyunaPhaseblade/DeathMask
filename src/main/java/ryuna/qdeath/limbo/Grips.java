package ryuna.qdeath.limbo;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ryuna.qdeath.Quantumdeath;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tongue grips: one captor holds one victim and a victim admits a single grip. While held the victim's own legs barely
 * help (a transient speed penalty, nothing saved); when freed, for whatever reason, it is immune to new tongues for
 * TONGUE_IMMUNITY. Looking, attacking and using items are never touched.
 */
public final class Grips {
    private Grips() {}

    private static final Identifier DRAG = Quantumdeath.id("tongue_drag");
    private static final Map<UUID, UUID> HELD = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    public static int grabs, releases, blocked;

    public static boolean canGrab(LivingEntity victim) {
        return !HELD.containsKey(victim.getUUID()) && IMMUNE.getOrDefault(victim.getUUID(), Long.MIN_VALUE) <= victim.level().getGameTime();
    }

    public static boolean held(LivingEntity victim) { return HELD.containsKey(victim.getUUID()); }
    public static boolean held(UUID victim) { return HELD.containsKey(victim); }

    public static boolean grab(LivingEntity victim, Entity captor) {
        if (!canGrab(victim)) return false;
        HELD.put(victim.getUUID(), captor.getUUID());
        AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(DRAG) == null)
            speed.addTransientModifier(new AttributeModifier(DRAG, -LimboTuning.TONGUE_DRAG, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        grabs++;
        return true;
    }

    /** Frees {@code victim} from {@code captor}'s grip (if it is the one holding it) and starts the victim's immunity. */
    public static void release(LivingEntity victim, Entity captor) {
        if (!captor.getUUID().equals(HELD.get(victim.getUUID()))) return;
        HELD.remove(victim.getUUID());
        IMMUNE.put(victim.getUUID(), victim.level().getGameTime() + LimboTuning.TONGUE_IMMUNITY);
        AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(DRAG);
        releases++;
    }

    public static void forget(UUID victim) { HELD.remove(victim); IMMUNE.remove(victim); }

    public static void clear() { HELD.clear(); IMMUNE.clear(); grabs = 0; releases = 0; blocked = 0; }
}
