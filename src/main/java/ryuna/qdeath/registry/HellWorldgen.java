package ryuna.qdeath.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.world.HellFeatures;
import ryuna.qdeath.world.HellPiece;
import ryuna.qdeath.world.HellStructure;

public final class HellWorldgen {
    private HellWorldgen() {}

    public static final StructureType<HellStructure> HELL_STRUCTURE =
            Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Quantumdeath.id("hell_structure"), () -> HellStructure.CODEC);
    public static final StructurePieceType HELL_PIECE =
            Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Quantumdeath.id("hell_piece"), (StructurePieceType.ContextlessType) HellPiece::new);

    private static void feature(String id, MapCodec<? extends Feature> codec) {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Quantumdeath.id(id), codec);
    }

    public static void init() {
        feature("dead_tree", HellFeatures.DeadTree.CODEC);
        feature("spire", HellFeatures.Spire.CODEC);
        feature("hanging", HellFeatures.Hanging.CODEC);
        feature("statue", HellFeatures.Statue.CODEC);
        feature("limbo_decor", ryuna.qdeath.world.LimboDecor.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Quantumdeath.id("limbo_terrain"), ryuna.qdeath.world.LimboTerrain.CODEC);
    }
}
