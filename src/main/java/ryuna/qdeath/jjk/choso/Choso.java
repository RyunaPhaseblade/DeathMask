package ryuna.qdeath.jjk.choso;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.jjk.Ability;
import ryuna.qdeath.jjk.Cast;
import ryuna.qdeath.jjk.ClassHooks;
import ryuna.qdeath.jjk.Fx;
import ryuna.qdeath.jjk.JjkCombat;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.Ongoing;
import ryuna.qdeath.jjk.TechniqueClass;
import ryuna.qdeath.jjk.TechniqueData;
import ryuna.qdeath.jjk.Techniques;
import ryuna.qdeath.jjk.entity.TechniqueProjectile;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ryuna.qdeath.jjk.JjkBalance.Choso.*;

/**
 * Choso, Blood Manipulation: a ranged fighter who spends his own blood. Blood regenerates slowly from food; running
 * low weakens, then cripples him, and spending blood he does not have costs health. Convergence compresses blood
 * into a stored charge that powers Piercing Blood and Supernova; Flowing Red Scale turns blood into a body boost.
 */
public final class Choso {
    private Choso() {}

    public static final String ID = "choso";
    public static final String MANIPULATION = "choso:blood_manipulation", SLICING = "choso:slicing_exorcism", CONTROL = "choso:blood_control",
            CONVERGENCE = "choso:convergence", PIERCING = "choso:piercing_blood", SUPERNOVA = "choso:supernova",
            SCALE = "choso:flowing_red_scale", STACK = "choso:flowing_red_scale_stack", MASTERY = "choso:choso_mastery";

    static final class State {
        float stored;          // compressed blood ready for use, 0..1
        long storedUntil;
        float charging = -1;   // a charge in progress (-1: none)
        boolean firedOnPress;
        boolean scale, stack;
    }

    private static State st(Cast c) { return c.state(State.class, State::new); }

    public static TechniqueClass define() {
        TechniqueClass c = new TechniqueClass(ID, 0xd0302a, () -> Items.REDSTONE, "jjk.quantumdeath.resource.blood", 0xb01515, HOOKS);
        c.add(c.skill("blood_manipulation").tier(0).at(4, 0).icon(() -> Items.REDSTONE).ability(DRAW)
                .cooldown(() -> DRAW_COOLDOWN).resource(() -> -DRAW_BLOOD));
        c.add(c.skill("slicing_exorcism").tier(1).at(2, 1).requires("blood_manipulation").icon(() -> JjkContent.BLOOD_DISC).ability(SLICING_EXORCISM)
                .cooldown(() -> SLICING_COOLDOWN).resource(() -> SLICING_BLOOD));
        c.add(c.skill("blood_control").tier(1).at(6, 1).requires("blood_manipulation").icon(() -> Items.NETHER_WART));
        c.add(c.skill("convergence").tier(2).at(2, 2).requires("slicing_exorcism").icon(() -> Items.REDSTONE_BLOCK).ability(CONVERGE).hold()
                .cooldown(() -> 10).resource(() -> CONVERGENCE_BLOOD_PER_TICK * CONVERGENCE_CHARGE_TICKS));
        c.add(c.skill("flowing_red_scale").tier(2).at(6, 2).requires("blood_control").icon(() -> Items.DYE.pick(net.minecraft.world.item.DyeColor.RED)).ability(new Toggle(false))
                .cooldown(() -> FRS_TOGGLE_COOLDOWN).resource(() -> FRS_ACTIVATE_BLOOD));
        c.add(c.skill("piercing_blood").tier(3).at(1, 3).requires("convergence").icon(() -> Items.ARROW).ability(new Compressed(true)).hold()
                .cooldown(() -> PIERCING_COOLDOWN).resource(() -> PIERCING_BLOOD));
        c.add(c.skill("supernova").tier(4).at(3, 3).requires("convergence").icon(() -> Items.FIREWORK_STAR).ability(new Compressed(false)).hold()
                .cooldown(() -> SUPERNOVA_COOLDOWN).resource(() -> SUPERNOVA_BLOOD));
        c.add(c.skill("flowing_red_scale_stack").tier(4).at(6, 3).requires("flowing_red_scale").icon(() -> Items.NETHER_WART_BLOCK).ability(new Toggle(true))
                .cooldown(() -> FRS_TOGGLE_COOLDOWN).resource(() -> STACK_ACTIVATE_BLOOD));
        c.add(c.skill("choso_mastery").tier(5).at(4, 4).requires("piercing_blood", "supernova", "flowing_red_scale_stack").icon(() -> Items.NETHER_STAR));
        return c;
    }

