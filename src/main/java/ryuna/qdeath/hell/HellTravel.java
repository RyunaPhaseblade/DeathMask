package ryuna.qdeath.hell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Portal routing, layer gates and arrival shrines. */
public final class HellTravel {
    private HellTravel() {}

    /** Players standing this close to someone who may pass a gate are carried through with them. */
    public static final double ESCORT_RADIUS = 6.0;

    /** Nether portals: Overworld leads to the Vestibule; every Hell layer leads back to the Overworld. */
    public static @Nullable ResourceKey<Level> portalTarget(ResourceKey<Level> from) {
        if (from == Level.OVERWORLD) return HellLayer.VESTIBULE.level;
        if (HellLayer.of(from) != null) return Level.OVERWORLD;
        return null;
    }

    /** Called by a gate block when a player walks into it. */
    public static void useGate(ServerPlayer player, boolean descend) {
        if (player.isOnPortalCooldown()) return;
        player.setPortalCooldown(60);
        HellLayer here = HellLayer.of(player.level());
        if (here == null) return;
        HellLayer target = descend ? here.below() : here.above();
        if (target == null) return;
        HellProgress progress = HellProgress.of(player);
        if (descend && !progress.canEnter(target)) {
            ServerPlayer escort = findEscort(player, target);
            if (escort == null) {
                player.sendOverlayMessage(Component.translatable("message.quantumdeath.gate_sealed." + here.id).withStyle(ChatFormatting.DARK_RED));
                player.level().playSound(null, player.blockPosition(), ModSounds.GATE_DENIED, SoundSource.BLOCKS, 1.0f, 1.0f);
                return;
            }
            player.sendSystemMessage(Component.translatable("message.quantumdeath.escorted", escort.getDisplayName()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        List<ServerPlayer> group = new ArrayList<>();
        group.add(player);
        // Anyone already allowed through who is standing beside the traveller comes along as well.
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level() && other.distanceTo(player) <= ESCORT_RADIUS && !other.isSpectator()) group.add(other);
        }
        travel(group, target);
    }

    private static @Nullable ServerPlayer findEscort(ServerPlayer player, HellLayer target) {
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level() && other.distanceTo(player) <= ESCORT_RADIUS && HellProgress.of(other).canEnter(target)) return other;
        }
        return null;
    }

    /** Moves the whole group to {@code target}, keeping X/Z, and builds an arrival shrine there. */
    public static void travel(List<ServerPlayer> group, HellLayer target) {
        ServerPlayer lead = group.getFirst();
        ServerLevel level = lead.level().getServer().getLevel(target.level);
        if (level == null) return;
        Vec3 arrival = Vec3.atBottomCenterOf(buildShrineNear(level, target, lead.blockPosition()));
        for (int i = 0; i < group.size(); i++) {
            ServerPlayer p = group.get(i);
            Vec3 spot = arrival.add((i % 3) - 1, 0, -(i / 3));
            p.setPortalCooldown(100);
            p.teleport(new TeleportTransition(level, spot, Vec3.ZERO, 180f, 0f, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        }
    }

    /** Teleports one player to a safe spot of a layer (commands, compass of last resort). */
    public static void sendTo(ServerPlayer player, HellLayer target, boolean shrine) {
        ServerLevel level = player.level().getServer().getLevel(target.level);
        if (level == null) return;
        BlockPos pos = shrine ? buildShrineNear(level, target, player.blockPosition()) : findSafe(level, target, player.blockPosition());
        player.setPortalCooldown(100);
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.getYRot(), 0f, Set.<Relative>of(), TeleportTransition.DO_NOTHING));
    }

    public static void announce(ServerPlayer player, HellLayer layer) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(30, 80, 40));
        player.connection.send(new ClientboundSetSubtitleTextPacket(layer.subtitle()));
        player.connection.send(new ClientboundSetTitleTextPacket(layer.title()));
    }

    /** Finds a standing position in the column at {@code near}, searching outward; builds a platform when the layer is hollow there. */
    public static BlockPos findSafe(ServerLevel level, HellLayer layer, BlockPos near) {
        for (int r = 0; r <= 24; r += 4) {
            for (int dx = -r; dx <= r; dx += Math.max(1, r)) {
                for (int dz = -r; dz <= r; dz += Math.max(1, r)) {
                    BlockPos found = scanColumn(level, near.getX() + dx, near.getZ() + dz);
                    if (found != null) return found;
                }
            }
        }
        BlockPos platform = new BlockPos(near.getX(), Math.max(level.getSeaLevel() + 6, level.getMinY() + 40), near.getZ());
        for (BlockPos p : BlockPos.betweenClosed(platform.offset(-2, -1, -2), platform.offset(2, -1, 2))) level.setBlockAndUpdate(p, brick(layer));
        for (BlockPos p : BlockPos.betweenClosed(platform.offset(-2, 0, -2), platform.offset(2, 3, 2))) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        return platform;
    }

    private static @Nullable BlockPos scanColumn(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // force generation
        int top = Math.min(level.getMinY() + level.dimensionType().logicalHeight() - 3, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        int best = Integer.MIN_VALUE;
        int sea = level.getSeaLevel();
        for (int y = top; y > level.getMinY() + 2; y--) {
            pos.setY(y);
            if (!isStandable(level, pos)) continue;
            // Prefer the floor closest to (and above) the fluid sea: open caverns rather than tiny pockets near the roof.
            if (best == Integer.MIN_VALUE || Math.abs(y - (sea + 8)) < Math.abs(best - (sea + 8))) best = y;
        }
        return best == Integer.MIN_VALUE ? null : new BlockPos(x, best, z);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.BEDROCK) || below.is(ModBlocks.MOLTEN_DIS_IRON)) return false;
        return isClear(level.getBlockState(pos)) && isClear(level.getBlockState(pos.above())) && isClear(level.getBlockState(pos.above(2)));
    }

    private static boolean isClear(BlockState state) {
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty() && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE);
    }

    public static BlockState brick(HellLayer layer) {
        Block b = switch (layer) {
            case VESTIBULE -> ModBlocks.ASHEN_BRICKS;
            case GORGE -> ModBlocks.GRISTLE_BRICKS;
            case MIRE -> ModBlocks.STYGIAN_BRICKS;
            case DIS -> ModBlocks.DIS_BRICKS;
            case ZENITH -> ModBlocks.ZENITH_BRICKS;
        };
        return b.defaultBlockState();
    }

    /**
     * An arrival shrine: a small brick platform with a votive lantern (VIGIL will not enter its light),
     * an ascent gate back up, and a descent gate further down when a deeper layer exists.
     * Reuses an existing shrine within 12 blocks so repeated trips do not litter the world.
     */
    public static BlockPos buildShrineNear(ServerLevel level, HellLayer layer, BlockPos near) {
        BlockPos gate = findBlock(level, near, ModBlocks.GATES, 12);
        if (gate != null) {
            while (ModBlocks.GATES.contains(level.getBlockState(gate.below()).getBlock())) gate = gate.below();
            ryuna.qdeath.Quantumdeath.LOGGER.info("Reusing shrine in {} at gate {}", layer.id, gate);
            return gate.north(2);
        }
        BlockPos base = findSafe(level, layer, near);
        BlockState brick = brick(layer);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(3, -1, 3))) level.setBlockAndUpdate(p, brick);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 3, 3))) {
            if (!level.getBlockState(p).isAir()) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        if (layer.above() != null) gateway(level, base.offset(-2, 0, 2), ModBlocks.ASCENT_GATE.defaultBlockState(), brick);
        if (layer.below() != null) gateway(level, base.offset(2, 0, 2), ModBlocks.DESCENT_GATE.defaultBlockState(), brick);
        level.setBlockAndUpdate(base.offset(0, 0, 3), brick);
        level.setBlockAndUpdate(base.offset(0, 1, 3), ModBlocks.VOTIVE_LANTERN.defaultBlockState());
        level.playSound(null, base, ModSounds.SHRINE_FORMS, SoundSource.BLOCKS, 1.0f, 1.0f);
        ryuna.qdeath.Quantumdeath.LOGGER.info("Arrival shrine in {} at {}", layer.id, base);
        return base.north();
    }

    private static void gateway(ServerLevel level, BlockPos pos, BlockState gate, BlockState frame) {
        level.setBlockAndUpdate(pos, gate);
        level.setBlockAndUpdate(pos.above(), gate);
        level.setBlockAndUpdate(pos.west(), frame);
        level.setBlockAndUpdate(pos.west().above(), frame);
        level.setBlockAndUpdate(pos.east(), frame);
        level.setBlockAndUpdate(pos.east().above(), frame);
        level.setBlockAndUpdate(pos.above(2).west(), frame);
        level.setBlockAndUpdate(pos.above(2), frame);
        level.setBlockAndUpdate(pos.above(2).east(), frame);
    }

    public static @Nullable BlockPos findBlock(ServerLevel level, BlockPos center, Set<Block> blocks, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
            if (blocks.contains(level.getBlockState(p).getBlock())) return p.immutable();
        }
        return null;
    }

    /** True when the entity is inside a Hell layer. */
    public static boolean inHell(Entity entity) { return HellLayer.of(entity.level()) != null; }
}
