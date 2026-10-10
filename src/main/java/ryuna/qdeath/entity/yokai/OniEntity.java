package ryuna.qdeath.entity.yokai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.AnchoredYokai;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;
import ryuna.qdeath.yokai.YokaiGoals;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Oni: a territorial brute of mountains and ruins. Heavy steps announce its ground; it never chases anyone out of its
 * territory unless they have hurt it. Its club is raised for two seconds before the slam.
 */
public class OniEntity extends AbstractYokai implements AnchoredYokai {
    public static final int ANIM_WINDUP = 1, ANIM_SLAM = 2, ANIM_STOMP = 3;
    private BlockPos anchor = BlockPos.ZERO;
    private int windup, cooldown, stompCooldown = 200;
    /** Players who have hurt it: those it follows beyond its territory. */
    private final Set<UUID> provokers = new HashSet<>();

    public OniEntity(EntityType<? extends OniEntity> type, Level level) {
        super(type, level, Species.ONI);
        xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Oni.HEALTH).add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ATTACK_DAMAGE, YokaiBalance.Oni.SLAM_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override public void setAnchor(BlockPos pos) {
        anchor = pos;
        setHomeTo(pos, (int) YokaiBalance.Oni.TERRITORY);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new YokaiGoals.Defend(this));
        targetSelector.addGoal(2, new YokaiGoals.Hostile(this, YokaiBalance.Perception.DETECT_RANGE));
    }

    private boolean inTerritory(Vec3 p) { return anchor.equals(BlockPos.ZERO) || Vec3.atCenterOf(anchor).distanceToSqr(p) <= YokaiBalance.Oni.TERRITORY * YokaiBalance.Oni.TERRITORY; }

    @Override protected void serverTick(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        LivingEntity target = getTarget();
        // It does not leave its territory for anyone who has not hurt it.
        if (target != null && !core.bonded() && !provokers.contains(target.getUUID()) && !inTerritory(target.position())) {
            setTarget(null);
            getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0.9);
            target = null;
        }
        if (windup > 0) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 6f, 30f);
            if (--windup == 0) slam(level);
            return;
        }
        if (target != null) {
            double d = distanceToSqr(target);
            if (d > 6.5) getNavigation().moveTo(target, 1.15);
            else if (cooldown == 0) {
                windup = YokaiBalance.Oni.WINDUP;
                preparing(true);
                playAnim(ANIM_WINDUP, windup);
                playSound(ModSounds.ONI_WINDUP, 1.4f, 0.9f);
            }
        } else if (--stompCooldown <= 0) {
            // Something strange for those who cannot see it: the ground is struck where nobody stands.
            stompCooldown = 300 + random.nextInt(400);
            ServerPlayer near = level.getNearestPlayer(this, 24) instanceof ServerPlayer p ? p : null;
            if (near != null && Perception.view(near, this) < 2 && !core.bonded()) {
                playAnim(ANIM_STOMP, 20);
                playSound(ModSounds.ONI_SLAM, 1.6f, 0.7f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlockStateOn()), getX(), getY() + 0.1, getZ(), 30, 1.2, 0.1, 1.2, 0.15);
                Bestiary.clueNear(level, position(), 32, species(), "impact");
            }
        }
        if (tickCount % 40 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1e-3) Bestiary.clueNear(level, position(), 24, species(), "heavy_steps");
    }

    private void slam(ServerLevel level) {
        preparing(false);
        cooldown = YokaiBalance.Oni.COOLDOWN;
        playAnim(ANIM_SLAM, 14);
        Vec3 ahead = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
        playSound(ModSounds.ONI_SLAM, 2.0f, 0.8f);
        BlockState ground = level.getBlockState(BlockPos.containing(ahead).below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), ahead.x, ahead.y + 0.1, ahead.z, 40, 1.0, 0.1, 1.0, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, ahead.x, ahead.y + 0.3, ahead.z, 1, 0, 0, 0, 0);
        double r = YokaiBalance.Oni.SLAM_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 2), e -> e != this && e.isAlive())) {
            if (e.position().distanceToSqr(ahead) > r * r || !YokaiGoals.mayHarm(this, e)) continue;
            if (e.hurtServer(level, damageSources().mobAttack(this), YokaiBalance.Oni.SLAM_DAMAGE)) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.push(push.x * 0.9, 0.35, push.z * 0.9);
                e.syncVelocity = true;
            }
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof ServerPlayer p) {
            provokers.add(p.getUUID());
            core.detect(p);
        }
        return hurt;
    }

    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(ModSounds.ONI_STEP, 1.3f, 0.8f + random.nextFloat() * 0.15f); }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.ONI_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.YOKAI_DEATH; }
    @Override public int getAmbientSoundInterval() { return 240; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Anchor", BlockPos.CODEC, anchor);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("Anchor", BlockPos.CODEC).ifPresent(this::setAnchor);
    }
}
