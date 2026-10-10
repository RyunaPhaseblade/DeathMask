package ryuna.qdeath.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;

import static ryuna.qdeath.test.TestUtil.*;

/**
 * Screenshots of the item and equipment art (build/run/clientGameTest/screenshots/items_*): every sprite in the inventory,
 * the boss weapons in first and third person, each armor set worn by the player and by armor stands, and weapons in
 * item frames and on the ground. Skip with -Dquantumdeath.skipItems=true.
 */
public class ItemVisualTest implements FabricClientGameTest {
    static final String[] WEAPONS = {"requiem", "heretics_brand", "maw_cleaver", "stygian_harpoon", "toll_keepers_oar"};
    static final String[][] SETS = {
            {"pilgrim_hood", "pilgrim_robe", "pilgrim_leggings", "pilgrim_boots"},
            {"leviathan_helm", "leviathan_cuirass", "leviathan_greaves", "leviathan_boots"},
            {"dis_iron_helmet", "dis_iron_chestplate", "dis_iron_leggings", "dis_iron_boots"},
            {"vigils_death_mask", "dis_iron_chestplate", "dis_iron_leggings", "dis_iron_boots"}};
    static final int Y = 120;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (Boolean.getBoolean("quantumdeath.skipItems")) return;
        try (TestSingleplayerContext sp = ctx.worldBuilder()
                .adjustSettings(s -> { s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); s.setSeed("1313"); s.setAllowCommands(true); })
                .create()) {
            settle(ctx, sp, 40);
            for (String c : new String[]{"deathmask vigil pause", "time set noon", "gamerule advance_time false", "gamerule advance_weather false",
                    "weather clear", "gamerule spawn_monsters false", "gamerule send_command_feedback false", "forceload add -24 -24 24 24",
                    "fill -12 " + (Y - 1) + " -12 12 " + (Y - 1) + " 12 minecraft:polished_deepslate",
                    "fill -12 " + Y + " -12 12 " + (Y + 8) + " 12 minecraft:air"})
                cmd(sp, c);
            cmd(sp, "tp @p 0 " + Y + " 0 0 0");
            settle(ctx, sp, 20);
            hideHud(ctx);

            if (!Boolean.getBoolean("quantumdeath.itemsQuick")) inventory(ctx, sp);
            held(ctx, sp);
            worn(ctx, sp);
            displayed(ctx, sp);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    /** Every item sprite of the mod, 36 per page, in the survival inventory. */
    private void inventory(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        List<String> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM)
            if (!(item instanceof BlockItem) && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Quantumdeath.MOD_ID))
                ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        cmd(sp, "gamemode survival");
        for (int page = 0; page * 36 < ids.size(); page++) {
            cmd(sp, "clear @p");
            for (int i = 0; i < 36 && page * 36 + i < ids.size(); i++)
                cmd(sp, "item replace entity @p container." + i + " with " + ids.get(page * 36 + i));
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.setScreenAndShow(new InventoryScreen(mc.player)));
            ctx.waitForScreen(InventoryScreen.class);
            ctx.getInput().setCursorPos(2, 2);
            ctx.waitTicks(5);
            ctx.takeScreenshot("items_inventory_" + page);
            ctx.runOnClient(mc -> mc.setScreenAndShow(null));
            ctx.waitTicks(3);
        }
        cmd(sp, "clear @p");
        cmd(sp, "gamemode creative");
    }

    /** The boss weapons in hand: first person, then third person from the front. */
    private void held(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        List<String> held = new ArrayList<>(List.of("minecraft:iron_sword"));  // vanilla reference for size and grip
        for (String w : WEAPONS) held.add("quantumdeath:" + w);
        for (String id : held) {
            String w = id.substring(id.indexOf(':') + 1);
            cmd(sp, "item replace entity @p weapon.mainhand with " + id);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.getInput().lookAt(0, -10);
            ctx.waitTicks(15);
            ctx.takeScreenshot("items_held_first_" + w);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.getInput().lookAt(0, 15);
            ctx.waitTicks(10);
            ctx.takeScreenshot("items_held_third_" + w);
        }
        cmd(sp, "item replace entity @p weapon.mainhand with minecraft:air");
    }

    /** Each armor set on the player, front and back, holding a weapon. */
    private void worn(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String[] slots = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};
        for (int s = 0; s < SETS.length; s++) {
            for (int i = 0; i < 4; i++) cmd(sp, "item replace entity @p " + slots[i] + " with quantumdeath:" + SETS[s][i]);
            cmd(sp, "item replace entity @p weapon.mainhand with quantumdeath:" + WEAPONS[s]);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.getInput().lookAt(0, 12);
            ctx.waitTicks(10);
            ctx.takeScreenshot("items_worn_front_" + SETS[s][0]);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.getInput().lookAt(150, 18);
            ctx.waitTicks(10);
            ctx.takeScreenshot("items_worn_back_" + SETS[s][0]);
        }
        cmd(sp, "clear @p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /** Armor stands wearing the sets (with a weapon each), weapons in item frames and lying on the ground. */
    private void displayed(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        for (int s = 0; s < SETS.length; s++) {
            String[] a = SETS[s];
            cmd(sp, "summon armor_stand " + (-4.5 + s * 3) + " " + Y + " -4 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],equipment:{"
                    + "head:{id:\"quantumdeath:" + a[0] + "\"},chest:{id:\"quantumdeath:" + a[1] + "\"},legs:{id:\"quantumdeath:" + a[2]
                    + "\"},feet:{id:\"quantumdeath:" + a[3] + "\"},mainhand:{id:\"quantumdeath:" + WEAPONS[s] + "\"}}}");
        }
        cmd(sp, "summon armor_stand 7.5 " + Y + " -4 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],equipment:{mainhand:{id:\"minecraft:iron_sword\"}}}");
        cmd(sp, "fill -6 " + Y + " -9 6 " + (Y + 4) + " -9 minecraft:dark_oak_planks");
        for (int i = 0; i < WEAPONS.length; i++) {
            cmd(sp, "summon item_frame " + (-4 + i * 2) + " " + (Y + 3) + " -8 {Facing:3b,Fixed:1b,Item:{id:\"quantumdeath:" + WEAPONS[i] + "\"}}");
            cmd(sp, "summon item " + (-4 + i * 2) + ".5 " + Y + " 1.5 {NoGravity:1b,PickupDelay:32767,Age:-32768,Item:{id:\"quantumdeath:" + WEAPONS[i] + "\"}}");
        }
        cmd(sp, "tp @p 0 " + Y + " 4 180 10");
        ctx.getInput().lookAt(180, 10);
        settle(ctx, sp, 30);
        ctx.takeScreenshot("items_stands_and_frames");
        cmd(sp, "tp @p 0 " + (Y + 1) + " 5 180 35");
        ctx.getInput().lookAt(180, 40);
        ctx.waitTicks(20);
        ctx.takeScreenshot("items_on_ground");
        cmd(sp, "tp @p 1 " + Y + " -1 160 5");
        ctx.getInput().lookAt(160, 5);
        ctx.waitTicks(20);
        ctx.takeScreenshot("items_stands_close");
    }
}
