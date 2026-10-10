package ryuna.qdeath.jjk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Everything an ability or class hook needs about its caster, plus the shared cost / cooldown / feedback rules. */
public final class Cast {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final TechniqueData data;
    public final Runtime rt;
    public final TechniqueClass cls;
    /** The skill being used; null inside class hooks. */
    public final @Nullable SkillDef skill;

    public Cast(ServerPlayer player, TechniqueData data, Runtime rt, TechniqueClass cls, @Nullable SkillDef skill) {
        this.player = player;
        this.level = (ServerLevel) player.level();
        this.data = data;
        this.rt = rt;
        this.cls = cls;
        this.skill = skill;
    }

    public long now() { return Techniques.now(level.getServer()); }
    public boolean has(String skillId) { return data.unlocked(skillId); }
    public <T> T state(Class<T> type, Supplier<T> init) { return rt.state(type, init); }

    public int cooldownLeft(String id) {
        Long ready = data.cooldowns.get(id);
        return ready == null ? 0 : (int) Math.max(0, ready - now());
    }
    public boolean ready() { return skill == null || cooldownLeft(skill.id()) == 0; }
    public void cooldown(int ticks) { if (skill != null) cooldown(skill.id(), ticks); }
    public void cooldown(String id, int ticks) {
        if (ticks <= 0) { data.cooldowns.remove(id); return; }
        data.cooldowns.put(id, now() + ticks);
        rt.cooldownTotals.put(id, ticks);
    }

    public float maxEnergy() { return JjkBalance.maxEnergy(data.progress().level); }

    /** Pays cursed energy, or explains why not. */
    public boolean spendEnergy(float amount) {
        if (player.isCreative()) return true;
        if (data.energy + 1e-3f < amount) {
            fail("jjk.quantumdeath.fail.energy", Math.round(amount), Math.round(data.energy));
            return false;
        }
        data.energy -= amount;
        return true;
    }

    public boolean hasEnergy(float amount) { return player.isCreative() || data.energy + 1e-3f >= amount; }

    /** Red action-bar feedback with a dull click. */
    public void fail(String key, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
        level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    public void info(String key, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args).withColor(cls.color));
    }
}
