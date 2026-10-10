package ryuna.qdeath.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.item.HellItemTypes.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class HellItems {
    private HellItems() {}

    public static final List<Item> CREATIVE = new ArrayList<>();
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();

    /** Journal page index carried by a {@code journal_page} item. */
    public static final DataComponentType<Integer> PAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Quantumdeath.id("page"),
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final TagKey<Item> REPAIRS_HELL_GEAR = TagKey.create(Registries.ITEM, Quantumdeath.id("repairs_hell_gear"));
    public static final ToolMaterial HELLFORGED = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9.0f, 4.0f, 18, REPAIRS_HELL_GEAR);
    public static final ToolMaterial ZENITH = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3000, 10.0f, 6.0f, 22, REPAIRS_HELL_GEAR);

    private static ArmorMaterial armor(String id, int durability, int boots, int legs, int chest, int helm, float toughness, float kb) {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, boots); defense.put(ArmorType.LEGGINGS, legs); defense.put(ArmorType.CHESTPLATE, chest);
        defense.put(ArmorType.HELMET, helm); defense.put(ArmorType.BODY, chest);
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Quantumdeath.id(id));
        return new ArmorMaterial(durability, defense, 16, SoundEvents.ARMOR_EQUIP_NETHERITE, toughness, kb, REPAIRS_HELL_GEAR, asset);
    }

    public static final ArmorMaterial PILGRIM = armor("pilgrim", 22, 2, 5, 6, 2, 1.0f, 0.0f);
    public static final ArmorMaterial LEVIATHAN = armor("leviathan", 30, 3, 6, 8, 3, 2.0f, 0.0f);
    public static final ArmorMaterial DIS_IRON = armor("dis_iron", 40, 3, 6, 8, 3, 3.0f, 0.1f);
    public static final ArmorMaterial DEATH_MASK = armor("death_mask", 45, 3, 6, 8, 4, 3.0f, 0.1f);

    public static final ResourceKey<JukeboxSong> SONG_LAST_VIGIL = ResourceKey.create(Registries.JUKEBOX_SONG, Quantumdeath.id("last_vigil"));

    private static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id));
        T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
        CREATIVE.add(item);
        return item;
    }

    private static Item lore(String id, Rarity rarity) { return reg(id, Lore::new, new Item.Properties().rarity(rarity)); }
    private static Item hidden(String id) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    // Materials
    public static final Item ASHEN_OBOL = lore("ashen_obol", Rarity.COMMON);
    public static final Item SOUL_ASH = lore("soul_ash", Rarity.COMMON);
    public static final Item CINDER_SHARD = lore("cinder_shard", Rarity.COMMON);
    public static final Item SINEW = lore("sinew", Rarity.COMMON);
    public static final Item BILE_SAC = lore("bile_sac", Rarity.COMMON);
    public static final Item HUNGER_FANG = lore("hunger_fang", Rarity.UNCOMMON);
    public static final Item STYGIAN_PEARL = lore("stygian_pearl", Rarity.UNCOMMON);
    public static final Item LEVIATHAN_SCALE = lore("leviathan_scale", Rarity.RARE);
    public static final Item RAW_DIS_IRON = lore("raw_dis_iron", Rarity.COMMON);
    public static final Item DIS_IRON_INGOT = lore("dis_iron_ingot", Rarity.UNCOMMON);
    public static final Item HERETIC_EMBER = lore("heretic_ember", Rarity.UNCOMMON);
    public static final Item FROZEN_TEAR = lore("frozen_tear", Rarity.UNCOMMON);
    public static final Item ZENITH_SHARD = lore("zenith_shard", Rarity.RARE);
    public static final Item MASK_FRAGMENT = lore("mask_fragment", Rarity.RARE);
    public static final Item OSSUARY_KEY = lore("ossuary_key", Rarity.RARE);
    public static final Item JOURNAL_PAGE = reg("journal_page", JournalPage::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    // Offerings that summon the layer bosses
    public static final Item FERRYMAN_TOLL = reg("ferryman_toll", p -> new Lore(p, true), new Item.Properties().rarity(Rarity.RARE));
    public static final Item GLUTTONS_OFFERING = reg("gluttons_offering", p -> new Lore(p, true), new Item.Properties().rarity(Rarity.RARE));
    public static final Item STYGIAN_LURE = reg("stygian_lure", p -> new Lore(p, true), new Item.Properties().rarity(Rarity.RARE));
    public static final Item HERETICS_CONFESSION = reg("heretics_confession", p -> new Lore(p, true), new Item.Properties().rarity(Rarity.RARE));
    public static final Item LAST_CANDLE = reg("last_candle", p -> new Lore(p, true), new Item.Properties().rarity(Rarity.EPIC));

    // The Limbo: votive fuel and the breath that leads the dead astray
    public static final Item FUNERAL_WAX = reg("funeral_wax", p -> new ryuna.qdeath.item.LimboItemTypes.FuneralWax(p, ryuna.qdeath.limbo.LimboTuning.WAX_UNITS),
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item CONSECRATED_WAX = reg("consecrated_wax", p -> new ryuna.qdeath.item.LimboItemTypes.FuneralWax(p, ryuna.qdeath.limbo.LimboTuning.CONSECRATED_UNITS),
            new Item.Properties().rarity(Rarity.RARE));
    public static final Item BREATH_LURE = reg("breath_lure", ryuna.qdeath.item.LimboItemTypes.BreathLure::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    // Relics and tools
    public static final Item PILGRIMS_COMPASS = reg("pilgrims_compass", PilgrimsCompass::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item CINDER_HOOK = reg("cinder_hook", CinderHook::new, new Item.Properties().durability(256).rarity(Rarity.UNCOMMON));
    public static final Item MOURNING_BELL = reg("mourning_bell", MourningBell::new, new Item.Properties().durability(16).rarity(Rarity.RARE));
    public static final Item MIRROR_OF_LETHE = reg("mirror_of_lethe", MirrorOfLethe::new, new Item.Properties().durability(240).rarity(Rarity.EPIC));
    public static final Item FERRYMAN_LANTERN = reg("ferryman_lantern", FerrymanLantern::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Boss weapons
    public static final Item TOLL_KEEPERS_OAR = reg("toll_keepers_oar", p -> new HellWeapon(p, HellWeapon.Kind.OAR),
            new Item.Properties().sword(HELLFORGED, 4.0f, -2.9f).fireResistant().rarity(Rarity.EPIC));
    public static final Item MAW_CLEAVER = reg("maw_cleaver", p -> new HellWeapon(p, HellWeapon.Kind.CLEAVER),
            new Item.Properties().axe(HELLFORGED, 6.0f, -3.0f).fireResistant().rarity(Rarity.EPIC));
    public static final Item STYGIAN_HARPOON = reg("stygian_harpoon", p -> new HellWeapon(p, HellWeapon.Kind.HARPOON),
            new Item.Properties().sword(HELLFORGED, 2.0f, -2.6f).fireResistant().rarity(Rarity.EPIC));
    public static final Item HERETICS_BRAND = reg("heretics_brand", p -> new HellWeapon(p, HellWeapon.Kind.BRAND),
            new Item.Properties().sword(HELLFORGED, 4.0f, -2.4f).fireResistant().rarity(Rarity.EPIC));
    public static final Item REQUIEM = reg("requiem", p -> new HellWeapon(p, HellWeapon.Kind.REQUIEM),
            new Item.Properties().sword(ZENITH, 5.0f, -2.4f).fireResistant().rarity(Rarity.EPIC));

    // Food and hearts
    public static final Item ROTTEN_FRUIT = reg("rotten_fruit", Lore::new, new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.4f).build()));
    public static final Item MATRIARCHS_HEART = reg("matriarchs_heart", p -> new Heart(p, "matriarchs_heart", 4.0), new Item.Properties().rarity(Rarity.EPIC)
            .food(new FoodProperties.Builder().nutrition(8).saturationModifier(1.0f).alwaysEdible().build()));
    public static final Item ZENITH_HEART = reg("zenith_heart", p -> new Heart(p, "zenith_heart", 4.0), new Item.Properties().rarity(Rarity.EPIC)
            .food(new FoodProperties.Builder().nutrition(8).saturationModifier(1.0f).alwaysEdible().build()));

    // Armor
    public static final Item PILGRIM_HOOD = reg("pilgrim_hood", Lore::new, new Item.Properties().humanoidArmor(PILGRIM, ArmorType.HELMET));
    public static final Item PILGRIM_ROBE = reg("pilgrim_robe", Lore::new, new Item.Properties().humanoidArmor(PILGRIM, ArmorType.CHESTPLATE));
    public static final Item PILGRIM_LEGGINGS = reg("pilgrim_leggings", Lore::new, new Item.Properties().humanoidArmor(PILGRIM, ArmorType.LEGGINGS));
    public static final Item PILGRIM_BOOTS = reg("pilgrim_boots", Lore::new, new Item.Properties().humanoidArmor(PILGRIM, ArmorType.BOOTS));
    public static final Item LEVIATHAN_HELM = reg("leviathan_helm", Lore::new, new Item.Properties().humanoidArmor(LEVIATHAN, ArmorType.HELMET).rarity(Rarity.RARE));
    public static final Item LEVIATHAN_CUIRASS = reg("leviathan_cuirass", Lore::new, new Item.Properties().humanoidArmor(LEVIATHAN, ArmorType.CHESTPLATE).rarity(Rarity.RARE));
    public static final Item LEVIATHAN_GREAVES = reg("leviathan_greaves", Lore::new, new Item.Properties().humanoidArmor(LEVIATHAN, ArmorType.LEGGINGS).rarity(Rarity.RARE));
    public static final Item LEVIATHAN_BOOTS = reg("leviathan_boots", Lore::new, new Item.Properties().humanoidArmor(LEVIATHAN, ArmorType.BOOTS).rarity(Rarity.RARE));
    public static final Item DIS_IRON_HELMET = reg("dis_iron_helmet", Lore::new, new Item.Properties().humanoidArmor(DIS_IRON, ArmorType.HELMET).fireResistant().rarity(Rarity.RARE));
    public static final Item DIS_IRON_CHESTPLATE = reg("dis_iron_chestplate", Lore::new, new Item.Properties().humanoidArmor(DIS_IRON, ArmorType.CHESTPLATE).fireResistant().rarity(Rarity.RARE));
    public static final Item DIS_IRON_LEGGINGS = reg("dis_iron_leggings", Lore::new, new Item.Properties().humanoidArmor(DIS_IRON, ArmorType.LEGGINGS).fireResistant().rarity(Rarity.RARE));
    public static final Item DIS_IRON_BOOTS = reg("dis_iron_boots", Lore::new, new Item.Properties().humanoidArmor(DIS_IRON, ArmorType.BOOTS).fireResistant().rarity(Rarity.RARE));
    public static final Item VIGILS_DEATH_MASK = reg("vigils_death_mask", p -> new Lore(p, true), new Item.Properties().humanoidArmor(DEATH_MASK, ArmorType.HELMET).fireResistant().rarity(Rarity.EPIC));

    public static final Item MUSIC_DISC_LAST_VIGIL = reg("music_disc_last_vigil", Lore::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).jukeboxPlayable(SONG_LAST_VIGIL));

    // Projectile sprites (not obtainable)
    public static final Item SOUL_WISP = hidden("soul_wisp");
    public static final Item BILE_GLOB = hidden("bile_glob");
    public static final Item TAR_GLOB = hidden("tar_glob");
    public static final Item CHAIN_HOOK = hidden("chain_hook");
    public static final Item HELL_EMBER = hidden("hell_ember");
    public static final Item HARPOON_BOLT = hidden("harpoon_bolt");
    public static final Item HOOK_HEAD = hidden("hook_head");

    static Item egg(String id, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id + "_spawn_egg"));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
        SPAWN_EGGS.add(item);
        return item;
    }

    public static void init() { HellEntities.registerSpawnEggs(); }
}
