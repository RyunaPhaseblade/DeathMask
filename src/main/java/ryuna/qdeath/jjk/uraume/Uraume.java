package ryuna.qdeath.jjk.uraume;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.jjk.Ability;
import ryuna.qdeath.jjk.Cast;
import ryuna.qdeath.jjk.ClassHooks;
import ryuna.qdeath.jjk.Freeze;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static ryuna.qdeath.jjk.JjkBalance.Uraume.*;

/**
 * Uraume, Ice Formation: battlefield control. Every ice technique builds the shared {@link Freeze} up on its target
 * until it is frozen solid. Cursed ice blocks are always temporary (they melt on a scheduled tick) and only ever
 * replace air, so the world is never left cluttered or changed.
 */
public final class Uraume {
    private Uraume() {}

    public static final String ID = "uraume";
    public static final String FORMATION = "uraume:ice_formation", PROJECTILE = "uraume:ice_projectile", TOUCH = "uraume:freeze_touch",
            ICEFALL = "uraume:icefall", ENHANCED = "uraume:enhanced_icefall", WALL = "uraume:ice_wall", CALM = "uraume:frost_calm",
            MASTERY = "uraume:ice_mastery";

    static final class State {
        final Deque<BlockPos> walls = new ArrayDeque<>();
        LivingEntity lastTarget;
        int calmPulses;
    }

    private static State st(Cast c) { return c.state(State.class, State::new); }

    public static TechniqueClass define() {
        TechniqueClass c = new TechniqueClass(ID, 0x9fdcff, () -> Items.PACKED_ICE, null, 0, HOOKS);
        c.add(c.skill("ice_formation").tier(0).at(4, 0).icon(() -> Items.ICE).ability(FORMATION_ABILITY)
                .cooldown(() -> FORMATION_COOLDOWN).energy(() -> FORMATION_ENERGY));
        c.add(c.skill("ice_projectile").tier(1).at(2, 1).requires("ice_formation").icon(() -> JjkContent.ICE_SHARD).ability(SHARD)
                .cooldown(() -> SHARD_COOLDOWN).energy(() -> SHARD_ENERGY));
        c.add(c.skill("freeze_touch").tier(1).at(6, 1).requires("ice_formation").icon(() -> Items.SNOWBALL).ability(TOUCH_ABILITY)
                .cooldown(() -> TOUCH_COOLDOWN).energy(() -> TOUCH_ENERGY));
        c.add(c.skill("icefall").tier(2).at(2, 2).requires("ice_projectile").icon(() -> Items.PACKED_ICE).ability(ICEFALL_ABILITY)
                .cooldown(() -> ICEFALL_COOLDOWN).energy(() -> ICEFALL_ENERGY));
        c.add(c.skill("ice_wall").tier(2).at(6, 2).requires("freeze_touch").icon(() -> Items.BLUE_ICE).ability(WALL_ABILITY)
                .cooldown(() -> WALL_COOLDOWN).energy(() -> WALL_ENERGY));
        c.add(c.skill("enhanced_icefall").tier(3).at(2, 3).requires("icefall").icon(() -> Items.BLUE_ICE));
        c.add(c.skill("frost_calm").tier(4).at(6, 3).requires("ice_wall").icon(() -> Items.POWDER_SNOW_BUCKET).ability(CALM_ABILITY).hold()
                .cooldown(() -> CALM_COOLDOWN).energy(() -> CALM_ENERGY));
        c.add(c.skill("ice_mastery").tier(5).at(4, 4).requires("enhanced_icefall", "frost_calm").icon(() -> Items.NETHER_STAR));
        return c;
    }

    static int iceDuration(Cast c, int base) { return c.has(MASTERY) ? Math.round(base * MASTERY_DURATION_MULT) : base; }

