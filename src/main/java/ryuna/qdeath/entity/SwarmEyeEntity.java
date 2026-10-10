package ryuna.qdeath.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
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
import ryuna.qdeath.yokai.YokaiCombat;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiCore;
import ryuna.qdeath.yokai.YokaiCreature;
import ryuna.qdeath.yokai.YokaiGoals;
import ryuna.qdeath.yokai.YokaiHooks;
import ryuna.qdeath.yokai.YokaiNet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Swarm Eye: a floating eye trailing flexible nerves. In Hell it keeps its role of spotter (its gaze turns Hell's
 * creatures on you and feeds VIGIL). In the Overworld it is a neutral observer: after watching someone for 3 s it
 * shrieks and passes their last seen position to nearby yokai (each keeps its own rules; nothing kind turns hostile),
 * at most every 20 s. Hurt it and it defends itself with small eyes (3 damage every 2.5 s, 20 blocks).
 * Bonded, it can track a player physically: it reports only what it observes, and when sight is lost it searches the
 * last place for 30 s and then reports the trail as lost. It never loads chunks or follows across dimensions.
 * It never lands: it drifts about, bobbing between one and two blocks over the floor (or over water).
 */
public class SwarmEyeEntity extends Monster implements RangedAttackMob, ryuna.qdeath.entity.hell.HellMob, YokaiCreature, OwnableEntity, Companions.Tracker {
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
            SynchedEntityData.defineId(SwarmEyeEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** 0 idle, 1 observing (the iris narrows), 2 alarmed. */
    private static final EntityDataAccessor<Integer> DATA_GAZE = SynchedEntityData.defineId(SwarmEyeEntity.class, EntityDataSerializers.INT);
    private final YokaiCore core = new YokaiCore(Species.SWARM_EYE, this);
    private int watching;
    private @Nullable UUID watched;
    private int observe, alertCooldown, pactGaze;
    /** Entity event: it threw one of its small eyes. */
    private static final byte SHOT = 100;
    /** Client: when it last threw a small eye (the one that left grows back, see YokaiAnims.SWARM_EYE). */
    public int shotTick = -1000;

    // Tracking (persisted)
    private @Nullable UUID trackTarget;
    private String trackName = "";
    private Vec3 trackLast = Vec3.ZERO;
    private String trackDim = "";
    private long trackObservedAt;
    private String trackStatus = "";
    private long searchUntil;

    public SwarmEyeEntity(EntityType<? extends SwarmEyeEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 10, true);
        setNoGravity(true);
        this.xpReward = 10;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.monster.Blaze.createAttributes().add(Attributes.FLYING_SPEED, 0.35).add(Attributes.MAX_HEALTH, YokaiBalance.SwarmEye.HEALTH);
    }
    @Override public YokaiCore yokai() { return core; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_OWNER, Optional.empty());
        b.define(DATA_GAZE, 0);
    }
    public int gaze() { return entityData.get(DATA_GAZE); }
    @Override public @Nullable EntityReference<LivingEntity> getOwnerReference() { return entityData.get(DATA_OWNER).orElse(null); }

    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return level instanceof ServerLevelAccessor server && net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(ModEntities.SWARM_EYE, server, reason, blockPosition(), random);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new YokaiGoals.Rest(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, YokaiBalance.SwarmEye.SHOT_INTERVAL, YokaiBalance.SwarmEye.RANGE) {
            @Override public boolean canUse() { return trackTarget == null && super.canUse(); }
        });
        goalSelector.addGoal(3, new YokaiGoals.Follow(this, 1.0) { @Override public boolean canUse() { return trackTarget == null && super.canUse(); } });
        // Always on the move: a new spot in the air as soon as it reaches the last one (not while it watches or tracks someone).
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6, 1, false) {
            // Like a bee: somewhere ahead, one or two blocks over solid ground (DefaultRandomPos never suits a flyer).
            @Override protected @Nullable Vec3 getPosition() {
                Vec3 ahead = getViewVector(0);
                return HoverRandomPos.getPos(SwarmEyeEntity.this, 8, 7, ahead.x, ahead.z, Mth.HALF_PI, 2, 1);
            }
            @Override public boolean canUse() { return gaze() == 0 && trackTarget == null && super.canUse(); }
            @Override public boolean canContinueToUse() { return gaze() == 0 && trackTarget == null && super.canContinueToUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) { @Override public boolean canUse() { return !core.bonded() && super.canUse(); } });
        targetSelector.addGoal(2, new YokaiGoals.Defend(this) { @Override public boolean canUse() { return trackTarget == null && super.canUse(); } });
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }

    /** Top of the first floor (or liquid surface) up to 7 blocks below, or NaN over a deeper drop. */
    private double floorBelow(BlockGetter level) {
        BlockPos.MutableBlockPos pos = blockPosition().mutable();
        for (int i = 0; i < 7; i++, pos.move(Direction.DOWN)) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return pos.getY() + state.getFluidState().getHeight(level, pos);
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    /** Hover height over the floor: a slow bob between one and two blocks, out of step with other eyes. */
    private double hoverHeight() { return 1.5 + 0.5 * Math.sin((tickCount + getId() * 31) * 0.06); }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        double floor = floorBelow(level);
        if (Double.isNaN(floor)) return;
        double want = floor + hoverHeight();
        // A path that climbs (over a wall, up to a ledge) may lift it higher; nothing ever brings it lower.
        if (moveControl.hasWanted()) want = Math.max(want, Math.min(moveControl.getWantedY(), floor + 6));
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, Mth.clamp((want - getY()) * 0.15, -0.15, 0.15), v.z);
    }

    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        // It appears already in flight and drifting.
        double floor = floorBelow(level);
        if (!Double.isNaN(floor)) {
            Vec3 up = new Vec3(getX(), floor + hoverHeight(), getZ());
            if (level.noCollision(this, getBoundingBox().move(up.subtract(position())))) setPos(up);
        }
        setDeltaMovement(random.nextGaussian() * 0.05, 0, random.nextGaussian() * 0.05);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        if (Perception.exempt(this)) {
            if (getTarget() == null) {
                Player player = level().getNearestPlayer(this, 12.0);
                if (player != null) {
                    getNavigation().moveTo(player, 0.35);
                    getLookControl().setLookAt(player, 30.0f, 30.0f);
                }
                spot();
            }
            return;
        }
        if (!YokaiHooks.serverTick(this, core)) return;
        ServerLevel level = (ServerLevel) level();
        if (core.bonded() != entityData.get(DATA_OWNER).isPresent())
            entityData.set(DATA_OWNER, Optional.ofNullable(core.owner()).map(EntityReference::of));
        if (alertCooldown > 0) alertCooldown--;
        if (core.bonded()) {
            if (gaze() != 0) entityData.set(DATA_GAZE, 0);
            if (trackTarget != null) track(level);
            return;
        }
        if (getTarget() == null) observe(level);
        pactGaze(level);
    }

    /**
     * Hell role: the Swarm Eye is a spotter. Held in its gaze for three seconds in Hell, a player is announced:
     * nearby Hell monsters turn on them, and VIGIL learns where they are.
     */
    private void spot() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server) || ryuna.qdeath.hell.HellLayer.of(server) == null) return;
        Player seen = level().getNearestPlayer(this, 24.0);
        if (seen == null || seen.isCreative() || seen.isSpectator() || !hasLineOfSight(seen)) { watching = 0; return; }
        if (++watching < 60) return;
        watching = -400;
        playSound(ryuna.qdeath.registry.ModSounds.FURY_SHRIEK, 2.0f, 1.6f);
        server.sendParticles(ryuna.qdeath.registry.ModParticles.VIGIL_MOTE, getX(), getEyeY(), getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        for (Monster m : server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16), m -> m != this && m instanceof ryuna.qdeath.entity.hell.HellMob && m.getTarget() == null))
            m.setTarget(seen);
        if (seen instanceof net.minecraft.server.level.ServerPlayer sp) ryuna.qdeath.entity.vigil.VigilDirector.addTension(sp, 25f);
    }

    // ------------------------------------------------------------------------------------------------ the Overworld observer

    private void observe(ServerLevel level) {
        ServerPlayer seen = level.getNearestPlayer(this, YokaiBalance.SwarmEye.RANGE) instanceof ServerPlayer p && Perception.targetable(p) && hasLineOfSight(p) ? p : null;
        if (seen == null || !seen.getUUID().equals(watched)) {
            watched = seen == null ? null : seen.getUUID();
            observe = 0;
            entityData.set(DATA_GAZE, 0);
            return;
        }
        getLookControl().setLookAt(seen, 30f, 30f);
        // Watching, it hangs in the air and sways from side to side.
        Vec3 side = new Vec3(getZ() - seen.getZ(), 0, seen.getX() - getX()).normalize();
        setDeltaMovement(getDeltaMovement().add(side.scale(Math.cos(tickCount * 0.05) * 0.004)));
        if (observe == 0) {
            entityData.set(DATA_GAZE, 1);
            playSound(ModSounds.EYE_GAZE, 0.9f, 1.0f);
            Bestiary.clue(seen, species(), "watched");
        }
        if (++observe < YokaiBalance.SwarmEye.OBSERVE || alertCooldown > 0) return;
        // Alert: the last observed position goes to nearby yokai; each one decides by its own rules.
        alertCooldown = YokaiBalance.SwarmEye.ALERT_COOLDOWN;
        observe = 0;
        entityData.set(DATA_GAZE, 2);
        playSound(ModSounds.EYE_SHRIEK, 1.6f, 1.0f);
        Bestiary.clueNear(level, position(), 32, species(), "shriek");
        Vec3 at = seen.position();
        double r = YokaiBalance.SwarmEye.ALERT_RADIUS;
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r), m -> m != this && m instanceof YokaiCreature y && !y.yokai().bonded())) {
            YokaiCreature y = (YokaiCreature) m;
            if (!y.species().ordinaryHostile() || m.getTarget() != null) continue;
            // Only a creature that could pursue this player goes to look; it still has to find them itself.
            if (y.yokai().encounter(seen) == null && Perception.view(seen, m) == 0) continue;
            m.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
        }
    }

    private void pactGaze(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) > YokaiBalance.SwarmEye.PACT_RANGE * YokaiBalance.SwarmEye.PACT_RANGE || !Perception.canInteract(p, this)
                    || !SkillManager.has(p, SkillId.BOND_I)) continue;
            YokaiCore.Ritual r = core.ritual(p);
            if (r.stage == 1 && level.getGameTime() - r.time > YokaiBalance.SwarmEye.PACT_WINDOW) { r.stage = 0; r.count = 0; }
            if (r.stage == 1) continue;
            Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
            boolean looking = p.getLookAngle().dot(to) > 0.97 && p.hasLineOfSight(this);
            r.count = looking ? r.count + 1 : 0;
            if (r.count >= YokaiBalance.SwarmEye.PACT_GAZE) {
                r.stage = 1;
                r.time = level.getGameTime();
                getLookControl().setLookAt(p);
                playSound(ModSounds.EYE_GAZE, 1.0f, 1.5f);
                p.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.eye_gazed").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || Perception.exempt(this)) return super.mobInteract(player, hand);
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(YokaiContent.AMULET)) {
            if (core.isOwner(sp)) Bonds.useOn(sp, stack, this);
            return InteractionResult.SUCCESS;
        }
        if (core.isOwner(sp) && stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            Companions.open(sp, this);
            return InteractionResult.SUCCESS;
        }
        if (core.bonded() || !Perception.canInteract(sp, this) || !stack.is(Items.SPIDER_EYE)) return InteractionResult.PASS;
        YokaiCore.Ritual r = core.ritual(sp);
        if (r.stage < 1) {
            sp.sendOverlayMessage(Component.translatable("yokai.quantumdeath.ritual.eye_gaze_first").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (Bonds.pact(sp, this)) {
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            core.resetRitual(sp);
            setTarget(null);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------------------------------------ tracking

    @Override public boolean tracking() { return trackTarget != null; }
    public String trackStatus() { return trackStatus; }
    public Vec3 trackLast() { return trackLast; }
    public long trackObservedAt() { return trackObservedAt; }

    /** Online players in this dimension within 32 blocks of the eye and in its line of sight (never its owner). */
    @Override public List<String> trackable() {
        List<String> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel level)) return out;
        for (ServerPlayer p : level.players())
            if (!core.isOwner(p) && !p.isSpectator() && p.distanceTo(this) <= YokaiBalance.SwarmEye.TRACK_START_RANGE && sees(level, p)) out.add(p.getGameProfile().name());
        return out;
    }

    private boolean sees(ServerLevel level, ServerPlayer p) {
        return level.clip(new ClipContext(getEyePosition(), p.getEyePosition(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override public void track(ServerPlayer owner, String targetName) {
        if (!(level() instanceof ServerLevel level) || !core.isOwner(owner)) return;
        ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(targetName);
        // Initial validation: connected, same dimension, within 32 blocks of the eye, seen by it.
        if (target == null || target.level() != level || core.isOwner(target) || target.distanceTo(this) > YokaiBalance.SwarmEye.TRACK_START_RANGE || !sees(level, target)) {
            owner.sendOverlayMessage(Component.translatable("yokai.quantumdeath.tracking.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        trackTarget = target.getUUID();
        trackName = target.getGameProfile().name();
        setTarget(null);
        observed(level, target);
        report(owner, level);
    }

    @Override public void untrack(ServerPlayer owner) {
        if (!core.isOwner(owner)) return;
        trackTarget = null;
        trackStatus = "";
        YokaiNet.send(owner, YokaiNet.Tracking.NONE);
    }

    private void observed(ServerLevel level, ServerPlayer target) {
        trackLast = target.position();
        trackDim = level.dimension().identifier().toString();
        trackObservedAt = level.getGameTime();
        trackStatus = "observing";
    }

    @Override public @Nullable String storeBlocker() { return trackTarget != null ? "tracking" : null; }

    /** One update a second. Coordinates are read only while the eye actually observes the target. */
    private void track(ServerLevel level) {
        if (tickCount % YokaiBalance.SwarmEye.TRACK_UPDATE != 0) {
            if (trackStatus.equals("observing") || trackStatus.equals("searching")) steer();
            return;
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(trackTarget);
        long now = level.getGameTime();
        if (target == null) freeze("offline");
        else if (target.level() != level) freeze("dimension");
        else if (trackStatus.equals("searching") && !level.isLoaded(BlockPos.containing(trackLast))) freeze("unloaded");
        else if (target.distanceToSqr(this) < 48 * 48 && sees(level, target)) observed(level, target);
        else if (trackStatus.equals("observing")) {
            trackStatus = "searching";
            searchUntil = now + YokaiBalance.SwarmEye.TRACK_SEARCH;
        } else if (trackStatus.equals("searching") && now > searchUntil) {
            trackStatus = "lost";
            getNavigation().stop();
        }
        ServerPlayer owner = core.ownerPlayer();
        if (owner == null && level.getServer().getPlayerList().getPlayer(core.owner()) instanceof ServerPlayer elsewhere) owner = elsewhere;
        if (owner != null) report(owner, level);
    }

    private void freeze(String why) {
        if (!trackStatus.equals("lost")) trackStatus = why;
        getNavigation().stop();
    }

    /** Follows the target while observed (keeping its distance); searching, it circles the last observed place. */
    private void steer() {
        Vec3 goal = trackLast;
        if (trackStatus.equals("searching")) {
            double a = tickCount * 0.05;
            goal = trackLast.add(Math.cos(a) * 4, 2, Math.sin(a) * 4);
        } else if (position().distanceTo(trackLast) < YokaiBalance.SwarmEye.TRACK_FOLLOW_DISTANCE) return;
        getMoveControl().setWantedPosition(goal.x, goal.y + 1.5, goal.z, 1.0);
        getLookControl().setLookAt(trackLast.x, trackLast.y + 1.5, trackLast.z);
    }

    private void report(ServerPlayer owner, ServerLevel level) {
        BlockPos p = BlockPos.containing(trackLast);
        YokaiNet.send(owner, new YokaiNet.Tracking(true, trackName, p.getX(), p.getY(), p.getZ(), trackDim, trackObservedAt, level.getGameTime(), trackStatus));
    }

    // ------------------------------------------------------------------------------------------------ combat

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (level().isClientSide()) return;
        Vec3 origin = position().add(0, getBbHeight() * .55, 0);
        Vec3 direction = target.getEyePosition().subtract(origin).normalize();
        SwarmArmProjectile shot = new SwarmArmProjectile(level(), this);
        shot.setDamage(Perception.exempt(this) ? 6.0f : YokaiBalance.SwarmEye.SHOT_DAMAGE);
        shot.setPos(origin);
        shot.shoot(direction.x, direction.y, direction.z, 0.55f, 1.0f);
        level().addFreshEntity(shot);
        level().broadcastEntityEvent(this, SHOT);
        playSound(ModSounds.EYE_SHOT, 1.0f, 1.0f);
    }
    @Override public void handleEntityEvent(byte id) {
        if (id == SHOT) shotTick = tickCount;
        else super.handleEntityEvent(id);
    }
    @Override public boolean isOnFire() { return false; }
    @Override public boolean isPickable() { return super.isPickable() && YokaiHooks.pickable(this); }
    @Override public boolean ignoreExplosion(Explosion explosion) { return YokaiCombat.ignoresExplosion(this, explosion) || super.ignoreExplosion(explosion); }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) Bonds.died(this);
    }
    @Override public void checkDespawn() { if (!core.bonded()) super.checkDespawn(); }
    @Override public boolean removeWhenFarAway(double distance) { return !core.bonded() && super.removeWhenFarAway(distance); }
    @Override public boolean canStandAt(ServerLevel level, BlockPos pos) { return true; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return Perception.exempt(this) ? super.getHurtSound(s) : ModSounds.YOKAI_HURT; }
    @Override protected SoundEvent getDeathSound() { return Perception.exempt(this) ? super.getDeathSound() : ModSounds.YOKAI_DEATH; }
    @Override protected SoundEvent getAmbientSound() { return Perception.exempt(this) ? super.getAmbientSound() : null; }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        core.save(out);
        if (trackTarget != null) {
            ValueOutput t = out.child("Tracking");
            t.store("target", UUIDUtil.CODEC, trackTarget);
            t.putString("name", trackName);
            t.store("last", Vec3.CODEC, trackLast);
            t.putString("dim", trackDim);
            t.putLong("observed", trackObservedAt);
            t.putString("status", trackStatus);
            t.putLong("search_until", searchUntil);
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        core.load(in);
        in.child("Tracking").ifPresent(t -> {
            trackTarget = t.read("target", UUIDUtil.CODEC).orElse(null);
            trackName = t.getStringOr("name", "");
            trackLast = t.read("last", Vec3.CODEC).orElse(Vec3.ZERO);
            trackDim = t.getStringOr("dim", "");
            trackObservedAt = t.getLongOr("observed", 0);
            trackStatus = t.getStringOr("status", "");
            searchUntil = t.getLongOr("search_until", 0);
        });
        if (core.bonded()) setPersistenceRequired();
    }
}
