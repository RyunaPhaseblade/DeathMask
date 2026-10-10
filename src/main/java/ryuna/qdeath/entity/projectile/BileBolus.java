package ryuna.qdeath.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.BileResidue;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/**
 * The Bile Spitter's bolus: bile and tissue lobbed in an arc. A direct hit does 5 and slows (a shield blocks it all);
 * wherever it lands it leaves a puddle that slows whoever steps in it for a few seconds.
 */
public class BileBolus extends BodyProjectile {
    public BileBolus(EntityType<? extends BileBolus> type, Level level) { super(type, level); }

    @Override protected double getDefaultGravity() { return LimboTuning.BILE_GRAVITY; }

    @Override protected void trail() {
        level().addParticle(ModParticles.BILE_DRIP, getX(), getY(), getZ(), 0, -0.02, 0);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level && hit.getEntity() instanceof LivingEntity t
                && t.hurtServer(level, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), LimboTuning.BILE_DAMAGE))
            slow(t, LimboTuning.BILE_SLOW);
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 at = hit.getLocation();
        level.playSound(null, BlockPos.containing(at), ModSounds.SPITTER_SPLAT, SoundSource.HOSTILE, 1.2f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(ModParticles.BILE_DRIP, at.x, at.y + 0.1, at.z, 24, 0.4, 0.2, 0.4, 0.08);
        BlockPos floor = floor(level, hit instanceof EntityHitResult e ? e.getEntity().position() : at);
        if (floor != null && Consecration.at(level, Vec3.atBottomCenterOf(floor)) == null) {
            BileResidue r = HellEntities.BILE_RESIDUE.create(level, EntitySpawnReason.EVENT);
            if (r != null) {
                r.snapTo(at.x, floor.getY(), at.z, random.nextFloat() * 360f, 0);
                level.addFreshEntity(r);
            }
        }
        discard();
    }

    /** The ground under where it burst (a few blocks down at most), or null over a void. */
    private static @Nullable BlockPos floor(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at.x, at.y + 0.2, at.z);
        for (int i = 0; i < 4; i++, p = p.below())
            if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p;
        return null;
    }
}
