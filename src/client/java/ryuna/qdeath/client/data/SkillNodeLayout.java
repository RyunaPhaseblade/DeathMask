package ryuna.qdeath.client.data;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ryuna.qdeath.skills.SkillId;

/** Visual-only layout and icon choices for the fixed tree. */
public record SkillNodeLayout(SkillId skill, ItemStack icon) {
    public static SkillNodeLayout of(SkillId skill) {
        return new SkillNodeLayout(skill, switch (skill) {
            case HEALTH_I -> new ItemStack(Items.MELON_SLICE);
            case HEALTH_II -> new ItemStack(Items.GOLDEN_APPLE);
            case HEALTH_III -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            case SPEED_I -> new ItemStack(Items.SUGAR);
            case SPEED_II -> new ItemStack(Items.RABBIT_FOOT);
            case SPEED_III -> new ItemStack(Items.FEATHER);
            case STRENGTH_I -> new ItemStack(Items.WOODEN_SWORD);
            case STRENGTH_II -> new ItemStack(Items.IRON_SWORD);
            case STRENGTH_III -> new ItemStack(Items.DIAMOND_SWORD);
        });
    }
}
