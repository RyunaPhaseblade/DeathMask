package ryuna.qdeath.skills;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.YokaiNet;

import java.util.Arrays;

public final class SkillManager {
    private SkillManager() { }
    public static SkillProgress progress(ServerPlayer player) { return ((SkillProgressHolder) player).quantumdeath$getSkillProgress(); }
    /** Whether the node is in effect: learned, or (for the nine yokai nodes) granted by an operator's demonstration mode. */
    public static boolean has(ServerPlayer player, SkillId skill) {
        return progress(player).isUnlocked(skill) || !skill.legacy() && ryuna.qdeath.yokai.YokaiPlayer.of(player).demo();
    }
    public static void applyAttributes(ServerPlayer player) {
        var progress = progress(player);
        for (SkillId skill : SkillId.values()) {
            if (skill.attribute() == null || skill.operation() == null) continue;
            var instance = player.getAttribute(skill.attribute());
            if (instance == null) continue;
            instance.removeModifier(skill.modifierId());
            if (progress.isUnlocked(skill)) instance.addTransientModifier(new AttributeModifier(skill.modifierId(), skill.value(), skill.operation()));
        }
    }
    public static boolean unlock(ServerPlayer player, SkillId skill) {
        if (!progress(player).unlock(skill)) return false;
        applyAttributes(player);
        sync(player);
        player.level().playSound(null, player.blockPosition(), ModSounds.YOKAI_SKILL, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.0f);
        return true;
    }
    public static void reset(ServerPlayer player) {
        progress(player).reset();
        applyAttributes(player);
        sync(player);
    }
    public static void sync(ServerPlayer player) {
        var progress = progress(player);
        var ids = progress.unlocked().stream().sorted().map(s -> s.id).toList();
        int[] costs = Arrays.stream(SkillId.values()).mapToInt(SkillId::cost).toArray();
        ServerPlayNetworking.send(player, new SkillPackets.Sync(progress.points(), ids, costs));
        // Perception and the book's yokai pages derive from these nodes.
        YokaiNet.syncSelf(player);
    }
    public static void handleUnlock(ServerPlayer player, String id) {
        SkillId.byId(id).ifPresentOrElse(skill -> unlock(player, skill),
                () -> Quantumdeath.LOGGER.warn("Rejected invalid skill request '{}' from {}", id, player.getGameProfile().name()));
    }
}
