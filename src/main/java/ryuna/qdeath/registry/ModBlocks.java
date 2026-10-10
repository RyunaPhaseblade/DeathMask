package ryuna.qdeath.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.block.HellAltarBlock;
import ryuna.qdeath.block.HellBlockTypes.*;
import ryuna.qdeath.block.LimboBlocks;
import ryuna.qdeath.block.PuzzleBlocks;
import ryuna.qdeath.hell.HellLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ModBlocks {
    private ModBlocks() {}

    /** Blocks that get a BlockItem, in creative-tab order. */
    public static final List<Block> WITH_ITEMS = new ArrayList<>();

    private static Block reg(String id, Function<Properties, Block> factory, Properties props) { return reg(id, factory, props, true); }

    private static Block reg(String id, Function<Properties, Block> factory, Properties props, boolean item) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Quantumdeath.id(id));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
        if (item) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id));
            Item.Properties ip = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
            if (id.contains("gate") || id.contains("altar") || id.equals("toll_brazier") || id.equals("feast_altar") || id.equals("drowned_bell") || id.equals("confession_lectern"))
                ip = ip.rarity(Rarity.EPIC);
            Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, ip));
            WITH_ITEMS.add(block);
        }
        return block;
    }

    private static Properties stone(MapColor color, float hardness) {
        return Properties.of().mapColor(color).strength(hardness, 6.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERRACK);
    }

    // ---------------------------------------------------------------- I. The Ashen Vestibule
    public static final Block ASHEN_STONE = reg("ashen_stone", Block::new, stone(MapColor.COLOR_LIGHT_GRAY, 0.6f).sound(SoundType.TUFF));
    public static final Block ASHEN_BRICKS = reg("ashen_bricks", Block::new, stone(MapColor.COLOR_LIGHT_GRAY, 1.5f).sound(SoundType.TUFF_BRICKS));
    public static final Block CHISELED_ASHEN_BRICKS = reg("chiseled_ashen_bricks", Block::new, stone(MapColor.COLOR_LIGHT_GRAY, 1.5f).sound(SoundType.TUFF_BRICKS));
    public static final Block ASH = reg("ash", p -> new ColoredFallingBlock(new ColorRGBA(0xff9a958f), p), Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.4f).sound(SoundType.SAND));
    public static final Block CINDER_SOIL = reg("cinder_soil", Block::new, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.6f).sound(SoundType.SOUL_SOIL));
    public static final Block CHARRED_LOG = reg("charred_log", RotatedPillarBlock::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f).sound(SoundType.STEM));
    public static final Block CHARRED_PLANKS = reg("charred_planks", Block::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.NETHER_WOOD));
    public static final Block EMBER_ORE = reg("ember_ore", Block::new, stone(MapColor.COLOR_ORANGE, 2.0f).lightLevel(s -> 5));
    // The Limbo: hazards, graves and the defences its dead can and cannot break
    public static final Block DEEP_ASH = reg("deep_ash", LimboBlocks.DeepAsh::new, Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5f).speedFactor(0.45f).jumpFactor(0.8f).sound(SoundType.SAND));
    public static final Block UNSTABLE_BONE = reg("unstable_bone", LimboBlocks.UnstableBone::new, Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.BONE_BLOCK));
    public static final Block LIMBO_GRAVE = reg("limbo_grave", LimboBlocks.Grave::new, Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6.0f).sound(SoundType.ROOTED_DIRT).noOcclusion());
    /** Built to be broken: the only defence (with wooden doors, fences and gates) the Ossuary and the Bearer can batter down. */
    public static final Block FUNERAL_BARRICADE = reg("funeral_barricade", Block::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final Block BREACH_ANCHOR = reg("breach_anchor", LimboBlocks.BreachAnchor::new, Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(-1.0f, 3600000f).lightLevel(s -> 7).sound(SoundType.LODESTONE).noLootTable().pushReaction(PushReaction.IMMOVEABLE));

    // ---------------------------------------------------------------- II. The Gorge of Hunger
    public static final Block FLESH = reg("flesh", Block::new, Properties.of().mapColor(MapColor.COLOR_RED).strength(0.8f).sound(SoundType.MUD));
    public static final Block GRISTLE = reg("gristle", Block::new, stone(MapColor.TERRACOTTA_PINK, 1.0f).sound(SoundType.BONE_BLOCK));
    public static final Block GRISTLE_BRICKS = reg("gristle_bricks", Block::new, stone(MapColor.TERRACOTTA_PINK, 1.5f).sound(SoundType.BONE_BLOCK));
    public static final Block MAW_TRAP = reg("maw_trap", MawTrap::new, Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0f).sound(SoundType.MUD));
    public static final Block BILE = reg("bile", p -> new Sticky(true, p), Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.5f).speedFactor(0.5f).jumpFactor(0.6f).sound(SoundType.HONEY_BLOCK).noOcclusion());
    public static final Block FANG_SPIKE = reg("fang_spike", FangSpike::new, Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.8f).sound(SoundType.BONE_BLOCK).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final Block ROTTING_STALK = reg("rotting_stalk", RotatedPillarBlock::new, Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(2.0f).sound(SoundType.STEM));
    public static final Block SINEW_CAP = reg("sinew_cap", Block::new, Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0f).sound(SoundType.WART_BLOCK));

    // ---------------------------------------------------------------- III. The Stygian Mire
    public static final Block STYGIAN_MUD = reg("stygian_mud", Block::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).speedFactor(0.7f).sound(SoundType.MUD));
    public static final Block STYGIAN_STONE = reg("stygian_stone", Block::new, stone(MapColor.COLOR_CYAN, 1.2f).sound(SoundType.DEEPSLATE));
    public static final Block STYGIAN_BRICKS = reg("stygian_bricks", Block::new, stone(MapColor.COLOR_CYAN, 1.8f).sound(SoundType.DEEPSLATE_BRICKS));
    public static final Block PETRIFIED_SOUL = reg("petrified_soul", RotatedPillarBlock::new, stone(MapColor.COLOR_GRAY, 1.5f).sound(SoundType.CALCITE));
    public static final Block TAR = reg("tar", p -> new Sticky(false, p), Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).speedFactor(0.35f).jumpFactor(0.5f).sound(SoundType.HONEY_BLOCK));
    public static final Block DROWNED_LOG = reg("drowned_log", RotatedPillarBlock::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f).sound(SoundType.MANGROVE_ROOTS));

    // ---------------------------------------------------------------- IV. The Iron City of Dis
    public static final Block DIS_IRON_ORE = reg("dis_iron_ore", Block::new, stone(MapColor.COLOR_BLACK, 3.0f).sound(SoundType.GILDED_BLACKSTONE));
    public static final Block DIS_IRON_BLOCK = reg("dis_iron_block", Block::new, stone(MapColor.COLOR_RED, 5.0f).sound(SoundType.METAL));
    public static final Block DIS_BRICKS = reg("dis_bricks", Block::new, stone(MapColor.COLOR_BLACK, 2.0f).sound(SoundType.NETHER_BRICKS));
    public static final Block CRACKED_DIS_BRICKS = reg("cracked_dis_bricks", Block::new, stone(MapColor.COLOR_BLACK, 2.0f).sound(SoundType.NETHER_BRICKS));
    public static final Block MOLTEN_DIS_IRON = reg("molten_dis_iron", MagmaBlock::new, stone(MapColor.COLOR_ORANGE, 1.0f).lightLevel(s -> 9).emissiveRendering(s -> true).sound(SoundType.METAL));
    public static final Block BLEEDING_LOG = reg("bleeding_log", BleedingLog::new, Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f).sound(SoundType.STEM));
    public static final Block BURNING_TOMB = reg("burning_tomb", BurningTomb::new, stone(MapColor.COLOR_BLACK, 3.0f).lightLevel(s -> 12).noOcclusion());
    public static final Block CRUMBLING_BONE = reg("crumbling_bone", Crumbling::new, Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.BONE_BLOCK));
    /** Looks like a Dis brick wall but can be walked through. A faint crack gives it away. */
    public static final Block HOLLOW_WALL = reg("hollow_wall", Block::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f).noCollision().sound(SoundType.NETHER_BRICKS));
    public static final Block CHOIR_BELL = reg("choir_bell", PuzzleBlocks.ChoirBell::new, Properties.of().mapColor(MapColor.GOLD).strength(5.0f, 1200f).sound(SoundType.ANVIL).noOcclusion());
    public static final Block SEALED_DOOR = reg("sealed_door", Block::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000f).sound(SoundType.METAL).lightLevel(s -> 3));
    public static final Block SEALED_SARCOPHAGUS = reg("sealed_sarcophagus", PuzzleBlocks.Sarcophagus::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000f).sound(SoundType.STONE).noOcclusion());

    // ---------------------------------------------------------------- V. The Zenith
    public static final Block ZENITH_STONE = reg("zenith_stone", Block::new, stone(MapColor.SNOW, 1.2f).sound(SoundType.CALCITE));
    public static final Block ZENITH_BRICKS = reg("zenith_bricks", Block::new, stone(MapColor.SNOW, 1.8f).sound(SoundType.CALCITE));
    public static final Block PALE_ICE = reg("pale_ice", TransparentBlock::new, Properties.of().mapColor(MapColor.ICE).strength(0.8f).friction(0.98f).sound(SoundType.GLASS).noOcclusion());
    public static final Block RED_STATIC = reg("red_static", Static::new, Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).lightLevel(s -> 7).emissiveRendering(s -> true).sound(SoundType.AMETHYST));
    public static final Block VOID_GLASS = reg("void_glass", TransparentBlock::new, Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5f).sound(SoundType.GLASS).noOcclusion());
    public static final Block FALSE_BLOCK = reg("false_block", FalseBlock::new, Properties.of().mapColor(MapColor.SNOW).strength(1.2f).sound(SoundType.CALCITE));
    public static final Block FALSE_AIR = reg("false_air", FalseAir::new, Properties.of().noCollision().noLootTable().air().strength(-1.0f, 3600000f), false);
    public static final Block FROZEN_TEAR_ORE = reg("frozen_tear_ore", Block::new, stone(MapColor.SNOW, 3.0f).sound(SoundType.CALCITE).lightLevel(s -> 4));
    public static final Block VIGIL_BELL = reg("vigil_bell", PuzzleBlocks.VigilBell::new, Properties.of().mapColor(MapColor.SNOW).strength(-1.0f, 3600000f).sound(SoundType.ANVIL).noOcclusion());

    // ---------------------------------------------------------------- Shared: gates, sanctuary, traces, altars
    public static final Block DESCENT_GATE = reg("descent_gate", p -> new Gate(true, p), Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0f, 3600000f).noCollision().lightLevel(s -> 11).sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final Block ASCENT_GATE = reg("ascent_gate", p -> new Gate(false, p), Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000f).noCollision().lightLevel(s -> 11).sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final Block VOTIVE_LANTERN = reg("votive_lantern", VotiveLantern::new, Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final Block MOURNING_CANDLE = reg("mourning_candle", p -> new Decaying(Block.box(3, 0, 3, 13, 8, 13), true, p), Properties.of().mapColor(MapColor.SNOW).strength(0.1f).sound(SoundType.CANDLE).lightLevel(s -> 6).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final Block ASH_PRINT = reg("ash_print", p -> new Decaying(Block.box(0, 0, 0, 16, 1, 16), false, p), Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.0f).instabreak().sound(SoundType.SAND).noOcclusion().replaceable().noLootTable().pushReaction(PushReaction.POPPED));
    public static final Block PILGRIM_REMAINS = reg("pilgrim_remains", p -> new Facing(Block.box(1, 0, 1, 15, 5, 15), p), Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.BONE_BLOCK).noOcclusion());
    public static final Block TOLL_BRAZIER = reg("toll_brazier", p -> new HellAltarBlock(HellLayer.VESTIBULE, p), altar().lightLevel(s -> 13));
    public static final Block FEAST_ALTAR = reg("feast_altar", p -> new HellAltarBlock(HellLayer.GORGE, p), altar().sound(SoundType.BONE_BLOCK));
    public static final Block DROWNED_BELL = reg("drowned_bell", p -> new HellAltarBlock(HellLayer.MIRE, p), altar().sound(SoundType.ANVIL));
    public static final Block CONFESSION_LECTERN = reg("confession_lectern", p -> new HellAltarBlock(HellLayer.DIS, p), altar().lightLevel(s -> 10));
    public static final Block LAST_VIGIL_ALTAR = reg("last_vigil_altar", p -> new HellAltarBlock(HellLayer.ZENITH, p), altar().lightLevel(s -> 8));

    private static Properties altar() {
        return Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000f).sound(SoundType.STONE).noOcclusion();
    }

    public static final Set<Block> GATES = Set.of(DESCENT_GATE, ASCENT_GATE);

    public static void init() { /* class load registers everything */ }
}
