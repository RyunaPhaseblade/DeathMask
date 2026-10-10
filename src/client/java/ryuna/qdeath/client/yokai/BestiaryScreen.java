package ryuna.qdeath.client.yokai;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiContent;
import ryuna.qdeath.yokai.YokaiNet;
import ryuna.qdeath.yokai.YokaiPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The discovered bestiary. A creature appears first as clues (what was heard or seen around it), then as a shadow,
 * and is only named and shown (with its real model) once identified with complete sight. Interpretation explains the
 * clues; Prepared Offerings lists recipes and brings offerings to the hand.
 */
public final class BestiaryScreen extends Screen {
    private static final int BG = 0xff060509, TEXT = 0xffd8d4e0, MUTED = 0xff8a8696, GOLD = 0xffe8c547, ROW_H = 17;
    private static final String NOTE = "note", OFFERINGS = "offerings";

    private final Map<Species, LivingEntity> previews = new EnumMap<>(Species.class);
    private String selected = "";
    private double listScroll, textScroll;
    private final List<ItemHit> hits = new ArrayList<>();

    private record ItemHit(Item item, int x, int y) {}

    public BestiaryScreen() { super(Component.translatable("yokai.quantumdeath.book.tab.bestiary")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        if (selected.isEmpty()) selected = ClientYokai.noteUnread ? NOTE : entries().isEmpty() ? "" : entries().getFirst();
        readNote();
    }

    /** Showing the note is reading it. */
    private void readNote() {
        if (selected.equals(NOTE) && ClientYokai.noteUnread) {
            ClientYokai.noteUnread = false;
            ClientPlayNetworking.send(new YokaiNet.NoteRead());
        }
    }

    private List<String> entries() {
        List<String> out = new ArrayList<>();
        if (ClientYokai.awakened || ClientYokai.noteUnread) out.add(NOTE);
        if (ClientYokai.node(SkillId.SPIRIT_II)) out.add(OFFERINGS);
        for (Species s : Species.values()) out.add(s.id);
        return out;
    }

    private static int entry(Species s) { return ClientYokai.entry(s.id); }
    private static boolean known(Species s) { return entry(s) != 0; }
    private static boolean identified(Species s) { return (entry(s) & YokaiPlayer.IDENTIFIED) != 0; }

    private int listX() { return 10; }
    private int listW() { return Math.min(150, width / 3); }
    private int top() { return BookTabs.HEIGHT + 30; }
    private int bottom() { return height - 12; }
    private int panelX() { return listX() + listW() + 12; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, BG);
        g.fillGradient(0, 0, width, height / 2, 0x226ac890, 0x00000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        hits.clear();
        BookTabs.draw(g, font, width, BookTabs.Tab.BESTIARY, mx, my);
        int y = BookTabs.HEIGHT + 6;
        scaled(g, Component.translatable("yokai.quantumdeath.book.bestiary"), 12, y, 1.4f, 0xff6ac890, false);
        long ids = 0;
        for (Species s : Species.values()) if (identified(s)) ids++;
        Component count = Component.translatable("yokai.quantumdeath.book.identified", ids, Species.values().length);
        Component level = Component.translatable("yokai.quantumdeath.book.perception." + ClientYokai.perception);
        scaled(g, count, width - 12 - Math.round(font.width(count) * 0.8f), y, 0.8f, TEXT, false);
        scaled(g, level, width - 12 - Math.round(font.width(level) * 0.8f), y + 10, 0.8f, 0xff9a6ad8, false);
        g.fill(8, top() - 3, width - 8, top() - 2, 0x506ac890);
        list(g, mx, my);
        g.enableScissor(panelX(), top(), width - 8, bottom());
        detail(g, mx, my);
        g.disableScissor();
        super.extractRenderState(g, mx, my, a);
        for (ItemHit h : hits)
            if (mx >= h.x && mx < h.x + 16 && my >= h.y && my < h.y + 16) {
                List<Component> tip = new ArrayList<>();
                tip.add(new ItemStack(h.item).getHoverName());
                if (ClientYokai.node(SkillId.SPIRIT_II)) tip.add(Component.translatable("yokai.quantumdeath.book.quick_select").withStyle(ChatFormatting.GOLD));
                g.setTooltipForNextFrame(tip.stream().map(Component::getVisualOrderText).toList(), mx, my);
            }
    }

    // ------------------------------------------------------------------------------------------------ list

    private void list(GuiGraphicsExtractor g, int mx, int my) {
        List<String> es = entries();
        int maxScroll = Math.max(0, es.size() * ROW_H - (bottom() - top()));
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
        g.enableScissor(listX(), top(), listX() + listW(), bottom());
        int y = top() - (int) listScroll;
        for (String id : es) {
            boolean sel = id.equals(selected), hover = mx >= listX() && mx < listX() + listW() && my >= y && my < y + ROW_H - 1 && my >= top() && my < bottom();
            g.fill(listX(), y, listX() + listW(), y + ROW_H - 1, sel ? 0xff1c1a26 : hover ? 0xff14131a : 0xff0d0c12);
            Component label;
            int color;
            ItemStack icon;
            Species s = Species.byId(id);
            if (id.equals(NOTE)) {
                label = Component.translatable("yokai.quantumdeath.book.note");
                color = GOLD;
                icon = new ItemStack(Items.PAPER);
            } else if (id.equals(OFFERINGS)) {
                label = Component.translatable("yokai.quantumdeath.book.offerings");
                color = 0xffd8b84a;
                icon = new ItemStack(YokaiContent.AMULET);
            } else if (s != null && identified(s)) {
                label = Component.translatable(s.key());
                color = 0xff000000 | s.color;
                icon = new ItemStack(BuiltInRegistries.ITEM.getValue(ryuna.qdeath.Quantumdeath.id(s.id + "_spawn_egg")));
            } else if (s != null && known(s)) {
                label = Component.translatable("yokai.quantumdeath.book.unidentified");
                color = MUTED;
                icon = new ItemStack(Items.ECHO_SHARD);
            } else {
                label = Component.literal("???");
                color = 0xff3a3844;
                icon = ItemStack.EMPTY;
            }
            if (!icon.isEmpty()) g.item(icon, listX() + 1, y);
            scaled(g, Component.literal(font.plainSubstrByWidth(label.getString(), (int) ((listW() - 24) / 0.8f))), listX() + 20, y + 5, 0.8f, color, false);
            if (id.equals(NOTE) && ClientYokai.noteUnread) g.fill(listX() + listW() - 6, y + 6, listX() + listW() - 3, y + 9, 0xffe03a2a);
            if (s != null && known(s) && !identified(s) && (entry(s) & YokaiPlayer.SHADE) != 0) scaled(g, Component.literal("◐"), listX() + listW() - 9, y + 5, 0.8f, 0xff6a6a78, false);
            y += ROW_H;
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------------------------------------ detail

    private void detail(GuiGraphicsExtractor g, int mx, int my) {
        int x = panelX(), w = width - 8 - x;
        int y = top() - (int) textScroll;
        g.fill(x - 4, top(), x - 3, bottom(), 0x30ffffff);
        if (selected.equals(NOTE)) {
            scaled(g, Component.translatable("yokai.quantumdeath.book.note_title"), x, y + 2, 1.1f, GOLD, false);
            y += 18;
            y = para(g, ClientYokai.awakened ? Component.translatable("yokai.quantumdeath.book.note_text", ClientYokai.day + 1)
                    : Component.translatable("yokai.quantumdeath.book.note_asleep"), x, y, w, TEXT);
            return;
        }
        if (selected.equals(OFFERINGS)) {
            offerings(g, x, y, w);
            return;
        }
        Species s = Species.byId(selected);
        if (s == null) return;
        if (!known(s)) {
            para(g, Component.translatable("yokai.quantumdeath.book.unknown"), x, y + 4, w, MUTED);
            return;
        }
        if (identified(s)) {
            int pw = Math.min(90, w / 3);
            LivingEntity e = preview(s);
            if (e != null) {
                g.fill(x, y, x + pw, y + 110, 0xff0b0a10);
                g.outline(x, y, pw, 110, 0xff000000 | s.color);
                float size = (float) Math.min(46, 80 / Math.max(e.getBbHeight(), e.getBbWidth() * 1.2));
                InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + pw - 2, y + 108, (int) size, 0.0625f, mx, my, e);
            }
            int tx = x + pw + 8, tw = w - pw - 8;
            scaled(g, Component.translatable(s.key()), tx, y + 2, 1.3f, 0xff000000 | s.color, false);
            scaled(g, Component.translatable(s.key() + ".temper"), tx, y + 16, 0.8f, MUTED, false);
            int ty = y + 28;
            for (String k : new String[]{"habitat", "behaviour", "counter", "pact"}) {
                scaled(g, Component.translatable("yokai.quantumdeath.book.section." + k), tx, ty, 0.75f, GOLD, false);
                ty = para(g, Component.translatable(s.key() + "." + k), tx, ty + 9, tw, TEXT) + 4;
            }
            if (!s.offerings.isEmpty()) {
                int ix = tx;
                for (var o : s.offerings) {
                    g.item(new ItemStack(o.get()), ix, ty);
                    hits.add(new ItemHit(o.get(), ix, ty));
                    ix += 18;
                }
                ty += 20;
            }
            y = Math.max(y + 114, ty);
        } else {
            scaled(g, Component.translatable("yokai.quantumdeath.book.unidentified"), x, y + 2, 1.2f, MUTED, false);
            y += 18;
            if ((entry(s) & YokaiPlayer.SHADE) != 0) {
                y = para(g, Component.translatable("yokai.quantumdeath.book.shadow_seen"), x, y, w, 0xff9a6ad8) + 4;
            }
        }
        scaled(g, Component.translatable("yokai.quantumdeath.book.signs"), x, y, 0.8f, GOLD, false);
        y += 11;
        boolean interpret = ClientYokai.node(SkillId.SPIRIT_I);
        for (int i = 0; i < s.clues.size(); i++) {
            if ((entry(s) & (1 << i)) == 0) continue;
            String key = "yokai.quantumdeath.clue." + s.id + "." + s.clues.get(i);
            y = para(g, Component.literal("• ").append(Component.translatable(key)), x, y, w, TEXT);
            if (interpret) y = para(g, Component.translatable(key + ".meaning"), x + 8, y, w - 8, 0xff9fb8d0);
            y += 2;
        }
        if (!interpret) para(g, Component.translatable("yokai.quantumdeath.book.need_interpretation"), x, y + 4, w, 0xff6a6a78);
    }

    private void offerings(GuiGraphicsExtractor g, int x, int y, int w) {
        scaled(g, Component.translatable("yokai.quantumdeath.book.offerings"), x, y + 2, 1.1f, 0xffd8b84a, false);
        y += 18;
        // Amulet: gold on the corners, string on the sides, quartz in the middle.
        Item[] amulet = {Items.GOLD_INGOT, Items.STRING, Items.GOLD_INGOT, Items.STRING, Items.QUARTZ, Items.STRING, Items.GOLD_INGOT, Items.STRING, Items.GOLD_INGOT};
        y = recipe(g, x, y, Component.translatable("item.quantumdeath.shikigami_amulet"), amulet, YokaiContent.AMULET, true);
        Item[] thread = {Items.STRING, Items.STRING, Items.PAPER, Items.QUARTZ};
        y = recipe(g, x, y, Component.translatable("item.quantumdeath.ritual_thread"), thread, YokaiContent.RITUAL_THREAD, false);
        scaled(g, Component.translatable("yokai.quantumdeath.book.offerings_by_species"), x, y, 0.8f, GOLD, false);
        y += 12;
        for (Species s : Species.values()) {
            if (!identified(s) || s.offerings.isEmpty()) continue;
            scaled(g, Component.translatable(s.key()), x, y + 5, 0.8f, 0xff000000 | s.color, false);
            int ix = x + 90;
            for (var o : s.offerings) {
                g.item(new ItemStack(o.get()), ix, y);
                hits.add(new ItemHit(o.get(), ix, y));
                ix += 18;
            }
            y += 18;
        }
    }

    private int recipe(GuiGraphicsExtractor g, int x, int y, Component name, Item[] grid, Item result, boolean shaped) {
        scaled(g, name, x, y, 0.8f, TEXT, false);
        y += 11;
        for (int i = 0; i < grid.length; i++) {
            int gx = x + (shaped ? i % 3 : i) * 18, gy = y + (shaped ? i / 3 : 0) * 18;
            g.fill(gx, gy, gx + 17, gy + 17, 0xff1a1822);
            g.item(new ItemStack(grid[i]), gx, gy);
        }
        int rx = x + (shaped ? 3 : grid.length) * 18 + 14, ry = y + (shaped ? 18 : 0);
        scaled(g, Component.literal("→"), rx - 10, ry + 4, 1f, MUTED, false);
        g.item(new ItemStack(result), rx, ry);
        hits.add(new ItemHit(result, rx, ry));
        if (!shaped) scaled(g, Component.translatable("yokai.quantumdeath.book.shapeless"), x, y + 20, 0.7f, MUTED, false);
        return y + (shaped ? 58 : 32);
    }

    private @Nullable LivingEntity preview(Species s) {
        if (minecraft.level == null) return null;
        return previews.computeIfAbsent(s, k -> {
            Entity e = k.type().create(minecraft.level, EntitySpawnReason.LOAD);
            if (!(e instanceof LivingEntity l)) return null;
            // A model for the page, never added to the level: it still needs an id for the renderers.
            l.setId(-7000 - k.ordinal());
            YokaiRenderer.PREVIEW.add(l);
            return l;
        });
    }

    private int para(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(text, (int) (w / 0.8f))) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.8f, 0.8f);
            g.text(font, line, 0, 0, color, false);
            g.pose().popMatrix();
            y += 9;
        }
        return y;
    }

    private void scaled(GuiGraphicsExtractor g, Component text, int x, int y, float scale, int color, boolean centered) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (centered) g.centeredText(font, text, 0, 0, color);
        else g.text(font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------------------------------------ input

    /** Test hook: gui coordinates of an entry of the list. */
    public int[] entryCenter(String id) {
        int i = entries().indexOf(id);
        return new int[]{listX() + listW() / 2, top() - (int) listScroll + i * ROW_H + ROW_H / 2};
    }

    public String selected() { return selected; }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (BookTabs.click(minecraft, width, event.x(), event.y(), BookTabs.Tab.BESTIARY)) return true;
        if (super.mouseClicked(event, doubled)) return true;
        double mx = event.x(), my = event.y();
        if (mx >= listX() && mx < listX() + listW() && my >= top() && my < bottom()) {
            int i = (int) ((my - top() + listScroll) / ROW_H);
            List<String> es = entries();
            if (i >= 0 && i < es.size()) {
                selected = es.get(i);
                textScroll = 0;
                readNote();
            }
            return true;
        }
        if (ClientYokai.node(SkillId.SPIRIT_II)) for (ItemHit h : hits)
            if (mx >= h.x && mx < h.x + 16 && my >= h.y && my < h.y + 16) {
                ClientPlayNetworking.send(new YokaiNet.QuickSelect(BuiltInRegistries.ITEM.getKey(h.item).toString()));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2f));
                return true;
            }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < panelX()) listScroll -= v * ROW_H;
        else textScroll = Math.max(0, textScroll - v * 18);
        return true;
    }

    @Override public void removed() {
        previews.values().forEach(YokaiRenderer.PREVIEW::remove);
        super.removed();
    }
}
