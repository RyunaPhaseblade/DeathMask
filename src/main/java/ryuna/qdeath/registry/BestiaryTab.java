package ryuna.qdeath.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.moditems.modItems;
import ryuna.qdeath.yokai.YokaiContent;

/** Creative tab with every mob spawn egg plus the yokai tools. */
public final class BestiaryTab {
    private BestiaryTab() {}

    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), Quantumdeath.id("bestiary"));

    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
                .icon(() -> new ItemStack(modItems.SWARM_EYE_SPAWN_EGG))
                .title(Component.translatable("creativeTab.quantumdeath.bestiary"))
                .displayItems((params, out) -> {
                    out.accept(modItems.SWARM_EYE_SPAWN_EGG);
                    out.accept(modItems.DAMPER_SPAWN_EGG);
                    out.accept(modItems.BIFAUCE_SPAWN_EGG);
                    out.accept(modItems.FAKE_CREEPER_SPAWN_EGG);
                    HellItems.SPAWN_EGGS.forEach(out::accept);
                    out.accept(YokaiContent.AMULET);
                    out.accept(YokaiContent.RITUAL_THREAD);
                })
                .build());
    }
}
