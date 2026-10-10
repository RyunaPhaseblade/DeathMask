package ryuna.qdeath.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ryuna.qdeath.client.data.ClientSkillTreeData;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillPackets;

public class QuantumdeathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SkillPackets.Sync.TYPE, (payload, context) -> ClientSkillTreeData.INSTANCE.update(payload.points(), payload.unlocked(), payload.costs()));
		// The Wisdom of the Void opens on the Path of the Void (cursed techniques); General Skills and the Bestiary are its other tabs.
		ClientPlayNetworking.registerGlobalReceiver(SkillPackets.OpenSkills.TYPE, (payload, context) -> ryuna.qdeath.client.jjk.JjkClient.openTree(context.client()));
		ryuna.qdeath.client.jjk.JjkClient.init();
		ryuna.qdeath.client.render.HellRenderers.register();
		// The yokai: perception-aware renderers (also for the four original creatures), HUD panels and the book's tabs.
		ryuna.qdeath.client.yokai.YokaiClient.init();
	}
	public static void requestUnlock(SkillId skill) { ClientPlayNetworking.send(new SkillPackets.UnlockRequest(skill.id)); }
}
