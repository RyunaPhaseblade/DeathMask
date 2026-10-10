package ryuna.qdeath.entity.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Bonds;
import ryuna.qdeath.yokai.Companions;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiGoals;

/**
 * Ittan-momen: a long strip of white cotton that drifts over the plains at night. A rustle and a hiss warn for 2 s
 * before it wraps someone (3 damage and slowness for 2 s, 5 s cooldown). A snowball pins it to the ground for 5 s;
 * a ritual thread tied in that time seals a pact. Bonded, its owner can ride it: it glides, never climbs, always
 * sinks at least 0.5 blocks per second and never goes faster than 6 blocks per second.
 */
public class IttanMomenEntity extends AbstractYokai implements Companions.Mount {
    public static final int ANIM_WINDUP = 1, ANIM_WRAP = 2;
    private static final int FLAG_STUNNED = 1;
    private int windup, cooldown, stunned;

    public IttanMomenEntity(EntityType<? extends IttanMomenEntity> type, Level level) {
        super(type, level, Species.ITTAN_MOMEN);
        moveControl = new FlyingMoveControl<>(this, 12, true);
        setNoGravity(true);
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.IttanMomen.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, YokaiBalance.IttanMomen.WRAP_DAMAGE);
    }

    @Override protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        return nav;
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.2));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new YokaiGoals.Defend(this));
        targetSelector.addGoal(2, new YokaiGoals.Hostile(this, YokaiBalance.Perception.DETECT_RANGE));
    }

    public boolean stunnedClient() { return flag(FLAG_STUNNED); }

    @Override protected void serverTick(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        if (stunned > 0) {
            getNavigation().stop();
            setTarget(null);
            if (--stunned == 0) {
                flag(FLAG_STUNNED, false);
                setNoGravity(true);
            }
            if (tickCount % 5 == 0) level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.2, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
            return;
        }
        if (isVehicle()) {
            getNavigation().stop();
            return;
        }
        LivingEntity target = getTarget();
        if (windup > 0) {
            if (target != null) {
                getLookControl().setLookAt(target);
                getMoveControl().setWantedPosition(target.getX(), target.getY() + 1.0, target.getZ(), 0.6);
            }
            if (--windup == 0) wrap(level, target);
            return;
        }
        if (target != null && target.isAlive()) {
            getMoveControl().setWantedPosition(target.getX(), target.getY() + 1.2, target.getZ(), 1.0);
            if (distanceToSqr(target) < 9 && cooldown == 0) {
                windup = YokaiBalance.IttanMomen.WINDUP;
                preparing(true);
                playAnim(ANIM_WINDUP, windup);
                playSound(ModSounds.ITTAN_RUSTLE, 1.3f, 1.0f);
                Bestiary.clueNear(level, position(), 16, species(), "cloth_hiss");
            }
        } else if (!core.bonded() && tickCount % 200 == 0 && level.getNearestPlayer(this, 20) != null) {
            playSound(ModSounds.ITTAN_RUSTLE, 0.7f, 1.2f);
            Bestiary.clueNear(level, position(), 20, species(), "rustle");
        }
    }

    private void wrap(ServerLevel level, @Nullable LivingEntity target) {
        preparing(false);
        cooldown = YokaiBalance.IttanMomen.COOLDOWN;
        playAnim(ANIM_WRAP, 16);
        if (target == null || !target.isAlive() || distanceToSqr(target) > 6.5 || !YokaiGoals.mayHarm(this, target)) return;
        playSound(ModSounds.ITTAN_WRAP, 1.2f, 1.0f);
        if (target.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.IttanMomen.WRAP_DAMAGE))
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, YokaiBalance.IttanMomen.WRAP_SLOW_TICKS, 1), this);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // A snowball from someone who sees it pins it to the ground: the moment to tie a ritual thread.
        if (source.getDirectEntity() instanceof Snowball && Perception.attributable(source) instanceof ServerPlayer p
                && Perception.canInteract(p, this) && !core.bonded()) {
            stunned = YokaiBalance.IttanMomen.STUN_TICKS;
            flag(FLAG_STUNNED, true);
            setNoGravity(false);
            windup = 0;
            preparing(false);
            playSound(ModSounds.ITTAN_RUSTLE, 1.0f, 0.6f);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override protected InteractionResult ritual(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!stack.is(YokaiContent.RITUAL_THREAD)) return InteractionResult.PASS;
        if (!SkillManager.has(player, SkillId.BOND_I)) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.need_pacts").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (stunned <= 0) {
            player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.ittan_pin").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (Bonds.pact(player, this)) {
            consume(player, stack, 1);
            stunned = 1;
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------------------------------------ mount

    @Override public void mount(ServerPlayer owner) {
        if (!core.isOwner(owner) || !SkillManager.has(owner, SkillId.BOND_I) || isVehicle() || owner.distanceToSqr(this) > 25) return;
        owner.startRiding(this);
    }

    @Override protected InteractionResult ownerInteract(ServerPlayer owner, InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty() && owner.isShiftKeyDown()) {
            mount(owner);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override public @Nullable LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        var owner = getOwnerReference();
        return first instanceof Player p && owner != null && owner.getUUID().equals(p.getUUID()) ? p : null;
    }

    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }

    @Override protected Vec3 getRiddenInput(Player rider, Vec3 input) { return new Vec3(rider.xxa * 0.6, 0, rider.zza); }

    @Override protected float getRiddenSpeed(Player rider) { return YokaiBalance.IttanMomen.MAX_HORIZONTAL / 20f; }

    @Override protected void tickRidden(Player rider, Vec3 input) {
        setYRot(rider.getYRot());
        yRotO = getYRot();
        setXRot(0);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override public void travel(Vec3 input) {
        if (getControllingPassenger() instanceof Player rider) glide(rider, input);
        else super.travel(input);
    }

    /**
     * Gliding: no lift. Airborne, the vertical speed is always at most -MIN_DESCENT; horizontal speed eases toward the
     * rider's input and is clamped to MAX_HORIZONTAL. On the ground it only shuffles along at GROUND_SPEED.
     */
    private void glide(Player rider, Vec3 input) {
        double maxH = YokaiBalance.IttanMomen.MAX_HORIZONTAL / 20.0, minDescent = YokaiBalance.IttanMomen.MIN_DESCENT / 20.0;
        Vec3 wish = new Vec3(input.x, 0, input.z);
        if (wish.lengthSqr() > 1) wish = wish.normalize();
        wish = wish.yRot(-getYRot() * ((float) Math.PI / 180f));
        Vec3 v = getDeltaMovement();
        double nx, ny, nz;
        if (onGround()) {
            double g = YokaiBalance.IttanMomen.GROUND_SPEED / 20.0;
            nx = v.x + (wish.x * g - v.x) * 0.4;
            nz = v.z + (wish.z * g - v.z) * 0.4;
            ny = -0.08;
        } else {
            nx = v.x + (wish.x * maxH - v.x) * 0.08;
            nz = v.z + (wish.z * maxH - v.z) * 0.08;
            double h = Math.sqrt(nx * nx + nz * nz);
            if (h > maxH) { nx *= maxH / h; nz *= maxH / h; h = maxH; }
            // The faster it slides, the gentler the descent; never less than the minimum, never upward.
            double descent = minDescent + (0.12 - minDescent) * (1 - h / maxH);
            ny = v.y + (-descent - v.y) * 0.25;
            ny = Math.max(-0.6, Math.min(ny, -minDescent));
        }
        setDeltaMovement(nx, ny, nz);
        move(MoverType.SELF, getDeltaMovement());
        resetFallDistance();
        rider.resetFallDistance();
    }

    @Override public boolean causeFallDamage(double distance, float multiplier, DamageSource source) { return false; }

    @Override public void tick() {
        super.tick();
        if (isVehicle()) setNoGravity(true);
    }

    @Override public boolean canStandAt(ServerLevel level, BlockPos pos) { return true; }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.ITTAN_RUSTLE; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }
}
