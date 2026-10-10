package ryuna.qdeath.entity.limbo;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.limbo.Possession;

/**
 * One length of a Strangler's tongue: a small hitbox strung along it so a valid attack anywhere on the tongue (by the
 * victim, a friend, an arrow) cuts it. It is never saved; both sides place it from its Strangler's synced tongue, so
 * the client aims at the same spot the server checks. It vanishes with the tongue.
 */
public class TongueSegment extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(TongueSegment.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(TongueSegment.class, EntityDataSerializers.INT);
    public static final int COUNT = 8;

    public TongueSegment(EntityType<? extends TongueSegment> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setup(StranglerEntity owner, int index) {
        entityData.set(DATA_OWNER, owner.getId());
        entityData.set(DATA_INDEX, index);
        place();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_OWNER, -1);
        b.define(DATA_INDEX, 0);
    }

    public @Nullable StranglerEntity owner() {
        return level().getEntity(entityData.get(DATA_OWNER)) instanceof StranglerEntity s ? s : null;
    }

    @Override public void tick() {
        super.tick();
        StranglerEntity s = owner();
        if (!level().isClientSide() && (s == null || !s.isAlive() || !s.tongueOut())) { discard(); return; }
        if (s != null) place();
    }

    private void place() {
        StranglerEntity s = owner();
        if (s == null) return;
        Vec3 a = s.tongueStart(1f), b = s.tongueEnd(1f);
        if (b == null) return;
        double t = (entityData.get(DATA_INDEX) + 1) / (double) (COUNT + 1);
        Vec3 p = a.add(b.subtract(a).scale(t));
        setPos(p.x, p.y - getBbHeight() / 2, p.z);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean canBeCollidedWith(@Nullable Entity other) { return false; }
    @Override public boolean isPushable() { return false; }

    /** Any real attack on the tongue cuts it, except the horde's own. */
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        Entity by = source.getEntity();
        if (by instanceof LimboDead || (by != null && Possession.isPossessed(by)) || damage <= 0) return false;
        StranglerEntity s = owner();
        if (s != null) s.sever(level);
        return true;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {}
    @Override protected void addAdditionalSaveData(ValueOutput out) {}
}
