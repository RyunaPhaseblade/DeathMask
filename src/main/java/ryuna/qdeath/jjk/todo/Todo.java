package ryuna.qdeath.jjk.todo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.Ability;
import ryuna.qdeath.jjk.Cast;
import ryuna.qdeath.jjk.ClassHooks;
import ryuna.qdeath.jjk.Fx;
import ryuna.qdeath.jjk.JjkBalance;
import ryuna.qdeath.jjk.JjkCombat;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.TechniqueClass;
import ryuna.qdeath.jjk.Techniques;
import ryuna.qdeath.jjk.entity.CursedObject;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static ryuna.qdeath.jjk.JjkBalance.Todo.*;

/**
 * Aoi Todo, Boogie Woogie: a clap swaps the positions of two targets with cursed energy. Simple to start, strength from
 * positioning. Every swap is validated twice (when aimed and again when it resolves) and only ever moves entities to a
 * collision-free, loaded, in-border spot in the same dimension; anything else cancels the swap.
 * Controls: R claps (Self Swap / Entity Swap per the selected technique), sneak + R marks the first Entity Swap target,
 * the secondary key is a Fake Clap.
 */
public final class Todo {
    private Todo() {}

    public static final String ID = "todo";
    public static final String BOOGIE = "todo:boogie_woogie", SELF = "todo:self_swap", ENTITY = "todo:entity_swap", DOUBLE = "todo:double_swap",
            OBJECT = "todo:cursed_object_swap", PROJECTILE = "todo:projectile_swap", FAKE = "todo:fake_clap", RAPID = "todo:rapid_swaps",
            MASTERY = "todo:todo_mastery";
    static final String SWAP_KEY = "todo:swap", CLAP_KEY = "todo:clap";

    record Pending(Entity a, Entity b, long at) {}

    static final class State {
        @Nullable Pending pending;
        @Nullable Entity mark;
        long markUntil, freeUntil, strikeUntil;
        int freeSwaps;
        final Deque<Long> recent = new ArrayDeque<>();
    }

    private static State st(Cast c) { return c.state(State.class, State::new); }

    public static TechniqueClass define() {
        TechniqueClass c = new TechniqueClass(ID, 0xf0c040, () -> Items.GOLD_INGOT, null, 0, HOOKS);
        c.add(c.skill("boogie_woogie").tier(0).at(4, 0).icon(() -> Items.NOTE_BLOCK));
        c.add(c.skill("self_swap").tier(0).at(4, 1).requires("boogie_woogie").icon(() -> Items.ENDER_PEARL).ability(new Swap(false))
                .cooldown(() -> SWAP_COOLDOWN).energy(() -> SWAP_ENERGY));
        c.add(c.skill("entity_swap").tier(1).at(2, 2).requires("self_swap").icon(() -> Items.ENDER_EYE).ability(new Swap(true))
                .cooldown(() -> SWAP_COOLDOWN).energy(() -> SWAP_ENERGY));
        c.add(c.skill("cursed_object_swap").tier(2).at(6, 2).requires("self_swap").icon(() -> Items.AMETHYST_SHARD));
        c.add(c.skill("double_swap").tier(2).at(2, 3).requires("entity_swap").icon(() -> Items.CHORUS_FRUIT));
        c.add(c.skill("projectile_swap").tier(3).at(6, 3).requires("cursed_object_swap").icon(() -> Items.ARROW));
        c.add(c.skill("fake_clap").tier(3).at(3, 4).requires("double_swap", "projectile_swap").icon(() -> Items.BELL).ability(FAKE_CLAP)
                .cooldown(() -> CLAP_COOLDOWN).energy(() -> FAKE_CLAP_ENERGY));
        c.add(c.skill("rapid_swaps").tier(4).at(5, 4).requires("fake_clap").icon(() -> Items.CLOCK)
                .cooldown(() -> RAPID_SWAP_COOLDOWN));
        c.add(c.skill("todo_mastery").tier(5).at(4, 5).requires("rapid_swaps").icon(() -> Items.NETHER_STAR));
        return c;
    }

