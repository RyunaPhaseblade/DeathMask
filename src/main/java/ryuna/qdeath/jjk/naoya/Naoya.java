package ryuna.qdeath.jjk.naoya;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
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
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ryuna.qdeath.jjk.JjkBalance.Naoya.*;

/**
 * Naoya Zenin, Projection Sorcery. The 24 FPS rule, made playable:
 * <ul>
 * <li>Every Projection is planned before it starts: 24 frames along the horizontal look direction, played over
 * {@code PROJECTION_TICKS} ticks. The plan steps up small ledges and follows the ground, and is validated against the
 * world by the server. Naoya does not steer while projecting; he follows the plan.</li>
 * <li>A plan that runs into terrain, a press before the frames are done (except in the short buffer at the end) or a
 * chained turn sharper than the allowed angle breaks the rule: Naoya freezes for one second and loses his momentum.</li>
 * <li>Pressing again inside the rhythm window after a projection chains it (24 FPS Movement). Each chain raises the
 * momentum multiplier (Speed Stacking), which drives speed, distance and Momentum Attack damage. Mach Movement lifts
 * the cap and makes the turn rule stricter.</li>
 * <li>Frame Touch imposes the rule on whoever he strikes: during the window they must keep their motion (or stand
 * still). Breaking it freezes them in a frame (Frame Freeze), with diminishing returns so nobody is stun-locked.</li>
 * </ul>
 */
public final class Naoya {
    private Naoya() {}

    public static final String ID = "naoya";
    public static final String PROJECTION = "naoya:projection_sorcery", FPS = "naoya:fps_movement", SPEED = "naoya:speed_increase",
            MOMENTUM_ATTACK = "naoya:momentum_attack", STACKING = "naoya:speed_stacking", TOUCH = "naoya:frame_touch", FREEZE = "naoya:frame_freeze",
            EXTENDED = "naoya:extended_freeze", MACH = "naoya:mach_movement", MASTERY = "naoya:naoya_mastery";

    static final class Mark {
        long until;
        Vec3 lastPos;
        Vec3 lastVel = Vec3.ZERO;
        int checks;
    }

    static final class State {
        ProjectionPath.@Nullable Plan plan;
        boolean projecting, buffered, boomed;
        int frame, chain, bumps;
        float momentum = 1, speed;
        long endedAt = -1000, touchReadyAt, lastDecay;
        Vec3 lastDir = new Vec3(0, 0, 1);
        @Nullable Vec3 prevPos;
        int stillTicks;
        final Map<LivingEntity, Mark> marks = new HashMap<>();
        final Map<LivingEntity, Long> framed = new HashMap<>();
    }

    /** Per target: when it was last frozen in a frame and until when it cannot be marked again (any Naoya). */
    private static final Map<UUID, long[]> FRAME_HISTORY = new HashMap<>();

    private static State st(Cast c) { return c.state(State.class, State::new); }

    public static TechniqueClass define() {
        TechniqueClass c = new TechniqueClass(ID, 0xe8e2c8, () -> Items.CLOCK, null, 0, HOOKS);
        c.add(c.skill("projection_sorcery").tier(0).at(4, 0).icon(() -> Items.CLOCK).ability(PROJECT)
                .cooldown(() -> 0).energy(() -> PROJECTION_ENERGY));
        c.add(c.skill("fps_movement").tier(1).at(4, 1).requires("projection_sorcery").icon(() -> Items.PAINTING));
        c.add(c.skill("speed_increase").tier(2).at(2, 2).requires("fps_movement").icon(() -> Items.SUGAR));
        c.add(c.skill("frame_touch").tier(2).at(6, 2).requires("fps_movement").icon(() -> Items.ITEM_FRAME).energy(() -> FRAME_TOUCH_ENERGY)
                .cooldown(() -> FRAME_TOUCH_COOLDOWN));
        c.add(c.skill("momentum_attack").tier(3).at(1, 3).requires("speed_increase").icon(() -> Items.IRON_SWORD));
        c.add(c.skill("speed_stacking").tier(3).at(3, 3).requires("speed_increase").icon(() -> Items.RABBIT_FOOT));
        c.add(c.skill("frame_freeze").tier(3).at(6, 3).requires("frame_touch").icon(() -> Items.GLOW_ITEM_FRAME));
        c.add(c.skill("extended_freeze").tier(4).at(6, 4).requires("frame_freeze").icon(() -> Items.BLUE_ICE));
        c.add(c.skill("mach_movement").tier(4).cost(JjkBalance.Progression.TP_COST_MAXIMUM).level(18).at(2, 4)
                .requires("momentum_attack", "speed_stacking", "extended_freeze").icon(() -> Items.FEATHER));
        c.add(c.skill("naoya_mastery").tier(5).at(4, 5).requires("mach_movement").icon(() -> Items.NETHER_STAR));
        return c;
    }

