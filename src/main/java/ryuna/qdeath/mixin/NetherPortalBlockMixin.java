package ryuna.qdeath.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryuna.qdeath.hell.HellTravel;

/** Nether portals lead to Hell: Overworld to the Ashen Vestibule, and every Hell layer back to the Overworld. */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Shadow
    private TeleportTransition getExitPortal(ServerLevel newLevel, Entity entity, BlockPos portalEntryPos, BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder) {
        throw new AssertionError();
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void quantumdeath$routeToHell(ServerLevel current, Entity entity, BlockPos entryPos, CallbackInfoReturnable<TeleportTransition> cir) {
        // Crossing an obsidian portal from the Overworld: in an older world this is the awakening (and it delivers a pending advancement).
        if (current.dimension() == Level.OVERWORLD && entity instanceof net.minecraft.server.level.ServerPlayer player) ryuna.qdeath.yokai.Awakening.portalCrossed(player);
        ResourceKey<Level> target = HellTravel.portalTarget(current.dimension());
        if (target == null) return;
        ServerLevel newLevel = current.getServer().getLevel(target);
        if (newLevel == null) return;
        WorldBorder border = newLevel.getWorldBorder();
        double scale = DimensionType.getTeleportationScale(current.dimensionType(), newLevel.dimensionType());
        BlockPos approx = border.clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
        cir.setReturnValue(getExitPortal(newLevel, entity, entryPos, approx, target != Level.OVERWORLD, border));
    }
}
