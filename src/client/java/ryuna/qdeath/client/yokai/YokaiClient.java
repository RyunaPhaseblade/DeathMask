package ryuna.qdeath.client.yokai;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.client.model.HellModelLayers;
import ryuna.qdeath.client.render.HellModel;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.entity.yokai.AbstractYokai;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiNet;

/** Client side of the yokai: renderers, the perception presentation, packets, HUD panels and the book's screens. */
public final class YokaiClient {
    private YokaiClient() {}

    private static final DustParticleOptions SHADE = new DustParticleOptions(0x07060a, 2.2f);
    private static final DustParticleOptions SHADE_LIGHT = new DustParticleOptions(0x1a1622, 1.4f);

    public static void init() {
        AbstractYokai.CLIENT_PICKABLE = e -> ClientYokai.view(e) >= 2;
        ClientPlayNetworking.registerGlobalReceiver(YokaiNet.Self.TYPE, (p, ctx) -> ClientYokai.self(p));
        ClientPlayNetworking.registerGlobalReceiver(YokaiNet.Companions.TYPE, (p, ctx) -> ClientYokai.companions = p.list());
        ClientPlayNetworking.registerGlobalReceiver(YokaiNet.Tracking.TYPE, (p, ctx) -> {
            ClientYokai.tracking = p;
            ClientYokai.trackingAt = ClientYokai.clientTicks;
        });
        ClientPlayNetworking.registerGlobalReceiver(YokaiNet.OpenCompanion.TYPE, (p, ctx) -> {
            // A refresh after an order never reopens a screen the player closed.
            if (p.refresh() && !(ctx.client().gui.screen() instanceof CompanionScreen open && open.entity() == p.entity())) return;
            ctx.client().setScreenAndShow(new CompanionScreen(p));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientYokai.reset());

        reg(ModEntities.FAKE_CREEPER, HellModelLayers.FAKE_CREEPER, YokaiAnims.FAKE_CREEPER, "fake_creeper", 0.3f, true);
        reg(ModEntities.SWARM_EYE, HellModelLayers.SWARM_EYE, YokaiAnims.SWARM_EYE, "swarm_eye", 0.3f, false);
        reg(ModEntities.DAMPER, HellModelLayers.DAMPER, YokaiAnims.DAMPER, "damper_hell", 0.5f, false);
        reg(ModEntities.BIFAUCE, HellModelLayers.BIFAUCE, YokaiAnims.BIFAUCE, "bifauce", 0.6f, false);
        reg(YokaiContent.KAPPA, HellModelLayers.KAPPA, YokaiAnims.KAPPA, "kappa", 0.45f, false);
        reg(YokaiContent.ONI, HellModelLayers.ONI, YokaiAnims.ONI, "oni", 0.8f, false);
        reg(YokaiContent.CHEST, HellModelLayers.CHEST_TSUKUMOGAMI, YokaiAnims.CHEST, "chest_tsukumogami", 0.5f, false);
        reg(YokaiContent.TANUKI, HellModelLayers.TANUKI, YokaiAnims.TANUKI, "tanuki", 0.35f, false);
        reg(YokaiContent.TENGU, HellModelLayers.TENGU, YokaiAnims.TENGU, "tengu", 0.45f, false);
        reg(YokaiContent.KODAMA, HellModelLayers.KODAMA, YokaiAnims.KODAMA, "kodama", 0.25f, false);
        reg(YokaiContent.NURIKABE, HellModelLayers.NURIKABE, YokaiAnims.NURIKABE, "nurikabe", 1.2f, false);
        reg(YokaiContent.ITTAN_MOMEN, HellModelLayers.ITTAN_MOMEN, YokaiAnims.ITTAN_MOMEN, "ittan_momen", 0.4f, false);
        reg(YokaiContent.BELL, HellModelLayers.BELL_TSUKUMOGAMI, YokaiAnims.BELL, "bell_tsukumogami", 0.4f, false);
        EntityRendererRegistry.register(YokaiContent.TANUKI_DECOY, ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        EntityRendererRegistry.register(ModEntities.SWARM_ARM, SwarmEyeShotRenderer::new);

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Quantumdeath.id("yokai"), YokaiHud::extract);
        ClientTickEvents.END_CLIENT_TICK.register(YokaiClient::tick);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reg(EntityType<?> type, ModelLayerLocation layer, HellModel.Anim anim, String texture, float shadow, boolean translucent) {
        EntityRendererRegistry.register((EntityType) type, ctx -> (EntityRenderer) new YokaiRenderer<Mob>(ctx, layer, anim, texture, 1f, shadow, translucent));
    }

    /** Partial perception: dark motes gather where something stands, never its shape. */
    private static void tick(Minecraft mc) {
        ClientYokai.clientTicks++;
        if (mc.level == null || mc.player == null || mc.isPaused() || ClientYokai.clientTicks % 2 != 0) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof YokaiCreature) || e.distanceToSqr(mc.player) > 40 * 40 || ClientYokai.view(e) != 1) continue;
            var r = e.getRandom();
            double w = e.getBbWidth() * 0.6, h = e.getBbHeight();
            mc.level.addParticle(r.nextBoolean() ? SHADE : SHADE_LIGHT, e.getX() + (r.nextDouble() - 0.5) * w * 2, e.getY() + r.nextDouble() * h,
                    e.getZ() + (r.nextDouble() - 0.5) * w * 2, 0, 0.01, 0);
            if (r.nextInt(6) == 0) mc.level.addParticle(ParticleTypes.SMOKE, e.getX(), e.getY() + h * 0.5, e.getZ(), 0, 0.02, 0);
        }
    }
}
