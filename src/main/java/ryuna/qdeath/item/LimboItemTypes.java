package ryuna.qdeath.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.entity.projectile.HellBolt;
import ryuna.qdeath.item.HellItemTypes.Lore;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModSounds;

/** Item behaviours of the Limbo. */
public final class LimboItemTypes {
    private LimboItemTypes() {}

    /** Funeral wax (and its consecrated form, which burns longer): feed it to a votive lantern to consecrate it. */
    public static class FuneralWax extends Lore {
        private final int units;
        public FuneralWax(Properties p, int units) { super(p, units >= LimboTuning.CONSECRATED_UNITS); this.units = units; }

        @Override public InteractionResult useOn(UseOnContext ctx) {
            Level level = ctx.getLevel();
            BlockState state = level.getBlockState(ctx.getClickedPos());
            if (!state.is(ModBlocks.VOTIVE_LANTERN)) return InteractionResult.PASS;
            if (level instanceof ServerLevel server) {
                Player player = ctx.getPlayer();
                if (!VotiveLantern.consecrate(server, ctx.getClickedPos(), state, units)) {
                    if (player != null) player.sendOverlayMessage(Component.translatable("item.quantumdeath.funeral_wax.full").withStyle(ChatFormatting.GRAY));
                    return InteractionResult.FAIL;
                }
                int fuel = server.getBlockState(ctx.getClickedPos()).getValue(VotiveLantern.FUEL);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("item.quantumdeath.funeral_wax.fed", fuel, LimboTuning.MAX_FUEL).withStyle(ChatFormatting.GOLD));
                    if (!player.isCreative()) ctx.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * A stoppered breath. Thrown, it bursts into a slow mist that smells of the living for a while: Hounds and the dead who
     * are searching go to it. It does not pull anything off a victim it can still see, and nothing alive comes out of it.
     */
    public static class BreathLure extends Lore {
        public BreathLure(Properties p) { super(p); }

        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                HellBolt.shoot(server, player, HellBolt.Kind.BREATH, 1.1f);
                server.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
                server.playSound(null, player.blockPosition(), ModSounds.LURE_BREATH, SoundSource.PLAYERS, 0.5f, 1.3f);
                if (!player.isCreative()) stack.shrink(1);
            }
            player.getCooldowns().addCooldown(stack, 20);
            return InteractionResult.SUCCESS;
        }
    }
}
