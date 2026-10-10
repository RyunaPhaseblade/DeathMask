package ryuna.qdeath.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import ryuna.qdeath.limbo.Consecration;
import ryuna.qdeath.limbo.LimboTuning;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.hell.HellTravel;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

/** Small Hell block behaviours grouped in one place. */
public final class HellBlockTypes {
    private HellBlockTypes() {}

    /** Walk-in gate between layers. Descent requires the layer boss to have been beaten (or an escort). */
    public static class Gate extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 6, 16, 16, 10);
        private final boolean descend;
        public Gate(boolean descend, Properties p) { super(p); this.descend = descend; }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
        @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean precise) {
            if (entity instanceof ServerPlayer player) HellTravel.useGate(player, descend);
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(3) == 0) level.addParticle(descend ? ModParticles.GATE_EMBER : ParticleTypes.WHITE_ASH,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    0, descend ? -0.02 : 0.03, 0);
            if (random.nextInt(80) == 0) level.playLocalSound(pos, ModSounds.GATE_HUM, SoundSource.BLOCKS, 0.4f, descend ? 0.8f : 1.2f, false);
        }
    }

    /**
     * Sanctuary: VIGIL will not cross into the light of a votive lantern (always, fuel or not).
     * Consecrated with funeral wax, a lantern also keeps the common dead of the Limbo out while its fuel lasts:
     * the flame burns white-gold and the edge of its light glitters; at the last unit it gutters and sputters.
     */
    public static class VotiveLantern extends net.minecraft.world.level.block.LanternBlock {
        public static final int SANCTUARY_RADIUS = 8;
        public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, LimboTuning.MAX_FUEL);
        public VotiveLantern(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(HANGING, false).setValue(WATERLOGGED, false).setValue(FUEL, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(FUEL); }

        /** Adds {@code units} of fuel (capped). Returns false when the lantern is already full. */
        public static boolean consecrate(ServerLevel level, BlockPos pos, BlockState state, int units) {
            int fuel = state.getValue(FUEL);
            if (fuel >= LimboTuning.MAX_FUEL) return false;
            level.setBlock(pos, state.setValue(FUEL, Math.min(LimboTuning.MAX_FUEL, fuel + units)), 3);
            Consecration.set(level, pos, true);
            if (!level.getBlockTicks().hasScheduledTick(pos, state.getBlock())) level.scheduleTick(pos, state.getBlock(), LimboTuning.FUEL_UNIT_TICKS);
            level.playSound(null, pos, ModSounds.LANTERN_CONSECRATE, SoundSource.BLOCKS, 1.2f, 1.0f);
            level.sendParticles(ModParticles.VOTIVE_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.05);
            return true;
        }

        /** Burns one unit. Warns (sound) when only the last one is left, goes out with a hiss at zero. */
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int fuel = state.getValue(FUEL);
            if (fuel <= 0) { Consecration.set(level, pos, false); return; }
            fuel--;
            level.setBlock(pos, state.setValue(FUEL, fuel), 3);
            if (fuel == 1) level.playSound(null, pos, ModSounds.LANTERN_GUTTER, SoundSource.BLOCKS, 1.2f, 1.0f);
            if (fuel == 0) {
                Consecration.set(level, pos, false);
                level.playSound(null, pos, ModSounds.LANTERN_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 1.0f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.15, 0.2, 0.15, 0.01);
            } else {
                Consecration.set(level, pos, true); // also re-registers lanterns consecrated by worldgen
                level.scheduleTick(pos, this, LimboTuning.FUEL_UNIT_TICKS);
            }
        }

        @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
            Consecration.set(level, pos, false);
            super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        }

        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(4) == 0) level.addParticle(ModParticles.SOUL_MOTE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.5, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
            int fuel = state.getValue(FUEL);
            if (fuel == 0) return;
            // The boundary of consecrated light shows on the ground, so the safe ground is readable from outside.
            for (int i = 0; i < (fuel == 1 ? 1 : 3); i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ModParticles.VOTIVE_SPARK, pos.getX() + 0.5 + Math.cos(a) * SANCTUARY_RADIUS, pos.getY() + 0.2 + random.nextDouble() * 1.5,
                        pos.getZ() + 0.5 + Math.sin(a) * SANCTUARY_RADIUS, 0, 0.01, 0);
            }
            if (fuel == 1 && random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.02, 0);
            else if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 0, 0.005, 0);
        }
    }

    /** VIGIL traces: candles and ash prints that burn or blow away after a while. */
    public static class Decaying extends Block {
        private final VoxelShape shape;
        private final boolean flame;
        public Decaying(VoxelShape shape, boolean flame, Properties p) { super(p); this.shape = shape; this.flame = flame; }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return shape; }
        @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override protected boolean isRandomlyTicking(BlockState state) { return true; }
        @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (random.nextInt(6) == 0) level.removeBlock(pos, false);
        }
        @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }
        @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np, BlockState ns, RandomSource random) {
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (flame && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.6, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
                if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 0, 0.02, 0);
            }
        }
    }

    /** Decorative horizontally facing block with a custom shape (remains, tombs, altars base). */
    public static class Facing extends HorizontalDirectionalBlock {
        private final VoxelShape shape;
        public Facing(VoxelShape shape, Properties p) { super(p); this.shape = shape; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return shape; }
    }

    /** Burning tomb of the heretics (Dis): fire burns on its lid forever. */
    public static class BurningTomb extends Facing {
        public BurningTomb(Properties p) { super(Block.box(0, 0, 0, 16, 12, 16), p); }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            for (int i = 0; i < 2; i++) level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
            if (random.nextInt(30) == 0) level.playLocalSound(pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.7f, false);
        }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (!entity.isSteppingCarefully() && entity instanceof LivingEntity && !entity.fireImmune()) entity.igniteForSeconds(3);
            super.stepOn(level, pos, state, entity);
        }
    }

    /** Teeth in the floor of the Gorge: snaps shut on anything that walks over it without sneaking. */
    public static class MawTrap extends Block {
        public static final BooleanProperty BITING = BooleanProperty.create("biting");
        public MawTrap(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(BITING, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(BITING); }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (level instanceof ServerLevel server && !state.getValue(BITING) && entity instanceof LivingEntity living && !entity.isSteppingCarefully()
                    && !(entity instanceof ryuna.qdeath.entity.hell.HellMob)) {
                living.hurtServer(server, server.damageSources().cactus(), 5.0f);
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                living.makeStuckInBlock(state, new Vec3(0.2, 0.5, 0.2));
                server.setBlock(pos, state.setValue(BITING, true), 3);
                server.scheduleTick(pos, this, 40);
                server.playSound(null, pos, ModSounds.MAW_BITE, SoundSource.BLOCKS, 1.0f, 0.8f + server.getRandom().nextFloat() * 0.3f);
                server.sendParticles(ModParticles.BILE_DRIP, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.05);
            }
            super.stepOn(level, pos, state, entity);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(BITING)) level.setBlock(pos, state.setValue(BITING, false), 3);
        }
    }

    /** Bone fangs jutting from floors and ceilings of the Gorge. */
    public static class FangSpike extends Block {
        public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);
        public FangSpike(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(HANGING, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HANGING); }
        @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(HANGING, ctx.getClickedFace() == Direction.DOWN);
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
        @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockPos support = state.getValue(HANGING) ? pos.above() : pos.below();
            return level.getBlockState(support).isFaceSturdy(level, support, state.getValue(HANGING) ? Direction.DOWN : Direction.UP);
        }
        @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np, BlockState ns, RandomSource random) {
            return !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
        @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean precise) {
            if (entity instanceof LivingEntity living && !(entity instanceof ryuna.qdeath.entity.hell.HellMob)) {
                entity.makeStuckInBlock(state, new Vec3(0.7, 0.75, 0.7));
                if (level instanceof ServerLevel server && (entity.xOld != entity.getX() || entity.zOld != entity.getZ()))
                    living.hurtServer(server, server.damageSources().sweetBerryBush(), 2.0f);
            }
        }
    }

    /** Tar and bile: sticky, slowing; bile also sickens. */
    public static class Sticky extends Block {
        private final boolean poison;
        public Sticky(boolean poison, Properties p) { super(p); this.poison = poison; }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (poison && !level.isClientSide() && entity instanceof LivingEntity living && !(entity instanceof ryuna.qdeath.entity.hell.HellMob) && level.getGameTime() % 20 == 0)
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            super.stepOn(level, pos, state, entity);
        }
    }

    /** Ossuary floors that give way a moment after being stepped on. */
    public static class Crumbling extends Block {
        public Crumbling(Properties p) { super(p); }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (level instanceof ServerLevel server && entity instanceof Player && !server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 12);
                server.playSound(null, pos, SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 1.0f, 0.5f);
            }
            super.stepOn(level, pos, state, entity);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.destroyBlock(pos, false);
            // Neighbouring tiles go too, so the floor collapses as a pit rather than single holes.
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = pos.relative(d);
                if (level.getBlockState(n).is(this) && !level.getBlockTicks().hasScheduledTick(n, this)) level.scheduleTick(n, this, 4 + random.nextInt(6));
            }
        }
    }

    /** Zenith ground that is not really there: vanishes underfoot and returns later. */
    public static class FalseBlock extends Block {
        public FalseBlock(Properties p) { super(p); }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (level instanceof ServerLevel server && entity instanceof Player && !server.getBlockTicks().hasScheduledTick(pos, this)) server.scheduleTick(pos, this, 8);
            super.stepOn(level, pos, state, entity);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.setBlock(pos, ModBlocks.FALSE_AIR.defaultBlockState(), 3);
            level.scheduleTick(pos, ModBlocks.FALSE_AIR, 160 + random.nextInt(80));
            level.sendParticles(ModParticles.STATIC, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
            level.playSound(null, pos, ModSounds.STATIC_BURST, SoundSource.BLOCKS, 0.5f, 1.0f + random.nextFloat() * 0.3f);
        }
    }

    public static class FalseAir extends Block {
        public FalseAir(Properties p) { super(p); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos), e -> e instanceof LivingEntity).isEmpty())
                level.setBlock(pos, ModBlocks.FALSE_BLOCK.defaultBlockState(), 3);
            else level.scheduleTick(pos, this, 20);
        }
    }

    /** Logs of the forest of suicides: they bleed and cry out when cut. */
    public static class BleedingLog extends RotatedPillarBlock {
        public BleedingLog(Properties p) { super(p); }
        @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (level instanceof ServerLevel server) {
                server.sendParticles(ModParticles.BLOOD_EMBER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
                server.playSound(null, pos, ModSounds.TREE_WAIL, SoundSource.BLOCKS, 0.7f, 0.8f + server.getRandom().nextFloat() * 0.4f);
            }
            return super.playerWillDestroy(level, pos, state, player);
        }
    }

    /** Emissive glitch matter of the Zenith. */
    public static class Static extends Block {
        public Static(Properties p) { super(p); }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(6) == 0) {
                Direction d = Direction.getRandom(random);
                BlockPos side = pos.relative(d);
                if (!level.getBlockState(side).isSolidRender()) level.addParticle(ModParticles.STATIC,
                        side.getX() + random.nextDouble(), side.getY() + random.nextDouble(), side.getZ() + random.nextDouble(), 0, 0, 0);
            }
            if (random.nextInt(200) == 0) level.playLocalSound(pos, ModSounds.STATIC_BURST, SoundSource.BLOCKS, 0.25f, 0.6f + random.nextFloat(), false);
        }
    }
}
