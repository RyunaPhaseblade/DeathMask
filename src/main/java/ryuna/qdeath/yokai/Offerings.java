package ryuna.qdeath.yokai;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

/** Prepared Offerings: the book brings an offering (or ritual tool) from the inventory to the hand. */
public final class Offerings {
    private Offerings() {}

    /** Items the book may select: every species' offerings, the amulet and the ritual thread. */
    public static boolean selectable(Item item) {
        if (item == YokaiContent.AMULET || item == YokaiContent.RITUAL_THREAD) return true;
        for (Species s : Species.values()) for (var o : s.offerings) if (o.get() == item) return true;
        return item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PLANKS);
    }

    public static void quickSelect(ServerPlayer player, String id) {
        if (!SkillManager.has(player, SkillId.SPIRIT_II)) return;
        Identifier key = Identifier.tryParse(id);
        if (key == null) return;
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (!selectable(item)) return;
        Inventory inv = player.getInventory();
        int found = -1;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) || item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PLANKS) && s.is(net.minecraft.tags.ItemTags.PLANKS)) { found = i; break; }
        }
        if (found < 0) return;
        if (Inventory.isHotbarSlot(found)) {
            inv.setSelectedSlot(found);
        } else {
            int sel = inv.getSelectedSlot();
            ItemStack hand = inv.getItem(sel);
            inv.setItem(sel, inv.getItem(found));
            inv.setItem(found, hand);
        }
        player.connection.send(new ClientboundSetHeldSlotPacket(inv.getSelectedSlot()));
        player.containerMenu.broadcastChanges();
    }
}
