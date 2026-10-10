package ryuna.qdeath.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Shikigami amulet: reusable, one companion at a time, no energy cost. Bound during a pact; used on its companion it
 * stores it (or, if this amulet is free, rebinds it, voiding the old amulet); used on a block it summons it.
 */
public class AmuletItem extends Item {
    public AmuletItem(Properties p) { super(p); }

    @Override public InteractionResult useOn(UseOnContext ctx) {
        AmuletData d = Bonds.data(ctx.getItemInHand());
        if (!d.state().equals(AmuletData.STORED)) return InteractionResult.PASS;
        if (ctx.getPlayer() instanceof ServerPlayer p) return Bonds.summon(p, ctx.getItemInHand(), ctx.getClickedPos(), ctx.getClickedFace()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }

    @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner.tickCount % 20 == 0) Bonds.validate(level, stack);
    }

    @Override public boolean isFoil(ItemStack stack) { return Bonds.data(stack).state().equals(AmuletData.STORED); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        AmuletData d = Bonds.data(stack);
        Species s = Species.byId(d.species());
        Component who = d.name().isEmpty() ? (s == null ? Component.literal("?") : Component.translatable(s.key())) : Component.literal(d.name());
        switch (d.state()) {
            case AmuletData.DEPLOYED -> {
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.deployed", who).withStyle(ChatFormatting.AQUA));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.health", Math.round(d.health()), Math.round(d.max())).withStyle(ChatFormatting.GRAY));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.store_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            case AmuletData.STORED -> {
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.stored", who).withStyle(ChatFormatting.GOLD));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.health", Math.round(d.health()), Math.round(d.max())).withStyle(ChatFormatting.GRAY));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.summon_hint").withStyle(ChatFormatting.DARK_GRAY));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.warning").withStyle(ChatFormatting.RED));
            }
            case AmuletData.LOST -> {
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.lost", who).withStyle(ChatFormatting.DARK_RED));
                out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.empty_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> out.accept(Component.translatable("item.quantumdeath.shikigami_amulet.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
