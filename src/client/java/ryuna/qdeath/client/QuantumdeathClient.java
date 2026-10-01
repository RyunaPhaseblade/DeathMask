package ryuna.qdeath.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ryuna.qdeath.client.gui.SkillsScreen;
import ryuna.qdeath.client.data.ClientSkillTreeData;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillPackets;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.client.rendering.PlainEndermanRenderer;
import ryuna.qdeath.client.rendering.PlainBlazeRenderer;
import ryuna.qdeath.client.rendering.SwarmArmRenderer;

public class QuantumdeathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SkillPackets.Sync.TYPE, (payload, context) -> ClientSkillTreeData.INSTANCE.update(payload.points(), payload.unlocked()));
		ClientPlayNetworking.registerGlobalReceiver(SkillPackets.OpenSkills.TYPE, (payload, context) -> context.client().setScreenAndShow(new SkillsScreen()));
		registerEntityRenderers();
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerEntityRenderers() {
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.SWARM_EYE, context -> (EntityRenderer) new PlainBlazeRenderer(context, Quantumdeath.id("textures/entity/swarm-eye.png")));
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.DAMPER, context -> (EntityRenderer) new PlainEndermanRenderer(context, Quantumdeath.id("textures/entity/damper.png")));
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BIFAUCE, context -> (EntityRenderer) new PlainEndermanRenderer(context, Quantumdeath.id("textures/entity/doublemouth.png")));
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.FAKE_CREEPER, context -> (EntityRenderer) new CreeperRenderer(context) {
			@Override public Identifier getTextureLocation(CreeperRenderState state) { return Quantumdeath.id("textures/entity/fake-creeper.png"); }
		});
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.SWARM_ARM, context -> (EntityRenderer) new SwarmArmRenderer(context));
	}
	public static void requestUnlock(SkillId skill) { ClientPlayNetworking.send(new SkillPackets.UnlockRequest(skill.name())); }
}
