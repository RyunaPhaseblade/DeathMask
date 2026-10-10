package ryuna.qdeath.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ryuna.qdeath.entity.boss.BossSummons;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.registry.ModParticles;

/**
 * The summoning point of a layer boss. Bosses are re-summonable forever, so late friends can always earn their descent.
 * Using it empty-handed reads its inscription, which names the required offering.
 */
public class HellAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    public final HellLayer layer;

    public HellAltarBlock(HellLayer layer, Properties p) { super(p); this.layer = layer; }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(BossSummons.offering(layer))) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (BossSummons.summon(layer, server, pos, sp)) stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.translatable("altar.quantumdeath." + layer.id + ".inscription").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            player.sendSystemMessage(Component.translatable("altar.quantumdeath.requires", new net.minecraft.world.item.ItemStack(BossSummons.offering(layer)).getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        switch (layer) {
            case VESTIBULE -> { level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.04, 0); }
            case GORGE -> { if (random.nextInt(3) == 0) level.addParticle(ModParticles.BILE_DRIP, x + (random.nextDouble() - 0.5), y - 0.2, z + (random.nextDouble() - 0.5), 0, 0, 0); }
            case MIRE -> { if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.05, 0); }
            case DIS -> { level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0); }
            case ZENITH -> { if (random.nextInt(2) == 0) level.addParticle(ModParticles.VIGIL_MOTE, x + (random.nextDouble() - 0.5) * 2, y + random.nextDouble(), z + (random.nextDouble() - 0.5) * 2, 0, 0.01, 0); }
        }
    }
}
