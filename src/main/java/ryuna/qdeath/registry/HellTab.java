package ryuna.qdeath.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.effects.modEffects;

/** Creative tab with every Hell block, item, relic, weapon and armor piece for showcasing. */
public final class HellTab {
    private HellTab() {}

    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), Quantumdeath.id("hell"));

    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
                .icon(() -> new ItemStack(HellItems.VIGILS_DEATH_MASK))
                .title(Component.translatable("creativeTab.quantumdeath.hell"))
                .displayItems((params, out) -> {
                    HellItems.CREATIVE.forEach(out::accept);
                    for (int page = 0; page < 12; page++) {
                        ItemStack p = new ItemStack(HellItems.JOURNAL_PAGE);
                        p.set(HellItems.PAGE, page);
                        out.accept(p);
                    }
                    out.accept(PotionContents.createItemStack(Items.POTION, BuiltInRegistries.POTION.wrapAsHolder(modEffects.ANTI_PERMA_BURN_POTION)));
                    ModBlocks.WITH_ITEMS.forEach(out::accept);
                })
                .build());
    }
}
