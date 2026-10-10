package ryuna.qdeath.entity.yokai;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Perception;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;

/**
 * Nurikabe: a wall that is not there. It closes a path or a cave mouth for a while without placing or breaking a
 * single block. Striking its base (any player, even one who cannot see it) makes it step aside for 10 s and never
 * hurts it. It only moves back into a space nobody occupies, and it never appears closing the space a player stands in.
 */
public class NurikabeEntity extends AbstractYokai {
    public static final int ANIM_SHIFT = 1, ANIM_RETURN = 2;
    private @Nullable Vec3 post;
    private @Nullable Vec3 aside;
    private int asideTicks, met = -1, scrapeCooldown;

    public NurikabeEntity(EntityType<? extends NurikabeEntity> type, Level level) {
        super(type, level, Species.NURIKABE);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, YokaiBalance.Nurikabe.HEALTH).add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, 10);
    }

    @Override protected void registerGoals() {}

    @Override public boolean canBeCollidedWith(@Nullable Entity other) { return isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    /** Always pickable: even someone who cannot see it must be able to strike its base. */
    @Override public boolean isPickable() { return isAlive(); }

    @Override protected void serverTick(ServerLevel level) {
        if (post == null) post = position();
        Player near = level.getNearestPlayer(this, 6);
        if (near != null && met < 0) met = tickCount;
        // It closes the way for a while, then it is simply gone.
        if (met >= 0 && tickCount - met > YokaiBalance.Nurikabe.BLOCK_DURATION || tickCount > YokaiBalance.Nurikabe.BLOCK_DURATION * 3) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), getX(), getY() + 1.4, getZ(), 30, 0.8, 1.0, 0.3, 0.05);
            playSign(ModSounds.NURIKABE_SHIFT, 0.8f, 0.7f);
            discard();
            return;
        }
        if (asideTicks > 0 && --asideTicks == 0) aside = null;
        Vec3 goal = aside != null ? aside : post;
        Vec3 d = goal.subtract(position());
        if (d.lengthSqr() > 0.01) {
            // Return only into an empty space: never close the space someone is standing in.
            if (aside == null && !level.getEntities(this, getDimensions(getPose()).makeBoundingBox(goal).inflate(0.1), e -> e instanceof Player || e instanceof net.minecraft.world.entity.Mob).isEmpty()) {
                setDeltaMovement(Vec3.ZERO);
            } else {
                Vec3 step = d.lengthSqr() > 0.04 ? d.normalize().scale(0.15) : d;
                move(MoverType.SELF, new Vec3(step.x, 0, step.z));
            }
        }
        if (scrapeCooldown > 0) scrapeCooldown--;
        if (scrapeCooldown == 0) {
            for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.25), p -> !p.isSpectator())) {
                // Walking into it: an invisible wall.
                scrapeCooldown = 40;
                playSign(ModSounds.NURIKABE_SCRAPE, 0.8f, 0.9f + random.nextFloat() * 0.2f);
                if (p instanceof ServerPlayer sp) Bestiary.clue(sp, species(), "wall");
                break;
            }
        }
    }

    @Override public void travel(Vec3 input) {
        // It is moved only by serverTick (and never by gravity-less pushes).
        if (!onGround()) setDeltaMovement(0, Math.max(-0.5, getDeltaMovement().y - 0.08), 0);
        else setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** Struck at its base: it steps aside for 10 s. */
    private void stepAside(Player by) {
        if (post == null) post = position();
        Vec3 side = new Vec3(by.getX() - getX(), 0, by.getZ() - getZ()).normalize().cross(new Vec3(0, 1, 0)).scale(getBbWidth() + 0.6);
        for (Vec3 cand : new Vec3[]{post.add(side), post.subtract(side)}) {
            AABB box = getDimensions(getPose()).makeBoundingBox(cand);
            if (level().noCollision(this, box) && level().getEntities(this, box, e -> e instanceof Player).isEmpty()) {
                aside = cand;
                asideTicks = YokaiBalance.Nurikabe.STEP_ASIDE;
                playAnim(ANIM_SHIFT, 20);
                playSign(ModSounds.NURIKABE_SHIFT, 1.0f, 1.0f);
                return;
            }
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Player p && source.getEntity() == p) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getLookAngle().scale(6));
            AABB base = getBoundingBox().setMaxY(getY() + 1.2).inflate(0.05);
            if (base.clip(eye, end).isPresent() || base.contains(eye)) {
                stepAside(p);
                if (p instanceof ServerPlayer sp) Bestiary.clue(sp, species(), "scrape");
                return false;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.NURIKABE_SCRAPE; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.NURIKABE_SHIFT; }
    @Override protected SoundEvent getAmbientSound() { return null; }

    @Override public boolean removeWhenFarAway(double distance) { return true; }
}