    // ------------------------------------------------------------------------------------------------ rules

    static double range(Cast c) { return c.has(MASTERY) ? MASTERY_RANGE : RANGE; }
    static double cone(Cast c) { return c.has(MASTERY) ? MASTERY_AIM_CONE : AIM_CONE; }
    static int delay(Cast c) { return c.has(MASTERY) ? MASTERY_SWAP_DELAY : c.has(RAPID) ? RAPID_SWAP_DELAY : SWAP_DELAY; }
    static int swapCooldown(Cast c) { return c.has(RAPID) ? RAPID_SWAP_COOLDOWN : SWAP_COOLDOWN; }

    /** What Boogie Woogie can move: living things, plus cursed objects and projectiles once learned. Never bosses. */
    static boolean swappable(Cast c, Entity e) {
        if (e == c.player || !e.isAlive() || e.isRemoved() || e.isSpectator() || e.level() != c.level) return false;
        if (e.is(JjkContent.SWAP_IMMUNE) || e.isVehicle()) return false;
        // Other players: allies always (a swap can save them), anyone else only where they could be attacked (PvP, teams).
        if (e instanceof net.minecraft.world.entity.player.Player pl)
            return (!pl.isCreative() || c.player.isCreative()) && (pl.isAlliedTo(c.player) || c.player.canHarmPlayer(pl));
        if (e instanceof LivingEntity) return true;
        if (e instanceof Projectile pr) return c.has(PROJECTILE) && !(pr instanceof FishingHook) && !pr.isPassenger();
        return c.has(OBJECT) && CursedObject.is(e) && !e.isPassenger();
    }

