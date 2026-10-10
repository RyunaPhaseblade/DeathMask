package ryuna.qdeath.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import ryuna.qdeath.Quantumdeath;

public final class ModEntities {
    private ModEntities() {}

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Quantumdeath.MOD_ID, id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static final EntityType<SwarmEyeEntity> SWARM_EYE = register("swarm_eye", EntityType.Builder.of(SwarmEyeEntity::new, MobCategory.MONSTER).sized(0.8f, 0.8f).clientTrackingRange(8));
    public static final EntityType<DamperEntity> DAMPER = register("damper", EntityType.Builder.of(DamperEntity::new, MobCategory.MONSTER).sized(0.6f, 2.9f));
    public static final EntityType<BifauceEntity> BIFAUCE = register("bifauce", EntityType.Builder.of(BifauceEntity::new, MobCategory.MONSTER).sized(0.6f, 2.9f));
    public static final EntityType<FakeCreeperEntity> FAKE_CREEPER = register("fake_creeper", EntityType.Builder.of(FakeCreeperEntity::new, MobCategory.MONSTER).sized(0.6f, 1.7f));
    public static final EntityType<SwarmArmProjectile> SWARM_ARM = register("swarm_arm", EntityType.Builder.<SwarmArmProjectile>of(SwarmArmProjectile::new, MobCategory.MISC).sized(0.35f, 0.35f).clientTrackingRange(6).updateInterval(1));

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(SWARM_EYE, SwarmEyeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DAMPER, DamperEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BIFAUCE, BifauceEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FAKE_CREEPER, FakeCreeperEntity.createAttributes());
        // No Overworld biome spawns any more: before the awakening they do not appear at all, and afterwards the yokai
        // encounter director (ryuna.qdeath.yokai.EncounterDirector) places them, so encounters are never doubled.
        // Their Hell spawns live in the Hell biomes' JSON and are unchanged.
    }
}
