package ryuna.qdeath.entity.yokai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.yokai.Bestiary;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiBalance;

import java.util.UUID;

/**
 * A tanuki's illusion: something shiny lying on the ground. It is not an item and cannot be picked up; whoever comes
 * close to investigate sees it for what it is (a puff of leaves). It never replaces or takes a real item. Not saved.
 */
public class TanukiDecoy extends Entity implements net.minecraft.world.entity.projectile.ItemSupplier {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(TanukiDecoy.class, EntityDataSerializers.ITEM_STACK);
    private static final ItemStack[] LURES = {new ItemStack(Items.EMERALD), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND),
            new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.AMETHYST_SHARD)};

    private @Nullable UUID maker;
    private int life;

    public TanukiDecoy(EntityType<? extends TanukiDecoy> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public void setup(@Nullable UUID tanuki) {
        maker = tanuki;
        entityData.set(DATA_ITEM, LURES[random.nextInt(LURES.length)].copy());
    }

    public ItemStack item() { return entityData.get(DATA_ITEM); }
    @Override public ItemStack getItem() { return item(); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(DATA_ITEM, new ItemStack(Items.EMERALD)); }

    @Override public void tick() {
        super.tick();
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).scale(0.98));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (++life > YokaiBalance.Tanuki.DECOY_LIFETIME) { discard(); return; }
        if (life % 30 == 0) level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0);
        if (life % 5 != 0) return;
        Player near = level.getNearestPlayer(this, 1.6);
        if (near instanceof ServerPlayer p && !p.isSpectator()) reveal(level, p);
    }

    /** Investigated: it was never there. */
    private void reveal(ServerLevel level, ServerPlayer by) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 8, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 0.3, getZ(), 10, 0.25, 0.2, 0.25, 0.02);
        level.playSound(null, blockPosition(), ModSounds.TANUKI_PUFF, SoundSource.NEUTRAL, 0.9f, 1.0f);
        Bestiary.clue(by, Species.TANUKI, "decoy");
        if (maker != null && level.getEntity(maker) instanceof TanukiEntity t) t.decoyFound(by);
        discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer p) reveal(level, p);
        return false;
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput in) {}
    @Override protected void addAdditionalSaveData(ValueOutput out) {}
}
