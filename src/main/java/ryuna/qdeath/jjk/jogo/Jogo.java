package ryuna.qdeath.jjk.jogo;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.Ability;
import ryuna.qdeath.jjk.Cast;
import ryuna.qdeath.jjk.ClassHooks;
import ryuna.qdeath.jjk.Fx;
import ryuna.qdeath.jjk.JjkCombat;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.Ongoing;
import ryuna.qdeath.jjk.TechniqueClass;
import ryuna.qdeath.jjk.Techniques;
import ryuna.qdeath.jjk.entity.TechniqueBody;
import ryuna.qdeath.jjk.entity.TechniqueProjectile;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;

import static ryuna.qdeath.jjk.JjkBalance.Jogo.*;

/**
 * Jogo, Disaster Flames: explosive damage and area denial. Immune to fire. Volcanoes are temporary bodies that shell
 * nearby enemies; Volcano Control lets him aim and fire them; Mastery makes his fire set them off. Maximum: Meteor is
 * the one technique no one casts twice in a fight.
 */
public final class Jogo {
    private Jogo() {}

    public static final String ID = "jogo";
    public static final String FLAMES = "jogo:disaster_flames", BLAST = "jogo:fire_blast", BURST = "jogo:fire_burst", EMBER = "jogo:ember_insects",
            VOLCANO = "jogo:mini_volcano", CONTROL = "jogo:volcano_control", ERUPTION = "jogo:volcanic_eruption", METEOR = "jogo:maximum_meteor",
            MASTERY = "jogo:jogo_mastery";

    static final class State {
        final List<TechniqueBody> volcanoes = new ArrayList<>();
        final List<TechniqueProjectile> insects = new ArrayList<>();
        @Nullable LivingEntity focus;
    }

    private static State st(Cast c) { return c.state(State.class, State::new); }
    private static State st(ServerPlayer p) { return Techniques.rt(p).state(State.class, State::new); }

    public static TechniqueClass define() {
        TechniqueClass c = new TechniqueClass(ID, 0xff7a1a, () -> Items.MAGMA_BLOCK, null, 0, HOOKS);
        c.add(c.skill("disaster_flames").tier(0).at(4, 0).icon(() -> Items.BLAZE_POWDER).ability(FLAMETHROWER).hold()
                .cooldown(() -> FLAME_COOLDOWN).energy(() -> FLAME_ENERGY_PER_TICK * 20));
        c.add(c.skill("fire_blast").tier(1).at(2, 1).requires("disaster_flames").icon(() -> JjkContent.FIRE_BLAST).ability(FIRE_BLAST)
                .cooldown(() -> BLAST_COOLDOWN).energy(() -> BLAST_ENERGY));
        c.add(c.skill("mini_volcano").tier(1).at(6, 1).requires("disaster_flames").icon(() -> Items.MAGMA_BLOCK).ability(MINI_VOLCANO)
                .cooldown(() -> VOLCANO_COOLDOWN).energy(() -> VOLCANO_ENERGY));
        c.add(c.skill("fire_burst").tier(2).at(1, 2).requires("fire_blast").icon(() -> Items.FIRE_CHARGE).ability(FIRE_BURST)
                .cooldown(() -> BURST_COOLDOWN).energy(() -> BURST_ENERGY));
        c.add(c.skill("ember_insects").tier(3).at(3, 2).requires("fire_blast").icon(() -> JjkContent.EMBER_INSECT).ability(EMBER_INSECTS)
                .cooldown(() -> INSECT_COOLDOWN).energy(() -> INSECT_ENERGY));
        c.add(c.skill("volcano_control").tier(2).at(6, 2).requires("mini_volcano").icon(() -> Items.BASALT));
        c.add(c.skill("volcanic_eruption").tier(3).at(6, 3).requires("volcano_control").icon(() -> Items.LAVA_BUCKET).ability(ERUPTION_ABILITY)
                .cooldown(() -> ERUPTION_COOLDOWN).energy(() -> ERUPTION_ENERGY));
        c.add(c.skill("maximum_meteor").tier(4).cost(ryuna.qdeath.jjk.JjkBalance.Progression.TP_COST_MAXIMUM).level(18).at(4, 4)
                .requires("fire_burst", "ember_insects", "volcanic_eruption").icon(() -> Items.FIRE_CHARGE).ability(MAXIMUM_METEOR)
                .cooldown(() -> METEOR_COOLDOWN).energy(() -> -1));
        c.add(c.skill("jogo_mastery").tier(5).at(4, 5).requires("maximum_meteor").icon(() -> Items.NETHER_STAR));
        return c;
    }

