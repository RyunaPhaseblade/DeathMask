package ryuna.qdeath.moditems.items;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ryuna.qdeath.skills.SkillPackets;

/** Opens the skill tree after the server confirms use of this item. */
public class WisdomOfTheVoidItem extends Item {
    public WisdomOfTheVoidItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new SkillPackets.OpenSkills());
        }
        return InteractionResult.SUCCESS;
    }
}
