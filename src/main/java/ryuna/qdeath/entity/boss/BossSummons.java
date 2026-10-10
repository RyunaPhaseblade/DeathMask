package ryuna.qdeath.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModSounds;

/** Altars summon their layer's boss. The arena centre is 12 blocks north of the altar in every arena. */
public final class BossSummons {
    private BossSummons() {}

    public static final int ARENA_OFFSET = 12;

    public static Item offering(HellLayer layer) {
        return switch (layer) {
            case VESTIBULE -> HellItems.FERRYMAN_TOLL;
            case GORGE -> HellItems.GLUTTONS_OFFERING;
            case MIRE -> HellItems.STYGIAN_LURE;
            case DIS -> HellItems.HERETICS_CONFESSION;
            case ZENITH -> HellItems.LAST_CANDLE;
        };
    }

    public static EntityType<? extends HellBoss> bossType(HellLayer layer) {
        return switch (layer) {
            case VESTIBULE -> HellEntities.TOLL_KEEPER;
            case GORGE -> HellEntities.GORGED_MATRIARCH;
            case MIRE -> HellEntities.STYXMAW;
            case DIS -> HellEntities.HERESIARCH;
            case ZENITH -> HellEntities.VIGIL_WAKE;
        };
    }

    public static boolean summon(HellLayer layer, ServerLevel level, BlockPos altar, @Nullable ServerPlayer player) {
        if (!level.getEntitiesOfClass(HellBoss.class, new AABB(altar).inflate(96), b -> b.layer() == layer && b.isAlive()).isEmpty()) {
            if (player != null) player.sendOverlayMessage(Component.translatable("altar.quantumdeath.already_awake").withStyle(ChatFormatting.RED));
            return false;
        }
        if (player != null && HellLayer.of(level) != layer && !player.isCreative()) {
            player.sendOverlayMessage(Component.translatable("altar.quantumdeath.wrong_layer").withStyle(ChatFormatting.GRAY));
            return false;
        }
        HellBoss boss = bossType(layer).create(level, EntitySpawnReason.TRIGGERED);
        if (boss == null) return false;
        BlockPos center = arenaCenter(level, altar);
        boss.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 180f, 0f);
        boss.beginEntrance(center);
        level.addFreshEntity(boss);
        level.playSound(null, altar, ModSounds.ALTAR_SUMMON, SoundSource.BLOCKS, 3.0f, 1.0f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, altar.getX() + 0.5, altar.getY() + 1.2, altar.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        return true;
    }

    /** Arena centre for an altar: 12 blocks north, at the first open floor; falls back closer for hand-placed altars. */
    public static BlockPos arenaCenter(ServerLevel level, BlockPos altar) {
        for (int offset : new int[]{ARENA_OFFSET, 6, 3}) {
            BlockPos c = altar.north(offset);
            for (int dy = 0; dy <= 8; dy++) {
                for (int s : new int[]{1, -1}) {
                    BlockPos p = c.above(dy * s);
                    if (open(level, p)) return p;
                }
            }
        }
        return altar.above();
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p).isSolid() && !level.getBlockState(p.above()).isSolid() && !level.getBlockState(p.above(2)).isSolid()
                && (level.getBlockState(p.below()).isSolid() || !level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty());
    }
}
