package ryuna.qdeath.jjk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Freezing buildup, reusable by any ice technique: repeated cold builds Freeze I, II, III (slower, more vulnerable,
 * with the vanilla frost overlay and growing frost particles) and finally Immobilized. After an immobilization the
 * target keeps some buildup and gains it at half rate for a while, so nothing can be frozen solid back to back.
 * Only entities that were actually chilled are tracked; they are dropped once thawed.
 */
public final class Freeze {
    private Freeze() {}

    private static final class State {
        float buildup;
        long lastHit, resistUntil;
        @Nullable ServerPlayer source;
    }

    private static final Map<LivingEntity, State> ACTIVE = new HashMap<>();

    public static void add(LivingEntity target, float amount, @Nullable ServerPlayer source) {
        if (amount <= 0 || !target.isAlive() || !(target.level() instanceof ServerLevel level)) return;
        if (target instanceof ServerPlayer p && Techniques.isClass(p, "uraume")) return; // cursed ice obeys its maker
        if (target.is(net.minecraft.tags.EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) amount *= 0.35f;
        if (source != null && Techniques.has(source, "uraume:ice_mastery")) amount *= JjkBalance.Uraume.MASTERY_FREEZE_MULT;
        long now = Techniques.now(level.getServer());
        State s = ACTIVE.computeIfAbsent(target, k -> new State());
        if (now < s.resistUntil) amount *= 0.5f;
        s.lastHit = now;
        if (source != null) s.source = source;
        if (target.hasEffect(JjkContent.IMMOBILIZED)) return;
        int before = stage(s.buildup);
        s.buildup = Math.min(JjkBalance.Freeze.IMMOBILIZE, s.buildup + amount);
        if (s.buildup >= JjkBalance.Freeze.IMMOBILIZE) immobilize(level, target, s, now);
        else if (stage(s.buildup) > before) {
            level.playSound(null, target.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.0f, 0.7f + 0.2f * stage(s.buildup));
            Fx.burst(level, ParticleTypes.SNOWFLAKE, target.getBoundingBox().getCenter(), 6 + 4 * stage(s.buildup), 0.12);
        }
    }

    private static void immobilize(ServerLevel level, LivingEntity target, State s, long now) {
        int ticks = JjkBalance.Freeze.IMMOBILIZE_TICKS;
        if (s.source != null && Techniques.has(s.source, "uraume:ice_mastery")) ticks += JjkBalance.Freeze.MASTERY_EXTRA_TICKS;
        target.addEffect(new MobEffectInstance(JjkContent.IMMOBILIZED, ticks, 0, false, true, true));
        s.buildup = JjkBalance.Freeze.AFTER_IMMOBILIZE;
        s.resistUntil = now + ticks + JjkBalance.Freeze.RESIST_TICKS;
        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), c.x, c.y, c.z, 30,
                target.getBbWidth() * 0.5, target.getBbHeight() * 0.5, target.getBbWidth() * 0.5, 0.1);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, target.blockPosition(), ryuna.qdeath.registry.ModSounds.JJK_ICE_FORM, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (s.source != null && Techniques.xpTarget(s.source, target)) Techniques.addXp(s.source, JjkBalance.Progression.XP_SKILL_EVENT * 2, true);
        if (target instanceof ServerPlayer p) p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jjk.quantumdeath.msg.frozen_solid").withColor(0xbfe8ff));
    }

    private static int stage(float b) {
        if (b >= JjkBalance.Freeze.STAGE_3) return 3;
        if (b >= JjkBalance.Freeze.STAGE_2) return 2;
        return b >= JjkBalance.Freeze.STAGE_1 ? 1 : 0;
    }

    /** 0 none, 1..3 Freeze I..III, 4 immobilized. */
    public static int stage(LivingEntity e) {
        if (e.hasEffect(JjkContent.IMMOBILIZED) && ACTIVE.containsKey(e)) return 4;
        State s = ACTIVE.get(e);
        return s == null ? 0 : stage(s.buildup);
    }

    public static float buildup(LivingEntity e) {
        State s = ACTIVE.get(e);
        return s == null ? 0 : s.buildup;
    }

    public static float vulnerability(LivingEntity e) {
        return switch (stage(e)) {
            case 1 -> JjkBalance.Freeze.VULN_1;
            case 2 -> JjkBalance.Freeze.VULN_2;
            case 3 -> JjkBalance.Freeze.VULN_3;
            case 4 -> JjkBalance.Freeze.VULN_IMMOBILIZED;
            default -> 0f;
        };
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = Techniques.now(server);
        for (Iterator<Map.Entry<LivingEntity, State>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            LivingEntity e = entry.getKey();
            State s = entry.getValue();
            if (e.isRemoved() || !e.isAlive()) { it.remove(); continue; }
            boolean immobile = e.hasEffect(JjkContent.IMMOBILIZED);
            if (!immobile && now - s.lastHit > JjkBalance.Freeze.DECAY_DELAY) s.buildup -= JjkBalance.Freeze.DECAY;
            if (s.buildup <= 0 && !immobile) {
                it.remove();
                e.setTicksFrozen(0);
                continue;
            }
            int st = stage(s.buildup);
            // The vanilla frost look (and the player's frost vignette), always short of real freeze damage.
            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() - 1, (int) (s.buildup / 100f * e.getTicksRequiredToFreeze())));
            if (st > 0 && e.tickCount % 10 == 0)
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, st == 3 ? 3 : st - 1, true, false, true));
            if (e.tickCount % 5 == 0 && (st > 0 || immobile) && e.level() instanceof ServerLevel level) {
                Vec3 c = e.getBoundingBox().getCenter();
                if (immobile) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), c.x, c.y, c.z, 4,
                        e.getBbWidth() * 0.45, e.getBbHeight() * 0.45, e.getBbWidth() * 0.45, 0);
                level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + e.getBbHeight() * 0.3, c.z, st + (immobile ? 3 : 0), e.getBbWidth() * 0.4, e.getBbHeight() * 0.4, e.getBbWidth() * 0.4, 0.01);
            }
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Freeze::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> ACTIVE.clear());
    }
}
