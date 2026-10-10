package ryuna.qdeath.entity.hell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModSounds;

/** A bloated thing that cannot stop eating. Retches bile from range; bursts into Gluttlings when it dies. */
public class GluttonEntity extends HellMonster {
    public static final int ANIM_BITE = 1, ANIM_VOMIT = 2;
    private int vomitCooldown = 40;

    public GluttonEntity(EntityType<? extends GluttonEntity> type, Level level) { super(type, level); xpReward = 10; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.19).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || --vomitCooldown > 0) return;
        double d = distanceTo(target);
        if (d > 4 && d < 14 && hasLineOfSight(target)) {
            vomitCooldown = 70 + random.nextInt(40);
            playAnim(ANIM_VOMIT, 20);
            playSound(ModSounds.GLUTTON_VOMIT, 1.2f, 0.9f);
            Vec3 mouth = position().add(getLookAngle().scale(0.8)).add(0, 1.2, 0);
            HellBolt.shootAt(level, this, HellBolt.Kind.BILE, mouth, target.position().add(0, 0.5, 0), 0.9f, 4f);
        }
    }

    @Override protected void onHitTarget(ServerLevel level, LivingEntity target) {
        playAnim(ANIM_BITE, 10);
        target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            int n = 2 + random.nextInt(2);
            for (int i = 0; i < n; i++) {
                GluttlingEntity g = HellEntities.GLUTTLING.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (g == null) continue;
                g.snapTo(getX() + random.nextGaussian() * 0.5, getY() + 0.5, getZ() + random.nextGaussian() * 0.5, random.nextFloat() * 360, 0);
                g.setTarget(getTarget());
                level.addFreshEntity(g);
            }
            playSound(ModSounds.GLUTTON_VOMIT, 1.5f, 0.6f);
        }
    }

    @Override protected SoundEvent getAmbientSound() { return ModSounds.GLUTTON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.GLUTTON_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_DEATH; }
}
