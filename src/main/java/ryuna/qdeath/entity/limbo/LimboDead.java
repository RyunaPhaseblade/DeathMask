package ryuna.qdeath.entity.limbo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.hell.HellMonster;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.limbo.BreathClues;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.Horde;
import ryuna.qdeath.limbo.HordePaths;
import ryuna.qdeath.limbo.LightLures;
import ryuna.qdeath.limbo.LimboDirector;
import ryuna.qdeath.limbo.LimboSenses;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.LimboVoices;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.limbo.Provenance;
import ryuna.qdeath.limbo.Routes;
import ryuna.qdeath.limbo.Siege;
import ryuna.qdeath.limbo.Stimuli;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.UUID;

/**
 * A dead of the Limbo. They want life back and mistake warmth and breath for it. Every species runs the same detection
 * states ({@link Mind}); what changes is how far they see, how fast they run, and what their attack looks like. Every
 * attack is announced by a pose held long enough to read before it lands.
 *
 * <p>There is no limit to how many dead may hunt or strike the same creature: every dead that has found its quarry and
 * has a route to it chases and attacks, with only bodies, distance and its own attack rhythm in the way. Routes are
 * shared across a horde ({@link HordePaths}) so large crowds stay affordable.
 */
public abstract class LimboDead extends HellMonster {
    private static final EntityDataAccessor<Byte> DATA_MIND = SynchedEntityData.defineId(LimboDead.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(LimboDead.class, EntityDataSerializers.INT);
    /** Pose in a pile of bodies (ryuna.qdeath.limbo.Horde: none, pressing at the foot, bearing others, climbing). */
    private static final EntityDataAccessor<Byte> DATA_MOUND = SynchedEntityData.defineId(LimboDead.class, EntityDataSerializers.BYTE);
    public static final int ANIM_ATTACK = 1, ANIM_CALL = 2, ANIM_RECOGNIZE = 3, ANIM_SPECIAL = 4, ANIM_SIEGE = 5, ANIM_EMERGE = 6, ANIM_STAGGER = 7;

    protected Mind mind = Mind.REST;
    protected int mindTicks;
    protected @Nullable LivingEntity quarry;
    protected @Nullable Vec3 clue;
    protected float awareness;
    protected int unseen, unreachable, searchLeft, callCooldown, windup, attackCooldown, busy;
    private int recognitionLength = LimboTuning.RECOGNITION_TICKS;
    private long trailTime;
    private @Nullable UUID leader;
    private int slot;
    private @Nullable BlockPos home;
    private boolean resting;
    private float heading = Float.NaN;
    private int nextWander;
    private @Nullable BlockPos siegeAt;
    private int siegeTicks;
    private int emerge;
    /** A route assigned by a Foreman: approach the quarry from this offset (relative to it) for a while. */
    private @Nullable Vec3 approach;
    private int approachTicks;
    private int commotionCooldown, nextRepath;
    /** The plague focus this dead came out of (its breach anchor is {@link #home}), or -1: the plague's dead are marked, not guessed. */
    private int plagueFocus = -1;
    /** A light being inspected, and lights already inspected without finding life (position -> game time until ignored). */
    private @Nullable BlockPos lightClue;
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap ignoredLights = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private Stimuli.@Nullable Kind lastStimulus;
    private int lightSince;
    /** A weak block (glass, pane, leaves) being broken, and its preparation. */
    private @Nullable BlockPos weakAt;
    /** Where to stand to break {@link #weakAt} while still walking to it (a window found around the quarry). */
    private @Nullable Vec3 weakStand;
    private int weakTicks;
    /** Stuck watch: where it was STUCK_TICKS ago; accesses that failed it, with the time they cool until. */
    private Vec3 progressAnchor = Vec3.ZERO;
    private int progressTimer, accessRetry;
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap failedAccess = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private Routes.@Nullable Climb climb;
    private int climbTicks;
    /** The pile its horde is raising under an elevated quarry; when it last climbed someone, and was climbed (tickCount). */
    private Horde.@Nullable Ledge pile;
    private int lastLift = -100, lastBorne = -100;
    /** Digging a fellow out of the ground (ticks so far, 0 = not digging); dug-up dead never dig. */
    private int digTicks, digCooldown;
    private boolean risen;

    protected LimboDead(EntityType<? extends Monster> type, Level level) { super(type, level); }

    // ------------------------------------------------------------------ species tuning
    protected double sightRange() { return 18; }
    /** Cosine of the half field of view; anything closer than {@link LimboTuning#TOUCH_RANGE} is always felt. */
    protected double fovCos() { return 0.2; }
    protected double hearing() { return 1.0; }
    /** Pursuit speed as a fraction of the player's sprint (in the Limbo). */
    protected double chaseFraction() { return LimboTuning.CHASE_COMMON; }
    /** Navigation modifier used outside the Limbo (Gorge, Mire, Overworld breaches keep the pace they always had). */
    protected double legacyChase() { return 1.0; }
    /** Human dead walk at the Limbo's slow drift; the funeral monsters keep their own modifiers. */
    protected boolean human() { return true; }
    protected double wanderSpeed() { return inLimbo() && human() ? modifier(LimboTuning.WANDER) : 0.55; }
    protected double chaseSpeed() { return inLimbo() ? modifier(chaseFraction()) : legacyChase(); }
    protected double searchSpeed() { return chaseSpeed() * 0.8; }
    protected int searchTicks() { return 160; }
    /** 0 = never calls (solitary hunters). */
    protected double callRadius() { return 10; }
    protected int callTicks() { return 16; }
    /** Whether a hit during the call cancels it (the Crier's long inhale). */
    protected boolean callInterruptible() { return false; }
    protected double reach() { return 2.2; }
    protected int windupTicks() { return 12; }
    protected int attackRecovery() { return 26; }
    protected int siegePrepare() { return 0; }
    /** Number of skins (textures/entity/limbo/&lt;id&gt;_N.png); 0 for creatures drawn by the old Hell models. */
    public int skinCount() { return 0; }
    protected abstract SoundEvent recognizeSound();
    protected SoundEvent callSound() { return recognizeSound(); }
    /** Breath and snarls while running after someone (through the voice budget); null for none. */
    protected @Nullable SoundEvent chaseSound() { return null; }
    /** Specialists' voices carry over the crowd; common voices go through {@link LimboVoices}. */
    protected boolean priorityVoice() { return false; }

    protected final double modifier(double fraction) {
        return LimboTuning.chaseModifier(fraction, getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
    }

    public boolean inLimbo() { return HellLayer.of(level()) == HellLayer.VESTIBULE; }

    /** Marks this dead as released by a plague focus (with its breach anchor as home). */
    public void setPlagueOrigin(int focus, BlockPos anchor) {
        plagueFocus = focus;
        home = anchor;
    }
    public boolean fromPlague() { return plagueFocus >= 0; }
    public int plagueFocus() { return plagueFocus; }

    /**
     * The expansion's senses and tactics (stimuli, lights, weak blocks, breaches, piles, fire, digging) belong to the dead of the Limbo
     * and to those that came out of a plague focus; Hollows of the other layers keep the old ways.
     */
    public boolean expanded() { return LimboTuning.expansion && (inLimbo() || fromPlague()); }

    /** Humanoid dead of an ordinary build climb the others and dig them up; heavy, tall or ranged ones never do. */
    public boolean moundEligible() { return human() && getBbHeight() >= 1.6 && getBbHeight() <= 2.2; }
    public byte moundRole() { return entityData.get(DATA_MOUND); }
    public void setMoundRole(byte role) { entityData.set(DATA_MOUND, role); }
    public int lastLift() { return lastLift; }
    public int lastBorne() { return lastBorne; }
    public void climbed() { lastLift = tickCount; }
    public void borne() { lastBorne = tickCount; }
    public boolean piling() { return pile != null; }
    public boolean digging() { return digTicks > 0; }
    public boolean risen() { return risen; }
    public void markRisen() { risen = true; }

    /** Starts digging a fellow out of the ground (the hunt starts it by chance; demos and tests at once). */
    public void startDig() {
        digTicks = 1;
        getNavigation().stop();
        Horde.digs++;
    }

    /** A climbing body is not shoved about by the crowd under it (it would slide off every time). */
    @Override public boolean isPushable() { return super.isPushable() && tickCount - lastLift > LimboTuning.STACK_GLIDE; }

    // ------------------------------------------------------------------ state
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_MIND, (byte) 0);
        b.define(DATA_SKIN, 0);
        b.define(DATA_MOUND, (byte) 0);
    }