    static void burn(Cast c, LivingEntity e, float damage, int seconds) {
        JjkCombat.hurt(c.player, e, c.player, JjkCombat.FIRE, damage, true);
        e.igniteForSeconds(seconds);
        st(c).focus = e;
    }

    /** Disaster Flames: a short cone of cursed fire poured from the palm while held. */
    static final Ability FLAMETHROWER = new Ability() {
        @Override public void hold(Cast c, int ticks) {
            if (!c.hasEnergy(FLAME_ENERGY_PER_TICK)) { c.fail("jjk.quantumdeath.fail.energy", 1, Math.round(c.data.energy)); Techniques.releaseHeld(c.player); return; }
            c.spendEnergy(FLAME_ENERGY_PER_TICK);
            ServerPlayer p = c.player;
            Vec3 eye = p.getEyePosition().add(0, -0.25, 0);
            Vec3 dir = p.getLookAngle();
            var r = p.getRandom();
            c.rt.charge = Math.min(1f, ticks / 40f);
            for (int i = 0; i < 4; i++) {
                Vec3 spread = dir.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.18)).normalize();
                Fx.moving(c.level, i == 3 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, eye.add(dir.scale(0.7)), spread.scale(FLAME_RANGE / 13));
            }
            if (ticks % 8 == 1) c.level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.8f, 0.7f);
            if (ticks % FLAME_PULSE == 0) {
                for (LivingEntity e : JjkCombat.cone(p, eye, dir, FLAME_RANGE, FLAME_HALF_ANGLE)) burn(c, e, FLAME_DAMAGE, 3);
                if (ticks % (FLAME_PULSE * 2) == 0) react(p, eye.add(dir.scale(FLAME_RANGE * 0.6)));
            }
        }
        @Override public void release(Cast c, int ticks) { if (ticks > 2) c.cooldown(FLAME_COOLDOWN); }
    };

    static final Ability FIRE_BLAST = new Ability() {
        @Override public void press(Cast c) {
            if (!c.spendEnergy(BLAST_ENERGY)) return;
            ServerPlayer p = c.player;
            Vec3 look = p.getLookAngle();
            TechniqueProjectile.create(p, TechniqueProjectile.Kind.FIRE_BLAST, p.getEyePosition().add(look.scale(0.5)).add(0, -0.2, 0))
                    .damage(BLAST_DAMAGE).life(60).fire(look, BLAST_SPEED, 0.4f);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 1.1f);
            c.cooldown(BLAST_COOLDOWN);
        }
    };

    /** Fire Burst: the air at the aimed point detonates. */
    static final Ability FIRE_BURST = new Ability() {
        @Override public void press(Cast c) {
            float price = c.has(MASTERY) ? BURST_ENERGY * MASTERY_BURST_COST_MULT : BURST_ENERGY;
            if (!c.spendEnergy(price)) return;
            ServerPlayer p = c.player;
            Vec3 at = JjkCombat.aimPoint(p, BURST_RANGE);
            ServerLevel level = c.level;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            Fx.burst(level, ParticleTypes.FLAME, at, 40, 0.45);
            Fx.ring(level, ParticleTypes.FLAME, at, 0.5, 24, 0.5);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.6, 0.4, 0.6, 0.1);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.9f);
            level.playSound(null, at.x, at.y, at.z, ModSounds.JJK_ERUPTION, SoundSource.PLAYERS, 0.8f, 1.6f);
            for (LivingEntity e : JjkCombat.around(p, at, BURST_RADIUS)) {
                float falloff = 1f - 0.5f * (float) (e.getBoundingBox().getCenter().distanceTo(at) / BURST_RADIUS);
                burn(c, e, BURST_DAMAGE * Math.max(0.5f, falloff), 5);
                JjkCombat.knock(e, e.position().subtract(at), 0.9, 0.35);
            }
            react(p, at);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.cooldown(BURST_COOLDOWN);
        }
    };

    static TechniqueProjectile insect(ServerPlayer owner, Vec3 at, @Nullable LivingEntity target, boolean mastery) {
        TechniqueProjectile bug = TechniqueProjectile.create(owner, TechniqueProjectile.Kind.EMBER_INSECT, at)
                .damage(INSECT_DAMAGE).life(INSECT_LIFE).seek(target).mastery(mastery);
        bug.setDeltaMovement(new Vec3(owner.getRandom().nextGaussian() * 0.1, 0.15, owner.getRandom().nextGaussian() * 0.1));
        owner.level().addFreshEntity(bug);
        st(owner).insects.add(bug);
        return bug;
    }

    static final Ability EMBER_INSECTS = new Ability() {
        @Override public void press(Cast c) {
            State s = st(c);
            s.insects.removeIf(Entity::isRemoved);
            if (s.insects.size() >= MAX_INSECTS) { c.fail("jjk.quantumdeath.fail.insects"); return; }
            if (!c.spendEnergy(INSECT_ENERGY)) return;
            ServerPlayer p = c.player;
            Entity aimed = JjkCombat.aimed(p, 32, 6, e -> JjkCombat.canHit(p, e));
            LivingEntity target = aimed instanceof LivingEntity l ? l : null;
            int n = Math.min(MAX_INSECTS - s.insects.size(), c.has(MASTERY) ? MASTERY_INSECTS : INSECTS);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                insect(p, p.position().add(Math.cos(a) * 0.8, 1.4, Math.sin(a) * 0.8), target, c.has(MASTERY));
            }
            c.level.playSound(null, p.blockPosition(), ModSounds.JJK_EMBER_BUZZ, SoundSource.PLAYERS, 1.2f, 0.8f);
            c.level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.4, p.getZ(), 16, 0.6, 0.3, 0.6, 0.02);
            c.cooldown(INSECT_COOLDOWN);
        }
    };

    static int maxVolcanoes(Cast c) { return c.has(CONTROL) ? CONTROL_MAX_VOLCANOES : MAX_VOLCANOES; }

    static final Ability MINI_VOLCANO = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            Vec3 aim = JjkCombat.aimPoint(p, VOLCANO_RANGE);
            Vec3 at = JjkCombat.ground(c.level, aim, 8);
            if (!c.level.getBlockState(BlockPos.containing(at).below()).isSolid() && !c.level.getBlockState(BlockPos.containing(at.x, at.y - 0.05, at.z)).isSolid()) {
                c.fail("jjk.quantumdeath.fail.no_ground");
                return;
            }
            if (!c.spendEnergy(VOLCANO_ENERGY)) return;
            State s = st(c);
            s.volcanoes.removeIf(Entity::isRemoved);
            while (s.volcanoes.size() >= maxVolcanoes(c)) s.volcanoes.removeFirst().crumble(c.level);
            TechniqueBody v = TechniqueBody.volcano(p, at, c.has(MASTERY) ? MASTERY_VOLCANO_LIFE : VOLCANO_LIFE);
            v.controlled = c.has(CONTROL);
            v.mastery = c.has(MASTERY);
            c.level.addFreshEntity(v);
            s.volcanoes.add(v);
            c.level.playSound(null, BlockPos.containing(at), ModSounds.JJK_VOLCANO_RUMBLE, SoundSource.PLAYERS, 1.4f, 1.0f);
            c.level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 10, 0.5, 0.2, 0.5, 0.1);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.cooldown(c.has(MASTERY) ? VOLCANO_COOLDOWN / 2 : VOLCANO_COOLDOWN);
        }

        /** Volcano Control: every volcano fires a salvo at the crosshair. */
        @Override public void secondary(Cast c) { salvo(c); }
    };

    static void salvo(Cast c) {
        if (!c.has(CONTROL)) { c.fail("jjk.quantumdeath.fail.needs", net.minecraft.network.chat.Component.translatable("jjk.quantumdeath.skill.jogo.volcano_control")); return; }
        if (c.cooldownLeft("jogo:salvo") > 0) { c.fail("jjk.quantumdeath.fail.cooldown", Component.translatable("jjk.quantumdeath.salvo"), String.format("%.1f", c.cooldownLeft("jogo:salvo") / 20f)); return; }
        State s = st(c);
        s.volcanoes.removeIf(Entity::isRemoved);
        if (s.volcanoes.isEmpty()) { c.fail("jjk.quantumdeath.fail.no_volcano"); return; }
        Vec3 at = JjkCombat.aimPoint(c.player, 48);
        for (TechniqueBody v : s.volcanoes)
            for (int i = 0; i < 3; i++) {
                int delay = i * 4;
                Ongoing.start(c.player.getUUID(), c.level, (level, age) -> {
                    if (age < delay) return true;
                    if (!v.isRemoved()) bomb(v, c.player, at.add(level.getRandom().nextGaussian() * 1.2, 0, level.getRandom().nextGaussian() * 1.2));
                    return false;
                });
            }
        c.cooldown("jogo:salvo", SALVO_COOLDOWN);
    }

    /** Lobs a magma bomb from the crater to {@code target} on a ballistic arc. */
    static void bomb(TechniqueBody v, ServerPlayer owner, Vec3 target) {
        Vec3 crater = v.position().add(0, 2.2, 0);
        Vec3 d = target.subtract(crater);
        double t = Math.max(10, Math.min(30, d.horizontalDistance() / 0.8));
        double g = 0.05;
        Vec3 vel = new Vec3(d.x / t * 1.06, d.y / t + 0.5 * g * t, d.z / t * 1.06);
        TechniqueProjectile b = TechniqueProjectile.create(owner, TechniqueProjectile.Kind.MAGMA_BOMB, crater).damage(BOMB_DAMAGE).life(80);
        b.setDeltaMovement(vel);
        owner.level().addFreshEntity(b);
        ServerLevel level = (ServerLevel) owner.level();
        level.sendParticles(ParticleTypes.LAVA, crater.x, crater.y, crater.z, 4, 0.2, 0.1, 0.2, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, crater.x, crater.y, crater.z, 4, 0.2, 0.1, 0.2, 0.05);
        level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    /** Per-tick behaviour of a volcano (called by the body itself). */
    public static void tickVolcano(TechniqueBody v, ServerPlayer owner, ServerLevel level) {
        if (v.age < 15) {
            if (v.age % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, v.getX(), v.getY() + 0.5, v.getZ(), 3, 0.5, 0.2, 0.5, 0.02);
            return;
        }
        if (v.age % 10 == 0)
            for (LivingEntity e : JjkCombat.around(owner, v.position().add(0, 0.8, 0), 2.5)) {
                e.igniteForSeconds(3);
                JjkCombat.hurt(owner, e, v, JjkCombat.FIRE, 1, false);
            }
        if (v.age % 40 == 0) level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.JJK_VOLCANO_RUMBLE, SoundSource.PLAYERS, 0.5f, 1.2f);
        if (v.mastery && v.age % MASTERY_VOLCANO_INSECT_INTERVAL == 0 && st(owner).insects.stream().filter(e -> !e.isRemoved()).count() < MAX_INSECTS)
            insect(owner, v.position().add(0, 2.3, 0), null, true);
        if (--v.timer > 0) return;
        LivingEntity target = volcanoTarget(v, owner);
        if (target == null) { v.timer = 5; return; }
        bomb(v, owner, target.getBoundingBox().getCenter());
        v.timer = VOLCANO_FIRE_INTERVAL;
    }

    static @Nullable LivingEntity volcanoTarget(TechniqueBody v, ServerPlayer owner) {
        double range = v.controlled ? VOLCANO_CONTROL_RANGE : VOLCANO_ATTACK_RANGE;
        if (v.controlled) {
            Entity aimed = JjkCombat.aimed(owner, 48, 4, e -> JjkCombat.canHit(owner, e));
            if (aimed instanceof LivingEntity l && l.distanceToSqr(v) < range * range) return l;
            LivingEntity focus = st(owner).focus;
            if (focus != null && focus.isAlive() && focus.distanceToSqr(v) < range * range && JjkCombat.canHit(owner, focus)) return focus;
        }
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(v.position(), v.position()).inflate(range),
                e -> JjkCombat.canHit(owner, e) && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == owner) || e.getLastHurtByMob() == owner))) {
            double d = e.distanceToSqr(v);
            if (d < bestD && JjkCombat.los(owner.level(), v.position().add(0, 2.2, 0), e.getEyePosition())) { bestD = d; best = e; }
        }
        return best;
    }

    /** Jogo Mastery: his fire near one of his volcanoes makes it fire at once. */
    public static void react(ServerPlayer owner, Vec3 at) {
        if (!Techniques.has(owner, MASTERY)) return;
        for (TechniqueBody v : st(owner).volcanoes)
            if (!v.isRemoved() && v.age >= 15 && v.timer > 3 && v.position().distanceToSqr(at) < MASTERY_REACT_RANGE * MASTERY_REACT_RANGE) {
                v.timer = 1;
                ((ServerLevel) owner.level()).sendParticles(ParticleTypes.LAVA, v.getX(), v.getY() + 2.2, v.getZ(), 6, 0.3, 0.2, 0.3, 0.1);
            }
    }

    /** Volcanic Eruption: an owned volcano near the crosshair erupts and is spent; otherwise the ground itself erupts. */
    static final Ability ERUPTION_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            State s = st(c);
            s.volcanoes.removeIf(Entity::isRemoved);
            Vec3 aim = JjkCombat.aimPoint(p, Math.max(ERUPTION_RANGE, 30));
            TechniqueBody volcano = null;
            for (TechniqueBody v : s.volcanoes) if (v.age >= 15 && v.position().distanceTo(aim) < 6 && (volcano == null || v.position().distanceTo(aim) < volcano.position().distanceTo(aim))) volcano = v;
            if (volcano == null && aim.distanceTo(p.position()) > ERUPTION_RANGE) aim = p.getEyePosition().add(p.getLookAngle().scale(ERUPTION_RANGE));
            if (!c.spendEnergy(ERUPTION_ENERGY)) return;
            if (volcano != null) volcanoEruption(c, volcano);
            else groundEruption(c, JjkCombat.ground(c.level, aim, 16));
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.cooldown(ERUPTION_COOLDOWN);
        }
        @Override public void secondary(Cast c) { salvo(c); }
    };

    static void volcanoEruption(Cast c, TechniqueBody v) {
        ServerPlayer p = c.player;
        v.life = v.age + 70;
        v.timer = 1000;
        c.level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.JJK_ERUPTION, SoundSource.PLAYERS, 2.0f, 0.8f);
        Ongoing.start(p.getUUID(), c.level, (level, age) -> {
            if (v.isRemoved()) return false;
            Vec3 top = v.position().add(0, 2.2, 0);
            for (int y = 0; y < 6; y++) level.sendParticles(ParticleTypes.FLAME, top.x, top.y + y * 0.8, top.z, 6, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 4, 0.3, 0.3, 0.3, 0.2);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.x, top.y + 2, top.z, 3, 0.4, 0.4, 0.4, 0.05);
            if (age % 10 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = level.getRandom().nextDouble() * Math.PI * 2, r = 2 + level.getRandom().nextDouble() * 6;
                    bomb(v, p, v.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }
                for (LivingEntity e : JjkCombat.around(p, v.position().add(0, 1, 0), ERUPTION_RADIUS)) {
                    burn(c, e, ERUPTION_VOLCANO_WAVE_DAMAGE, 5);
                    e.push(0, 0.5, 0);
                    e.syncVelocity = true;
                }
                level.playSound(null, top.x, top.y, top.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
            }
            if (age >= 60) { v.crumble(level); return false; }
            return true;
        });
    }

    static void groundEruption(Cast c, Vec3 at) {
        ServerPlayer p = c.player;
        c.level.playSound(null, BlockPos.containing(at), ModSounds.JJK_VOLCANO_RUMBLE, SoundSource.PLAYERS, 1.6f, 0.7f);
        Ongoing.start(p.getUUID(), c.level, (level, age) -> {
            if (age < ERUPTION_TELEGRAPH) {
                if (age % 2 == 0) {
                    Fx.ring(level, ParticleTypes.LARGE_SMOKE, at.add(0, 0.1, 0), ERUPTION_RADIUS * (age / (float) ERUPTION_TELEGRAPH), 14, 0);
                    level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.1, at.z, 3, ERUPTION_RADIUS * 0.4, 0, ERUPTION_RADIUS * 0.4, 0);
                }
                return true;
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
            for (int y = 0; y < 10; y++) level.sendParticles(ParticleTypes.FLAME, at.x, at.y + y * 0.7, at.z, 14, 0.8, 0.3, 0.8, 0.08);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1, at.z, 20, 1.5, 0.5, 1.5, 0.3);
            Fx.ring(level, ParticleTypes.FLAME, at.add(0, 0.3, 0), 1, 32, 0.6);
            level.playSound(null, at.x, at.y, at.z, ModSounds.JJK_ERUPTION, SoundSource.PLAYERS, 2.0f, 1.0f);
            for (LivingEntity e : JjkCombat.around(p, at.add(0, 1, 0), ERUPTION_RADIUS)) {
                burn(c, e, ERUPTION_DAMAGE, 6);
                e.push(0, 1.0, 0);
                e.syncVelocity = true;
            }
            react(p, at);
            return false;
        });
    }

    // ------------------------------------------------------------------------------------------------ Maximum: Meteor

    static final Ability MAXIMUM_METEOR = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            float max = c.maxEnergy();
            if (!p.isCreative() && c.data.energy < max * METEOR_MIN_ENERGY_FRACTION) {
                c.fail("jjk.quantumdeath.fail.meteor_energy", Math.round(max * METEOR_MIN_ENERGY_FRACTION), Math.round(c.data.energy));
                return;
            }
            if (!p.isCreative()) c.data.energy = 0;
            Vec3 target = JjkCombat.ground(c.level, JjkCombat.aimPoint(p, METEOR_RANGE), 40);
            // It comes down out of the sky beyond the target, toward the caster: in plain view of everyone in front.
            Vec3 beyond = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize().scale(34);
            Vec3 from = target.add(beyond).add(0, 30, 0);
            ServerLevel level = c.level;
            Component title = Component.translatable("jjk.quantumdeath.meteor.title").withColor(0xff5a1a);
            Component subtitle = Component.translatable("jjk.quantumdeath.meteor.subtitle").withColor(0xffc080);
            for (ServerPlayer near : level.getPlayers(q -> q.distanceToSqr(target) < 110 * 110 || q == p)) {
                near.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
                near.connection.send(new ClientboundSetTitleTextPacket(title));
                near.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
            level.playSound(null, target.x, target.y, target.z, ModSounds.JJK_METEOR_FALL, SoundSource.PLAYERS, 6.0f, 1.0f);
            level.playSound(null, p.blockPosition(), ModSounds.JJK_METEOR_FALL, SoundSource.PLAYERS, 2.0f, 1.0f);
            level.addFreshEntity(TechniqueBody.falling(p, TechniqueBody.Kind.METEOR, from, target.add(0, 1, 0), METEOR_FALL_TICKS, METEOR_SCALE,
                    b -> meteorImpact(p, level, target)));
            Ongoing.start(p.getUUID(), level, (l, age) -> {
                if (age % 4 == 0) {
                    Fx.ring(l, ParticleTypes.FLAME, target.add(0, 0.2, 0), METEOR_RADIUS, 48, 0);
                    Fx.ring(l, ParticleTypes.SMALL_FLAME, target.add(0, 0.2, 0), METEOR_RADIUS * (age / (float) METEOR_FALL_TICKS), 24, 0);
                }
                return age < METEOR_FALL_TICKS;
            });
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            p.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
            c.cooldown(METEOR_COOLDOWN);
        }
    };

    public static void meteorTrail(TechniqueBody b, ServerLevel level, float t) {
        float s = b.scale();
        Fx.far(level, ParticleTypes.LARGE_SMOKE, b.position(), 6, s * 0.5, 0.05);
        Fx.far(level, ParticleTypes.FLAME, b.position(), 10, s * 0.45, 0.1);
        if (b.age % 3 == 0) Fx.far(level, ParticleTypes.LAVA, b.position(), 3, s * 0.4, 0.2);
        if (b.age % 15 == 0) level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 4.0f, 0.3f);
    }

    static void meteorImpact(ServerPlayer p, ServerLevel level, Vec3 at) {
        for (int i = 0; i < 4; i++) Fx.far(level, ParticleTypes.EXPLOSION_EMITTER, at.add(level.getRandom().nextGaussian() * 3, 1, level.getRandom().nextGaussian() * 3), 1, 0, 0);
        Fx.far(level, ParticleTypes.LAVA, at.add(0, 1, 0), 40, 4, 0.4);
        Fx.far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.add(0, 2, 0), 20, 3, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.JJK_METEOR_IMPACT, SoundSource.PLAYERS, 8.0f, 1.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0f, 0.5f);
        for (LivingEntity e : JjkCombat.around(p, at.add(0, 1, 0), METEOR_RADIUS)) {
            float t = (float) Math.min(1, e.position().distanceTo(at) / METEOR_RADIUS);
            JjkCombat.hurt(p, e, p, JjkCombat.FIRE, METEOR_DAMAGE_CENTER + (METEOR_DAMAGE_EDGE - METEOR_DAMAGE_CENTER) * t, true);
            e.igniteForSeconds(10);
            JjkCombat.knock(e, e.position().subtract(at), 2.0 * (1 - t) + 0.5, 0.8 * (1 - t) + 0.3);
        }
        for (ServerPlayer near : level.getPlayers(q -> q.distanceToSqr(at) < 64 * 64))
            ServerPlayNetworking.send(near, new JjkNet.Flash(0xc0ff7a1a, 14));
        // The shockwave: a ring of fire racing outward.
        Ongoing.start(p.getUUID(), level, (l, age) -> {
            double r = 1 + age * 1.3;
            Fx.ring(l, ParticleTypes.FLAME, at.add(0, 0.4, 0), r, (int) (12 + r * 5), 0.05);
            Fx.ring(l, ParticleTypes.CLOUD, at.add(0, 0.8, 0), r, (int) (6 + r * 2), 0.05);
            return age < 10;
        });
        if (METEOR_BREAKS_BLOCKS && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            // Blocks only: the meteor's own damage was dealt above, so the explosion hurts nobody.
            ExplosionDamageCalculator blocksOnly = new ExplosionDamageCalculator() {
                @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
                @Override public float getKnockbackMultiplier(Entity entity) { return 0; }
            };
            level.explode(null, null, blocksOnly, at.x, at.y, at.z, METEOR_BLOCK_POWER, true, Level.ExplosionInteraction.MOB);
        }
    }

    // ------------------------------------------------------------------------------------------------ class hooks

    static final ClassHooks HOOKS = new ClassHooks() {
        @Override public void tick(Cast c) {
            if (c.player.isOnFire()) c.player.clearFire();
            State s = st(c);
            if (c.player.tickCount % 20 == 0) {
                s.volcanoes.removeIf(Entity::isRemoved);
                s.insects.removeIf(Entity::isRemoved);
                if (s.focus != null && (!s.focus.isAlive() || s.focus.distanceToSqr(c.player) > 64 * 64)) s.focus = null;
            }
        }

        @Override public void afterHit(Cast c, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float taken, boolean technique) {
            st(c).focus = target;
        }

        @Override public void indicators(Cast c, List<JjkNet.Indicator> out) {
            State s = st(c);
            long volcanoes = s.volcanoes.stream().filter(v -> !v.isRemoved()).count();
            if (c.has(VOLCANO)) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.volcanoes", volcanoes + "/" + maxVolcanoes(c), -1, 0xffff7a1a));
            long insects = s.insects.stream().filter(v -> !v.isRemoved()).count();
            if (insects > 0) out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.insects", String.valueOf(insects), -1, 0xffffb040));
            if (c.has(METEOR)) {
                float need = c.maxEnergy() * METEOR_MIN_ENERGY_FRACTION;
                if (c.cooldownLeft(METEOR) == 0) out.add(new JjkNet.Indicator(c.data.energy >= need ? "jjk.quantumdeath.ind.meteor_ready" : "jjk.quantumdeath.ind.meteor_charging",
                        Math.round(Math.min(1f, c.data.energy / need) * 100) + "%", Math.min(1f, c.data.energy / need), 0xffff5a1a));
            }
        }

        @Override public void cleanup(ServerPlayer p) {
            State s = st(p);
            for (TechniqueBody v : s.volcanoes) if (!v.isRemoved()) v.discard();
            for (TechniqueProjectile i : s.insects) if (!i.isRemoved()) i.discard();
            s.volcanoes.clear();
            s.insects.clear();
        }
    };
}