    // ------------------------------------------------------------------------------------------------ rules

    static int stackCap(Cast c) { return c.has(MACH) ? MACH_CAP : STACK_CAP; }

    static float momentumFor(Cast c, int chain) {
        if (!c.has(STACKING)) return 1f;
        return MOMENTUM[Math.min(Math.min(chain, stackCap(c)), MOMENTUM.length - 1)];
    }

    static boolean mach(Cast c, State s) { return c.has(MACH) && s.chain >= MACH_FROM; }

    static float maxTurn(Cast c, State s) {
        float t = mach(c, s) ? MACH_TURN : MAX_TURN;
        return c.has(MASTERY) ? t + MASTERY_TURN_BONUS : t;
    }

    static int chainWindow(Cast c) { return c.has(MASTERY) ? MASTERY_CHAIN_WINDOW : CHAIN_WINDOW; }
    static int bufferFrames(Cast c) { return c.has(MASTERY) ? MASTERY_BUFFER_FRAMES : BUFFER_FRAMES; }

    static float speedFor(Cast c, float momentum) {
        float s = BASE_SPEED * (c.has(SPEED) ? SPEED_INCREASE_MULT : 1f) * momentum;
        return Math.min(MAX_SPEED, s);
    }

    /** Blocks the next projection would cover, for the client preview. */
    static double nextDistance(Cast c, State s) {
        boolean chainOpen = c.has(FPS) && c.now() - s.endedAt <= chainWindow(c);
        int chain = chainOpen ? s.chain + 1 : 0;
        return speedFor(c, momentumFor(c, chain)) * PROJECTION_TICKS;
    }

    static double stepUp(Cast c) { return c.has(MASTERY) ? 1.5 : 1.0; }

