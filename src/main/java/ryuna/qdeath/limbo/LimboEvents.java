package ryuna.qdeath.limbo;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Where the world reaches the expansion: block placements and changes (provenance, light index, shared routes), the
 * noises of the living, and the interactions a possessed host refuses (trade, breeding, leads...).
 */
public final class LimboEvents {
    private LimboEvents() {}

    public static void register() {
        Provenance.init();
        Possession.init();
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer p && LimboSenses.isLife(p))
                Stimuli.publish(server, Vec3.atCenterOf(pos), Stimuli.Kind.BREAK);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> LightLures.unloaded(level, chunk.getPos()));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Possession.forget(entity.getUUID()));
        // A possessed host trades with nobody, is not fed into breeding, cannot be leashed or sheared away from the horde.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> Possession.isPossessed(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /** A player placed a block (BlockItemProvenanceMixin). */
    public static void placed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Provenance.placed(level, pos, level.getBlockState(pos));
        if (LimboSenses.isLife(player)) Stimuli.publish(level, Vec3.atCenterOf(pos), Stimuli.Kind.PLACE);
    }

    /** Any block change in a loaded server chunk (LevelChunkBlockChangeMixin). Kept cheap: it runs for every change. */
    public static void blockChanged(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState old, BlockState now) {
        Provenance.changed(chunk, pos, old, now);
        LightLures.changed(level, chunk, old, now);
        HordePaths.blockChanged(level, pos);
    }

    /** A player opened a container (ContainerOpenersCounterMixin): chests and barrels are heard. */
    public static void containerOpened(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock)
            Stimuli.publish(level, Vec3.atCenterOf(pos), Stimuli.Kind.CONTAINER);
    }
}
