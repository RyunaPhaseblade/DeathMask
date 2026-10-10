package ryuna.qdeath.client.yokai;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.client.gui.SkillsScreen;
import ryuna.qdeath.client.jjk.TechniqueScreen;
import ryuna.qdeath.moditems.modItems;

/** The tabs of the Wisdom of the Void: Cursed Techniques, General Skills and the Bestiary, across the top of each screen. */
public final class BookTabs {
    private BookTabs() {}

    public enum Tab {
        TECHNIQUES("techniques", 0x9a6ad8), GENERAL("general", 0xd8b84a), BESTIARY("bestiary", 0x6ac890);
        public final String key;
        public final int color;
        Tab(String key, int color) { this.key = key; this.color = color; }
        public Component label() { return Component.translatable("yokai.quantumdeath.book.tab." + key); }
        ItemStack icon() {
            return switch (this) {
                case TECHNIQUES -> new ItemStack(modItems.WISDOM_OF_THE_VOID);
                case GENERAL -> new ItemStack(Items.NETHER_STAR);
                case BESTIARY -> new ItemStack(Items.ENDER_EYE);
            };
        }
    }

    /** Height of the tab strip; screens lay out their content below it. */
    public static final int HEIGHT = 22;
    private static final int TAB_W = 128;

    private static int x0(int width) { return width / 2 - TAB_W * 3 / 2; }

    public static void draw(GuiGraphicsExtractor g, Font font, int width, Tab current, int mx, int my) {
        int x = x0(width);
        g.fill(0, HEIGHT - 1, width, HEIGHT, 0x60000000 | (current.color & 0xffffff));
        for (Tab t : Tab.values()) {
            boolean sel = t == current, hover = !sel && mx >= x && mx < x + TAB_W - 4 && my >= 2 && my < HEIGHT;
            int top = sel ? 2 : 4;
            g.fill(x, top, x + TAB_W - 4, HEIGHT - (sel ? 0 : 1), sel ? 0xff17161f : hover ? 0xff131219 : 0xff0c0b11);
            g.fill(x, top, x + TAB_W - 4, top + 2, sel ? 0xff000000 | t.color : 0xff2a2832);
            g.item(t.icon(), x + 4, top + 2);
            Component label = t.label();
            float scale = Math.min(0.85f, (TAB_W - 30) / (float) font.width(label));
            g.pose().pushMatrix();
            g.pose().translate(x + 23, top + 7);
            g.pose().scale(scale, scale);
            g.text(font, label, 0, 0, sel ? 0xffffffff : 0xff8a8696, false);
            g.pose().popMatrix();
            if (t == Tab.BESTIARY && ClientYokai.noteUnread) g.fill(x + TAB_W - 10, top + 4, x + TAB_W - 7, top + 7, 0xffe03a2a);
            x += TAB_W;
        }
    }

    public static @Nullable Tab hit(int width, double mx, double my) {
        if (my < 2 || my >= HEIGHT) return null;
        int x = x0(width);
        for (Tab t : Tab.values()) {
            if (mx >= x && mx < x + TAB_W - 4) return t;
            x += TAB_W;
        }
        return null;
    }

    /** Test hook: centre of a tab in gui coordinates. */
    public static int[] center(int width, Tab tab) { return new int[]{x0(width) + tab.ordinal() * TAB_W + TAB_W / 2 - 2, HEIGHT / 2}; }

    public static void open(Minecraft mc, Tab tab) {
        mc.setScreenAndShow(switch (tab) {
            case TECHNIQUES -> new TechniqueScreen();
            case GENERAL -> new SkillsScreen();
            case BESTIARY -> new BestiaryScreen();
        });
    }

    /** Handles a click on the tab strip; true if it was one. */
    public static boolean click(Minecraft mc, int width, double mx, double my, Tab current) {
        Tab t = hit(width, mx, my);
        if (t == null) return false;
        if (t != current) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
            open(mc, t);
        }
        return true;
    }
}
