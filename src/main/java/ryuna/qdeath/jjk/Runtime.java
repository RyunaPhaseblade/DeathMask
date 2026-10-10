package ryuna.qdeath.jjk;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import ryuna.qdeath.Quantumdeath;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Transient, server-only per-player state (held keys, charges, toggles, momentum...). Never saved: a new player
 * entity (respawn) starts clean, and disconnecting discards it with the entity.
 */
public final class Runtime {
    public static final AttachmentType<Runtime> TYPE = AttachmentRegistry.createDefaulted(Quantumdeath.id("technique_runtime"), Runtime::new);

    public boolean holding;
    public int heldTicks;
    public String heldSkill = "";
    /** Input flood protection. */
    public long inputSecond;
    public int inputs;
    public long lastCombat = -10000;
    public long xpMinute;
    public int xpThisMinute;
    /** 0..1, shown as the HUD charge bar while a technique charges or channels. */
    public float charge;
    public final Map<String, Integer> cooldownTotals = new HashMap<>();
    public Object lastSent;
    public int forceSync;
    private final Map<Class<?>, Object> classState = new HashMap<>();

    /** Per-class transient state, created on first use. */
    @SuppressWarnings("unchecked")
    public <T> T state(Class<T> type, Supplier<T> init) {
        return (T) classState.computeIfAbsent(type, k -> init.get());
    }

    public void clearClassState() { classState.clear(); }
}
