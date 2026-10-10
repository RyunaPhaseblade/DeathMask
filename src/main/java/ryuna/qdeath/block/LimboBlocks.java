package ryuna.qdeath.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.plague.PlagueDirector;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/** Blocks of the Limbo: deep ash, unstable bone, sealed graves and the Overworld breach anchor. */
public final class LimboBlocks {
    private LimboBlocks() {}

    /** Deep ash: the foot sinks in and leaves prints; it slows, it never traps (there is always firmer ground around). */
    public static class DeepAsh extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);
        public DeepAsh(Properties p) { super(p); }
        @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
        @Override protected VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) { return Shapes.block(); }
        @Override protected VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.block(); }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (level instanceof ServerLevel server && entity instanceof LivingEntity && (entity.xOld != entity.getX() || entity.zOld != entity.getZ()) && server.getRandom().nextInt(4) == 0)
                server.sendParticles(ModParticles.ASH_FLAKE, entity.getX(), pos.getY() + 0.8, entity.getZ(), 3, 0.2, 0.05, 0.2, 0.01);
            super.stepOn(level, pos, state, entity);
        }
    }

    /**
     * Unstable bone: the Limbo's take on the crumbling bone. Weight cracks it in three visible stages, each with a creak,
     * before it gives way; step off in time and it holds (at its new crack).
     */
    public static class UnstableBone extends Block {
        public static final IntegerProperty CRACKS = IntegerProperty.create("cracks", 0, 3);
        public UnstableBone(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(CRACKS, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(CRACKS); }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (level instanceof ServerLevel server && entity instanceof Player && !server.getBlockTicks().hasScheduledTick(pos, this))
                server.scheduleTick(pos, this, 12);
            super.stepOn(level, pos, state, entity);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int cracks = state.getValue(CRACKS);
            if (cracks >= 3) {
                level.destroyBlock(pos, false);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = pos.relative(d);
                    if (level.getBlockState(n).is(this) && !level.getBlockTicks().hasScheduledTick(n, this)) level.scheduleTick(n, this, 10 + random.nextInt(10));
                }
                return;
            }
            level.setBlock(pos, state.setValue(CRACKS, cracks + 1), 3);
            level.playSound(null, pos, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 0.5f + cracks * 0.1f);
            level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.01);
            // Still loaded? It keeps cracking only while someone stands on it.
            boolean weighed = !level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos.above()).inflate(0.1, 0.3, 0.1)).isEmpty();
            if (weighed || cracks + 1 >= 3) level.scheduleTick(pos, this, cracks + 1 >= 3 ? 8 : 12);
        }
    }

    /**
     * A sealed grave of the Limbo: each holds exactly one of the dead (a finite reserve). When life comes near, the earth
     * scrapes and shifts first; then the occupant climbs out and the grave stays open forever. Graves placed by players
     * are already empty.
     */
    public static class Grave extends HorizontalDirectionalBlock {
        public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
        public static final int SEALED = 0, STIRRING = 1, OPEN = 2;
        private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(4, 0, 0, 12, 13, 2));

        public Grave(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, SEALED)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, STAGE); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(STAGE, OPEN);
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

        /** Starts the warning; the occupant rises when the scheduled tick fires. */
        public static void stir(ServerLevel level, BlockPos pos, BlockState state) {
            if (!state.is(ModBlocks.LIMBO_GRAVE) || state.getValue(STAGE) != SEALED) return;
            level.setBlock(pos, state.setValue(STAGE, STIRRING), 3);
            level.scheduleTick(pos, state.getBlock(), LimboTuning.GRAVE_WARNING_TICKS + level.getRandom().nextInt(20));
            level.playSound(null, pos, ModSounds.GRAVE_STIR, SoundSource.HOSTILE, 1.0f, 0.8f + level.getRandom().nextFloat() * 0.3f);
        }

        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(STAGE) == STIRRING) release(level, pos, state);
        }

        private static void release(ServerLevel level, BlockPos pos, BlockState state) {
            level.setBlock(pos, state.setValue(STAGE, OPEN), 3);
            EntityType<? extends LimboDead> type = level.getRandom().nextFloat() < 0.55f ? HellEntities.CRAWLER : HellEntities.HOLLOW;
            LimboDead d = type.create(level, EntitySpawnReason.TRIGGERED);
            if (d == null) return;
            Direction f = state.getValue(FACING);
            BlockPos at = pos.relative(f);
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !ryuna.qdeath.limbo.Processions.clearOfPlayers(level, at, 1.5)) at = pos.above();
            d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, f.toYRot(), 0);
            d.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
            level.addFreshEntity(d);
            d.emerge(30);
            level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 16, 0.4, 0.2, 0.4, 0.02);
            level.playSound(null, pos, ModSounds.CRAWLER_SCRAPE, SoundSource.HOSTILE, 1.2f, 0.7f);
        }

        /** Digging into a full grave only lets its occupant out sooner. */
        @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (level instanceof ServerLevel server && state.getValue(STAGE) != OPEN) release(server, pos, state);
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(STAGE) != STIRRING) return;
            for (int i = 0; i < 3; i++) level.addParticle(ModParticles.ASH_FLAKE, pos.getX() + random.nextDouble(), pos.getY() + 0.4, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
            if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    /**
     * Breach anchor of a plague focus in the Overworld: an ash-grey lodestone weeping soul-light. Unbreakable; it is
     * purged by holding a rite beside it (use it, then stay close and keep looking at it).
     */
    public static class BreachAnchor extends Block {
        public BreachAnchor(Properties p) { super(p); }
        @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) PlagueDirector.startPurge(server, sp, pos);
            return InteractionResult.SUCCESS;
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            level.addParticle(ModParticles.ASH_FLAKE, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.04, 0);
            if (random.nextInt(3) == 0) level.addParticle(ModParticles.SOUL_MOTE, pos.getX() + 0.5 + (random.nextDouble() - 0.5), pos.getY() + 1.2, pos.getZ() + 0.5 + (random.nextDouble() - 0.5), 0, 0.03, 0);
        }
    }
}