    /** Places one temporary cursed ice block, only into air. */
    static boolean ice(Cast c, BlockPos pos, int ticks) {
        ServerLevel level = c.level;
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()) return false;
        level.setBlock(pos, JjkContent.CURSED_ICE.defaultBlockState(), 3);
        level.scheduleTick(pos, JjkContent.CURSED_ICE, ticks + level.getRandom().nextInt(20));
        return true;
    }

    static void freezeHit(Cast c, LivingEntity e, float damage, float freeze) {
        JjkCombat.hurt(c.player, e, c.player, JjkCombat.FROST, damage, true);
        Freeze.add(e, freeze, c.player);
        st(c).lastTarget = e;
    }

    /** Ice Formation: a cluster of cursed ice spikes erupts where you aim. */
    static final Ability FORMATION_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            if (!c.spendEnergy(FORMATION_ENERGY)) return;
            Vec3 at = JjkCombat.ground(c.level, JjkCombat.aimPoint(c.player, FORMATION_RANGE), 4);
            c.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.2, at.z, 12, 0.6, 0.1, 0.6, 0.01);
            Ongoing.start(c.player.getUUID(), c.level, (level, age) -> {
                if (age < 4) return true;
                BlockPos base = BlockPos.containing(at.x, at.y + 0.01, at.z);
                int d = iceDuration(c, 60);
                ice(c, base, d);
                ice(c, base.above(), d);
                for (Direction dir : Direction.Plane.HORIZONTAL) if (level.getRandom().nextBoolean()) ice(c, base.relative(dir), d);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y + 0.5, at.z, 20, 0.6, 0.6, 0.6, 0.1);
                level.playSound(null, base, ModSounds.JJK_ICE_FORM, SoundSource.PLAYERS, 1.0f, 1.1f);
                for (LivingEntity e : JjkCombat.around(c.player, at.add(0, 0.8, 0), 1.6)) {
                    freezeHit(c, e, FORMATION_DAMAGE, FORMATION_FREEZE);
                    e.push(0, 0.45, 0);
                    e.syncVelocity = true;
                }
                return false;
            });
            c.cooldown(FORMATION_COOLDOWN);
        }
    };

    static final Ability SHARD = new Ability() {
        @Override public void press(Cast c) {
            if (!c.spendEnergy(SHARD_ENERGY)) return;
            ServerPlayer p = c.player;
            Vec3 look = p.getLookAngle();
            TechniqueProjectile.create(p, TechniqueProjectile.Kind.ICE_SHARD, p.getEyePosition().add(look.scale(0.4)).add(0, -0.15, 0))
                    .damage(SHARD_DAMAGE).freeze(SHARD_FREEZE).mastery(c.has(MASTERY)).life(50).fire(look, SHARD_SPEED, 0.3f);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.level.playSound(null, p.blockPosition(), ModSounds.JJK_ICE_FORM, SoundSource.PLAYERS, 0.7f, 1.6f);
            c.cooldown(SHARD_COOLDOWN);
        }
    };

    static final Ability TOUCH_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            Entity e = JjkCombat.aimed(p, TOUCH_RANGE, 20, x -> JjkCombat.canHit(p, x));
            if (!(e instanceof LivingEntity target)) { c.fail("jjk.quantumdeath.fail.no_target"); return; }
            if (!c.spendEnergy(TOUCH_ENERGY)) return;
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            boolean finisher = Freeze.stage(target) >= 3;
            freezeHit(c, target, TOUCH_DAMAGE, finisher ? 100 : TOUCH_FREEZE);
            Vec3 at = target.getBoundingBox().getCenter();
            c.level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 30, 0.35, 0.5, 0.35, 0.05);
            c.level.sendParticles(Fx.FROST, at.x, at.y, at.z, 20, 0.3, 0.5, 0.3, 0.02);
            c.level.playSound(null, target.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
            c.cooldown(TOUCH_COOLDOWN);
        }
    };

    /** Icefall: a mass of cursed ice gathers above the target area and crashes down. Enhanced: bigger, more, colder. */
    static final Ability ICEFALL_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            if (!c.spendEnergy(ICEFALL_ENERGY)) return;
            ServerPlayer p = c.player;
            boolean enhanced = c.has(ENHANCED);
            Vec3 at = JjkCombat.ground(c.level, JjkCombat.aimPoint(p, ICEFALL_RANGE), 12);
            float radius = enhanced ? ENHANCED_RADIUS : ICEFALL_RADIUS;
            int telegraph = c.has(MASTERY) ? ICEFALL_TELEGRAPH * 3 / 5 : ICEFALL_TELEGRAPH;
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.level.playSound(null, BlockPos.containing(at), ModSounds.JJK_FROST_CALM, SoundSource.PLAYERS, 0.8f, 1.5f);
            Ongoing.start(p.getUUID(), c.level, (level, age) -> {
                if (age % 3 == 0) Fx.ring(level, ParticleTypes.SNOWFLAKE, at.add(0, 0.15, 0), radius, 16 + (int) radius * 4, 0);
                if (age == 0) {
                    int masses = enhanced ? 3 : 1;
                    for (int i = 0; i < masses; i++) {
                        Vec3 off = i == 0 ? Vec3.ZERO : new Vec3(Math.cos(i * 2.1) * radius * 0.55, 0, Math.sin(i * 2.1) * radius * 0.55);
                        Vec3 land = at.add(off);
                        float scale = i == 0 ? (enhanced ? 5f : 3f) : 2.2f;
                        boolean main = i == 0;
                        level.addFreshEntity(TechniqueBody.falling(p, TechniqueBody.Kind.ICE_MASS, land.add(0, 11 + i * 2, 0), land.add(0, scale * 0.3, 0),
                                telegraph + i * 3, scale, b -> { if (main) impact(c, level, at, radius, enhanced); else shatter(level, land, 2); }));
                    }
                }
                return age < telegraph;
            });
            c.cooldown(ICEFALL_COOLDOWN);
        }
    };

    static void impact(Cast c, ServerLevel level, Vec3 at, float radius, boolean enhanced) {
        shatter(level, at, radius);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        float dmg = enhanced ? ENHANCED_DAMAGE : ICEFALL_DAMAGE;
        float freeze = enhanced ? ENHANCED_FREEZE : ICEFALL_FREEZE;
        for (LivingEntity e : JjkCombat.around(c.player, at.add(0, 0.5, 0), radius)) {
            boolean shattered = c.has(MASTERY) && e.hasEffect(JjkContent.IMMOBILIZED);
            freezeHit(c, e, shattered ? dmg * 1.5f : dmg, freeze);
            JjkCombat.knock(e, e.position().subtract(at), 0.5, 0.25);
        }
        if (enhanced) {
            int d = iceDuration(c, 80);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                BlockPos ring = BlockPos.containing(at.x + Math.cos(a) * radius * 0.8, at.y + 0.01, at.z + Math.sin(a) * radius * 0.8);
                if (ice(c, ring, d) && i % 2 == 0) ice(c, ring.above(), d);
            }
        }
    }

    static void shatter(ServerLevel level, Vec3 at, float radius) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y + 0.6, at.z, (int) (20 + radius * 10),
                radius * 0.4, 0.6, radius * 0.4, 0.2);
        Fx.ring(level, ParticleTypes.SNOWFLAKE, at.add(0, 0.3, 0), 0.5, 24, 0.5 + radius * 0.08);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.4, at.z, 10, radius * 0.3, 0.2, radius * 0.3, 0.03);
    }

    /** Ice Wall: a temporary wall of cursed ice across your line of sight. Secondary: shatter your walls into shards. */
    static final Ability WALL_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            ServerPlayer p = c.player;
            Vec3 aim = JjkCombat.aimPoint(p, WALL_RANGE);
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1e-4) { c.fail("jjk.quantumdeath.fail.no_target"); return; }
            if (aim.distanceTo(p.position()) < 3) aim = p.position().add(flat.normalize().scale(3));
            if (!c.spendEnergy(WALL_ENERGY)) return;
            Vec3 base = JjkCombat.ground(c.level, aim, 6);
            Vec3 side = new Vec3(-flat.z, 0, flat.x).normalize();
            int width = WALL_WIDTH + (c.has(MASTERY) ? 2 : 0), height = WALL_HEIGHT + (c.has(MASTERY) ? 1 : 0);
            int d = iceDuration(c, WALL_DURATION);
            State s = st(c);
            int placed = 0;
            for (int w = -width / 2; w <= width / 2; w++) {
                Vec3 col = base.add(side.scale(w));
                BlockPos foot = BlockPos.containing(col.x, base.y + 0.01, col.z);
                for (int h = 0; h < height; h++) {
                    BlockPos pos = foot.above(h);
                    if (ice(c, pos, d)) { s.walls.addLast(pos); placed++; }
                }
                for (LivingEntity e : JjkCombat.around(p, Vec3.atBottomCenterOf(foot).add(0, 1, 0), 0.9)) {
                    freezeHit(c, e, 3, 30);
                    JjkCombat.knock(e, flat, 0.6, 0.2);
                }
            }
            while (s.walls.size() > 160) s.walls.removeFirst();
            if (placed == 0) c.fail("jjk.quantumdeath.fail.no_space");
            c.level.sendParticles(ParticleTypes.SNOWFLAKE, base.x, base.y + 1.5, base.z, 40, width * 0.3, 1.2, width * 0.3, 0.02);
            c.level.playSound(null, BlockPos.containing(base), ModSounds.JJK_ICE_FORM, SoundSource.PLAYERS, 1.4f, 0.7f);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            c.cooldown(WALL_COOLDOWN);
        }

        @Override public void secondary(Cast c) {
            State s = st(c);
            int n = 0;
            while (!s.walls.isEmpty()) {
                BlockPos pos = s.walls.removeFirst();
                if (!c.level.getBlockState(pos).is(JjkContent.CURSED_ICE)) continue;
                JjkContent.CursedIce.melt(c.level, pos);
                n++;
                for (LivingEntity e : JjkCombat.around(c.player, Vec3.atCenterOf(pos), 2)) freezeHit(c, e, WALL_SHATTER_DAMAGE / 3f, WALL_SHATTER_FREEZE / 3f);
            }
            if (n > 0) c.level.playSound(null, c.player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
            else c.fail("jjk.quantumdeath.fail.no_walls");
        }
    };

    /** Frost Calm: a freezing cloud poured out in front of you while held; caught targets freeze over in seconds. */
    static final Ability CALM_ABILITY = new Ability() {
        @Override public void press(Cast c) {
            if (!c.spendEnergy(c.has(MASTERY) ? CALM_ENERGY * 0.75f : CALM_ENERGY)) { Techniques.releaseHeld(c.player); return; }
            st(c).calmPulses = 0;
            c.level.playSound(null, c.player.blockPosition(), ModSounds.JJK_FROST_CALM, SoundSource.PLAYERS, 1.6f, 1.0f);
            c.cooldown(CALM_COOLDOWN);
            c.rt.charge = 1f;
        }

        @Override public void hold(Cast c, int ticks) {
            if (ticks > CALM_DURATION) { Techniques.releaseHeld(c.player); return; }
            c.rt.charge = 1f - ticks / (float) CALM_DURATION;
            ServerPlayer p = c.player;
            ServerLevel level = c.level;
            boolean m = c.has(MASTERY);
            double range = CALM_RANGE + (m ? MASTERY_CALM_RANGE : 0), angle = CALM_HALF_ANGLE + (m ? MASTERY_CALM_ANGLE : 0);
            Vec3 eye = p.getEyePosition().add(0, -0.2, 0);
            Vec3 dir = p.getLookAngle();
            var r = p.getRandom();
            for (int i = 0; i < 6; i++) {
                Vec3 spread = dir.add(new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).scale(Math.tan(Math.toRadians(angle)) * 0.5)).normalize();
                Fx.moving(level, i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, eye.add(dir.scale(0.6)), spread.scale(range / 14));
                if (i < 2) Fx.moving(level, Fx.FROST, eye.add(dir.scale(0.6)), spread.scale(range / 12));
            }
            if (ticks % 10 == 0) level.playSound(null, p.blockPosition(), ModSounds.JJK_FROST_CALM, SoundSource.PLAYERS, 0.6f, 1.3f);
            if (ticks % CALM_PULSE != 0) return;
            for (LivingEntity e : JjkCombat.cone(p, eye, dir, range, angle)) {
                freezeHit(c, e, CALM_DAMAGE_PER_PULSE, CALM_FREEZE_PER_PULSE);
                e.clearFire();
            }
            // The cold settles on the ground: water skins over (vanilla frosted ice, which melts back by itself), fires go out.
            for (int i = 0; i < 4; i++) {
                Vec3 sample = eye.add(dir.scale(2 + r.nextDouble() * (range - 2))).add(r.nextGaussian(), 0, r.nextGaussian());
                BlockPos ground = BlockPos.containing(JjkCombat.ground(level, sample, 4)).below();
                BlockState bs = level.getBlockState(ground);
                if (bs.getFluidState().is(FluidTags.WATER) && bs.getFluidState().isSource() && level.getBlockState(ground.above()).isAir())
                    level.setBlockAndUpdate(ground, Blocks.FROSTED_ICE.defaultBlockState());
                if (level.getBlockState(ground.above()).getBlock() instanceof BaseFireBlock) level.removeBlock(ground.above(), false);
            }
        }

        @Override public void release(Cast c, int ticks) { c.rt.charge = 0; }
    };

    static final ClassHooks HOOKS = new ClassHooks() {
        @Override public void tick(Cast c) {
            if (c.player.getTicksFrozen() > 0) c.player.setTicksFrozen(0); // immune to freezing, cursed or not
            State s = st(c);
            if (s.lastTarget != null && (!s.lastTarget.isAlive() || s.lastTarget.distanceToSqr(c.player) > 48 * 48)) s.lastTarget = null;
        }

        @Override public void indicators(Cast c, List<JjkNet.Indicator> out) {
            State s = st(c);
            if (s.lastTarget != null) {
                int stage = Freeze.stage(s.lastTarget);
                if (stage > 0 || Freeze.buildup(s.lastTarget) > 0)
                    out.add(new JjkNet.Indicator("jjk.quantumdeath.ind.freeze." + stage, s.lastTarget.getName().getString(),
                            Math.min(1f, Freeze.buildup(s.lastTarget) / 100f), 0xff9fdcff));
            }
        }

        @Override public void cleanup(ServerPlayer p) {
            // Walls melt on their own schedule; nothing else outlives the caster.
        }
    };
}
