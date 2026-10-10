package ryuna.qdeath.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/** The Rib Thrower's rib: a fast, slightly dropping physical throw. 6 on a direct hit (a shield blocks it); nothing else. */
public class RibShard extends BodyProjectile {
    public RibShard(EntityType<? extends RibShard> type, Level level) { super(type, level); }

    @Override protected double getDefaultGravity() { return LimboTuning.RIB_GRAVITY; }

    @Override protected void trail() {
        if (tickCount % 2 == 0) level().addParticle(ModParticles.BLOOD_EMBER, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level && hit.getEntity() instanceof LivingEntity t)
            t.hurtServer(level, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), LimboTuning.RIB_DAMAGE);
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = hit.getLocation();
        level.playSound(null, BlockPos.containing(at), ModSounds.RIBS_IMPACT, SoundSource.HOSTILE, 1.1f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BONE), at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.12);
        discard();
    }
}