    static boolean markValid(Cast c, State s) {
        if (s.mark == null) return false;
        if (c.now() > s.markUntil || !swappable(c, s.mark) || s.mark.distanceTo(c.player) > range(c) * 1.5) { s.mark = null; return false; }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ the clap

    /** The clap: identical whether or not anything is swapped (that is the whole point of Fake Clap). */
    static void clap(Cast c) {
        ServerPlayer p = c.player;
        p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        p.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
        Vec3 hands = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.45, 0);
        c.level.sendParticles(ParticleTypes.CLOUD, hands.x, hands.y, hands.z, 4, 0.08, 0.08, 0.08, 0.04);
        c.level.sendParticles(ParticleTypes.CRIT, hands.x, hands.y, hands.z, 6, 0.1, 0.1, 0.1, 0.2);
        c.level.sendParticles(ParticleTypes.SWEEP_ATTACK, hands.x, hands.y, hands.z, 1, 0, 0, 0, 0);
        c.level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.JJK_CLAP, SoundSource.PLAYERS, 1.6f, 0.95f + p.getRandom().nextFloat() * 0.1f);
        c.cooldown(CLAP_KEY, CLAP_COOLDOWN);
    }

    record Swap(boolean entityMode) implements Ability {
        @Override public boolean ignoresCooldown(Cast c) { return true; } // the shared swap cooldown is checked below (free swaps bypass it)

        @Override public void press(Cast c) {
            State s = st(c);
            ServerPlayer p = c.player;
            boolean marking = (entityMode || c.has(MASTERY)) && p.isShiftKeyDown() && c.has(ENTITY);
            if (marking) { mark(c, s); return; }
            if (c.cooldownLeft(CLAP_KEY) > 0 || s.pending != null) return;
            boolean entity = c.has(ENTITY) && (entityMode || (c.has(MASTERY) && markValid(c, s)));
            Entity a = p;
            if (entity) {
                if (!markValid(c, s)) { c.fail("jjk.quantumdeath.fail.no_mark"); return; }
                a = s.mark;
            }
            Entity first = a;
            Entity b = JjkCombat.aimed(p, range(c), cone(c), e -> e != first && swappable(c, e));
            if (b == null) { c.fail("jjk.quantumdeath.fail.no_swap_target"); return; }
            boolean free = s.freeSwaps > 0 && c.now() <= s.freeUntil;
            if (!free && c.cooldownLeft(SWAP_KEY) > 0) {
                c.fail("jjk.quantumdeath.fail.cooldown", Component.translatable("jjk.quantumdeath.skill.todo.self_swap"), String.format("%.1f", c.cooldownLeft(SWAP_KEY) / 20f));
                return;
            }
            long now = c.now();
            while (!s.recent.isEmpty() && now - s.recent.peekFirst() > RATE_WINDOW) s.recent.removeFirst();
            if (s.recent.size() >= RATE_LIMIT) { c.fail("jjk.quantumdeath.fail.swap_rate"); return; }
            if (!c.spendEnergy(SWAP_ENERGY)) return;
            if (free) s.freeSwaps--;
            else c.cooldown(SWAP_KEY, swapCooldown(c));
            s.recent.addLast(now);
            clap(c);
            s.pending = new Pending(a, b, now + delay(c));
        }

        @Override public void secondary(Cast c) { FAKE_CLAP.secondary(c); }
    }

    static void mark(Cast c, State s) {
        Entity e = JjkCombat.aimed(c.player, range(c), cone(c), x -> swappable(c, x));
        if (e == null) {
            if (s.mark != null) { s.mark = null; c.info("jjk.quantumdeath.msg.mark_cleared"); }
            else c.fail("jjk.quantumdeath.fail.no_swap_target");
            return;
        }
        s.mark = e;
        s.markUntil = c.now() + MARK_TICKS;
        c.info("jjk.quantumdeath.msg.marked", e.getName());
        c.level.sendParticles(c.player, ParticleTypes.END_ROD, true, false, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 8, 0.15, 0.15, 0.15, 0.01);
    }

    static final Ability FAKE_CLAP = new Ability() {
        @Override public boolean ignoresCooldown(Cast c) { return true; }
        @Override public void press(Cast c) { secondary(c); }
        @Override public void secondary(Cast c) {
            if (!c.has(FAKE) || c.cooldownLeft(CLAP_KEY) > 0 || st(c).pending != null) return;
            if (!c.spendEnergy(FAKE_CLAP_ENERGY)) return;
            clap(c);
        }
    };

    // ------------------------------------------------------------------------------------------------ the swap

    /** A spot near {@code dest} where {@code e} fits: in loaded chunks, inside the border and the build height. */
    static @Nullable Vec3 safeSpot(ServerLevel level, Entity e, Vec3 dest) {
        if (!Double.isFinite(dest.x) || !Double.isFinite(dest.y) || !Double.isFinite(dest.z)) return null;
        double[] up = {0, 0.5, 1.0, 1.5};
        double[][] side = {{0, 0}, {0.7, 0}, {-0.7, 0}, {0, 0.7}, {0, -0.7}};
        for (double dy : up)
            for (double[] o : side) {
                Vec3 q = dest.add(o[0], dy, o[1]);
                BlockPos bp = BlockPos.containing(q);
                if (level.isOutsideBuildHeight(bp) || !level.hasChunkAt(bp) || !level.getWorldBorder().isWithinBounds(bp)) continue;
                AABB box = e.getDimensions(e.getPose()).makeBoundingBox(q).deflate(1e-3);
                if (level.noCollision(e, box)) return q;
            }
        return null;
    }

    static void place(ServerLevel level, Entity e, Vec3 at) {
        if (e.isPassenger()) e.stopRiding();
        if (e instanceof ServerPlayer sp) sp.teleportTo(level, at.x, at.y, at.z, Relative.union(Relative.ROTATION, Relative.DELTA), 0, 0, false);
        else e.teleportTo(level, at.x, at.y, at.z, Relative.DELTA, e.getYRot(), e.getXRot(), false);
        e.resetFallDistance();
    }

    static void resolve(Cast c, State s, Pending pend) {
        Entity a = pend.a(), b = pend.b();
        ServerLevel level = c.level;
        boolean aOk = a == c.player ? c.player.isAlive() && c.player.level() == level : swappable(c, a);
        if (!aOk || !swappable(c, b)) { c.fail("jjk.quantumdeath.fail.swap_lost"); return; }
        Vec3 pa = a.position(), pb = b.position();
        Vec3 da = safeSpot(level, a, pb), db = safeSpot(level, b, pa);
        if (da == null || db == null) { c.fail("jjk.quantumdeath.fail.no_room"); return; }
        place(level, a, da);
        place(level, b, db);
        for (Vec3 v : new Vec3[]{pa, pb}) {
            Vec3 mid = v.add(0, 1, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mid.x, mid.y, mid.z, 24, 0.3, 0.6, 0.3, 0.6);
            Fx.ring(level, Fx.FRAME, v.add(0, 0.1, 0), 0.8, 14, 0.15);
            level.playSound(null, v.x, v.y, v.z, ModSounds.JJK_SWAP, SoundSource.PLAYERS, 1.0f, 1.0f + level.getRandom().nextFloat() * 0.15f);
        }
        boolean combat = false;
        for (Entity e : new Entity[]{a, b}) {
            if (e == c.player || !(e instanceof LivingEntity l)) continue;
            // Disoriented for a moment: the swap is a weapon, not just a teleport.
            l.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, OFF_BALANCE_TICKS, 1, false, false));
            if (l instanceof Mob mob) mob.getNavigation().stop();
            if (Techniques.xpTarget(c.player, l)) combat = true;
        }
        if (combat) Techniques.addXp(c.player, JjkBalance.Progression.XP_SKILL_EVENT, true);
        s.strikeUntil = c.now() + BOOGIE_STRIKE_TICKS;
        if (c.has(DOUBLE) && s.freeSwaps <= 0) {
            s.freeSwaps = 1 + (c.has(MASTERY) ? MASTERY_EXTRA_FREE_SWAPS : 0);
            s.freeUntil = c.now() + (c.has(RAPID) ? RAPID_DOUBLE_WINDOW : DOUBLE_WINDOW);
        }
    }

    static final ClassHooks HOOKS = new ClassHooks() {
        @Override public void tick(Cast c) {
            State s = st(c);
            if (s.pending != null && c.now() >= s.pending.at()) {
                Pending pend = s.pending;
                s.pending = null;
                resolve(c, s, pend);
            }
            if (s.freeSwaps > 0 && c.now() > s.freeUntil) s.freeSwaps = 0;
            if (s.mark != null && c.player.tickCount % 10 == 0 && markValid(c, s))
                c.level.sendParticles(c.player, ParticleTypes.END_ROD, true, false, s.mark.getX(), s.mark.getY() + s.mark.getBbHeight() + 0.3, s.mark.getZ(), 2, 0.1, 0.05, 0.1, 0);
        }

        @Override public float outgoingMultiplier(Cast c, LivingEntity target, DamageSource source, float amount) {
            return JjkCombat.isMelee(source, c.player) && c.now() <= st(c).strikeUntil ? 1f + BOOGIE_STRIKE_BONUS : 1f;
        }

        @Override public void indicators(Cast c, List<JjkNet.Indicator> out) {
            State s = st(c);
            if (markValid(c, s)) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.mark", s.mark.getName().getString(),
                    (s.markUntil - c.now()) / (float) MARK_TICKS, 0xfff0c040));
            if (s.freeSwaps > 0 && c.now() <= s.freeUntil) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.double", String.valueOf(s.freeSwaps),
                    (s.freeUntil - c.now()) / (float) (c.has(RAPID) ? RAPID_DOUBLE_WINDOW : DOUBLE_WINDOW), 0xffffe080));
            int left = c.cooldownLeft(SWAP_KEY);
            if (left > 0) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.swap_cd", String.format("%.1f", left / 20f), left / (float) swapCooldown(c), 0xffa08030));
        }

        @Override public void cleanup(ServerPlayer p) {
            State s = Techniques.rt(p).state(State.class, State::new);
            s.pending = null;
            s.mark = null;
        }
    };
}
