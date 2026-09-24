package ryuna.qdeath.effects;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import ryuna.qdeath.Quantumdeath;

public class modEffects {
    
    public static final Holder<MobEffect> ANTI_PERMA_BURN = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, 
            Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "anti_perma_burn"), 
            new antiPermaBurn()
    );
    
    public static final Potion ANTI_PERMA_BURN_POTION = new Potion(
            "anti_perma_burn", 
            new MobEffectInstance(ANTI_PERMA_BURN, 6000, 0, false, false, true)
    );

    public static void register() {
        Registry.register(
                BuiltInRegistries.POTION, 
                Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "anti_perma_burn"), 
                ANTI_PERMA_BURN_POTION
        );
    }
}