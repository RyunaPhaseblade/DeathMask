package ryuna.qdeath.client.render;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.particle.EmissiveRisingParticle;
import net.minecraft.client.particle.GlowParticle;
import net.minecraft.client.particle.SuspendedTownParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import ryuna.qdeath.client.model.DeadModelLayers;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModParticles;

/** Client registration for Hell: model layers, renderers and particle providers. */
public final class HellRenderers {
    private HellRenderers() {}

    public static void register() {
        HellModelLayers.register();
        // 3D details of the armor sets, drawn over the vanilla armor layers of every humanoid (tools/gen_items.py).
        ArmorDetailModels.register();
        ArmorDetailLayer.register();
        // The human dead of the Limbo: player-proportioned bodies, one skin each (DeadModelLayers, tools/gen_zombies.py).
        DeadModelLayers.register();
        dead(HellEntities.HOLLOW, DeadModelLayers.HUMAN, DeadModelLayers.HUMAN_SLIM, DeadAnims.HOLLOW, "hollow", 1f, 0.45f);
        dead(HellEntities.STALKER, DeadModelLayers.STALKER, null, DeadAnims.STALKER, "stalker", 1f, 0.45f);
        dead(HellEntities.CRIER, DeadModelLayers.CRIER, null, DeadAnims.CRIER, "crier", 1f, 0.45f);
        dead(HellEntities.FOREMAN, DeadModelLayers.FOREMAN, null, DeadAnims.FOREMAN, "foreman", 1f, 0.55f);
        dead(HellEntities.LANKY, DeadModelLayers.LANKY, null, DeadAnims.LANKY, "lanky", 1f, 0.5f);
        dead(HellEntities.BLOATER, DeadModelLayers.BLOATER, null, DeadAnims.BLOATER, "bloater", 1f, 0.6f);
        dead(HellEntities.BREAKER, DeadModelLayers.BRUTE, null, DeadAnims.BRUTE, "breaker", 1f, 0.8f);
        dead(HellEntities.RUNT, DeadModelLayers.HUMAN, null, DeadAnims.RUNT, "runt", 0.62f, 0.35f);
        dead(HellEntities.RUNAWAY, DeadModelLayers.RUNAWAY, null, DeadAnims.RUNAWAY, "runaway", 1f, 0.45f);
        dead(HellEntities.CRAWLER, DeadModelLayers.HUMAN, null, DeadAnims.CRAWLER, "crawler", 1f, 0.55f);
        dead(HellEntities.SUNDERED, DeadModelLayers.SUNDERED, null, DeadAnims.SUNDERED, "sundered", 1f, 0.5f);
        // The expansion: the tongue, the two throwers, the parasite and the Ticker, and what they throw or leave.
        EntityRendererRegistry.register(HellEntities.STRANGLER, StranglerRenderer::new);
        dead(HellEntities.BILE_SPITTER, DeadModelLayers.SPITTER, null, DeadAnims.SPITTER, "bile_spitter", 1f, 0.55f);
        dead(HellEntities.RIB_THROWER, DeadModelLayers.RIBS, null, DeadAnims.RIBS, "rib_thrower", 1f, 0.45f);
        mob(HellEntities.MOURNING_PARASITE, HellModelLayers.MOURNING_PARASITE, LimboAnims.PARASITE, "mourning_parasite", 1f, 0.3f, false);
        mob(HellEntities.BREACH_TICKER, HellModelLayers.BREACH_TICKER, LimboAnims.TICKER, "breach_ticker", 1f, 0.45f, false);
        EntityRendererRegistry.register(HellEntities.TONGUE_SEGMENT, net.minecraft.client.renderer.entity.NoopRenderer::new);
        EntityRendererRegistry.register(HellEntities.BILE_BOLUS, ctx -> new BodyProjectileRenderer<>(ctx, HellModelLayers.BILE_BOLUS, "bile_bolus", false));
        EntityRendererRegistry.register(HellEntities.RIB_SHARD, ctx -> new BodyProjectileRenderer<>(ctx, HellModelLayers.RIB_SHARD, "rib_shard", true));
        EntityRendererRegistry.register(HellEntities.BILE_RESIDUE, BileResidueRenderer::new);
        EntityRendererRegistry.register(HellEntities.BLOATER_CORPSE, BloaterCorpseRenderer::new);
        mob(HellEntities.WALKING_OSSUARY, HellModelLayers.WALKING_OSSUARY, LimboAnims.OSSUARY, "walking_ossuary", 1f, 1.0f, false);
        mob(HellEntities.MOURNING_HOUND, HellModelLayers.MOURNING_HOUND, LimboAnims.HOUND, "mourning_hound", 1f, 0.55f, false);
        mob(HellEntities.WAKE_BEARER, HellModelLayers.WAKE_BEARER, LimboAnims.BEARER, "wake_bearer", 1.15f, 0.7f, false);
        mob(HellEntities.CINDER_KNIGHT, HellModelLayers.CINDER_KNIGHT, HellAnims.CINDER_KNIGHT, "cinder_knight", 1f, 0.6f, false);
        mob(HellEntities.GLUTTON, HellModelLayers.GLUTTON, HellAnims.GLUTTON, "glutton", 1f, 0.9f, false);
        mob(HellEntities.GLUTTLING, HellModelLayers.GLUTTLING, HellAnims.GLUTTLING, "gluttling", 1f, 0.3f, false);
        mob(HellEntities.SULLEN, HellModelLayers.SULLEN, HellAnims.SULLEN, "sullen", 1f, 0.5f, false);
        mob(HellEntities.WRATHBORN, HellModelLayers.WRATHBORN, HellAnims.WRATHBORN, "wrathborn", 1f, 0.7f, false);
        mob(HellEntities.HERETIC, HellModelLayers.HERETIC, HellAnims.HERETIC, "heretic", 1f, 0.5f, false);
        mob(HellEntities.IRON_FURY, HellModelLayers.IRON_FURY, HellAnims.IRON_FURY, "iron_fury", 1f, 0.5f, false);
        mob(HellEntities.PENITENT, HellModelLayers.PENITENT, HellAnims.PENITENT, "penitent", 1f, 0.5f, false);
        mob(HellEntities.ECHO, HellModelLayers.ECHO, HellAnims.ECHO, "echo", 1f, 0.4f, true);
        mob(HellEntities.TOLL_KEEPER, HellModelLayers.TOLL_KEEPER, HellAnims.TOLL_KEEPER, "toll_keeper", 2.6f, 0.6f, false);
        mob(HellEntities.GORGED_MATRIARCH, HellModelLayers.GORGED_MATRIARCH, HellAnims.MATRIARCH, "gorged_matriarch", 2.2f, 1.0f, false);
        mob(HellEntities.STYXMAW, HellModelLayers.STYXMAW, HellAnims.STYXMAW, "styxmaw", 2.1f, 0f, false);
        mob(HellEntities.HERESIARCH, HellModelLayers.HERESIARCH, HellAnims.HERESIARCH, "heresiarch", HellAnims.HERESIARCH_SCALE, 0.4f, false);
        mob(HellEntities.VIGIL, HellModelLayers.VIGIL, HellAnims.VIGIL, "vigil_1", 1f, 0.4f, true);
        mob(HellEntities.VIGIL_WAKE, HellModelLayers.VIGIL, HellAnims.VIGIL, "vigil_5", 2.8f, 0.5f, true);
        // The original Quantumdeath creatures (Swarm Eye, Damper, Doublemouth, False Creeper) are yokai now: see YokaiClient.
        EntityRendererRegistry.register(HellEntities.HELL_BOLT, ctx -> new ThrownItemRenderer<>(ctx, 1.25f, true));

        ParticleProviderRegistry particles = ParticleProviderRegistry.getInstance();
        particles.register(ModParticles.ASH_FLAKE, WhiteAshParticle.Provider::new);
        particles.register(ModParticles.TAR_DRIP, WhiteAshParticle.Provider::new);
        particles.register(ModParticles.BILE_DRIP, WhiteAshParticle.Provider::new);
        particles.register(ModParticles.SOUL_MOTE, EmissiveRisingParticle.EmissiveProvider::new);
        particles.register(ModParticles.BLOOD_EMBER, EmissiveRisingParticle.EmissiveProvider::new);
        particles.register(ModParticles.GATE_EMBER, EmissiveRisingParticle.EmissiveProvider::new);
        particles.register(ModParticles.STATIC, GlowParticle.ElectricSparkProvider::new);
        particles.register(ModParticles.VIGIL_MOTE, SuspendedTownParticle.Provider::new);
        particles.register(ModParticles.BREATH, WhiteAshParticle.Provider::new);
        particles.register(ModParticles.VOTIVE_SPARK, EmissiveRisingParticle.EmissiveProvider::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dead(EntityType<?> type, ModelLayerLocation layer, ModelLayerLocation slim, HellModel.Anim anim, String id, float scale, float shadow) {
        EntityRendererRegistry.register((EntityType) type, ctx -> (EntityRenderer) new DeadRenderer(ctx, layer, slim, anim, id, scale, shadow));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void mob(EntityType<?> type, ModelLayerLocation layer, HellModel.Anim anim, String texture, float scale, float shadow, boolean translucent) {
        EntityRendererRegistry.register((EntityType) type, ctx -> (EntityRenderer) new HellMobRenderer<Mob>(ctx, layer, anim, texture, scale, shadow, translucent));
    }
}