    static double angle(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ProjectionPath.horizontal(a).dot(ProjectionPath.horizontal(b))))));
    }

    // ------------------------------------------------------------------------------------------------ projection

    static final Ability PROJECT = new Ability() {
        @Override public boolean ignoresCooldown(Cast c) { return true; }
        @Override public void press(Cast c) { attempt(c, st(c)); }
        /** Secondary: stop cleanly (ends the sequence without breaking the rule). */
        @Override public void secondary(Cast c) {
            State s = st(c);
            if (!s.projecting && s.chain == 0) return;
            s.projecting = false;
            s.buffered = false;
            s.chain = 0;
            s.momentum = 1;
            c.player.setDeltaMovement(c.player.getDeltaMovement().multiply(0.2, 1, 0.2));
            c.player.syncVelocity = true;
            c.info("jjk.quantumdeath.msg.projection_stop");
        }
    };

    static void attempt(Cast c, State s) {
        ServerPlayer p = c.player;
        if (p.hasEffect(JjkContent.IMMOBILIZED)) { c.fail("jjk.quantumdeath.fail.frozen"); return; }
        if (s.projecting) {
            if (c.has(FPS) && FRAMES - s.frame <= bufferFrames(c)) { s.buffered = true; return; }
            violation(c, s, Component.translatable("jjk.quantumdeath.violation.early", s.frame, FRAMES));
            return;
        }
        boolean chain = c.has(FPS) && s.chain >= 0 && c.now() - s.endedAt <= chainWindow(c);
        Vec3 dir = ProjectionPath.horizontal(p.getLookAngle());
        if (chain) {
            double turn = angle(s.lastDir, dir);
            float max = maxTurn(c, s);
            if (turn > max) {
                violation(c, s, Component.translatable("jjk.quantumdeath.violation.turn", Math.round(turn), Math.round(max)));
                return;
            }
        } else {
            s.chain = 0;
            s.momentum = 1;
        }
        float price = (chain ? CHAIN_ENERGY : PROJECTION_ENERGY) * (c.has(MASTERY) ? MASTERY_COST_MULT : 1f);
        if (!c.spendEnergy(price)) return;
        int nextChain = chain ? s.chain + 1 : 0;
        float momentum = momentumFor(c, nextChain);
        float speed = speedFor(c, momentum);
        ProjectionPath.Plan plan = ProjectionPath.plan(c.level, p, p.position(), dir, speed * PROJECTION_TICKS, FRAMES, stepUp(c));
        if (!plan.clear() && plan.blockedAt() <= 2) {
            violation(c, s, Component.translatable("jjk.quantumdeath.violation.collision"));
            return;
        }
        if (chain) {
            s.chain = nextChain;
            c.data.energy = Math.min(c.maxEnergy(), c.data.energy + CHAIN_REFUND);
            if (p.tickCount % 2 == 0) Techniques.addXp(p, JjkBalance.Progression.XP_SKILL_EVENT * 0.5f, true);
            c.info(mach(c, s) ? "jjk.quantumdeath.msg.mach" : "jjk.quantumdeath.msg.chain", s.chain, String.format("%.2f", momentum));
        }
        s.momentum = momentum;
        s.plan = plan;
        s.frame = 0;
        s.projecting = true;
        s.buffered = false;
        s.boomed = false;
        s.bumps = 0;
        s.lastDir = plan.dir();
        p.resetFallDistance();
        c.level.playSound(null, p.blockPosition(), ModSounds.JJK_PROJECTION_FRAME, SoundSource.PLAYERS, 0.9f, 0.9f + Math.min(0.8f, s.chain * 0.12f));
        frameOutline(c.level, p.position(), p.getBbWidth(), p.getBbHeight(), plan.dir(), Fx.FRAME);
    }

    static void violation(Cast c, State s, Component reason) {
        ServerPlayer p = c.player;
        s.projecting = false;
        s.buffered = false;
        s.chain = 0;
        s.momentum = 1;
        s.endedAt = -1000;
        p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
        p.syncVelocity = true;
        p.addEffect(new MobEffectInstance(JjkContent.IMMOBILIZED, VIOLATION_FREEZE, 0, false, false, true));
        frameOutline(c.level, p.position(), p.getBbWidth() + 0.3, p.getBbHeight() + 0.3, s.lastDir, Fx.FRAME);
        c.level.playSound(null, p.blockPosition(), ModSounds.JJK_FRAME_FREEZE, SoundSource.PLAYERS, 1.0f, 1.2f);
        ServerPlayNetworking.send(p, new JjkNet.Flash(0x60f4f1e8, 6));
        p.sendOverlayMessage(Component.translatable("jjk.quantumdeath.violation", reason).withColor(0xff5050));
    }

    /** A flat rectangle of white "frame" particles around a body, facing across {@code dir}. */
    static void frameOutline(ServerLevel level, Vec3 feet, double width, double height, Vec3 dir, net.minecraft.core.particles.ParticleOptions p) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(width * 0.5 + 0.2);
        Vec3 bl = feet.subtract(side), br = feet.add(side);
        Vec3 up = new Vec3(0, height + 0.1, 0);
        Fx.line(level, p, bl, br, 0.25);
        Fx.line(level, p, bl.add(up), br.add(up), 0.25);
        Fx.line(level, p, bl, bl.add(up), 0.25);
        Fx.line(level, p, br, br.add(up), 0.25);
    }

    static void tickProjection(Cast c, State s) {
        ServerPlayer p = c.player;
        ProjectionPath.Plan plan = s.plan;
        if (plan == null || p.hasEffect(JjkContent.IMMOBILIZED)) { s.projecting = false; return; }
        int perTick = Math.max(1, FRAMES / PROJECTION_TICKS);
        int prevFrame = s.frame;
        s.frame = Math.min(FRAMES, s.frame + perTick);
        if (!plan.clear() && s.frame >= plan.blockedAt()) {
            p.setDeltaMovement(Vec3.ZERO);
            violation(c, s, Component.translatable("jjk.quantumdeath.violation.collision"));
            return;
        }
        // The client reports real collisions; two in a row (not a glancing touch) break the plan.
        s.bumps = s.frame > perTick * 2 && p.horizontalCollision ? s.bumps + 1 : 0;
        if (s.bumps >= 2) {
            violation(c, s, Component.translatable("jjk.quantumdeath.violation.collision"));
            return;
        }
        Vec3 from = plan.frames()[prevFrame], to = plan.frames()[s.frame];
        Vec3 vel = to.subtract(from);
        p.setDeltaMovement(vel.x, vel.y > 0 ? vel.y + 0.08 : Math.max(vel.y, p.getDeltaMovement().y), vel.z);
        p.syncVelocity = true;
        p.resetFallDistance();
        // Afterimages: the frames left behind.
        if (s.frame % 4 == 0) {
            c.level.sendParticles(Fx.FRAME, from.x, from.y + p.getBbHeight() * 0.5, from.z, 6, p.getBbWidth() * 0.35, p.getBbHeight() * 0.4, p.getBbWidth() * 0.35, 0);
            c.level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.1, from.z, 1, 0.1, 0, 0.1, 0.01);
        }
        if (mach(c, s) && !s.boomed) {
            for (LivingEntity e : JjkCombat.around(p, p.position().add(0, 1, 0), MACH_BOOM_RADIUS)) {
                JjkCombat.hurt(p, e, p, JjkCombat.TECHNIQUE, MACH_BOOM_DAMAGE, true);
                JjkCombat.knock(e, e.position().subtract(p.position()), 0.9, 0.3);
                s.boomed = true;
            }
            if (s.frame == FRAMES / 2) {
                c.level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                c.level.playSound(null, p.blockPosition(), ModSounds.JJK_PROJECTION_FRAME, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
        }
        if (s.frame >= FRAMES) {
            s.projecting = false;
            s.endedAt = c.now();
            Vec3 carry = plan.dir().scale(speedFor(c, s.momentum) * 0.6);
            p.setDeltaMovement(carry.x, p.getDeltaMovement().y, carry.z);
            p.syncVelocity = true;
            if (s.buffered) {
                s.buffered = false;
                attempt(c, s);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ Frame Touch

    static void mark(Cast c, State s, LivingEntity target) {
        long now = c.now();
        long[] hist = FRAME_HISTORY.get(target.getUUID());
        if (hist != null && now < hist[1]) return; // still immune from the last frame
        if (s.marks.containsKey(target)) return;
        Mark m = new Mark();
        m.until = now + (c.has(EXTENDED) ? EXTENDED_FRAME_WINDOW : FRAME_WINDOW);
        m.lastPos = target.position();
        m.lastVel = target.getDeltaMovement();
        s.marks.put(target, m);
        frameOutline(c.level, target.position(), target.getBbWidth(), target.getBbHeight(), c.player.getLookAngle(), Fx.FRAME);
        c.level.playSound(null, target.blockPosition(), ModSounds.JJK_PROJECTION_FRAME, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (target instanceof ServerPlayer victim)
            victim.sendOverlayMessage(Component.translatable("jjk.quantumdeath.msg.frame_imposed").withColor(0xf4f1e8));
    }

    static void tickMarks(Cast c, State s) {
        long now = c.now();
        for (Iterator<Map.Entry<LivingEntity, Mark>> it = s.marks.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            LivingEntity t = en.getKey();
            Mark m = en.getValue();
            if (!t.isAlive() || t.isRemoved() || t.level() != c.level || now > m.until) { it.remove(); continue; }
            Vec3 vel = t.position().subtract(m.lastPos);
            m.lastPos = t.position();
            m.checks++;
            if (t.tickCount % 4 == 0) frameOutline(c.level, t.position(), t.getBbWidth(), t.getBbHeight(), c.player.getLookAngle(), Fx.FRAME);
            if (m.checks > 2) {
                double hv = vel.horizontalDistance(), hl = m.lastVel.horizontalDistance();
                boolean broke = Math.abs(hv - hl) > FRAME_SPEED_CHANGE
                        || (hv > 0.04 && hl > 0.04 && angle(vel, m.lastVel) > FRAME_TURN)
                        || vel.y - m.lastVel.y > 0.25;
                if (broke) {
                    it.remove();
                    if (c.has(FREEZE)) frameFreeze(c, s, t);
                    continue;
                }
            }
            m.lastVel = vel;
        }
        s.framed.entrySet().removeIf(e -> now > e.getValue() || !e.getKey().isAlive());
    }

    static void frameFreeze(Cast c, State s, LivingEntity t) {
        long now = c.now();
        int ticks = c.has(EXTENDED) ? EXTENDED_FREEZE_TICKS : FRAME_FREEZE_TICKS;
        if (FRAME_HISTORY.size() > 256) FRAME_HISTORY.values().removeIf(h -> now > h[1] + FRAME_DIMINISH_WINDOW);
        long[] hist = FRAME_HISTORY.computeIfAbsent(t.getUUID(), k -> new long[]{-100000, 0});
        if (now - hist[0] < FRAME_DIMINISH_WINDOW) ticks /= 2; // diminishing returns
        hist[0] = now;
        hist[1] = now + ticks + FRAME_IMMUNITY;
        t.addEffect(new MobEffectInstance(JjkContent.IMMOBILIZED, ticks, 0, false, false, true));
        s.framed.put(t, now + ticks);
        Vec3 look = c.player.getLookAngle();
        frameOutline(c.level, t.position().add(0, -0.1, 0), t.getBbWidth() + 0.5, t.getBbHeight() + 0.4, look, Fx.FRAME);
        frameOutline(c.level, t.position().add(0, -0.1, 0), t.getBbWidth() + 0.8, t.getBbHeight() + 0.7, look, ParticleTypes.END_ROD);
        c.level.playSound(null, t.blockPosition(), ModSounds.JJK_FRAME_FREEZE, SoundSource.PLAYERS, 1.2f, 1.0f);
        if (Techniques.xpTarget(c.player, t)) Techniques.addXp(c.player, JjkBalance.Progression.XP_SKILL_EVENT * 2, true);
        if (t instanceof ServerPlayer victim) {
            victim.sendOverlayMessage(Component.translatable("jjk.quantumdeath.msg.frame_frozen").withColor(0xf4f1e8));
            ServerPlayNetworking.send(victim, new JjkNet.Flash(0x70f4f1e8, 8));
        }
    }

    // ------------------------------------------------------------------------------------------------ hooks

    static final ClassHooks HOOKS = new ClassHooks() {
        @Override public void tick(Cast c) {
            State s = st(c);
            ServerPlayer p = c.player;
            Vec3 pos = p.position();
            s.speed = s.prevPos == null ? 0 : (float) pos.subtract(s.prevPos).horizontalDistance();
            s.prevPos = pos;
            if (s.projecting) tickProjection(c, s);
            else if (s.chain > 0 || s.momentum > 1) {
                long idle = c.now() - s.endedAt;
                if (idle > chainWindow(c)) {
                    // Missing the rhythm ends the sequence; Mastery bleeds momentum off one stack at a time instead.
                    if (c.has(MASTERY) && s.chain > 0) {
                        if (c.now() - s.lastDecay >= 20) {
                            s.lastDecay = c.now();
                            s.chain--;
                            s.momentum = momentumFor(c, s.chain);
                            s.endedAt = c.now() - chainWindow(c) + 1;
                        }
                    } else { s.chain = 0; s.momentum = 1; }
                }
            }
            // No momentum while standing still.
            if (!s.projecting && s.speed < 0.03 && p.onGround()) {
                if (++s.stillTicks > 10 && s.chain > 0) { s.chain = 0; s.momentum = 1; }
            } else s.stillTicks = 0;
            if (!s.marks.isEmpty() || !s.framed.isEmpty()) tickMarks(c, s);
        }

        @Override public float outgoingMultiplier(Cast c, LivingEntity target, DamageSource source, float amount) {
            State s = st(c);
            if (!JjkCombat.isMelee(source, c.player)) return 1f;
            float mult = 1f;
            if (c.has(MOMENTUM_ATTACK) && (s.projecting || s.speed >= MOMENTUM_ATTACK_MIN_SPEED)) {
                mult += MOMENTUM_ATTACK_PER_STACK * (s.momentum - 1) + MOMENTUM_ATTACK_PER_SPEED * Math.min(4f, s.speed / BASE_SPEED);
            }
            if (s.marks.containsKey(target)) mult += FRAME_MARK_BONUS;
            if (s.framed.containsKey(target)) {
                mult += FRAME_SHATTER_BONUS;
                s.framed.remove(target);
                target.removeEffect(JjkContent.IMMOBILIZED);
                c.level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
                c.level.sendParticles(Fx.FRAME, target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
            }
            return mult;
        }

        @Override public void afterHit(Cast c, LivingEntity target, DamageSource source, float taken, boolean technique) {
            if (!JjkCombat.isMelee(source, c.player)) return;
            State s = st(c);
            if (c.has(MOMENTUM_ATTACK) && (s.projecting || s.speed >= MOMENTUM_ATTACK_MIN_SPEED)) {
                JjkCombat.knock(target, s.lastDir, 0.4 + 0.2 * s.momentum, 0.15);
                c.level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
            }
            if (!c.has(TOUCH) || !target.isAlive()) return;
            boolean free = c.has(MASTERY) && (s.projecting || c.now() - s.endedAt < 10);
            if (!free && c.now() < s.touchReadyAt) return;
            if (!free && !c.hasEnergy(FRAME_TOUCH_ENERGY)) return;
            if (!free) c.spendEnergy(FRAME_TOUCH_ENERGY);
            s.touchReadyAt = c.now() + (c.has(MASTERY) ? FRAME_TOUCH_COOLDOWN * 2 / 3 : FRAME_TOUCH_COOLDOWN);
            c.cooldown(TOUCH, c.has(MASTERY) ? FRAME_TOUCH_COOLDOWN * 2 / 3 : FRAME_TOUCH_COOLDOWN);
            mark(c, s, target);
        }

        @Override public void indicators(Cast c, List<JjkNet.Indicator> out) {
            State s = st(c);
            if (s.projecting) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.frame", s.frame + "/" + FRAMES, s.frame / (float) FRAMES, 0xfff4f1e8));
            if (s.momentum > 1 || s.chain > 0)
                out.add(new JjkNet.Indicator(mach(c, s) ? "jjk.quantumdeath.ind.mach" : "jjk.quantumdeath.ind.momentum", String.format("%.2f", s.momentum),
                        s.momentum / MOMENTUM[Math.min(MOMENTUM.length - 1, stackCap(c))], mach(c, s) ? 0xffffe070 : 0xffe8e2c8));
            long window = chainWindow(c) - (c.now() - s.endedAt);
            if (!s.projecting && c.has(FPS) && window > 0)
                out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.chain_window", String.valueOf(s.chain + 1), window / (float) chainWindow(c), 0xff9fe0a0));
            if (!s.marks.isEmpty()) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.marks", String.valueOf(s.marks.size()), -1, 0xfff4f1e8));
            // Hidden channel for the client's plan preview: distance;stepUp;chainOpen;lastDirX;lastDirZ;maxTurn
            boolean open = c.has(FPS) && !s.projecting && c.now() - s.endedAt <= chainWindow(c);
            out.add(new JjkNet.Indicator("@naoya", String.format(java.util.Locale.ROOT, "%.2f;%.1f;%d;%.3f;%.3f;%.0f", nextDistance(c, s), stepUp(c), open ? 1 : 0,
                    s.lastDir.x, s.lastDir.z, maxTurn(c, s)), -1, 0));
        }

        @Override public void cleanup(ServerPlayer p) {
            State s = Techniques.rt(p).state(State.class, State::new);
            s.projecting = false;
            s.marks.clear();
            s.framed.clear();
        }
    };

    public static void clearHistory() { FRAME_HISTORY.clear(); }

    /** Diagnostic line for tests and /jjk info. */
    public static String debug(ServerPlayer p) {
        State s = Techniques.rt(p).state(State.class, State::new);
        return "projecting=" + s.projecting + " frame=" + s.frame + " chain=" + s.chain + " momentum=" + s.momentum
                + " blockedAt=" + (s.plan == null ? "-" : s.plan.blockedAt()) + " frozen=" + p.hasEffect(JjkContent.IMMOBILIZED);
    }
}
