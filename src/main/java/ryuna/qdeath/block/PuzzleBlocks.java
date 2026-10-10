package ryuna.qdeath.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ryuna.qdeath.entity.hell.HereticEntity;
import ryuna.qdeath.entity.vigil.VigilWakeEntity;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;

/** Blocks that drive the Ossuary of Dis and the final arena. */
public final class PuzzleBlocks {
    private PuzzleBlocks() {}

    private static final VoxelShape BELL_SHAPE = Block.box(3, 2, 3, 13, 16, 13);

    /**
     * The Choir of Bells. Each bell is named for one sin (NOTE 0..3: Pride, Gluttony, Deceit, Wrath).
     * The Ossuary library book gives the order the heretics fell: Deceit, Gluttony, Wrath, Pride.
     * Ringing them in that order breaks every Sealed Door nearby.
     */
    public static class ChoirBell extends Block {
        public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 3);
        public static final int[] ORDER = {2, 1, 3, 0};
        private static final Map<BlockPos, int[]> PROGRESS = new HashMap<>(); // key -> {index, lastTick}

        public ChoirBell(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(NOTE, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(NOTE); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return BELL_SHAPE; }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            int note = state.getValue(NOTE);
            float pitch = new float[]{0.5f, 0.63f, 0.75f, 0.94f}[note];
            server.playSound(null, pos, ModSounds.CHOIR_BELL, SoundSource.BLOCKS, 2.0f, pitch);
            server.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, note / 4.0);
            BlockPos key = roomKey(server, pos);
            int[] prog = PROGRESS.computeIfAbsent(key, k -> new int[]{0, 0});
            if (server.getGameTime() - prog[1] > 600) prog[0] = 0;
            prog[1] = (int) server.getGameTime();
            if (ORDER[prog[0]] == note) {
                prog[0]++;
                if (prog[0] == ORDER.length) {
                    PROGRESS.remove(key);
                    openSeals(server, pos);
                    player.sendSystemMessage(Component.translatable("puzzle.quantumdeath.choir_solved").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                }
            } else {
                prog[0] = 0;
                server.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5f, 0.5f);
                player.sendOverlayMessage(Component.translatable("puzzle.quantumdeath.choir_wrong").withStyle(ChatFormatting.DARK_RED));
                // A wrong note wakes a heretic from the walls.
                HereticEntity heretic = HellEntities.HERETIC.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (heretic != null) {
                    heretic.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5, 0, 0);
                    server.addFreshEntity(heretic);
                }
            }
            return InteractionResult.SUCCESS;
        }

        private static BlockPos roomKey(ServerLevel level, BlockPos pos) {
            BlockPos min = pos;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-10, -3, -10), pos.offset(10, 3, 10))) {
                if (level.getBlockState(p).is(ModBlocks.CHOIR_BELL) && p.asLong() < min.asLong()) min = p.immutable();
            }
            return min;
        }

        private static void openSeals(ServerLevel level, BlockPos pos) {
            level.playSound(null, pos, ModSounds.SEAL_BREAK, SoundSource.BLOCKS, 3.0f, 1.0f);
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-24, -8, -24), pos.offset(24, 8, 24))) {
                if (level.getBlockState(p).is(ModBlocks.SEALED_DOOR)) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    level.sendParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    /** The Unburied Inquisitor sleeps inside. Opening the lid wakes it; the tomb then burns forever. */
    public static class Sarcophagus extends HorizontalDirectionalBlock {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);
        public Sarcophagus(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            server.setBlockAndUpdate(pos, ModBlocks.BURNING_TOMB.defaultBlockState().setValue(FACING, state.getValue(FACING)));
            server.playSound(null, pos, ModSounds.SEAL_BREAK, SoundSource.BLOCKS, 2.0f, 0.6f);
            HereticEntity inquisitor = HellEntities.HERETIC.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (inquisitor != null) {
                inquisitor.makeInquisitor();
                inquisitor.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0);
                server.addFreshEntity(inquisitor);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** The four bells of the Last Vigil. Rung together, they strip VIGIL's veil in its final phase. */
    public static class VigilBell extends Block {
        public static final BooleanProperty RUNG = BooleanProperty.create("rung");
        public VigilBell(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(RUNG, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(RUNG); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return BELL_SHAPE; }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel server) || state.getValue(RUNG)) return InteractionResult.SUCCESS;
            server.playSound(null, pos, ModSounds.VIGIL_TOLL, SoundSource.BLOCKS, 4.0f, 1.2f);
            var bosses = server.getEntitiesOfClass(VigilWakeEntity.class, new AABB(pos).inflate(96));
            if (bosses.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("puzzle.quantumdeath.bell_silent").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            server.setBlockAndUpdate(pos, state.setValue(RUNG, true));
            bosses.forEach(b -> b.onBellRung(pos, player));
            return InteractionResult.SUCCESS;
        }
    }
}
