package ryuna.qdeath.moditems.items;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ryuna.qdeath.Quantumdeath;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;

//import net.minecraft.world.item.Rarity;


public class qapplei extends Item {

public int M;

    private static final Identifier HEALTH_I =
        Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "quantum_apple_i");

    private static final Identifier HEALTH_II =
        Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "quantum_apple_ii");

    private static final Identifier HEALTH_III =
        Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "quantum_apple_iii");

    private static final Identifier HEALTH_IV =
        Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "quantum_apple_iv");

    public qapplei(Properties properties, int Mn) {
        super(properties);
        this.M = Mn;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            
            var attribute = player.getAttribute(Attributes.MAX_HEALTH);
            
            // Comprobar si el modificador ya existe antes de añadirlo
            if (attribute != null && attribute.getModifier(HEALTH_I) == null && this.M == 1) {
                attribute.addPermanentModifier(
                    new AttributeModifier(HEALTH_I, 2.0, AttributeModifier.Operation.ADD_VALUE)
                );
                //player.heal(4.0f);
            }
            
            if (attribute != null && attribute.getModifier(HEALTH_II) == null && this.M == 2) {
                attribute.addPermanentModifier(
                    new AttributeModifier(HEALTH_II, 4.0, AttributeModifier.Operation.ADD_VALUE)
                );
                //player.heal(4.0f);
            }

            if (attribute != null && attribute.getModifier(HEALTH_III) == null && this.M == 3) {
                attribute.addPermanentModifier(
                    new AttributeModifier(HEALTH_III, 6.0, AttributeModifier.Operation.ADD_VALUE)
                );
                //player.heal(4.0f);
            }

            if (attribute != null && attribute.getModifier(HEALTH_IV) == null && this.M == 4) {
                attribute.addPermanentModifier(
                    new AttributeModifier(HEALTH_IV, 8.0, AttributeModifier.Operation.ADD_VALUE)
                );
                //player.heal(4.0f);
            }

            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        }
        return super.finishUsingItem(stack, level, entity);
    }
    
}