package ryuna.qdeath.jjk;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.jjk.JjkBalance.Energy;
import ryuna.qdeath.jjk.JjkBalance.Progression;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of the cursed-technique classes: selection, progression, unlocking, key input, per-tick upkeep and
 * synchronisation. Gameplay decisions are only ever made here (and in the abilities it calls).
 */
public final class Techniques {
    private Techniques() {}

    public static final String SWITCH_LOCK = "jjk:switch";

    public static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    public static TechniqueData data(ServerPlayer p) { return p.getAttachedOrCreate(TechniqueData.TYPE); }
    public static Runtime rt(ServerPlayer p) { return p.getAttachedOrCreate(Runtime.TYPE); }

    public static @Nullable TechniqueClass cls(ServerPlayer p) {
        TechniqueData d = p.getAttached(TechniqueData.TYPE);
        return d == null || d.classId.isEmpty() ? null : JjkClasses.get(d.classId);
    }

    /** True if {@code p} has the skill unlocked in their currently selected class. */
    public static boolean has(Player p, String skillId) {
        if (!(p instanceof ServerPlayer sp)) return false;
        TechniqueData d = sp.getAttached(TechniqueData.TYPE);
        return d != null && skillId.startsWith(d.classId + ":") && d.unlocked(skillId);
    }

    public static boolean isClass(Player p, String classId) {
        if (!(p instanceof ServerPlayer sp)) return false;
        TechniqueData d = sp.getAttached(TechniqueData.TYPE);
        return d != null && d.classId.equals(classId);
    }

    public static @Nullable Cast cast(ServerPlayer p, @Nullable SkillDef skill) {
        TechniqueClass c = cls(p);
        return c == null ? null : new Cast(p, data(p), rt(p), c, skill);
    }

    // ------------------------------------------------------------------------------------------------ class

    /** Selects a class (admin or validated request). Grants its starting techniques and fills a first-time pool. */
    public static boolean setClass(ServerPlayer p, String classId) {
        TechniqueClass c = JjkClasses.get(classId);
        if (c == null) return false;
        cleanup(p);
        TechniqueData d = data(p);
        boolean fresh = !d.classes.containsKey(classId);
        d.classId = classId;
        TechniqueData.ClassProgress prog = d.progress();
        c.starters().forEach(s -> prog.unlocked.add(s.id()));
        d.selected = "";
        ensureSelected(d, c);
        d.energy = fresh ? JjkBalance.maxEnergy(prog.level) : Math.min(d.energy, JjkBalance.maxEnergy(prog.level));
        c.hooks.fill(d, fresh ? 1f : 0.5f);
        syncProgress(p);
        syncState(p, true);
        return true;
    }

    public static void clearClass(ServerPlayer p) {
        cleanup(p);
        TechniqueData d = data(p);
        d.classId = "";
        d.selected = "";
        syncProgress(p);
        syncState(p, true);
    }

