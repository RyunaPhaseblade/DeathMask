package ryuna.qdeath.yokai;

import net.minecraft.world.entity.Mob;
import ryuna.qdeath.entity.yokai.AbstractYokai;

/** Bookkeeping shared by the four original creatures, which keep their own base classes (Monster, Creeper). */
public final class YokaiHooks {
    private YokaiHooks() {}

    /** Every server tick: encounters, bestiary sightings once a second, and the one-instance rule for companions. */
    public static boolean serverTick(Mob mob, YokaiCore core) {
        core.tick();
        if (mob.tickCount % 20 != 0) return true;
        Bestiary.observe(mob, core.species);
        if (core.bonded()) {
            if (!Bonds.current(mob)) {
                core.storing = true;
                mob.discard();
                return false;
            }
            Bonds.snapshot(mob);
        }
        return true;
    }

    /** Client crosshair: the local player only targets what they fully see. */
    public static boolean pickable(Mob mob) {
        return !mob.level().isClientSide() || AbstractYokai.CLIENT_PICKABLE.test(mob);
    }
}
