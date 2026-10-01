package ryuna.qdeath.skills;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import ryuna.qdeath.Quantumdeath;

import java.util.EnumSet;

public final class SkillManager {
    private SkillManager() { }
    public static SkillProgress progress(ServerPlayer player) { return ((SkillProgressHolder) player).quantumdeath$getSkillProgress(); }
    public static void applyAttributes(ServerPlayer player) {
        var progress = progress(player);
        for (SkillId skill : SkillId.values()) {
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
        return true;
    }
    public static void reset(ServerPlayer player) {
        progress(player).reset();
        applyAttributes(player);
        sync(player);
    }
    public static void sync(ServerPlayer player) {
        var progress = progress(player);
        var unlocked = EnumSet.noneOf(SkillId.class);
        unlocked.addAll(progress.unlocked());
        ServerPlayNetworking.send(player, new SkillPackets.Sync(progress.points(), unlocked));
    }
    public static void handleUnlock(ServerPlayer player, String id) {
        try { unlock(player, SkillId.valueOf(id)); }
        catch (IllegalArgumentException exception) { Quantumdeath.LOGGER.warn("Rejected invalid skill request '{}' from {}", id, player.getGameProfile().name()); }
    }
}