    /** A player choosing (or renouncing) a class from the book. Changing an existing class locks techniques briefly. */
    public static void requestClass(ServerPlayer p, String classId) {
        TechniqueData d = data(p);
        if (classId.equals(d.classId)) return;
        boolean had = d.hasClass();
        if (classId.isEmpty()) clearClass(p);
        else if (!setClass(p, classId)) return;
        if (had) d.cooldowns.put(SWITCH_LOCK, now(p.level().getServer()) + 200);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.5f, 1.6f);
        if (!classId.isEmpty())
            p.sendSystemMessage(Component.translatable("jjk.quantumdeath.msg.chosen", Component.translatable(JjkClasses.get(classId).nameKey())).withColor(JjkClasses.get(classId).color));
    }

    // ------------------------------------------------------------------------------------------------ progression

    public static int tpEarned(int level) {
        int tp = 0;
        for (int l = 2; l <= level; l++) {
            tp += Progression.TP_PER_LEVEL;
            if (l == Progression.TIER_2_LEVEL || l == Progression.TIER_3_LEVEL || l == Progression.TIER_4_LEVEL || l == Progression.TIER_5_LEVEL)
                tp += Progression.TP_MILESTONE_BONUS;
        }
        return tp;
    }

    private static final java.util.Map<java.util.UUID, Float> XP_FRACTION = new java.util.HashMap<>();

    /** Adds class XP. {@code capped}: subject to the per-minute anti-farming cap. */
    public static void addXp(ServerPlayer p, float amount, boolean capped) {
        TechniqueData d = data(p);
        if (!d.hasClass() || amount <= 0) return;
        if (capped) {
            Runtime rt = rt(p);
            long minute = now(p.level().getServer()) / 1200;
            if (rt.xpMinute != minute) { rt.xpMinute = minute; rt.xpThisMinute = 0; }
            amount = Math.min(amount, Progression.XP_CAP_PER_MINUTE - rt.xpThisMinute);
            if (amount <= 0) return;
        }
        float total = amount + XP_FRACTION.getOrDefault(p.getUUID(), 0f);
        int whole = (int) total;
        XP_FRACTION.put(p.getUUID(), total - whole);
        if (whole <= 0) return;
        if (capped) rt(p).xpThisMinute += whole;
        TechniqueData.ClassProgress prog = d.progress();
        if (prog.level >= Progression.MAX_LEVEL) { syncProgress(p); return; }
        prog.xp += whole;
        int gained = 0;
        while (prog.level < Progression.MAX_LEVEL && prog.xp >= JjkBalance.xpToNext(prog.level)) {
            prog.xp -= JjkBalance.xpToNext(prog.level);
            prog.level++;
            gained++;
        }
        if (gained > 0) {
            int before = tpEarned(prog.level - gained);
            prog.tp += tpEarned(prog.level) - before;
            if (prog.level >= Progression.MAX_LEVEL) prog.xp = 0;
            TechniqueClass c = JjkClasses.get(d.classId);
            p.sendSystemMessage(Component.translatable("jjk.quantumdeath.msg.level_up", Component.translatable(c.nameKey()), prog.level, prog.tp).withColor(c.color));
            p.level().playSound(null, p.blockPosition(), ryuna.qdeath.registry.ModSounds.JJK_LEVEL_UP, SoundSource.PLAYERS, 0.9f, 1.0f);
        }
        syncProgress(p);
    }

    public static void setLevel(ServerPlayer p, int level) {
        TechniqueData d = data(p);
        if (!d.hasClass()) return;
        TechniqueData.ClassProgress prog = d.progress();
        level = Math.max(1, Math.min(Progression.MAX_LEVEL, level));
        prog.tp = Math.max(0, prog.tp + tpEarned(level) - tpEarned(prog.level));
        prog.level = level;
        prog.xp = 0;
        syncProgress(p);
    }

    public enum UnlockResult { OK, NO_CLASS, UNKNOWN, ALREADY, NO_LEVEL, NO_PREREQ, NO_TP }

    public static UnlockResult unlock(ServerPlayer p, String skillId, boolean free) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        if (c == null) return UnlockResult.NO_CLASS;
        SkillDef s = c.get(skillId);
        if (s == null) return UnlockResult.UNKNOWN;
        TechniqueData.ClassProgress prog = d.progress();
        if (!free) {
            switch (JjkClasses.status(s, prog.level, prog.tp, prog.unlocked)) {
                case UNLOCKED -> { return UnlockResult.ALREADY; }
                case NO_LEVEL -> { return UnlockResult.NO_LEVEL; }
                case NO_PREREQ, LOCKED -> { return UnlockResult.NO_PREREQ; }
                case NO_TP -> { return UnlockResult.NO_TP; }
                case AVAILABLE -> prog.tp -= s.cost();
            }
        } else if (prog.unlocked.contains(skillId)) return UnlockResult.ALREADY;
        prog.unlocked.add(skillId);
        ensureSelected(d, c);
        syncProgress(p);
        return UnlockResult.OK;
    }

    /** The tree screen's unlock click. */
    public static void requestUnlock(ServerPlayer p, String skillId) {
        UnlockResult r = unlock(p, skillId, false);
        if (r == UnlockResult.OK) {
            SkillDef s = JjkClasses.skill(skillId);
            p.sendOverlayMessage(Component.translatable("jjk.quantumdeath.msg.unlocked", Component.translatable(s.key())).withColor(cls(p).color));
            p.level().playSound(null, p.blockPosition(), ryuna.qdeath.registry.ModSounds.JJK_UNLOCK, SoundSource.PLAYERS, 0.8f, 1.0f);
        } else if (r != UnlockResult.ALREADY) {
            p.sendOverlayMessage(Component.translatable("jjk.quantumdeath.unlock." + r.name().toLowerCase()).withStyle(ChatFormatting.RED));
        }
    }

    /** Takes back every bought skill and refunds all earned TP. */
    public static void resetSkills(ServerPlayer p) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        if (c == null) return;
        cleanup(p);
        TechniqueData.ClassProgress prog = d.progress();
        prog.unlocked.clear();
        c.starters().forEach(s -> prog.unlocked.add(s.id()));
        prog.tp = tpEarned(prog.level);
        d.selected = "";
        ensureSelected(d, c);
        syncProgress(p);
    }

    public static void unlockAll(ServerPlayer p) {
        TechniqueClass c = cls(p);
        if (c == null) return;
        c.skills().forEach(s -> data(p).progress().unlocked.add(s.id()));
        ensureSelected(data(p), c);
        syncProgress(p);
    }

    public static void fill(ServerPlayer p) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        if (c == null) return;
        d.energy = JjkBalance.maxEnergy(d.progress().level);
        c.hooks.fill(d, 1f);
        syncState(p, true);
    }

    // ------------------------------------------------------------------------------------------------ input

    private static List<SkillDef> unlockedActives(TechniqueData d, TechniqueClass c) {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef s : c.actives()) if (d.unlocked(s.id())) out.add(s);
        return out;
    }

    private static void ensureSelected(TechniqueData d, TechniqueClass c) {
        List<SkillDef> actives = unlockedActives(d, c);
        if (actives.isEmpty()) { d.selected = ""; return; }
        if (actives.stream().noneMatch(s -> s.id().equals(d.selected))) d.selected = actives.getFirst().id();
    }

    public static void select(ServerPlayer p, String skillId) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        if (c == null) return;
        SkillDef s = c.get(skillId);
        if (s == null || !s.active() || !d.unlocked(skillId)) return;
        releaseHeld(p);
        d.selected = skillId;
        syncProgress(p);
    }

    public static void input(ServerPlayer p, byte action) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        Runtime rt = rt(p);
        if (c == null || !p.isAlive() || p.isSpectator()) return;
        long t = now(p.level().getServer());
        if (rt.inputSecond != t / 20) { rt.inputSecond = t / 20; rt.inputs = 0; }
        if (++rt.inputs > 40 && action != JjkNet.RELEASE) return; // flood guard
        switch (action) {
            case JjkNet.NEXT, JjkNet.PREV -> {
                List<SkillDef> actives = unlockedActives(d, c);
                if (actives.isEmpty()) return;
                releaseHeld(p);
                int i = 0;
                for (int k = 0; k < actives.size(); k++) if (actives.get(k).id().equals(d.selected)) i = k;
                i = Math.floorMod(i + (action == JjkNet.NEXT ? 1 : -1), actives.size());
                d.selected = actives.get(i).id();
                syncProgress(p);
            }
            case JjkNet.PRESS -> {
                if (rt.holding) return;
                ensureSelected(d, c);
                SkillDef s = c.get(d.selected);
                if (s == null) return;
                Cast cast = new Cast(p, d, rt, c, s);
                if (cast.cooldownLeft(SWITCH_LOCK) > 0) { cast.fail("jjk.quantumdeath.fail.switching", cast.cooldownLeft(SWITCH_LOCK) / 20 + 1); return; }
                if (!cast.ready() && !s.ability().ignoresCooldown(cast)) {
                    cast.fail("jjk.quantumdeath.fail.cooldown", Component.translatable(s.key()), String.format("%.1f", cast.cooldownLeft(s.id()) / 20f));
                    return;
                }
                rt.holding = true;
                rt.heldTicks = 0;
                rt.heldSkill = s.id();
                s.ability().press(cast);
            }
            case JjkNet.RELEASE -> releaseHeld(p);
            case JjkNet.SECONDARY -> {
                ensureSelected(d, c);
                SkillDef s = c.get(d.selected);
                if (s != null) s.ability().secondary(new Cast(p, d, rt, c, s));
            }
            default -> {}
        }
    }

    /** Ends a held key: the ability's release runs exactly once. */
    public static void releaseHeld(ServerPlayer p) {
        Runtime rt = rt(p);
        if (!rt.holding) return;
        rt.holding = false;
        TechniqueClass c = cls(p);
        SkillDef s = c == null ? null : c.get(rt.heldSkill);
        if (s != null && s.ability() != null) s.ability().release(new Cast(p, data(p), rt, c, s), rt.heldTicks);
        rt.charge = 0;
    }

    // ------------------------------------------------------------------------------------------------ tick + sync

    private static void tickPlayer(ServerPlayer p) {
        TechniqueClass c = cls(p);
        if (c == null) return;
        TechniqueData d = data(p);
        Runtime rt = rt(p);
        Cast hook = new Cast(p, d, rt, c, null);
        if (p.isAlive()) {
            int level = d.progress().level;
            float max = JjkBalance.maxEnergy(level);
            boolean combat = hook.now() - rt.lastCombat < Energy.COMBAT_WINDOW;
            float regen = (Energy.REGEN + Energy.REGEN_PER_LEVEL * level) * (combat ? Energy.COMBAT_REGEN_MULT : 1f);
            d.energy = Math.min(max, d.energy + regen);
            if (rt.holding) {
                SkillDef s = c.get(rt.heldSkill);
                rt.heldTicks++;
                if (s == null || s.ability() == null || rt.heldTicks > 400) releaseHeld(p);
                else s.ability().hold(new Cast(p, d, rt, c, s), rt.heldTicks);
            }
            c.hooks.tick(hook);
        }
        syncState(p, false);
    }

    public static void syncProgress(ServerPlayer p) {
        TechniqueData d = data(p);
        if (!d.hasClass()) {
            ServerPlayNetworking.send(p, new JjkNet.Progress("", 0, 0, 0, 0, List.of(), ""));
            return;
        }
        TechniqueData.ClassProgress prog = d.progress();
        ServerPlayNetworking.send(p, new JjkNet.Progress(d.classId, prog.level, prog.xp, JjkBalance.xpToNext(prog.level), prog.tp,
                List.copyOf(prog.unlocked), d.selected));
    }

    public static void syncState(ServerPlayer p, boolean force) {
        Runtime rt = rt(p);
        TechniqueClass c = cls(p);
        if (c == null) {
            if (force || rt.lastSent != null) { ServerPlayNetworking.send(p, new JjkNet.State(0, 0, 0, 0, 0, List.of(), List.of())); rt.lastSent = null; }
            return;
        }
        if (!force && (p.tickCount & 1) != 0) return;
        TechniqueData d = data(p);
        Cast hook = new Cast(p, d, rt, c, null);
        List<JjkNet.Cooldown> cds = new ArrayList<>();
        for (SkillDef s : c.actives()) {
            if (!d.unlocked(s.id())) continue;
            int left = hook.cooldownLeft(s.id());
            if (left > 0) cds.add(new JjkNet.Cooldown(s.id(), left, Math.max(left, rt.cooldownTotals.getOrDefault(s.id(), s.cooldown().getAsInt()))));
        }
        int lock = hook.cooldownLeft(SWITCH_LOCK);
        if (lock > 0) cds.add(new JjkNet.Cooldown(SWITCH_LOCK, lock, 200));
        List<JjkNet.Indicator> ind = new ArrayList<>();
        c.hooks.indicators(hook, ind);
        JjkNet.State s = new JjkNet.State(Math.round(d.energy * 2) / 2f, hook.maxEnergy(), Math.round(d.resource * 2) / 2f, c.hooks.resourceMax(d),
                Math.round(rt.charge * 50) / 50f, cds, ind);
        if (force || !s.equals(rt.lastSent) || --rt.forceSync <= 0) {
            ServerPlayNetworking.send(p, s);
            rt.lastSent = s;
            rt.forceSync = 40;
        }
    }

    /** Releases held keys and removes everything the player's class has left in the world. */
    public static void cleanup(ServerPlayer p) {
        releaseHeld(p);
        TechniqueClass c = cls(p);
        if (c != null) c.hooks.cleanup(p);
        Ongoing.cancel(p.getUUID());
        rt(p).clearClassState();
        rt(p).charge = 0;
    }

    // ------------------------------------------------------------------------------------------------ events

    /** Valid sources of class XP: hostile creatures, other players, and anything that is fighting you. */
    public static boolean xpTarget(ServerPlayer p, LivingEntity e) {
        if (e == p || e instanceof ArmorStand || e.getMaxHealth() < 4) return false;
        if (e instanceof Player other) return !other.isCreative() && !other.isSpectator();
        return e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == p);
    }

    public static void register() {
        JjkNet.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!RESPAWNED.isEmpty()) {
                RESPAWNED.forEach((id, alive) -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) respawned(p, alive);
                });
                RESPAWNED.clear();
            }
            Ongoing.tick(server);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.player;
            TechniqueData d = data(p);
            TechniqueClass c = cls(p);
            if (c != null) {
                c.starters().forEach(s -> d.progress().unlocked.add(s.id()));
                ensureSelected(d, c);
            }
            // Cooldowns saved from another world/clock can never exceed their own length.
            long t = now(server);
            d.cooldowns.entrySet().removeIf(e -> e.getValue() <= t || e.getValue() - t > 20L * 60 * 30);
            syncProgress(p);
            syncState(p, true);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cleanup(handler.player);
            XP_FRACTION.remove(handler.player.getUUID());
        });
        // Fabric moves attachments to the new player in its own AFTER_RESPAWN listener, in no fixed order with this
        // one: never touch the new player's data here, settle it on the next tick instead.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> RESPAWNED.put(newPlayer.getUUID(), alive));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            cleanup(player);
            syncState(player, true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { Ongoing.clear(); XP_FRACTION.clear(); RESPAWNED.clear(); });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
            long t = now(level.getServer());
            if (entity instanceof ServerPlayer victim) rt(victim).lastCombat = t;
            if (source.getEntity() instanceof ServerPlayer p && p != entity && taken > 0) {
                rt(p).lastCombat = t;
                if (cls(p) == null) return;
                boolean technique = JjkCombat.isTechnique(source);
                if (xpTarget(p, entity))
                    addXp(p, taken * (technique ? Progression.XP_PER_TECHNIQUE_DAMAGE : Progression.XP_PER_MELEE_DAMAGE), true);
                JjkCombat.afterHit(p, entity, source, taken, technique);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer dead) cleanup(dead);
            if (source.getEntity() instanceof ServerPlayer p && p != entity && cls(p) != null && xpTarget(p, entity))
                addXp(p, entity.getMaxHealth() * Progression.XP_KILL_FRACTION, true);
        });
        Quantumdeath.LOGGER.info("Cursed techniques: {} classes", JjkClasses.all().size());
    }

    private static final java.util.Map<java.util.UUID, Boolean> RESPAWNED = new java.util.HashMap<>();

    /** After a respawn (the data has been carried over by then): pools refill to a fraction after a death. */
    private static void respawned(ServerPlayer p, boolean alive) {
        TechniqueData d = data(p);
        TechniqueClass c = cls(p);
        if (c != null && !alive) {
            d.energy = JjkBalance.maxEnergy(d.progress().level) * Energy.RESPAWN_FILL;
            c.hooks.fill(d, Energy.RESPAWN_FILL);
        }
        syncProgress(p);
        syncState(p, true);
    }

    /** Lets the damage hooks know who is fighting whom. */
    public static void noteCombat(ServerPlayer p) { rt(p).lastCombat = now(p.level().getServer()); }
}
