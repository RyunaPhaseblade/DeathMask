package ryuna.qdeath.yokai;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.entity.yokai.*;
import ryuna.qdeath.item.HellItemTypes;
import ryuna.qdeath.registry.HellItems;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Registration of the yokai expansion: creatures, items, components, attachments, events and commands. */
public final class YokaiContent {
    private YokaiContent() {}

    private static <T extends Entity> EntityType<T> entity(String id, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static <T extends Entity> EntityType.Builder<T> spirit(EntityType.EntityFactory<T> f, MobCategory c, float w, float h) {
        return EntityType.Builder.of(f, c).sized(w, h).clientTrackingRange(10);
    }

    public static final EntityType<KappaEntity> KAPPA = entity("kappa", spirit(KappaEntity::new, MobCategory.MISC, 0.7f, 1.3f));
    public static final EntityType<OniEntity> ONI = entity("oni", spirit(OniEntity::new, MobCategory.MONSTER, 1.1f, 2.9f));
    public static final EntityType<ChestTsukumogami> CHEST = entity("chest_tsukumogami", spirit(ChestTsukumogami::new, MobCategory.MISC, 0.875f, 0.95f));
    public static final EntityType<TanukiEntity> TANUKI = entity("tanuki", spirit(TanukiEntity::new, MobCategory.MISC, 0.6f, 0.9f));
    public static final EntityType<TenguEntity> TENGU = entity("tengu", spirit(TenguEntity::new, MobCategory.MISC, 0.7f, 2.1f));
    public static final EntityType<KodamaEntity> KODAMA = entity("kodama", spirit(KodamaEntity::new, MobCategory.MISC, 0.4f, 0.75f));
    public static final EntityType<NurikabeEntity> NURIKABE = entity("nurikabe", spirit(NurikabeEntity::new, MobCategory.MISC, 2.4f, 2.9f));
    public static final EntityType<IttanMomenEntity> ITTAN_MOMEN = entity("ittan_momen", spirit(IttanMomenEntity::new, MobCategory.MONSTER, 0.9f, 0.45f));
    public static final EntityType<BellTsukumogami> BELL = entity("bell_tsukumogami", spirit(BellTsukumogami::new, MobCategory.MISC, 0.8f, 1.0f));
    public static final EntityType<TanukiDecoy> TANUKI_DECOY = entity("tanuki_decoy",
            EntityType.Builder.<TanukiDecoy>of(TanukiDecoy::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(10));

    public static final DataComponentType<AmuletData> AMULET_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Quantumdeath.id("amulet"),
            DataComponentType.<AmuletData>builder().persistent(AmuletData.CODEC).networkSynchronized(AmuletData.STREAM_CODEC).build());

    private static <T extends Item> T item(String id, Function<Item.Properties, T> factory, Item.Properties props, boolean creative) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id));
        T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
        if (creative) HellItems.CREATIVE.add(item);
        return item;
    }

    public static final Item AMULET = item("shikigami_amulet", AmuletItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), false);
    public static final Item RITUAL_THREAD = item("ritual_thread", HellItemTypes.Lore::new, new Item.Properties().rarity(Rarity.UNCOMMON), false);

    public static final TagKey<Block> KAPPA_VEGETATION = TagKey.create(Registries.BLOCK, Quantumdeath.id("kappa_vegetation"));

    private static void egg(String id, EntityType<?> type) {
        HellItems.SPAWN_EGGS.add(item(id + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type), false));
    }

    /** Players whose own data must be re-sent next tick (after Fabric has copied their attachments on respawn). */
    private static final Set<UUID> RESYNC = new HashSet<>();

    public static void init() {
        YokaiBalance.load();
        FabricDefaultAttributeRegistry.register(KAPPA, KappaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ONI, OniEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CHEST, ChestTsukumogami.createAttributes());
        FabricDefaultAttributeRegistry.register(TANUKI, TanukiEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TENGU, TenguEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(KODAMA, KodamaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NURIKABE, NurikabeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ITTAN_MOMEN, IttanMomenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BELL, BellTsukumogami.createAttributes());
        Species.FALSE_CREEPER.bind(() -> ModEntities.FAKE_CREEPER);
        Species.BIFAUCE.bind(() -> ModEntities.BIFAUCE);
        Species.DAMPER.bind(() -> ModEntities.DAMPER);
        Species.SWARM_EYE.bind(() -> ModEntities.SWARM_EYE);
        Species.KAPPA.bind(() -> KAPPA);
        Species.ONI.bind(() -> ONI);
        Species.CHEST.bind(() -> CHEST);
        Species.TANUKI.bind(() -> TANUKI);
        Species.TENGU.bind(() -> TENGU);
        Species.KODAMA.bind(() -> KODAMA);
        Species.NURIKABE.bind(() -> NURIKABE);
        Species.ITTAN_MOMEN.bind(() -> ITTAN_MOMEN);
        Species.BELL.bind(() -> BELL);
        for (String id : new String[]{"kappa", "oni", "chest_tsukumogami", "tanuki", "tengu", "kodama", "nurikabe", "ittan_momen", "bell_tsukumogami"})
            egg(id, BuiltInRegistries.ENTITY_TYPE.getValue(Quantumdeath.id(id)));

        // Attachments register on class load.
        var unused = new Object[]{YokaiPlayer.TYPE, YokaiWorld.TYPE, Bonds.TYPE, Provenance.NATURAL};

        YokaiNet.register();
        YokaiCombat.register();
        Provenance.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            EncounterDirector.tick(server);
            Companions.tick(server);
            YokaiCombat.tick(server);
            if (!RESYNC.isEmpty()) {
                for (UUID u : RESYNC) {
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null) YokaiNet.syncSelf(p);
                }
                RESYNC.clear();
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Awakening.join(handler.player);
            YokaiNet.syncSelf(handler.player);
            Companions.push(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            EncounterDirector.forget(handler.player.getUUID());
            YokaiCombat.forget(handler.player.getUUID());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EncounterDirector.respawned(newPlayer);
            RESYNC.add(newPlayer.getUUID());
        });
        // Tearing up water plants near a kappa provokes it, whatever the player's perception.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (state.is(KAPPA_VEGETATION) && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) KappaEntity.vegetationBroken(sl, pos, sp);
        });
        // A stored amulet destroyed (burnt, in the void, despawned) loses its companion for good.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().is(AMULET) && !item.getItem().isEmpty() && entity.getRemovalReason() != null
                    && entity.getRemovalReason().shouldDestroy()) Bonds.amuletDestroyed(level.getServer(), item.getItem());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EncounterDirector.reset();
            YokaiGoals.clearShelters();
            RESYNC.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> YokaiCommands.register(dispatcher));
    }
}
