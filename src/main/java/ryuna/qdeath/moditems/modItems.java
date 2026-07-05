package ryuna.qdeath.moditems;

import ryuna.qdeath.Quantumdeath;
import net.minecraft.core.component.DataComponents;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.item.component.Consumable;
//import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
//import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.CompostableRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Rarity;
import java.util.List;

//Custom items
import ryuna.qdeath.moditems.items.qapplei;



public class modItems {
	public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
		// Create the item key.
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, name));

		// Create the item instance.
		T item = itemFactory.apply(settings.setId(itemKey));

		// Register the item.
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);

		return item;
	}

    public static final Item SUSPICIOUS_SUBSTANCE = register("suspicious_substance", Item::new, new Item.Properties());

	public static final Item SHARD_OF_DISCORD = register("shard_of_discord", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item PURIFIED_SCULK_FRAGMENT = register("purified_sculk_fragment", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item SOUL_VESSEL = register("soul_vessel", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item PURIFIED_SOUL_FRAGMENT = register("purified_soul_fragment", Item::new, new Item.Properties().rarity(Rarity.RARE));
	public static final Item REMNANT_OF_THE_VOID = register("remnant_of_the_void", Item::new, new Item.Properties().rarity(Rarity.RARE));
	public static final Item FRAGMENT_OF_THE_VOID = register("fragment_of_the_void", Item::new, new Item.Properties().rarity(Rarity.EPIC));
	public static final Item REINFORCED_SHULKER_SHELL = register("reinforced_shulker_shell", Item::new, new Item.Properties().rarity(Rarity.RARE));

	public static final Item LIFE_FRUIT = register("life_fruit", 
	properties -> new Item(properties.food(
		new net.minecraft.world.food.FoodProperties.Builder()
			.nutrition(6)        // Puntos de comida
			.saturationModifier(1.2f) // Saturación 
			.build()
	)),
	new Item.Properties()
    	.rarity(Rarity.UNCOMMON));

	public static final Item APPLE_OF_DISCORD = register("apple_of_discord", 
	properties -> new qapplei(properties.food(
		new net.minecraft.world.food.FoodProperties.Builder()
			.nutrition(4)        // Puntos de comida
			.saturationModifier(0.6f) // Saturación 
			.alwaysEdible()      // Aunque el jugador no tenga hambre
			.build()
	),1),
	new Item.Properties()
    	.rarity(Rarity.UNCOMMON)
		.component(
    		DataComponents.LORE,
    		new ItemLore(List.of(
        		Component.translatable("item.quantumdeath.apple_of_discord.lore1"),
				Component.translatable("item.quantumdeath.apple_of_discord.lore2"),
				Component.translatable("item.quantumdeath.apple_of_discord.lore3")
    	))));

	public static final Item PURIFIED_SCULK_APPLE = register("purified_sculk_apple",
	properties -> new qapplei(properties.food(
		new net.minecraft.world.food.FoodProperties.Builder()
			.nutrition(4)        // Puntos de comida
			.saturationModifier(0.6f) // Saturación 
			.alwaysEdible()      // Aunque el jugador no tenga hambre
			.build()
	),2),
	new Item.Properties()
    	.rarity(Rarity.RARE)
		.component(
    		DataComponents.LORE,
    		new ItemLore(List.of(
        		Component.translatable("item.quantumdeath.purified_sculk_apple.lore1"),
				Component.translatable("item.quantumdeath.purified_sculk_apple.lore2"),
				Component.translatable("item.quantumdeath.purified_sculk_apple.lore3")
    	))));

	public static final Item PURIFIED_SOUL_APPLE = register("purified_soul_apple",
	properties -> new qapplei(properties.food(
		new net.minecraft.world.food.FoodProperties.Builder()
			.nutrition(4)        // Puntos de comida
			.saturationModifier(0.6f) // Saturación 
			.alwaysEdible()      // Aunque el jugador no tenga hambre
			.build()
	),3),
	new Item.Properties()
    	.rarity(Rarity.EPIC)
		.component(
    		DataComponents.LORE,
    		new ItemLore(List.of(
        		Component.translatable("item.quantumdeath.purified_souls_apple.lore1"),
				Component.translatable("item.quantumdeath.purified_souls_apple.lore2"),
				Component.translatable("item.quantumdeath.purified_souls_apple.lore3")
    	))));

	public static final Item APPLE_OF_THE_VOID = register("apple_of_the_void",
	properties -> new qapplei(properties.food(
		new net.minecraft.world.food.FoodProperties.Builder()
			.nutrition(4)        // Puntos de comida
			.saturationModifier(0.6f) // Saturación 
			.alwaysEdible()      // Aunque el jugador no tenga hambre
			.build()
	),4),
	new Item.Properties()
    	.rarity(Rarity.EPIC)
		.component(
    		DataComponents.LORE,
    		new ItemLore(List.of(
        		Component.translatable("item.quantumdeath.apple_of_the_void.lore1"),
				Component.translatable("item.quantumdeath.apple_of_the_void.lore2"),
				Component.translatable("item.quantumdeath.apple_of_the_void.lore3")
    	))));

	//Crear un creative tab

	public static final ResourceKey<CreativeModeTab> CUSTOM_CREATIVE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, "creative_tab")
	);

	//Registrar items en el creative tab
	public static final CreativeModeTab QUANTUM_DEATH_CREATIVE_TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(modItems.APPLE_OF_THE_VOID))
			.title(Component.translatable("creativeTab.quantum-death-mod"))
			.displayItems((params, output) -> {
				output.accept(modItems.SUSPICIOUS_SUBSTANCE);
				output.accept(modItems.APPLE_OF_DISCORD);
				output.accept(modItems.PURIFIED_SCULK_APPLE);
				output.accept(modItems.PURIFIED_SOUL_APPLE);
				output.accept(modItems.APPLE_OF_THE_VOID);
				output.accept(modItems.LIFE_FRUIT);
				output.accept(modItems.SHARD_OF_DISCORD);
				output.accept(modItems.PURIFIED_SCULK_FRAGMENT);
				output.accept(modItems.SOUL_VESSEL);
				output.accept(modItems.PURIFIED_SOUL_FRAGMENT);
				output.accept(modItems.REMNANT_OF_THE_VOID);
				output.accept(modItems.FRAGMENT_OF_THE_VOID);
				output.accept(modItems.REINFORCED_SHULKER_SHELL);

			})	
			.build();

	
    public static void initialize() {
		
		//Inicializar el creative tab
    	Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CUSTOM_CREATIVE_TAB_KEY, QUANTUM_DEATH_CREATIVE_TAB);
		
		// Register items here

		//Registrarlo en una creative tab
        /*CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).
        register((creativeTab) -> {
			creativeTab.accept(modItems.SUSPICIOUS_SUBSTANCE);
			creativeTab.accept(modItems.QUANTUM_APPLE);
		});*/

		//Registrarlo como composter item
		CompostableRegistry.INSTANCE.add(modItems.SUSPICIOUS_SUBSTANCE, 0.3f);

		//Registrarlo como combustible
		FuelValueEvents.BUILD.register((builder, context) -> {
			builder.add(modItems.SUSPICIOUS_SUBSTANCE, 30 * 20);
		});



    }

}