    public Mind mind() { return level().isClientSide() ? Mind.values()[entityData.get(DATA_MIND)] : mind; }
    public @Nullable LivingEntity quarry() { return quarry; }
    public boolean isResting() { return resting; }
    public void setResting(boolean r) { resting = r; }
    public void setHome(@Nullable BlockPos home) { this.home = home; }
    public @Nullable BlockPos home() { return home; }
    public int skin() { return entityData.get(DATA_SKIN); }
    public void setSkin(int skin) { entityData.set(DATA_SKIN, skinCount() <= 0 ? 0 : Math.floorMod(skin, skinCount())); }
    public @Nullable Vec3 approach() { return approachTicks > 0 ? approach : null; }

    protected void setMind(Mind m) {
        if (mind == m) return;
        mind = m;
        mindTicks = 0;
        entityData.set(DATA_MIND, (byte) m.ordinal());
        if (LimboDirector.debug) refreshDebugName();
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Makes this dead pursue {@code target} directly (boss calls, breaches, tests): a summons already knows where to go. */
    public void engage(LivingEntity target) {
        quarry = target;
        clue = target.position();
        resting = false;
        startPursuit();
    }

    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && !level().isClientSide() && target != quarry && LimboSenses.isLife(target)) engage(target);
    }

    /** Rise out of the ground (graves, breaches, the Ossuary's occupants): a visible, harmless entrance. */
    public void emerge(int ticks) {
        emerge = ticks;
        playAnim(ANIM_EMERGE, ticks);
    }

    /** A Foreman's order: come at the quarry from {@code offset} (relative to it) for {@code ticks}. */
    public void assignApproach(Vec3 offset, int ticks) {
        approach = offset;
        approachTicks = ticks;
    }

    // ------------------------------------------------------------------ the mind
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        mindTicks++;
        if (callCooldown > 0) callCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (commotionCooldown > 0) commotionCooldown--;
        if (approachTicks > 0) approachTicks--;
        if (emerge > 0) { emerge--; getNavigation().stop(); return; }
        if (expanded()) Horde.tick(level, this);
        if (busy > 0) { busy--; getNavigation().stop(); if (quarry != null) getLookControl().setLookAt(quarry, 30f, 30f); return; }
        BlockPos holy = Consecration.at(level, position());
        if (holy != null) { leaveSanctuary(level, holy); return; }
        // Dead far from everyone think slowly; once alert they always think at full rate.
        int interval = mind.alert() || mind == Mind.SEARCH || level.getNearestPlayer(this, LimboTuning.FAR_THINKING) != null
                ? LimboTuning.SCAN_INTERVAL : LimboTuning.SCAN_INTERVAL_FAR;
        if ((tickCount + getId()) % interval == 0) sense(level);
        if (expanded() && (mind == Mind.REST || mind == Mind.SUSPICION || mind == Mind.DISPERSAL || mind == Mind.SEARCH)) lookForLight(level);
        switch (mind) {
            case REST -> rest(level);
            case SUSPICION -> suspicion();
            case RECOGNITION -> recognition();
            case CALL -> call(level);
            case PURSUIT -> pursuit(level);
            case SEARCH -> search(level);
            case DISPERSAL -> dispersal(level);
        }
    }

    private void sense(ServerLevel level) {
        if (mind == Mind.RECOGNITION || mind == Mind.CALL) return;
        LivingEntity seen = null, heard = null;
        double seenD = Double.MAX_VALUE, heardD = Double.MAX_VALUE;
        double range = Math.max(sightRange() * 1.4, LimboTuning.NOISE_SPRINT * hearing()) + 1;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), LimboSenses::isLife)) {
            if (LimboSenses.inGrace(e)) continue;
            double d = distanceTo(e);
            if (canSee(e, d)) {
                if (d < seenD) { seen = e; seenD = d; }
            } else if (d <= LimboSenses.noiseRadius(e) * hearing() && d < heardD) {
                heard = e;
                heardD = d;
            }
        }
        switch (mind) {
            case REST, SUSPICION, DISPERSAL -> {
                float scale = mind == Mind.DISPERSAL ? 0.5f : 1f;
                if (seen != null) {
                    awareness += scale * (seenD < 6 ? LimboTuning.AWARE_SIGHT_NEAR : LimboTuning.AWARE_SIGHT_FAR);
                    clue = seen.position();
                    if (awareness >= 1f) { recognize(seen, LimboTuning.RECOGNITION_TICKS); return; }
                    suspect();
                } else if (heard != null) {
                    awareness = Math.min(LimboTuning.NOISE_CAP, awareness + scale * LimboTuning.AWARE_NOISE);
                    clue = heard.position();
                    suspect();
                } else {
                    awareness = Math.max(0f, awareness - LimboTuning.AWARE_DECAY);
                }
            }
            case SEARCH -> {
                if (seen != null) recognize(seen, LimboTuning.RECOGNITION_TICKS / 2);
                else if (heard != null) { clue = heard.position(); searchLeft = Math.min(searchTicks(), searchLeft + 20); }
            }
            case PURSUIT -> {
                // Something alive much closer than the current quarry, and in plain view, takes its place.
                if (seen != null && quarry != null && seen != quarry && seenD < 3 && seenD < 0.5 * distanceTo(quarry)) quarry = seen;
            }
            default -> {}
        }
    }

    private boolean canSee(LivingEntity e, double d) {
        if (d <= LimboTuning.TOUCH_RANGE) return getSensing().hasLineOfSight(e); // felt whatever the facing, never through a wall
        double range = sightRange() * (sightBonus(e) ? 1.4 : 1.0);
        if (d > range || e.isInvisible()) return false;
        if (mind != Mind.PURSUIT && mind != Mind.SEARCH) {
            Vec3 to = e.getEyePosition().subtract(getEyePosition()).normalize();
            if (getViewVector(1f).dot(to) < fovCos()) return false;
        }
        return getSensing().hasLineOfSight(e);
    }

    /** Species that react more strongly to some prey (the Runaway and fleeing targets). */
    protected boolean sightBonus(LivingEntity e) { return false; }

    private void suspect() {
        if (mind == Mind.REST || mind == Mind.DISPERSAL) {
            resting = false;
            setMind(Mind.SUSPICION);
            getNavigation().stop();
        }
    }

    protected void recognize(LivingEntity target, int ticks) {
        quarry = target;
        clue = target.position();
        awareness = 1f;
        resting = false;
        recognitionLength = Math.max(4, ticks);
        setMind(Mind.RECOGNITION);
        getNavigation().stop();
        playAnim(ANIM_RECOGNIZE, recognitionLength);
        voice(recognizeSound(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    /** Plays a vocalization: specialists always, common dead only when the local voice budget allows it. */
    protected void voice(SoundEvent sound, float volume, float pitch) {
        if (level() instanceof ServerLevel l && !priorityVoice() && !LimboVoices.allow(l, position())) return;
        playSound(sound, volume, pitch);
    }

    @Override public void playAmbientSound() {
        if (level() instanceof ServerLevel l && !priorityVoice() && !LimboVoices.allow(l, position())) return;
        super.playAmbientSound();
    }

    private void rest(ServerLevel level) {
        if (resting) { getNavigation().stop(); return; }
        if (home != null && distanceToSqr(Vec3.atBottomCenterOf(home)) > 24 * 24) {
            if (getNavigation().isDone()) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, wanderSpeed());
            return;
        }
        LivingEntity lead = leader(level);
        if (lead instanceof LimboDead l) {
            float yaw = l.yBodyRot * Mth.DEG_TO_RAD;
            int row = 1 + slot / 3;
            double side = ((slot % 3) - 1) * 1.6;
            Vec3 spot = l.position().add(Mth.sin(yaw) * row * 2.0 + Mth.cos(yaw) * side, 0, -Mth.cos(yaw) * row * 2.0 + Mth.sin(yaw) * side);
            if (distanceToSqr(spot) > 4 && (tickCount + getId()) % 10 == 0) getNavigation().moveTo(spot.x, spot.y, spot.z, wanderSpeed() * 1.15);
            return;
        }
        if (--nextWander > 0) return;
        nextWander = 60 + random.nextInt(140);
        Vec3 dest;
        if (leader != null && lead == null) leader = null; // the procession broke up
        if (isProcessionLeader()) {
            // Processions keep a heading and walk in long, slow legs with pauses between them.
            if (Float.isNaN(heading)) heading = random.nextFloat() * Mth.TWO_PI;
            heading += (random.nextFloat() - 0.5f) * 0.9f;
            dest = LandRandomPos.getPosTowards(this, 12, 5, position().add(Mth.cos(heading) * 14, 0, Mth.sin(heading) * 14));
        } else {
            dest = LandRandomPos.getPos(this, 9, 5);
        }
        if (dest != null && Consecration.at(level, dest) == null) getNavigation().moveTo(dest.x, dest.y, dest.z, wanderSpeed());
    }

    private void suspicion() {
        getNavigation().stop();
        if (clue != null) getLookControl().setLookAt(clue.x, clue.y + 1.2, clue.z, 12f, 20f);
        if (awareness <= 0f || mindTicks > 160) setMind(Mind.REST);
    }

    private void recognition() {
        if (quarry == null || !quarry.isAlive()) { setMind(Mind.REST); return; }
        getNavigation().stop();
        getLookControl().setLookAt(quarry, 30f, 30f);
        if (mindTicks < recognitionLength) return;
        if (callRadius() > 0 && callCooldown == 0 && hasListeners(callRadius())) startCall();
        else startPursuit();
    }

    /** True when some dead within {@code radius} is not already hunting (a call would reach someone). */
    protected boolean hasListeners(double radius) {
        return !level().getEntitiesOfClass(LimboDead.class, getBoundingBox().inflate(radius), d -> d != this && !d.mind.alert()).isEmpty();
    }

    protected void startCall() {
        setMind(Mind.CALL);
        getNavigation().stop();
        playAnim(ANIM_CALL, callTicks());
        playSound(callSound(), 1.6f, 0.8f + random.nextFloat() * 0.2f);
        onCallStart();
    }

    protected void onCallStart() {}

    private void call(ServerLevel level) {
        if (quarry != null) getLookControl().setLookAt(quarry, 30f, 30f);
        getNavigation().stop();
        if (mindTicks < callTicks()) return;
        if (quarry != null) broadcast(level, this, quarry.position(), callRadius());
        callCooldown = LimboTuning.CALL_COOLDOWN;
        onCallComplete(level);
        startPursuit();
    }

    protected void onCallComplete(ServerLevel level) {}

    /** The call: nearby dead that are not already hunting go to look where the caller saw life. They are not given the target. */
    public static void broadcast(ServerLevel level, LimboDead source, Vec3 where, double radius) {
        for (LimboDead d : level.getEntitiesOfClass(LimboDead.class, source.getBoundingBox().inflate(radius), x -> x != source && x.isAlive()))
            d.hearAlert(where);
    }

    public void hearAlert(Vec3 where) {
        if (mind.alert() || emerge > 0) return;
        resting = false;
        clue = where;
        searchLeft = searchTicks();
        if (mind != Mind.SEARCH) {
            setMind(Mind.SEARCH);
            playAnim(ANIM_RECOGNIZE, 10); // heads come up before they change direction
        }
    }

    /**
     * A noise or a light of the living reached this dead. It goes to look: a clue that lasts {@link LimboTuning#STIMULUS_LIFE},
     * never the target. A dead hunting something in sight ignores it; one that lost sight of its quarry takes a sound as
     * the newest trace. A new sound also lifts the "already looked there" memory of lights.
     */
    public void hearStimulus(Vec3 at, Stimuli.Kind kind) {
        if (emerge > 0 || !expanded() || !canInvestigate()) return;
        if (kind != Stimuli.Kind.LIGHT) ignoredLights.clear();
        if (mind == Mind.PURSUIT) {
            if (unseen > 0 && kind != Stimuli.Kind.LIGHT) clue = at;
            return;
        }
        if (mind.alert()) return;
        lastStimulus = kind;
        lightClue = kind == Stimuli.Kind.LIGHT ? BlockPos.containing(at) : null;
        lightSince = tickCount;
        resting = false;
        clue = at;
        searchLeft = Math.max(searchLeft, LimboTuning.STIMULUS_LIFE);
        if (mind != Mind.SEARCH) {
            setMind(Mind.SEARCH);
            playAnim(ANIM_RECOGNIZE, 10);
        }
    }

    /** Whether this dead follows clues of its own (mound bearers and the attached do not leave their place). */
    protected boolean canInvestigate() { return true; }

    public Stimuli.@Nullable Kind lastStimulus() { return lastStimulus; }

    /** Looks for an artificial light in sight now and then while it has nothing better to do. */
    private void lookForLight(ServerLevel level) {
        if (lightClue != null || (mind == Mind.SEARCH && clue != null)) return;
        if ((tickCount + getId()) % LimboTuning.LIGHT_SCAN != 0) return;
        if (level.getNearestPlayer(this, LimboTuning.FAR_THINKING) == null) return;
        long now = level.getGameTime();
        BlockPos light = LightLures.visible(level, this, l -> ignoredLights.get(l) > now);
        if (light != null) hearStimulus(Vec3.atCenterOf(light), Stimuli.Kind.LIGHT);
    }

    /** A light inspected without finding anyone alive: leave it alone for a while (unless another clue comes). */
    private void lightInspected(ServerLevel level) {
        if (lightClue == null) return;
        ignoredLights.put(lightClue.asLong(), level.getGameTime() + LimboTuning.LIGHT_IGNORE);
        if (ignoredLights.size() > 16) ignoredLights.long2LongEntrySet().removeIf(e -> e.getLongValue() < level.getGameTime());
        lightClue = null;
    }

    /** Every dead that has found its quarry chases it: there is no quota of pursuers per victim. */
    protected void startPursuit() {
        if (quarry == null) { setMind(Mind.REST); return; }
        unseen = 0;
        unreachable = 0;
        nextRepath = 0;
        setMind(Mind.PURSUIT);
    }

    private void pursuit(ServerLevel level) {
        LivingEntity q = quarry;
        if (q == null || !q.isAlive() || q.level() != level || !LimboSenses.isLife(q)) { quarry = null; toSearch(clue); return; }
        if (home != null && q.distanceToSqr(Vec3.atBottomCenterOf(home)) > LimboTuning.PLAGUE_LEASH * LimboTuning.PLAGUE_LEASH) { toSearch(clue); return; }
        // A siege under way is not given up for lack of sight: the door it is battering is what hides the target.
        if (siegeAt != null) { tickSiege(level); return; }
        double d = distanceTo(q);
        boolean visible = d < sightRange() * 2.5 && getSensing().hasLineOfSight(q);
        // On a pile the edge hides the quarry standing above it: still there, by the top, it counts as seen.
        if (visible || (pile != null && Horde.leadsTo(pile, q))) { unseen = 0; clue = q.position(); } else unseen++;
        if (unseen > LimboTuning.LOSE_SIGHT_TICKS) { toSearch(clue); return; }
        if (digTicks > 0) { tickDig(level, q); return; }
        if (weakAt != null) { tickWeak(level); return; }
        if (climb != null && tickClimb(level)) return;
        if (windup > 0) { tickWindup(level, q); return; }
        getLookControl().setLookAt(q, 30f, 30f);
        BlockPos holy = Consecration.at(level, q.position());
        if (holy != null) {
            // The living sit inside consecrated light: gather at its edge, never cross, never strike through it.
            Vec3 edge = Consecration.edge(holy, position(), 1.5);
            if ((tickCount + getId()) % 10 == 0) getNavigation().moveTo(edge.x, edge.y, edge.z, wanderSpeed());
            if (++unreachable > LimboTuning.UNREACHABLE_TICKS * 5) toSearch(clue);
            return;
        }
        if (visible && commotionCooldown == 0) commotion(level, q);
        if (visible && chaseSound() != null && random.nextInt(70) == 0) voice(chaseSound(), 0.9f, 0.9f + random.nextFloat() * 0.25f);
        if (pursueSpecial(level, q, d, visible)) return;
        if (d <= reach() && attackCooldown == 0 && visible) { startWindup(); return; }
        if (pile != null && tickPile(level, q)) return;
        if (expanded()) mayDig(level, d);
        Vec3 offset = approach();
        if (offset == null && visible && d < 9 && Math.abs(q.getY() - getY()) < 1.2 && clearRun(level, q)) {
            // Close, in sight, on the same level and nothing in the way: run straight at it (a route to where it stood a
            // moment ago would always arrive late, and a walking quarry would never be caught).
            getNavigation().stop();
            getMoveControl().setWantedPosition(q.getX(), q.getY(), q.getZ(), chaseSpeed());
            nextRepath = 0;
        } else if (offset != null && d > 5) {
            // A Foreman's route: come round to the side or the back it pointed at.
            Vec3 goal = q.position().add(offset);
            if (--nextRepath <= 0 || getNavigation().isDone()) {
                getNavigation().moveTo(goal.x, goal.y, goal.z, chaseSpeed());
                nextRepath = 10;
            }
        } else if (--nextRepath <= 0 || getNavigation().isDone()) {
            HordePaths.chase(this, q, chaseSpeed());
            // Near pursuers re-route often (the quarry moves), far ones less: the shared routes cover the rest.
            nextRepath = d < 8 ? 4 : d < 20 ? 8 : 16;
        }
        if (expanded()) watchProgress(level, q, d, visible);
        trackReach(level, q.position(), d);
    }

    /**
     * Stuck: less than STUCK_PROGRESS blocks in STUCK_TICKS while far from the quarry. The access it was trying is
     * cooled for ACCESS_COOLDOWN and the search for another way starts at once.
     */
    private void watchProgress(ServerLevel level, LivingEntity q, double d, boolean visible) {
        if (++progressTimer < LimboTuning.STUCK_TICKS) return;
        progressTimer = 0;
        double moved = position().distanceTo(progressAnchor);
        progressAnchor = position();
        boolean close = d <= reach() + 1.0 || (d < 7 && visible && Math.abs(q.getY() - getY()) < 1.5);
        if (close || moved >= LimboTuning.STUCK_PROGRESS) return;
        Vec3 offset = approach();
        if (offset != null) {
            failedAccess.put(Routes.accessKey(offset), level.getGameTime() + LimboTuning.ACCESS_COOLDOWN);
            approachTicks = 0;
        }
        unreachable = Math.max(unreachable, 31);
    }

    /**
     * The quarry cannot be reached by the ordinary route. In the document's order: another access beside or around it,
     * a ladder or vine up to it, then an action (a weak block in the way, a pile of bodies against the wall). The Ticker's breach
     * is its own. True when something was started (the search waits for it).
     */
    private boolean findAnotherWay(ServerLevel level, LivingEntity q, Vec3 goal) {
        long now = level.getGameTime();
        boolean elevated = q.getY() - getY() >= 1.5;
        boolean retry = --accessRetry <= 0;
        if (retry) {
            accessRetry = 20;
            Vec3 off = Routes.access(level, this, q, k -> failedAccess.get(k) > now);
            if (off != null) { assignApproach(off, 100); return true; }
        }
        if (elevated && human() && climb == null) {
            Routes.Climb c = Routes.climb(level, this, q);
            if (c != null && failedAccess.get(c.base().asLong()) <= now) { climb = c; climbTicks = 0; return true; }
        }
        if (human() && Siege.allowed(level) && seekWeak(level, goal, retry)) return true;
        if (elevated && moundEligible()) pile = Horde.pile(level, this, q);
        return pile != null;
    }

    /**
     * At its horde's pile: up to the foot, then pressing into the wall toward the top; the bodies of the crowd lift it
     * (Horde). Once over the top the ordinary pursuit takes over. False when the pile no longer leads to the quarry.
     */
    private boolean tickPile(ServerLevel level, LivingEntity q) {
        Horde.Ledge l = pile;
        Vec3 foot = l.foot(), exit = Vec3.atBottomCenterOf(l.exit());
        if (onGround() && getY() >= exit.y - 0.1) { pile = null; unreachable = 0; Horde.overTop++; return false; }
        if (!Horde.leadsTo(l, q) || ((tickCount + getId()) % 20 == 0 && !Horde.stands(level, l))) { pile = null; return false; }
        Horde.refresh(level, q, l);
        getLookControl().setLookAt(q, 30f, 30f);
        if (onGround() && Horde.horizontalSqr(position(), foot) > 2.5 * 2.5) {
            if ((tickCount + getId()) % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(foot.x, foot.y, foot.z, chaseSpeed());
            return true;
        }
        // Into the press and against the wall, straight at the top: whoever is in the way gets climbed.
        getNavigation().stop();
        getMoveControl().setWantedPosition(exit.x, exit.y, exit.z, chaseSpeed());
        return true;
    }

    // ------------------------------------------------------------------ digging the dead up (Horde)
    /** Now and then a hunting dead on soft ground, not yet at its quarry, stops to dig a fellow out (as Enhanced Hordes). */
    private void mayDig(ServerLevel level, double d) {
        if (digCooldown > 0) { digCooldown--; return; }
        if (!LimboTuning.digging || risen || !moundEligible() || !onGround() || d < LimboTuning.DIG_MIN_DISTANCE) return;
        if (random.nextFloat() >= LimboTuning.DIG_CHANCE || Horde.diggable(level, this) == null) return;
        if (LimboDirector.deadAround(level, position(), 32) >= LimboTuning.DIG_LOCAL_CAP) return;
        startDig();
    }

    /** Clawing at the ground for DIG_TICKS, a blow every second; then a common dead comes out beside it. */
    private void tickDig(ServerLevel level, LivingEntity q) {
        getNavigation().stop();
        BlockPos ground = Horde.diggable(level, this);
        if (ground == null) { digTicks = 0; return; } // shoved off the soft ground
        getLookControl().setLookAt(getX() + getLookAngle().x, getY(), getZ() + getLookAngle().z);
        if (digTicks % 20 == 1) Horde.claw(level, this, ground);
        if (++digTicks < LimboTuning.DIG_TICKS) return;
        digTicks = 0;
        digCooldown = LimboTuning.DIG_COOLDOWN;
        Horde.digUp(level, this, q, ground);
    }

    /** Up a ladder or vine: walk to its foot, press into the wall it hangs on (the climbing lifts it), step off at the top. */
    private boolean tickClimb(ServerLevel level) {
        Routes.Climb c = climb;
        if (c == null) return false;
        if (++climbTicks > 300 || !level.getBlockState(c.base()).is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
            failedAccess.put(c.base().asLong(), level.getGameTime() + LimboTuning.ACCESS_COOLDOWN);
            climb = null;
            return false;
        }
        if (onGround() && getY() >= c.top().getY() + 0.5) { climb = null; unreachable = 0; return false; }
        Vec3 foot = Vec3.atBottomCenterOf(c.base());
        if (!onClimbable() && getY() < c.base().getY() + 0.5) {
            double dx = getX() - foot.x, dz = getZ() - foot.z;
            if (dx * dx + dz * dz > 0.35 * 0.35) {
                if ((tickCount + getId()) % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(foot.x, foot.y, foot.z, chaseSpeed());
                return true;
            }
        }
        getNavigation().stop();
        Vec3 into = foot.add(Vec3.atLowerCornerOf(c.wall().getUnitVec3i()).scale(0.8));
        getMoveControl().setWantedPosition(into.x, getY() + 1, into.z, chaseSpeed());
        return true;
    }

    // ------------------------------------------------------------------ weak blocks
    /** A weak block just ahead, or (every {@code around} call) one in what hides the goal, walked to first. */
    private boolean seekWeak(ServerLevel level, Vec3 goal, boolean around) {
        BlockPos weak = Siege.weakToward(level, this, goal);
        if (weak != null) { startWeak(weak, null); return true; }
        if (!around) return false;
        Siege.Access far = Siege.weakAround(level, this, goal);
        if (far == null || failedAccess.get(far.block().asLong()) > level.getGameTime()) return false;
        startWeak(far.block(), Vec3.atBottomCenterOf(far.stand()));
        return true;
    }

    private void startWeak(BlockPos p, @Nullable Vec3 stand) {
        weakAt = p.immutable();
        weakStand = stand;
        weakTicks = 0;
        getNavigation().stop();
    }

    /** Glass, panes, leaves: battered for WEAK_PREPARE (cracks and blows on the block), then broken with their drops. */
    private void tickWeak(ServerLevel level) {
        BlockPos p = weakAt;
        BlockState s = level.getBlockState(p);
        if (!s.is(Siege.WEAK) || !Siege.allowed(level) || Provenance.protectedBlock(level, p, s)) { stopWeak(level); return; }
        Vec3 stand = weakStand;
        if (stand != null) {
            // On its way to a window found around the quarry: the preparation starts once it stands before it.
            if (distanceToSqr(Vec3.atCenterOf(p)) > 2.4 * 2.4) {
                if (++weakTicks > 200) { failedAccess.put(p.asLong(), level.getGameTime() + LimboTuning.ACCESS_COOLDOWN); stopWeak(level); return; }
                if (weakTicks % 10 == 1 || getNavigation().isDone()) getNavigation().moveTo(stand.x, stand.y, stand.z, chaseSpeed());
                return;
            }
            weakStand = null;
            weakTicks = 0;
        }
        if (distanceToSqr(Vec3.atCenterOf(p)) > 3.5 * 3.5) { stopWeak(level); return; }
        getNavigation().stop();
        getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (weakTicks % 10 == 0) {
            playAnim(ANIM_SIEGE, 10);
            level.playSound(null, p, s.getSoundType().getHitSound(), SoundSource.HOSTILE, 1.2f, 0.7f);
            level.playSound(null, p, ModSounds.DEAD_CLAW, SoundSource.HOSTILE, 0.8f, 0.9f + random.nextFloat() * 0.2f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        level.destroyBlockProgress(getId(), p, Math.min(9, weakTicks * 10 / LimboTuning.WEAK_PREPARE));
        if (++weakTicks >= LimboTuning.WEAK_PREPARE) {
            level.destroyBlockProgress(getId(), p, -1);
            level.destroyBlock(p, true, this);
            weakAt = null;
            unreachable = 0;
        }
    }

    private void stopWeak(ServerLevel level) {
        if (weakAt != null) level.destroyBlockProgress(getId(), weakAt, -1);
        weakAt = null;
        weakStand = null;
        weakTicks = 0;
    }

    /** True when the ground between this dead and {@code q} is open and walkable (no wall, no drop) along a straight line. */
    private boolean clearRun(ServerLevel level, LivingEntity q) {
        Vec3 from = position(), to = q.position();
        double len = from.distanceTo(to);
        for (double t = 0.5; t < len; t += 0.75) {
            Vec3 p = from.add(to.subtract(from).scale(t / len));
            BlockPos feet = BlockPos.containing(p.x, p.y + 0.1, p.z);
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty())
                return false; // a wall
            if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                    && level.getBlockState(feet.below(2)).getCollisionShape(level, feet.below(2)).isEmpty()) return false; // a drop
        }
        return true;
    }

    /** Debug: the pursuit counters (tests and /deathmask limbo debug). */
    public String debugState() {
        return mind + " unseen=" + unseen + " unreachable=" + unreachable + " quarry=" + (quarry != null) + (quarry != null ? " d=" + String.format(java.util.Locale.ROOT, "%.1f", distanceTo(quarry)) + " los=" + getSensing().hasLineOfSight(quarry) : "");
    }

    /** The noise of a chase reaches resting dead nearby: they come to look where the quarry is, without being told who it is. */
    private void commotion(ServerLevel level, LivingEntity q) {
        commotionCooldown = LimboTuning.COMMOTION_INTERVAL + random.nextInt(20);
        Vec3 at = q.position();
        for (LimboDead d : level.getEntitiesOfClass(LimboDead.class, getBoundingBox().inflate(LimboTuning.COMMOTION_RADIUS),
                x -> x != this && (x.mind == Mind.REST || x.mind == Mind.SUSPICION || x.mind == Mind.DISPERSAL) && !x.resting))
            d.hearAlert(at);
    }

    /** Counts how long the target has been out of reach; siege species look for a door to break, the others give up. */
    private void trackReach(ServerLevel level, Vec3 goal, double d) {
        Path path = getNavigation().getPath();
        // In the second row of a crowd the quarry is close and in sight but bodies are in the way: that is not "unreachable".
        // (A quarry standing above, on a tower or a wall, is close and in sight but out of reach: that is not a crowd.)
        boolean elevated = expanded() && quarry != null && quarry.getY() - getY() >= 1.5;
        boolean crowded = d < 7 && getSensing().hasLineOfSight(quarry != null ? quarry : this) && !elevated;
        boolean stuck = !crowded && d > reach() + 0.5 && (path == null || !path.canReach() || getNavigation().isDone());
        unreachable = stuck ? unreachable + 1 : Math.max(0, unreachable - 2);
        if (stuck && unreachable > 30 && siegePrepare() > 0 && Siege.allowed(level)) {
            BlockPos barrier = Siege.barrierToward(level, this, goal);
            if (barrier != null) { startSiege(barrier); return; }
        }
        if (expanded() && stuck && unreachable > 30 && quarry != null && findAnotherWay(level, quarry, goal)) { unreachable = 31; return; }
        if (unreachable > LimboTuning.UNREACHABLE_TICKS) toSearch(goal);
    }

    /** Species ability during pursuit. Return true when it took over this tick (dash, leap, shed, signal). */
    protected boolean pursueSpecial(ServerLevel level, LivingEntity q, double distance, boolean visible) { return false; }

    // ------------------------------------------------------------------ the announced attack
    protected void startWindup() {
        windup = windupTicks();
        if (!windupMoves()) getNavigation().stop();
        playAnim(ANIM_ATTACK, windupTicks() + 6);
        onWindup();
    }

    protected void onWindup() {}

    /**
     * The common dead keep running while they raise their arms (walking away from one fails; sprinting, a shield or
     * a first blow work). Heavy blows are planted: the striker stops, which is the opening to step out of the way.
     */
    protected boolean windupMoves() { return human(); }

    private void tickWindup(ServerLevel level, LivingEntity q) {
        if (windupMoves()) {
            if ((tickCount + getId()) % 4 == 0) getNavigation().moveTo(q, chaseSpeed() * 0.88);
        } else getNavigation().stop();
        getLookControl().setLookAt(q, 30f, 30f);
        if (--windup > 0) return;
        attackCooldown = attackRecovery();
        if (inReach(q, reach() + 0.8)) {
            ryuna.qdeath.limbo.LimboStats.strike(getUUID(), q.getUUID());
            strike(level, q);
        }
    }

    /** Within {@code range} (to the nearest point of its body) and in sight: blows never pass through walls. */
    protected boolean inReach(LivingEntity q, double range) {
        double dx = Math.max(0, Math.abs(q.getX() - getX()) - q.getBbWidth() / 2);
        double dz = Math.max(0, Math.abs(q.getZ() - getZ()) - q.getBbWidth() / 2);
        double dy = Math.max(0, Math.max(q.getY() - (getY() + getBbHeight()), getY() - (q.getY() + q.getBbHeight())));
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= range && getSensing().hasLineOfSight(q);
    }

    /** The blow that ends the windup. */
    protected void strike(ServerLevel level, LivingEntity q) { doHurtTarget(level, q); }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        // Consecrated light: no blow crosses it, in either direction.
        if (Consecration.at(level, target.position()) != null || Consecration.at(level, position()) != null) return false;
        return super.doHurtTarget(level, target);
    }

    // ------------------------------------------------------------------ siege
    private void startSiege(BlockPos at) {
        siegeAt = at;
        siegeTicks = 0;
        getNavigation().stop();
    }

    private void tickSiege(ServerLevel level) {
        BlockPos at = siegeAt;
        BlockState state = level.getBlockState(at);
        if (!state.is(Siege.BREAKABLE) || !Siege.allowed(level)) { stopSiege(level); return; }
        getNavigation().stop();
        getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        siegeTicks++;
        int total = siegePrepare();
        if (siegeTicks % 20 == 1) {
            playAnim(ANIM_SIEGE, 20);
            level.playSound(null, at, ModSounds.SIEGE_STRAIN, SoundSource.HOSTILE, 1.0f, 0.7f + random.nextFloat() * 0.2f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        }
        level.destroyBlockProgress(getId(), at, Math.min(9, siegeTicks * 10 / total));
        if (siegeTicks >= total) {
            level.destroyBlockProgress(getId(), at, -1);
            level.destroyBlock(at, true, this);
            playSound(ModSounds.OSSUARY_SLAM, 1.2f, 0.9f);
            siegeAt = null;
            unreachable = 0;
        }
    }

    private void stopSiege(ServerLevel level) {
        if (siegeAt != null) level.destroyBlockProgress(getId(), siegeAt, -1);
        siegeAt = null;
        siegeTicks = 0;
    }

    // ------------------------------------------------------------------ search and dispersal
    protected void toSearch(@Nullable Vec3 where) {
        if (siegeAt != null && level() instanceof ServerLevel l) stopSiege(l);
        climb = null;
        pile = null;
        digTicks = 0;
        windup = 0;
        clue = where;
        searchLeft = searchTicks();
        trailTime = level().getGameTime() - LimboTuning.TRAIL_LIFE;
        setMind(Mind.SEARCH);
    }

    private void search(ServerLevel level) {
        if (weakAt != null) { tickWeak(level); return; }
        if (--searchLeft <= 0) { lightInspected(level); toDispersal(); return; }
        Vec3 goal = searchGoal(level);
        if (searchSpecial(level, goal)) return;
        // A pane or a hedge between it and the clue is no reason to give up looking.
        if (expanded() && human() && goal != null && (tickCount + getId()) % 20 == 0 && getNavigation().isDone() && distanceToSqr(goal) > 6 && Siege.allowed(level)) {
            if (seekWeak(level, goal, true)) return;
        }
        // A light on a wall or a ceiling is never stood on: close enough, or as close as the ground allows, is "there".
        boolean atLight = lightClue != null && goal != null && (distanceToSqr(goal) < 9 || (getNavigation().isDone() && tickCount - lightSince > 40));
        if (goal != null && distanceToSqr(goal) > 2.0 && !atLight) {
            if ((tickCount + getId()) % 20 == 0 || getNavigation().isDone()) getNavigation().moveTo(goal.x, goal.y, goal.z, searchSpeed());
        } else {
            lightInspected(level);
            clue = null;
            getNavigation().stop();
            if (mindTicks % 30 == 0) {
                float yaw = yHeadRot + (random.nextFloat() - 0.5f) * 160f;
                getLookControl().setLookAt(getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * 4, getEyeY(), getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * 4);
            }
        }
    }

    /** Where to look next. The dead are drawn to Breath Lures; the Hound follows the quarry's breath. */
    protected @Nullable Vec3 searchGoal(ServerLevel level) {
        Vec3 lure = BreathClues.nearestLure(level, position(), lurePull());
        if (lure != null) return lure;
        if (followsBreath() && quarry != null) {
            BreathClues.Point next = BreathClues.follow(level, quarry.getUUID(), clue != null ? clue : position(), trailTime, 10);
            if (next != null && (clue == null || distanceToSqr(clue) < 4)) {
                trailTime = next.time();
                clue = next.pos();
                if (random.nextInt(3) == 0) playSound(ModSounds.HOUND_SNIFF, 0.8f, 1.0f);
            }
        }
        return clue;
    }

    /** Species behaviour while searching (a Ticker opening the wall that hides the clue). True when it took over this tick. */
    protected boolean searchSpecial(ServerLevel level, @Nullable Vec3 goal) { return false; }

    protected double lurePull() { return LimboTuning.LURE_PULL_DEAD; }
    protected boolean followsBreath() { return false; }

    private void toDispersal() {
        quarry = null;
        awareness = 0f;
        approachTicks = 0;
        setMind(Mind.DISPERSAL);
        Vec3 dest = home != null ? LandRandomPos.getPosTowards(this, 14, 6, Vec3.atBottomCenterOf(home)) : LandRandomPos.getPos(this, 14, 6);
        if (dest != null) getNavigation().moveTo(dest.x, dest.y, dest.z, wanderSpeed());
    }

    private void dispersal(ServerLevel level) {
        if (mindTicks < LimboTuning.DISPERSAL_TICKS) return;
        // Breach dead with nobody left to find sink back into the ash instead of roaming the Overworld.
        if (home != null && level.getNearestPlayer(this, 48) == null) { dissolve(level); return; }
        clue = null;
        setMind(Mind.REST);
    }

    public void dissolve(ServerLevel level) {
        level.sendParticles(ModParticles.ASH_FLAKE, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        discard();
    }

    // ------------------------------------------------------------------ holy ground
    private void leaveSanctuary(ServerLevel level, BlockPos lantern) {
        if (siegeAt != null) stopSiege(level);
        windup = 0;
        Vec3 out = Consecration.edge(lantern, position(), 3);
        if ((tickCount + getId()) % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(out.x, out.y, out.z, chaseSpeed());
        Vec3 push = new Vec3(getX() - lantern.getX() - 0.5, 0, getZ() - lantern.getZ() - 0.5);
        if (push.lengthSqr() > 1.0e-3) setDeltaMovement(getDeltaMovement().add(push.normalize().scale(0.06)));
        if (tickCount % 15 == 0) level.sendParticles(ModParticles.SOUL_MOTE, getX(), getY() + 1, getZ(), 3, 0.3, 0.5, 0.3, 0.01);
    }

    // ------------------------------------------------------------------ hurt, interrupt, rescue
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (!hurt || !isAlive()) return hurt;
        resting = false;
        if (siegeAt != null) { stopSiege(level); stagger(20); }
        if (weakAt != null) { stopWeak(level); stagger(LimboTuning.WEAK_RECOVERY); }
        if (digTicks > 0) { digTicks = 0; digCooldown = LimboTuning.DIG_COOLDOWN; stagger(12); } // a blow stops the digging
        if (mind == Mind.CALL && callInterruptible()) { callCooldown = 200; stagger(24); onCallInterrupted(level); }
        if (source.getEntity() instanceof LivingEntity attacker && attacker != this && LimboSenses.isLife(attacker) && attacker != quarry) {
            // Damage turns the dead toward whoever dealt it: that is how a friend pulls them off you.
            quarry = attacker;
            clue = attacker.position();
            if (mind == Mind.PURSUIT) unseen = 0;
            else if (mind != Mind.CALL) recognize(attacker, 6);
        }
        return hurt;
    }

    protected void onCallInterrupted(ServerLevel level) {}

    protected void stagger(int ticks) {
        windup = 0;
        busy = ticks;
        playAnim(ANIM_STAGGER, ticks);
        if (mind == Mind.CALL) startPursuit();
    }

    // ------------------------------------------------------------------ elites
    /** The Swift affix would push runners past the sprint they are tuned under; the dead of the Limbo never get it. */
    @Override protected boolean allowsAffix(Affix affix) { return affix != Affix.SWIFT; }

    // ------------------------------------------------------------------ processions
    /** Spawn-group data that turns a natural pack into a procession: the first is the leader, the rest walk behind it. */
    public static final class Procession implements SpawnGroupData {
        final UUID leader;
        int next;
        Procession(UUID leader) { this.leader = leader; }
    }

    public void follow(LimboDead lead, int slot) {
        this.leader = lead.getUUID();
        this.slot = slot;
    }

    private @Nullable LivingEntity leader(ServerLevel level) {
        if (leader == null) return null;
        Entity e = level.getEntity(leader);
        return e instanceof LimboDead d && d.isAlive() && distanceToSqr(d) < 48 * 48 ? d : null;
    }

    protected boolean isProcessionLeader() { return leader == null; }

    /** Points a procession leader's walk (radians, as atan2(dz, dx)). */
    public void setHeading(float radians) { heading = radians; nextWander = 0; }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        if (skinCount() > 0) setSkin(level.getRandom().nextInt(skinCount()));
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (reason == EntitySpawnReason.NATURAL) {
            if (data instanceof Procession p) { leader = p.leader; slot = p.next++; return p; }
            return new Procession(getUUID());
        }
        return result;
    }

    /** They do not vanish near the living, nor while hunting; far away they return to the ash like any monster. */
    @Override public boolean removeWhenFarAway(double distSqr) {
        return !mind.alert() && mind != Mind.SEARCH && distSqr > LimboTuning.DESPAWN_DISTANCE * LimboTuning.DESPAWN_DISTANCE;
    }

    // ------------------------------------------------------------------ persistence
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (leader != null) out.putString("LimboLeader", leader.toString());
        out.putInt("LimboSlot", slot);
        if (home != null) out.putLong("LimboHome", home.asLong());
        out.putBoolean("LimboResting", resting);
        out.putInt("LimboSkin", skin());
        if (plagueFocus >= 0) out.putInt("LimboPlagueFocus", plagueFocus);
        if (risen) out.putBoolean("LimboRisen", true);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        // Nothing that holds a dead in the air (a Ticker on a wall) survives a save: it lands.
        setNoGravity(false);
        String l = in.getStringOr("LimboLeader", "");
        leader = l.isEmpty() ? null : UUID.fromString(l);
        slot = in.getIntOr("LimboSlot", 0);
        long h = in.getLongOr("LimboHome", Long.MIN_VALUE);
        home = h == Long.MIN_VALUE ? null : BlockPos.of(h);
        resting = in.getBooleanOr("LimboResting", false);
        setSkin(in.getIntOr("LimboSkin", Math.floorMod(getUUID().hashCode(), Math.max(1, skinCount()))));
        plagueFocus = in.getIntOr("LimboPlagueFocus", -1);
        risen = in.getBooleanOr("LimboRisen", false);
    }

    @Override public void remove(RemovalReason reason) {
        if (siegeAt != null && level() instanceof ServerLevel l) l.destroyBlockProgress(getId(), siegeAt, -1);
        if (weakAt != null && level() instanceof ServerLevel l) l.destroyBlockProgress(getId(), weakAt, -1);
        super.remove(reason);
    }

    /** Shows the state above the head while /deathmask limbo debug is on; restores the elite title (or no name) when it is off. */
    public void refreshDebugName() {
        if (LimboDirector.debug) setCustomName(Component.literal("[" + mind.name().toLowerCase() + "]").withStyle(ChatFormatting.GRAY));
        else if (isElite()) setCustomName(eliteName());
        else setCustomName(null);
        setCustomNameVisible(LimboDirector.debug);
    }
}
