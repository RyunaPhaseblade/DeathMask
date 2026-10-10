package ryuna.qdeath.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.entity.boss.GorgedMatriarchEntity;
import ryuna.qdeath.entity.boss.HeresiarchEntity;
import ryuna.qdeath.entity.boss.StyxmawEntity;
import ryuna.qdeath.entity.boss.TollKeeperEntity;
import ryuna.qdeath.entity.hell.*;
import ryuna.qdeath.entity.limbo.*;
import ryuna.qdeath.entity.projectile.BileBolus;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.entity.projectile.RibShard;
import ryuna.qdeath.entity.vigil.VigilEntity;
import ryuna.qdeath.entity.vigil.VigilWakeEntity;

public final class HellEntities {
    private HellEntities() {}

    private static <T extends Entity> EntityType<T> reg(String id, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static <T extends Entity> EntityType.Builder<T> monster(EntityType.EntityFactory<T> f, float w, float h) {
        return EntityType.Builder.of(f, MobCategory.MONSTER).sized(w, h).clientTrackingRange(10);
    }

    // Layer I
    public static final EntityType<HollowEntity> HOLLOW = reg("hollow", monster(HollowEntity::new, 0.6f, 1.95f));
    public static final EntityType<CinderKnightEntity> CINDER_KNIGHT = reg("cinder_knight", monster(CinderKnightEntity::new, 0.8f, 2.6f).fireImmune());
    // Layer I, the Limbo: the dead of the procession (the Hollow above is their core)
    public static final EntityType<RunawayEntity> RUNAWAY = reg("runaway", monster(RunawayEntity::new, 0.6f, 1.95f));
    public static final EntityType<CrierEntity> CRIER = reg("crier", monster(CrierEntity::new, 0.6f, 2.1f));
    // The specialists of the refined Limbo: human bodies with one legible deformity each.
    public static final EntityType<StalkerEntity> STALKER = reg("stalker", monster(StalkerEntity::new, 0.6f, 1.95f));
    public static final EntityType<ForemanEntity> FOREMAN = reg("foreman", monster(ForemanEntity::new, 0.75f, 2.0f));
    public static final EntityType<LankyEntity> LANKY = reg("lanky", monster(LankyEntity::new, 0.7f, 2.65f));
    public static final EntityType<BloaterEntity> BLOATER = reg("bloater", monster(BloaterEntity::new, 0.8f, 1.95f));
    public static final EntityType<BreakerEntity> BREAKER = reg("breaker", monster(BreakerEntity::new, 1.1f, 2.45f));
    /** Small enough to pass through gaps a man cannot (one block high). */
    public static final EntityType<RuntEntity> RUNT = reg("runt", monster(RuntEntity::new, 0.5f, 0.95f));
    /** The Putrescent's corpse: not a creature, a body that bursts (fuse and state saved with it). */
    public static final EntityType<BloaterCorpse> BLOATER_CORPSE = reg("bloater_corpse",
            EntityType.Builder.<BloaterCorpse>of(BloaterCorpse::new, MobCategory.MISC).sized(0.9f, 0.5f).clientTrackingRange(8).updateInterval(4));
    /** Low enough to crawl through one-block gaps, never through solid blocks. */
    public static final EntityType<CrawlerEntity> CRAWLER = reg("crawler", monster(CrawlerEntity::new, 0.9f, 0.6f));
    public static final EntityType<SunderedEntity> SUNDERED = reg("sundered", monster(SunderedEntity::new, 0.7f, 2.0f));
    public static final EntityType<WalkingOssuaryEntity> WALKING_OSSUARY = reg("walking_ossuary", monster(WalkingOssuaryEntity::new, 1.6f, 2.3f));
    public static final EntityType<MourningHoundEntity> MOURNING_HOUND = reg("mourning_hound", monster(MourningHoundEntity::new, 1.0f, 1.0f));
    public static final EntityType<WakeBearerEntity> WAKE_BEARER = reg("wake_bearer", monster(WakeBearerEntity::new, 1.2f, 3.0f).clientTrackingRange(12));
    // The expansion (docs/ampliacion-limbo-especificacion.md): a parasite of the living, a sapper, a tongue and two throwers.
    public static final EntityType<MourningParasiteEntity> MOURNING_PARASITE = reg("mourning_parasite", monster(MourningParasiteEntity::new, 0.55f, 0.45f));
    public static final EntityType<BreachTickerEntity> BREACH_TICKER = reg("breach_ticker", monster(BreachTickerEntity::new, 0.7f, 0.65f));
    public static final EntityType<StranglerEntity> STRANGLER = reg("strangler", monster(StranglerEntity::new, 0.6f, 1.95f));
    public static final EntityType<BileSpitterEntity> BILE_SPITTER = reg("bile_spitter", monster(BileSpitterEntity::new, 0.7f, 1.95f));
    public static final EntityType<RibThrowerEntity> RIB_THROWER = reg("rib_thrower", monster(RibThrowerEntity::new, 0.6f, 1.95f));
    /** A hittable length of a Strangler's tongue: placed by both sides from the tongue, never saved. */
    public static final EntityType<TongueSegment> TONGUE_SEGMENT = reg("tongue_segment",
            EntityType.Builder.<TongueSegment>of(TongueSegment::new, MobCategory.MISC).sized(0.45f, 0.45f).clientTrackingRange(8).updateInterval(1).noSave().noSummon());
    /** A puddle of bile: slows whoever steps in it for three seconds; never saved. */
    public static final EntityType<BileResidue> BILE_RESIDUE = reg("bile_residue",
            EntityType.Builder.<BileResidue>of(BileResidue::new, MobCategory.MISC).sized(3.0f, 0.1f).clientTrackingRange(8).updateInterval(20).noSave().noSummon());
    // Layer II
    public static final EntityType<GluttonEntity> GLUTTON = reg("glutton", monster(GluttonEntity::new, 1.3f, 1.8f));
    public static final EntityType<GluttlingEntity> GLUTTLING = reg("gluttling", monster(GluttlingEntity::new, 0.5f, 0.5f));
    // Layer III
    public static final EntityType<SullenEntity> SULLEN = reg("sullen", monster(SullenEntity::new, 0.7f, 1.9f));
    public static final EntityType<WrathbornEntity> WRATHBORN = reg("wrathborn", monster(WrathbornEntity::new, 1.0f, 2.3f));
    // Layer IV
    public static final EntityType<HereticEntity> HERETIC = reg("heretic", monster(HereticEntity::new, 0.6f, 2.0f).fireImmune());
    public static final EntityType<IronFuryEntity> IRON_FURY = reg("iron_fury", monster(IronFuryEntity::new, 1.0f, 1.2f).fireImmune());
    // Layer V
    public static final EntityType<PenitentEntity> PENITENT = reg("penitent", monster(PenitentEntity::new, 0.8f, 2.2f));
    public static final EntityType<EchoEntity> ECHO = reg("echo", monster(EchoEntity::new, 0.6f, 1.8f));
    // Bosses
    public static final EntityType<TollKeeperEntity> TOLL_KEEPER = reg("toll_keeper", monster(TollKeeperEntity::new, 2.2f, 6.4f).fireImmune().clientTrackingRange(16));
    public static final EntityType<GorgedMatriarchEntity> GORGED_MATRIARCH = reg("gorged_matriarch", monster(GorgedMatriarchEntity::new, 4.2f, 5.4f).clientTrackingRange(16));
    public static final EntityType<StyxmawEntity> STYXMAW = reg("styxmaw", monster(StyxmawEntity::new, 2.8f, 7.4f).clientTrackingRange(16));
    public static final EntityType<HeresiarchEntity> HERESIARCH = reg("heresiarch", monster(HeresiarchEntity::new, 1.8f, 5.4f).fireImmune().clientTrackingRange(16));
    public static final EntityType<VigilWakeEntity> VIGIL_WAKE = reg("vigil_wake", monster(VigilWakeEntity::new, 2.2f, 8.2f).fireImmune().clientTrackingRange(20));
    // VIGIL the stalker: tracked from far away so it can be seen standing in the distance.
    public static final EntityType<VigilEntity> VIGIL = reg("vigil", EntityType.Builder.of(VigilEntity::new, MobCategory.MISC).sized(0.7f, 3.0f).clientTrackingRange(16).fireImmune().noSummon());
    // Projectile
    public static final EntityType<HellBolt> HELL_BOLT = reg("hell_bolt", EntityType.Builder.<HellBolt>of(HellBolt::new, MobCategory.MISC).sized(0.35f, 0.35f).clientTrackingRange(8).updateInterval(2));
    public static final EntityType<BileBolus> BILE_BOLUS = reg("bile_bolus", EntityType.Builder.<BileBolus>of(BileBolus::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(2));
    public static final EntityType<RibShard> RIB_SHARD = reg("rib_shard", EntityType.Builder.<RibShard>of(RibShard::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(2));

    public static void init() {
        FabricDefaultAttributeRegistry.register(HOLLOW, HollowEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CINDER_KNIGHT, CinderKnightEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RUNAWAY, RunawayEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FOREMAN, ForemanEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LANKY, LankyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOATER, BloaterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BREAKER, BreakerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RUNT, RuntEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CRIER, CrierEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CRAWLER, CrawlerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SUNDERED, SunderedEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WALKING_OSSUARY, WalkingOssuaryEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MOURNING_HOUND, MourningHoundEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WAKE_BEARER, WakeBearerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MOURNING_PARASITE, MourningParasiteEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BREACH_TICKER, BreachTickerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(STRANGLER, StranglerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BILE_SPITTER, BileSpitterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIB_THROWER, RibThrowerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GLUTTON, GluttonEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GLUTTLING, GluttlingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SULLEN, SullenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WRATHBORN, WrathbornEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HERETIC, HereticEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(IRON_FURY, IronFuryEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PENITENT, PenitentEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ECHO, EchoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TOLL_KEEPER, TollKeeperEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GORGED_MATRIARCH, GorgedMatriarchEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(STYXMAW, StyxmawEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HERESIARCH, HeresiarchEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(VIGIL_WAKE, VigilWakeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(VIGIL, VigilEntity.createAttributes());

        for (EntityType<? extends Monster> t : java.util.List.<EntityType<? extends Monster>>of(HOLLOW, STALKER, FOREMAN, LANKY, BLOATER, BREAKER, RUNT, CINDER_KNIGHT, RUNAWAY, CRIER, CRAWLER, SUNDERED, WALKING_OSSUARY, MOURNING_HOUND, WAKE_BEARER, MOURNING_PARASITE, BREACH_TICKER, STRANGLER, BILE_SPITTER, RIB_THROWER, GLUTTON, GLUTTLING, WRATHBORN, HERETIC, PENITENT, ECHO))
            ground(t);
        // The original ground mobs never had a placement rule; in Hell's caverns that would drop them in mid-air.
        SpawnPlacements.register(ModEntities.DAMPER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(ModEntities.BIFAUCE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(SULLEN, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, HellMonster::checkHellSpawn);
        SpawnPlacements.register(IRON_FURY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, HellMonster::checkHellSpawn);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Monster> void ground(EntityType<? extends Monster> type) {
        SpawnPlacements.register((EntityType<T>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, HellMonster::checkHellSpawn);
    }

    /** Called from {@link HellItems#init()} once both registries exist. */
    static void registerSpawnEggs() {
        HellItems.egg("hollow", HOLLOW);
        HellItems.egg("cinder_knight", CINDER_KNIGHT);
        HellItems.egg("stalker", STALKER);
        HellItems.egg("foreman", FOREMAN);
        HellItems.egg("lanky", LANKY);
        HellItems.egg("bloater", BLOATER);
        HellItems.egg("breaker", BREAKER);
        HellItems.egg("runt", RUNT);
        HellItems.egg("runaway", RUNAWAY);
        HellItems.egg("crier", CRIER);
        HellItems.egg("crawler", CRAWLER);
        HellItems.egg("sundered", SUNDERED);
        HellItems.egg("walking_ossuary", WALKING_OSSUARY);
        HellItems.egg("mourning_hound", MOURNING_HOUND);
        HellItems.egg("wake_bearer", WAKE_BEARER);
        HellItems.egg("mourning_parasite", MOURNING_PARASITE);
        HellItems.egg("breach_ticker", BREACH_TICKER);
        HellItems.egg("strangler", STRANGLER);
        HellItems.egg("bile_spitter", BILE_SPITTER);
        HellItems.egg("rib_thrower", RIB_THROWER);
        HellItems.egg("glutton", GLUTTON);
        HellItems.egg("gluttling", GLUTTLING);
        HellItems.egg("sullen", SULLEN);
        HellItems.egg("wrathborn", WRATHBORN);
        HellItems.egg("heretic", HERETIC);
        HellItems.egg("iron_fury", IRON_FURY);
        HellItems.egg("penitent", PENITENT);
        HellItems.egg("echo", ECHO);
        HellItems.egg("toll_keeper", TOLL_KEEPER);
        HellItems.egg("gorged_matriarch", GORGED_MATRIARCH);
        HellItems.egg("styxmaw", STYXMAW);
        HellItems.egg("heresiarch", HERESIARCH);
        HellItems.egg("vigil_wake", VIGIL_WAKE);
    }

    /** Existing Quantumdeath mobs that now also live in Hell. */
    public static final java.util.List<EntityType<?>> RECAST = java.util.List.of(ModEntities.SWARM_EYE, ModEntities.DAMPER, ModEntities.BIFAUCE, ModEntities.FAKE_CREEPER);
}
