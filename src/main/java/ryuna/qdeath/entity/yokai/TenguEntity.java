package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Companions;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tengu: a guardian of mountains and tall groves. It only fights whoever attacks it: the feather fan is raised for
 * 1.5 s, then a gust hits (4 damage and a strong push). Pact: run the visible route of three high points it marks for
 * you, in order, within a minute, then offer a feather. Bonded, it supports its owner with gusts; it never lends flight.
 */
public class TenguEntity extends AbstractYokai implements Companions.Special {
    public static final int ANIM_WINDUP = 1, ANIM_GUST = 2, ANIM_FLAP = 3;
    private static final DustParticleOptions ROUTE = new DustParticleOptions(0xe83a2a, 1.4f);
    private static final DustParticleOptions ROUTE_DONE = new DustParticleOptions(0xf0e6c8, 1.0f);
    private int windup, cooldown, gustCooldown, signCooldown = 200;

    private static final class Route {
        final List<BlockPos> points = new ArrayList<>();
        int next;
        long until;
    }
    private final Map<UUID, Route> routes = new HashMap<>();

    public TenguEntity(EntityType<? extends TenguEntity> type, Level level) {
        super(type, level, Species.TENGU);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Tengu.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, YokaiBalance.Tengu.FAN_DAMAGE)
                .add(Attributes.SAFE_FALL_DISTANCE, 32);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new YokaiGoals.Defend(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override protected void serverTick(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        if (gustCooldown > 0) gustCooldown--;
        tickRoutes(level);
        LivingEntity target = getTarget();
        if (windup > 0) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 10f, 30f);
            if (--windup == 0) fan(level);
            return;
        }
        if (target != null && target.isAlive()) {
            if (distanceToSqr(target) > 12) {
                getNavigation().moveTo(target, 1.2);
                if (onGround() && random.nextInt(60) == 0) flap();
            } else if (cooldown == 0) {
                windup = YokaiBalance.Tengu.WINDUP;
                preparing(true);
                playAnim(ANIM_WINDUP, windup);
                playSound(ModSounds.TENGU_WINDUP, 1.3f, 1.0f);
            }
        }
        if (core.bonded()) {
            ServerPlayer owner = core.ownerPlayer();
            if (owner != null && core.order() == YokaiCore.Order.DEFEND && owner.hurtTime > 0 && gustCooldown == 0) special(owner);
        } else if (--signCooldown <= 0) {
            signCooldown = 300 + random.nextInt(300);
            if (level.getNearestPlayer(this, 24) != null) {
                playSound(ModSounds.TENGU_WINGS, 1.0f, 1.0f);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.FEATHER), getX(), getY() + 2.2, getZ(), 6, 0.6, 0.3, 0.6, 0.02);
                Bestiary.clueNear(level, position(), 24, species(), random.nextBoolean() ? "gust" : "feathers");
                if (onGround()) flap();
            }
        }
    }

    private void flap() {
        playAnim(ANIM_FLAP, 16);
        setDeltaMovement(getDeltaMovement().add(0, 0.55, 0));
        playSound(ModSounds.TENGU_WINGS, 0.8f, 1.2f);
    }

    /** The fan: a 60-degree gust in front, 5 blocks long. It hurts only what it is fighting. */
    private void fan(ServerLevel level) {
        preparing(false);
        cooldown = YokaiBalance.Tengu.COOLDOWN;
        playAnim(ANIM_GUST, 12);
        playSound(ModSounds.TENGU_GUST, 1.5f, 1.0f);
        Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
        level.sendParticles(ParticleTypes.GUST, getX() + look.x * 2, getY() + 1.2, getZ() + look.z * 2, 1, 0, 0, 0, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), e -> e != this && e.isAlive())) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > 5 || to.normalize().dot(look) < 0.5) continue;
            if (e != getTarget() && !(core.bonded() && core.defendTarget(e))) continue;
            e.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Tengu.FAN_DAMAGE);
            e.push(look.x * YokaiBalance.Tengu.FAN_KNOCKBACK, 0.45, look.z * YokaiBalance.Tengu.FAN_KNOCKBACK);
            e.syncVelocity = true;
        }
    }

    /** Support gust: enemies around the owner are blown away (no flight is ever given to the owner). */
    @Override public void special(ServerPlayer owner) {
        if (gustCooldown > 0 || !(level() instanceof ServerLevel level)) return;
        gustCooldown = YokaiBalance.Tengu.GUST_COOLDOWN;
        playAnim(ANIM_GUST, 12);
        playSound(ModSounds.TENGU_GUST, 1.5f, 1.1f);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, owner.getX(), owner.getY() + 0.5, owner.getZ(), 1, 0, 0, 0, 0);
        for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(6), m -> m.isAlive() && m != this
                && (m instanceof Enemy && !(m instanceof YokaiCreature y && y.yokai().bonded()) || m.getTarget() == owner))) {
            Vec3 d = m.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            m.push(d.x * 1.4, 0.5, d.z * 1.4);
            m.syncVelocity = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ the route

    private void tickRoutes(ServerLevel level) {
        routes.entrySet().removeIf(e -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            Route r = e.getValue();
            if (p == null || p.level() != level) return true;
            if (level.getGameTime() > r.until) {
                p.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tengu_failed").withStyle(ChatFormatting.GRAY));
                return true;
            }
            if (tickCount % 4 == 0) for (int i = 0; i < r.points.size(); i++) {
                Vec3 c = Vec3.atBottomCenterOf(r.points.get(i)).add(0, 0.6, 0);
                DustParticleOptions dust = i < r.next ? ROUTE_DONE : ROUTE;
                for (int k = 0; k < 6; k++) {
                    double a = (tickCount * 0.15 + k * Math.PI / 3);
                    level.sendParticles(p, dust, true, false, c.x + Math.cos(a) * 0.6, c.y, c.z + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
                }
            }
            BlockPos next = r.points.get(r.next);
            if (p.position().distanceToSqr(Vec3.atBottomCenterOf(next)) < 1.6 * 1.6) {
                r.next++;
                level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f + 0.2f * r.next);
                if (r.next >= r.points.size()) {
                    core.ritual(p).stage = 1;
                    p.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tengu_done").withStyle(ChatFormatting.GOLD));
                    return true;
                }
            }
            return false;
        });
    }

    /** Three points around the tengu, each a little higher, on solid footing (deterministic for a given place). */
    private Route route(ServerLevel level) {
        Route r = new Route();
        int seed = (int) (blockPosition().asLong() ^ (blockPosition().asLong() >>> 32));
        double base = Math.floorMod(seed, 360) * Math.PI / 180;
        int lastY = blockPosition().getY();
        for (int k = 0; k < 3; k++) {
            double a = base + k * Math.PI * 2 / 3;
            int x = (int) Math.floor(getX() + Math.cos(a) * (5 + 2 * k)), z = (int) Math.floor(getZ() + Math.sin(a) * (5 + 2 * k));
            BlockPos best = null;
            for (int y = lastY + 4; y >= lastY - 3; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!level.isLoaded(p)) continue;
                if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) { best = p; break; }
            }
            if (best == null) best = new BlockPos(x, lastY, z);
            r.points.add(best);
            lastY = best.getY();
        }
        r.until = level.getGameTime() + YokaiBalance.Tengu.ROUTE_TIME;
        return r;
    }

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            if (stack.isEmpty() || stack.is(Items.FEATHER))
                player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.need_pacts").withStyle(ChatFormatting.GRAY));
            return stack.isEmpty() || stack.is(Items.FEATHER) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        YokaiCore.Ritual ritual = core.ritual(player);
        if (stack.is(Items.FEATHER)) {
            if (ritual.stage < 1) {
                player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tengu_route_first").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            if (Bonds.pact(player, this)) {
                consume(player, stack, 1);
                core.resetRitual(player);
                routes.remove(player.getUUID());
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && ritual.stage < 1 && !routes.containsKey(player.getUUID())) {
            routes.put(player.getUUID(), route((ServerLevel) level()));
            flap();
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.tengu_route").withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.TENGU_WINGS; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }
    @Override public int getAmbientSoundInterval() { return 400; }
}
