package ryuna.qdeath.effects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class antiPermaBurn extends MobEffect {
    public antiPermaBurn() {
        // Categoría BENEFICIAL (positivo) y color naranja en hexadecimal
        super(MobEffectCategory.BENEFICIAL, 0xFFAA00); 
    }
}