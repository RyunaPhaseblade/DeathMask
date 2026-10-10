package ryuna.qdeath.entity.hell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModParticles;

/**
 * Base for every Hell creature. Provides a synced "animation" channel (id + length) that models read as
 * a 0..1 progress, and the elite system: any Hell monster may spawn as a named elite with an affix.
 */
public abstract class HellMonster extends Monster implements HellMob {
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(HellMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_LEN = SynchedEntityData.defineId(HellMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_ELITE = SynchedEntityData.defineId(HellMonster.class, EntityDataSerializers.BYTE);

    public enum Affix {
        NONE(0), SEARING(0xff6a00), SWIFT(0x9be7ff), VAMPIRIC(0xb3001b), WARDED(0xe8d9a8), DREADFUL(0x2a1f3d);
        public final int color;
        Affix(int color) { this.color = color; }
    }

    public int animStartTick;
    private int animCounter;

    protected HellMonster(EntityType<? extends Monster> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ANIM, 0);
        b.define(DATA_ANIM_LEN, 1);
        b.define(DATA_ELITE, (byte) 0);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ANIM.equals(key)) animStartTick = tickCount;
    }

    /** Server: starts animation {@code id} lasting {@code length} ticks on every client. */
    public void playAnim(int id, int length) {
        entityData.set(DATA_ANIM_LEN, Math.max(1, length));
        entityData.set(DATA_ANIM, (++animCounter << 8) | (id & 0xff));
        animStartTick = tickCount;
    }

    public int animId() { return entityData.get(DATA_ANIM) & 0xff; }

    /** 0..1 progress of the current animation, or -1 when none is playing. */
    public float animProgress(float partial) {
        if (animId() == 0) return -1;
        float p = (tickCount - animStartTick + partial) / entityData.get(DATA_ANIM_LEN);
        return p >= 1 ? -1 : Math.max(0, p);
    }

    public Affix affix() { return Affix.values()[Math.min(entityData.get(DATA_ELITE), Affix.values().length - 1)]; }
    public boolean isElite() { return affix() != Affix.NONE; }

    /** Hell creatures spawn regardless of light, as long as the difficulty allows monsters. */
    public static <T extends Monster> boolean checkHellSpawn(EntityType<T> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
    }

    protected boolean canBeElite() { return true; }
    /** Elite chance at a given layer depth (6% on the first layer, +2% per layer). */
    protected float eliteChance(int depth) { return 0.04f + 0.02f * depth; }
    /** Affixes this creature may receive as an elite. */
    protected boolean allowsAffix(Affix affix) { return true; }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        HellLayer layer = HellLayer.of(level.getLevel());
        if (layer != null && canBeElite() && reason != EntitySpawnReason.SPAWN_ITEM_USE && level.getRandom().nextFloat() < eliteChance(layer.depth)) {
            java.util.List<Affix> options = java.util.Arrays.stream(Affix.values()).filter(a -> a != Affix.NONE && allowsAffix(a)).toList();
            makeElite(options.get(level.getRandom().nextInt(options.size())));
        }
        return result;
    }

    public void makeElite(Affix affix) {
        entityData.set(DATA_ELITE, (byte) affix.ordinal());
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.addPermanentModifier(new AttributeModifier(Quantumdeath.id("elite_health"), 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.addPermanentModifier(new AttributeModifier(Quantumdeath.id("elite_damage"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (affix == Affix.SWIFT) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addPermanentModifier(new AttributeModifier(Quantumdeath.id("elite_speed"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        setHealth(getMaxHealth());
        setCustomName(eliteName());
        setPersistenceRequired();
        xpReward *= 3;
    }

    protected Component eliteName() {
        return Component.translatable("elite.quantumdeath." + affix().name().toLowerCase(), getType().getDescription()).withStyle(ChatFormatting.GOLD);
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide() && isElite() && random.nextInt(3) == 0) {
            ParticleOptions p = switch (affix()) {
                case SEARING -> ParticleTypes.FLAME;
                case SWIFT -> ParticleTypes.CLOUD;
                case VAMPIRIC -> ModParticles.BLOOD_EMBER;
                case WARDED -> ParticleTypes.ENCHANT;
                default -> ModParticles.VIGIL_MOTE;
            };
            level().addParticle(p, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
        }
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            switch (affix()) {
                case SEARING -> living.igniteForSeconds(4);
                case VAMPIRIC -> heal(3.0f);
                case DREADFUL -> living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                default -> {}
            }
            onHitTarget(level, living);
        }
        return hit;
    }

    /** Hook for subclass on-hit effects. */
    protected void onHitTarget(ServerLevel level, LivingEntity target) {}

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (affix() == Affix.WARDED && source.getDirectEntity() != source.getEntity()) damage *= 0.4f;
        return super.hurtServer(level, source, damage);
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (isElite() && killedByPlayer) {
            spawnAtLocation(level, new ItemStack(HellItems.ASHEN_OBOL, 3 + random.nextInt(5)));
            if (random.nextFloat() < 0.25f) spawnAtLocation(level, new ItemStack(ryuna.qdeath.moditems.modItems.SOUL_VESSEL));
            if (random.nextFloat() < 0.15f) spawnAtLocation(level, new ItemStack(HellItems.JOURNAL_PAGE));
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putByte("HellAffix", entityData.get(DATA_ELITE));
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(DATA_ELITE, in.getByteOr("HellAffix", (byte) 0));
    }

    /** Vertical spread helper used by ranged Hell attacks. */
    protected static double horizontalDistance(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