    // ------------------------------------------------------------------------------------------------ blood

    static float cost(Cast c, float base) {
        float m = c.has(CONTROL) ? BLOOD_CONTROL_COST_MULT : 1f;
        return base * m;
    }

    /** Pays blood. Beyond what is left, the body pays in health; a cast that would kill is refused. */
    static boolean spendBlood(Cast c, float amount) {
        if (c.player.isCreative()) return true;
        if (c.data.resource >= amount) { c.data.resource -= amount; return true; }
        float hp = (amount - c.data.resource) * BLOOD_DEFICIT_HEALTH;
        if (c.player.getHealth() - hp < 1f) { c.fail("jjk.quantumdeath.fail.blood"); return false; }
        c.data.resource = 0;
        bleed(c.player, hp);
        c.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jjk.quantumdeath.msg.hemorrhage", String.format("%.1f", hp)).withColor(0xff3030));
        return true;
    }

    /** Blood taken from the body itself: armour, resistance and absorption do not stop it (it never kills: callers check). */
    static void bleed(ServerPlayer p, float health) {
        p.setHealth(Math.max(1f, p.getHealth() - health));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.7f, 0.8f);
        Vec3 c = p.position().add(0, 1, 0);
        ((ServerLevel) p.level()).sendParticles(Fx.BLOOD_DARK, c.x, c.y, c.z, 8, 0.25, 0.4, 0.25, 0.02);
    }

    /** Between the palms, far enough ahead that first-person particles do not fill the screen. */
    static Vec3 hands(ServerPlayer p) { return p.getEyePosition().add(p.getLookAngle().scale(1.1)).add(0, -0.55, 0); }

    // ------------------------------------------------------------------------------------------------ actives

    /** Blood Manipulation: draw blood from your own body to refill the pool. */
    static final Ability DRAW = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            if (p.getHealth() <= DRAW_HEALTH + 1) { c.fail("jjk.quantumdeath.fail.draw_health"); return; }
            if (c.data.resource >= HOOKS.resourceMax(c.data) - 1) { c.fail("jjk.quantumdeath.fail.blood_full"); return; }
            bleed(p, DRAW_HEALTH);
            c.data.resource = Math.min(HOOKS.resourceMax(c.data), c.data.resource + DRAW_BLOOD);
            Vec3 h = hands(p);
            for (int i = 0; i < 12; i++) {
                Vec3 from = p.position().add(0, 1.0, 0).add(new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian() * 0.5, p.getRandom().nextGaussian()).scale(0.5));
                Fx.moving(c.level, Fx.BLOOD_SMALL, from, h.subtract(from).scale(0.25));
            }
            c.level.playSound(null, p.blockPosition(), ModSounds.JJK_BLOOD_CONVERGE, SoundSource.PLAYERS, 0.8f, 0.7f);
            c.cooldown(DRAW_COOLDOWN);
        }
    };

    static final Ability SLICING_EXORCISM = new Ability() {
        @Override public void press(Cast c) {
            State s = st(c);
            float price = cost(c, SLICING_BLOOD) * (s.scale && c.has(MASTERY) ? 0.5f : 1f);
            if (!spendBlood(c, price)) return;
            ServerPlayer p = c.player;
            float dmg = SLICING_DAMAGE;
            int pierce = SLICING_PIERCE;
            if (s.stored > 0.2f) {
                dmg *= 1 + (SLICING_CONVERGED_MULT - 1) * s.stored;
                pierce += 2;
                consumeStored(c, s);
            }
            Vec3 look = p.getLookAngle();
            TechniqueProjectile.create(p, TechniqueProjectile.Kind.BLOOD_DISC, p.getEyePosition().add(look.scale(0.4)).add(0, -0.15, 0))
                    .damage(dmg).pierce(pierce).life(40).fire(look, SLICING_SPEED, 0.2f);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.level.playSound(null, p.blockPosition(), ModSounds.JJK_BLOOD_SHOT, SoundSource.PLAYERS, 1.0f, 1.0f + p.getRandom().nextFloat() * 0.2f);
            c.cooldown(SLICING_COOLDOWN);
        }
    };

    static float chargeSpeed(Cast c) {
        float speed = 1f / CONVERGENCE_CHARGE_TICKS;
        if (c.has(CONTROL)) speed *= CONVERGENCE_BLOOD_CONTROL_SPEED;
        if (c.has(MASTERY)) speed *= CONVERGENCE_MASTERY_SPEED;
        return speed;
    }

    /** One tick of compressing blood. Returns false when it can no longer be paid. */
    static boolean compress(Cast c, State s) {
        if (s.charging < 0) s.charging = 0;
        float add = Math.min(chargeSpeed(c), 1f - s.charging);
        float pay = cost(c, CONVERGENCE_BLOOD_PER_TICK) * (add > 0 ? 1f : 0.3f);
        if (!c.player.isCreative()) {
            if (c.data.resource < pay) { c.fail("jjk.quantumdeath.fail.blood"); return false; }
            c.data.resource -= pay;
        }
        s.charging += add;
        c.rt.charge = s.charging;
        ServerPlayer p = c.player;
        Vec3 h = hands(p);
        var r = p.getRandom();
        for (int i = 0; i < 3; i++) {
            Vec3 from = h.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(1.2 - s.charging * 0.5));
            Fx.moving(c.level, Fx.BLOOD_SMALL, from, h.subtract(from).scale(0.22));
        }
        c.level.sendParticles(Fx.BLOOD_SMALL, h.x, h.y, h.z, 1, 0.04 + 0.08 * s.charging, 0.04 + 0.08 * s.charging, 0.04 + 0.08 * s.charging, 0);
        if (c.rt.heldTicks % 6 == 1)
            c.level.playSound(null, p.blockPosition(), ModSounds.JJK_BLOOD_CONVERGE, SoundSource.PLAYERS, 0.5f, 0.6f + s.charging * 0.9f);
        if (s.charging >= 1f && c.rt.heldTicks % 10 == 0) c.info("jjk.quantumdeath.msg.converged");
        return true;
    }

    static void store(Cast c, State s, float amount) {
        s.stored = Math.max(s.stored, Math.min(1f, amount));
        s.storedUntil = c.now() + (c.has(MASTERY) ? CONVERGENCE_MASTERY_HOLD_TICKS : CONVERGENCE_HOLD_TICKS);
    }

    /** Using compressed blood leaves a residue with Mastery, so the next technique can follow at once. */
    static void consumeStored(Cast c, State s) {
        if (c.has(MASTERY)) {
            s.stored = Math.min(s.stored, 0.3f);
            s.storedUntil = c.now() + 60;
        } else s.stored = 0;
    }

    static final Ability CONVERGE = new Ability() {
        @Override public void press(Cast c) { st(c).charging = 0; }
        @Override public void hold(Cast c, int ticks) {
            State s = st(c);
            if (s.charging >= 0 && !compress(c, s)) Techniques.releaseHeld(c.player);
        }
        @Override public void release(Cast c, int ticks) {
            State s = st(c);
            if (s.charging > 0.05f) {
                store(c, s, s.charging);
                c.info("jjk.quantumdeath.msg.convergence_stored", Math.round(s.stored * 100));
                c.level.playSound(null, c.player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.8f, 0.5f);
            }
            s.charging = -1;
            c.cooldown(10);
        }
        @Override public void secondary(Cast c) {
            State s = st(c);
            if (s.stored > 0) { s.stored = 0; c.info("jjk.quantumdeath.msg.convergence_released"); }
        }
    };

    /** Piercing Blood (beam) and Supernova (orbs): fire at once from stored blood, or hold to compress first. */
    record Compressed(boolean piercing) implements Ability {
        float min(Cast c) {
            return piercing ? (c.has(MASTERY) ? PIERCING_MIN_CHARGE * 0.5f : PIERCING_MIN_CHARGE) : SUPERNOVA_MIN_CHARGE;
        }

        @Override public void press(Cast c) {
            State s = st(c);
            s.firedOnPress = false;
            if (s.stored >= min(c)) {
                float charge = s.stored;
                if (fire(c, charge)) { consumeStored(c, s); s.firedOnPress = true; }
                return;
            }
            s.charging = 0;
        }

        @Override public void hold(Cast c, int ticks) {
            State s = st(c);
            if (!s.firedOnPress && s.charging >= 0 && !compress(c, s)) Techniques.releaseHeld(c.player);
        }

        @Override public void release(Cast c, int ticks) {
            State s = st(c);
            if (!s.firedOnPress) {
                if (s.charging >= min(c)) fire(c, s.charging);
                else if (s.charging >= 0) c.fail("jjk.quantumdeath.fail.compression", Math.round(min(c) * 100));
            }
            s.charging = -1;
            s.firedOnPress = false;
        }

        @Override public void secondary(Cast c) { CONVERGE.secondary(c); }

        boolean fire(Cast c, float charge) {
            if (!spendBlood(c, cost(c, piercing ? PIERCING_BLOOD : SUPERNOVA_BLOOD))) return false;
            if (piercing) piercingBlood(c, charge);
            else supernova(c, charge);
            int cd = piercing ? PIERCING_COOLDOWN : SUPERNOVA_COOLDOWN;
            c.cooldown(c.has(MASTERY) ? cd * 3 / 4 : cd);
            return true;
        }
    }

    /** A hitscan line of blood compressed to the limit: instant, long, narrow, piercing. */
    static void piercingBlood(Cast c, float charge) {
        ServerPlayer p = c.player;
        ServerLevel level = c.level;
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = eye.add(dir.scale(PIERCING_RANGE));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        final Vec3 stop = end;
        List<LivingEntity> hit = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1),
                e -> JjkCombat.canHit(p, e) && e.getBoundingBox().inflate(0.25).clip(eye, stop).isPresent()));
        hit.sort((a, b) -> Double.compare(a.distanceToSqr(eye), b.distanceToSqr(eye)));
        int pierce = PIERCING_PIERCE + (c.has(MASTERY) ? 2 : 0);
        float dmg = PIERCING_DAMAGE + PIERCING_DAMAGE_PER_CHARGE * charge;
        Vec3 last = stop;
        for (int i = 0; i < hit.size() && i < pierce; i++) {
            LivingEntity e = hit.get(i);
            JjkCombat.hurt(p, e, p, JjkCombat.TECHNIQUE, dmg * (1 - 0.15f * i), true);
            JjkCombat.knock(e, dir, 0.5, 0.1);
            Vec3 at = e.getBoundingBox().getCenter();
            level.sendParticles(Fx.BLOOD_BRIGHT, at.x, at.y, at.z, 20, 0.2, 0.3, 0.2, 0.25);
            if (i == pierce - 1) last = at;
        }
        // A dense red line with a bright core, starting a little ahead so it never fills the caster's own view.
        Vec3 from = eye.add(dir.scale(2.2)).add(0, -0.25, 0);
        Fx.line(level, Fx.BLOOD_BRIGHT, from, last, 0.22);
        Fx.line(level, Fx.BLOOD, from.add(dir.scale(0.8)), last, 0.55);
        Fx.line(level, ParticleTypes.CRIT, from.add(dir.scale(1.5)), last, 1.6);
        level.sendParticles(ParticleTypes.CRIT, last.x, last.y, last.z, 12, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), last.x, last.y, last.z, 14, 0.2, 0.2, 0.2, 0.2);
        level.playSound(null, p.blockPosition(), ModSounds.JJK_PIERCING_BLOOD, SoundSource.PLAYERS, 1.6f, 1.0f);
        level.playSound(null, last.x, last.y, last.z, ModSounds.JJK_PIERCING_BLOOD, SoundSource.PLAYERS, 1.0f, 1.4f);
        JjkCombat.knock(p, dir.reverse(), 0.25, 0.02);
        p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        // Seen end-on by the caster, the beam reads as a red flash and a burst at the palms.
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new JjkNet.Flash(0x50c01010, 4));
        Vec3 h = hands(p);
        level.sendParticles(Fx.BLOOD_BRIGHT, h.x, h.y, h.z, 6, 0.1, 0.1, 0.1, 0.12);
    }

    /** Orbs of compressed blood circle Choso, then burst outward and detonate in a ring. */
    static void supernova(Cast c, float charge) {
        ServerPlayer p = c.player;
        int count = SUPERNOVA_ORBS + Math.round(SUPERNOVA_ORBS_PER_CHARGE * charge) + (c.has(MASTERY) ? SUPERNOVA_MASTERY_ORBS : 0);
        boolean homing = c.has(MASTERY);
        Vec3[] pos = new Vec3[count];
        Vec3[] dir = new Vec3[count];
        boolean[] done = new boolean[count];
        double[] travelled = new double[count];
        Map<LivingEntity, Integer> struck = new HashMap<>();
        float base = p.getYRot() * (float) Math.PI / 180f;
        c.level.playSound(null, p.blockPosition(), ModSounds.JJK_BLOOD_CONVERGE, SoundSource.PLAYERS, 1.0f, 1.2f);
        Ongoing.start(p.getUUID(), c.level, (level, age) -> {
            if (!p.isAlive() || p.level() != level) return false;
            Vec3 center = p.position().add(0, 1.1, 0);
            if (age < 12) {
                for (int i = 0; i < count; i++) {
                    double a = base + Math.PI * 2 * i / count + age * 0.25;
                    pos[i] = center.add(Math.cos(a) * 1.7, Math.sin(age * 0.5 + i) * 0.15, Math.sin(a) * 1.7);
                    dir[i] = new Vec3(Math.cos(a), 0.05, Math.sin(a));
                    level.sendParticles(Fx.BLOOD_SMALL, pos[i].x, pos[i].y, pos[i].z, 3, 0.06, 0.06, 0.06, 0);
                }
                return true;
            }
            if (age == 12) level.playSound(null, p.blockPosition(), ModSounds.JJK_BLOOD_SHOT, SoundSource.PLAYERS, 1.2f, 0.7f);
            boolean any = false;
            for (int i = 0; i < count; i++) {
                if (done[i]) continue;
                any = true;
                if (homing) {
                    LivingEntity near = null;
                    for (LivingEntity e : JjkCombat.around(p, pos[i], 4)) if (near == null || e.distanceToSqr(pos[i]) < near.distanceToSqr(pos[i])) near = e;
                    if (near != null) dir[i] = dir[i].scale(0.7).add(near.getBoundingBox().getCenter().subtract(pos[i]).normalize().scale(0.3)).normalize();
                }
                Vec3 next = pos[i].add(dir[i].scale(0.7));
                travelled[i] += 0.7;
                boolean blocked = !level.getBlockState(net.minecraft.core.BlockPos.containing(next)).getCollisionShape(level, net.minecraft.core.BlockPos.containing(next)).isEmpty();
                boolean touch = !JjkCombat.around(p, next, 0.7).isEmpty();
                pos[i] = next;
                level.sendParticles(Fx.BLOOD, next.x, next.y, next.z, 2, 0.08, 0.08, 0.08, 0);
                if (blocked || touch || travelled[i] >= SUPERNOVA_RANGE) {
                    done[i] = true;
                    detonate(p, level, next, struck);
                }
            }
            return any && age < 60;
        });
    }

    static void detonate(ServerPlayer p, ServerLevel level, Vec3 at, Map<LivingEntity, Integer> struck) {
        Fx.burst(level, Fx.BLOOD_BRIGHT, at, 14, 0.35);
        Fx.ring(level, Fx.BLOOD, at, 0.3, 10, 0.4);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, at.x, at.y, at.z, ModSounds.JJK_SUPERNOVA, SoundSource.PLAYERS, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.3f);
        for (LivingEntity e : JjkCombat.around(p, at, SUPERNOVA_ORB_RADIUS)) {
            int n = struck.getOrDefault(e, 0);
            if (n >= 2) continue; // at most two orbs count on one target
            struck.put(e, n + 1);
            JjkCombat.hurt(p, e, p, JjkCombat.TECHNIQUE, SUPERNOVA_ORB_DAMAGE, true);
            JjkCombat.knock(e, e.position().subtract(at), 0.4, 0.15);
        }
    }

    // ------------------------------------------------------------------------------------------------ Flowing Red Scale

    private static final Identifier SPEED = Quantumdeath.id("jjk.frs.speed"), ATTACK_SPEED = Quantumdeath.id("jjk.frs.attack_speed"),
            DAMAGE = Quantumdeath.id("jjk.frs.damage"), JUMP = Quantumdeath.id("jjk.frs.jump"), KNOCKBACK = Quantumdeath.id("jjk.frs.knockback"),
            STEP = Quantumdeath.id("jjk.frs.step");

    private static void modifier(ServerPlayer p, Holder<Attribute> attr, Identifier id, double value, AttributeModifier.Operation op) {
        AttributeInstance i = p.getAttribute(attr);
        if (i == null) return;
        i.removeModifier(id);
        if (value != 0) i.addTransientModifier(new AttributeModifier(id, value, op));
    }

    static void applyScale(ServerPlayer p, State s) {
        boolean on = s.scale || s.stack;
        modifier(p, Attributes.MOVEMENT_SPEED, SPEED, !on ? 0 : s.stack ? STACK_SPEED : FRS_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED, !on ? 0 : s.stack ? STACK_ATTACK_SPEED : FRS_ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE, !on ? 0 : s.stack ? STACK_DAMAGE : FRS_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
        modifier(p, Attributes.JUMP_STRENGTH, JUMP, !on ? 0 : s.stack ? STACK_JUMP : FRS_JUMP, AttributeModifier.Operation.ADD_VALUE);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, s.stack ? STACK_KNOCKBACK_RESIST : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(p, Attributes.STEP_HEIGHT, STEP, s.stack ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    static void endScale(Cast c, State s) {
        boolean crash = s.stack;
        s.scale = false;
        s.stack = false;
        applyScale(c.player, s);
        if (crash) {
            int t = c.has(MASTERY) ? STACK_CRASH_TICKS / 2 : STACK_CRASH_TICKS;
            c.player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, t, 1));
            c.player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 0));
            c.player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, t, 0));
            c.info("jjk.quantumdeath.msg.stack_crash");
        }
        c.level.playSound(null, c.player.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    record Toggle(boolean stack) implements Ability {
        @Override public boolean ignoresCooldown(Cast c) { State s = st(c); return stack ? s.stack : s.scale; }
        @Override public void press(Cast c) {
            State s = st(c);
            if (stack ? s.stack : s.scale) { endScale(c, s); c.cooldown(FRS_TOGGLE_COOLDOWN); return; }
            if (!spendBlood(c, cost(c, stack ? STACK_ACTIVATE_BLOOD : FRS_ACTIVATE_BLOOD))) return;
            if (s.stack && !stack) endScale(c, s);
            s.scale = !stack;
            s.stack = stack;
            applyScale(c.player, s);
            Vec3 face = c.player.getEyePosition();
            c.level.sendParticles(Fx.BLOOD_BRIGHT, face.x, face.y, face.z, 16, 0.25, 0.3, 0.25, 0.02);
            c.level.playSound(null, c.player.blockPosition(), ModSounds.JJK_BLOOD_CONVERGE, SoundSource.PLAYERS, 0.9f, stack ? 0.5f : 0.8f);
            c.level.playSound(null, c.player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, stack ? 1.4f : 1.0f);
            c.info(stack ? "jjk.quantumdeath.msg.stack_on" : "jjk.quantumdeath.msg.scale_on");
            c.cooldown(FRS_TOGGLE_COOLDOWN);
        }
    }

    // ------------------------------------------------------------------------------------------------ class hooks

    static final ClassHooks HOOKS = new ClassHooks() {
        @Override public float resourceMax(TechniqueData d) { return BLOOD_MAX; }

        @Override public void tick(Cast c) {
            ServerPlayer p = c.player;
            State s = st(c);
            float max = BLOOD_MAX;
            if (p.hasEffect(MobEffects.POISON)) p.removeEffect(MobEffects.POISON); // his blood is poison; his body is used to it
            if (s.stored > 0 && c.now() > s.storedUntil) s.stored = 0;
            boolean combat = c.now() - c.rt.lastCombat < 100;
            if (c.data.resource < max && p.getFoodData().getFoodLevel() >= 6 && !s.scale && !s.stack) {
                float regen = BLOOD_REGEN * (c.has(CONTROL) ? BLOOD_CONTROL_REGEN_MULT : 1f) * (combat ? 0.5f : 1f);
                c.data.resource = Math.min(max, c.data.resource + regen);
                p.causeFoodExhaustion(regen * BLOOD_REGEN_EXHAUSTION);
            }
            if (s.scale || s.stack) {
                float perSecond = s.stack ? STACK_BLOOD_PER_SECOND : FRS_BLOOD_PER_SECOND;
                if (c.has(MASTERY)) perSecond *= MASTERY_FRS_COST_MULT;
                float drain = cost(c, perSecond) / 20f;
                if (!p.isCreative()) c.data.resource -= drain;
                if (c.data.resource <= 1) { c.data.resource = Math.max(0, c.data.resource); endScale(c, s); }
                else if (p.tickCount % 4 == 0) {
                    Vec3 face = p.getEyePosition();
                    c.level.sendParticles(s.stack ? Fx.BLOOD_BRIGHT : Fx.BLOOD, face.x, face.y - 0.1, face.z, s.stack ? 3 : 1, 0.2, 0.25, 0.2, 0);
                }
            }
            if (p.tickCount % 20 == 0 && !p.isCreative()) {
                float frac = c.data.resource / max;
                if (frac < SEVERE_THRESHOLD) {
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, true, true));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, true));
                    if (p.tickCount % 100 == 0) p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, true, true));
                } else if (frac < ANEMIA_THRESHOLD) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, true));
            }
        }

        @Override public void afterHit(Cast c, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float taken, boolean technique) {
            if (technique && c.has(MASTERY)) c.data.resource = Math.min(BLOOD_MAX, c.data.resource + MASTERY_REFUND_ON_HIT * SLICING_BLOOD);
        }

        @Override public void indicators(Cast c, List<JjkNet.Indicator> out) {
            State s = st(c);
            if (s.stored > 0)
                out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.convergence", Math.round(s.stored * 100) + "%", s.stored, 0xff9a0a0a));
            if (s.stack) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.stack", "", -1, 0xffff3a2a));
            else if (s.scale) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.scale", "", -1, 0xffd0302a));
            float frac = c.data.resource / BLOOD_MAX;
            if (frac < SEVERE_THRESHOLD) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.severe", "", -1, 0xffff2020));
            else if (frac < ANEMIA_THRESHOLD) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.anemia", "", -1, 0xffe08040));
        }

        @Override public void cleanup(ServerPlayer p) {
            State s = Techniques.rt(p).state(State.class, State::new);
            s.scale = false;
            s.stack = false;
            applyScale(p, s);
        }
    };
}
