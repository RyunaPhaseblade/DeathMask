package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.world.HellPiece;

final class TestUtil {
    private TestUtil() {}

    /** Origin of the arena nearest to (0,0) in a layer, or null. */
    static @Nullable BlockPos arenaOrigin(TestSingleplayerContext sp, HellLayer layer) {
        return sp.getServer().computeOnServer(server -> {
            ServerLevel level = server.getLevel(layer.level);
            BlockPos found = level.findNearestMapStructure(layer.arenaTag, BlockPos.ZERO, 64, false);
            if (found == null) return null;
            String id = switch (layer) { case VESTIBULE -> "ferry_landing"; case GORGE -> "feasting_pit"; case MIRE -> "drowned_choir"; case DIS -> "ossuary"; case ZENITH -> "last_vigil"; };
            var structure = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, ryuna.qdeath.Quantumdeath.id(id))).value();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var chunk = level.getChunk((found.getX() >> 4) + dx, (found.getZ() >> 4) + dz);
                StructureStart start = chunk.getStartForStructure(structure);
                if (start != null && start.isValid() && start.getPieces().getFirst() instanceof HellPiece piece) return piece.origin();
            }
            ryuna.qdeath.Quantumdeath.LOGGER.warn("GAMETEST: located {} near {} but found no start", id, found);
            return null;
        });
    }

    static void hideHud(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));
    }

    static void settle(ClientGameTestContext ctx, TestSingleplayerContext sp, int ticks) {
        ctx.waitTicks(ticks);
        sp.getConnection().waitForChunksRender(200);
        ctx.waitTicks(10);
    }

    /** Runs a command as (and at) the player, like typing it in chat. */
    static void cmd(TestSingleplayerContext sp, String command) {
        sp.getServer().runCommand("execute as @p at @p run " + command);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        ryuna.qdeath.Quantumdeath.LOGGER.info("GAMETEST OK: {}", what);
    }
}
