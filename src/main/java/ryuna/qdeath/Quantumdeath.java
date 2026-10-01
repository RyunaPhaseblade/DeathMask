package ryuna.qdeath;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ryuna.qdeath.moditems.modItems;
import ryuna.qdeath.entity.ModEntities;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.skills.SkillPackets;
import ryuna.qdeath.skills.SkillProgressHolder;
import ryuna.qdeath.intro.InitialNotes;
import ryuna.qdeath.intro.PlayerIntroHolder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

public class Quantumdeath implements ModInitializer {
	public static final String MOD_ID = "quantumdeath";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		modItems.initialize();
		ModEntities.initialize();
		PayloadTypeRegistry.serverboundPlay().register(SkillPackets.UnlockRequest.TYPE, SkillPackets.UnlockRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkillPackets.Sync.TYPE, SkillPackets.Sync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkillPackets.OpenSkills.TYPE, SkillPackets.OpenSkills.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkillPackets.UnlockRequest.TYPE, (payload, context) ->
				SkillManager.handleUnlock(context.player(), payload.skill()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			giveInitialNotes(handler.player);
			SkillManager.applyAttributes(handler.player);
			SkillManager.sync(handler.player);
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			((SkillProgressHolder) newPlayer).quantumdeath$getSkillProgress().load(
					((SkillProgressHolder) oldPlayer).quantumdeath$getSkillProgress().save());
			((PlayerIntroHolder) newPlayer).quantumdeath$setReceivedNotes(
					((PlayerIntroHolder) oldPlayer).quantumdeath$hasReceivedNotes());
			SkillManager.applyAttributes(newPlayer);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			var progress = SkillManager.progress(player);
			// The tracker is maintained by the player mixin; see updateLevelTracking.
			if (((ryuna.qdeath.skills.SkillLevelTracker) player).quantumdeath$updateLevelTracking(player.experienceLevel)) SkillManager.sync(player);
		}));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("quantumdeath").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("skills").then(Commands.literal("reset")
								.then(Commands.argument("targets", EntityArgument.players()).executes(context -> {
									var players = EntityArgument.getPlayers(context, "targets");
									players.forEach(SkillManager::reset);
									context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("command.quantumdeath.skills.reset", players.size()), true);
									return players.size();
								}))))));
	}

	private static void giveInitialNotes(net.minecraft.server.level.ServerPlayer player) {
		PlayerIntroHolder intro = (PlayerIntroHolder) player;
		if (intro.quantumdeath$hasReceivedNotes()) return;
		intro.quantumdeath$setReceivedNotes(true);
		var notes = InitialNotes.create();
		if (!player.addItem(notes)) player.drop(notes, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
