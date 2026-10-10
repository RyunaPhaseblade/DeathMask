package ryuna.qdeath.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ryuna.qdeath.Quantumdeath;

/** Hell status effects. The original Anti-Perma Burn effect lives in {@link ryuna.qdeath.effects.modEffects}. */
public final class ModEffects {
    private ModEffects() {}

    private static class Simple extends MobEffect {
        private final int interval;
        private final float damage;
        Simple(MobEffectCategory category, int color, int interval, float damage) { super(category, color); this.interval = interval; this.damage = damage; }
        @Override public boolean shouldApplyEffectTickThisTick(int tick, int amp) { return interval > 0 && tick % interval == 0; }
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
            if (damage > 0) mob.hurtServer(level, level.damageSources().magic(), damage * (amp + 1));
            return true;
        }
    }

    private static Holder<MobEffect> reg(String id, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Quantumdeath.id(id), effect);
    }

    /** Requiem's curse and VIGIL's touch: a slow bleeding grief. */
    public static final Holder<MobEffect> MOURNED = reg("mourned", new Simple(MobEffectCategory.HARMFUL, 0x1d1b22, 40, 1.0f)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, Quantumdeath.id("effect.mourned"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Left by VIGIL. While marked, the stalker finds you faster and your heart is lighter. */
    public static final Holder<MobEffect> VIGILS_MARK = reg("vigils_mark", new Simple(MobEffectCategory.HARMFUL, 0xe8e4dc, 0, 0)
            .addAttributeModifier(Attributes.MAX_HEALTH, Quantumdeath.id("effect.vigils_mark"), -4.0, AttributeModifier.Operation.ADD_VALUE));
    /** Shown while standing in a votive lantern's light: VIGIL cannot enter. */
    public static final Holder<MobEffect> SANCTUARY = reg("sanctuary", new Simple(MobEffectCategory.BENEFICIAL, 0xf2c66d, 0, 0));
    /** Hell heat near lava and molten iron when unprotected. */
    public static final Holder<MobEffect> SEARING = reg("searing", new Simple(MobEffectCategory.HARMFUL, 0xff5a1f, 60, 1.0f));

    public static void init() {}
}
