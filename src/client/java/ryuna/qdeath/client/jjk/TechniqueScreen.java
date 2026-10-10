package ryuna.qdeath.client.jjk;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.jjk.JjkBalance;
import ryuna.qdeath.jjk.JjkClasses;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.SkillDef;
import ryuna.qdeath.jjk.TechniqueClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The Path of the Void: choose a cursed technique, then grow its skill tree. Nodes show every state (unlocked,
 * available, short of TP, missing a prerequisite, missing the level); hovering explains everything; left-click
 * requests an unlock (the server decides), right-click selects an unlocked technique for the HUD.
 * Generic over {@link TechniqueClass}: a new class appears here with no screen changes.
 */
public final class TechniqueScreen extends Screen {
    /** Content starts below the book's tab strip. */
    private static final int T = ryuna.qdeath.client.yokai.BookTabs.HEIGHT;
    private static final int BG = 0xff060509, TOP = 50 + T, NODE = 22;
    private static final int GOLD = 0xffe8c547, TEXT = 0xffd8d4e0, MUTED = 0xff8a8696;

    private boolean choosing;
    private @Nullable String picked;
    private double scroll, pressX, pressY, scrollAtPress;
    private boolean pressedTree;

    public TechniqueScreen() {
        super(Component.translatable("jjk.quantumdeath.gui.title"));
        choosing = ClientTechnique.classId.isEmpty();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        int bw = 118;
        if (choosing) {
            if (picked != null) {
                TechniqueClass c = JjkClasses.get(picked);
                addRenderableWidget(Button.builder(Component.translatable("jjk.quantumdeath.gui.awaken", Component.translatable(c.techniqueKey())),
                        b -> ClientPlayNetworking.send(new JjkNet.ChooseClass(picked))).bounds(width / 2 - 90, height - 28, 180, 20).build());
            }
            if (!ClientTechnique.classId.isEmpty())
                addRenderableWidget(Button.builder(Component.translatable("jjk.quantumdeath.gui.back"), b -> { choosing = false; picked = null; rebuildWidgets(); })
                        .bounds(8, height - 28, 80, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("jjk.quantumdeath.gui.change"), b -> { choosing = true; picked = null; rebuildWidgets(); })
                    .bounds(width - 8 - bw, height - 26, bw, 18).build());
        }
    }

    @Override public void tick() {
        if (choosing && picked != null && picked.equals(ClientTechnique.classId)) { choosing = false; picked = null; scroll = 0; rebuildWidgets(); }
        else if (!choosing && ClientTechnique.classId.isEmpty()) { choosing = true; rebuildWidgets(); }
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, BG);
        TechniqueClass c = choosing ? (picked == null ? null : JjkClasses.get(picked)) : ClientTechnique.cls();
        int glow = c == null ? 0x5a3a8a : c.color;
        g.fillGradient(0, 0, width, height / 2, 0x26000000 | (glow & 0xffffff), 0x00000000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x30000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ryuna.qdeath.client.yokai.BookTabs.draw(g, font, width, ryuna.qdeath.client.yokai.BookTabs.Tab.TECHNIQUES, mouseX, mouseY);
        if (choosing) drawChoice(g, mouseX, mouseY);
        else drawTree(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    // ------------------------------------------------------------------------------------------------ class choice

    private record Card(TechniqueClass cls, int x, int y, int w, int h) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private List<Card> cards() {
        List<TechniqueClass> all = new ArrayList<>(JjkClasses.all());
        int n = all.size(), gap = 6;
        int cols = (width - 24 - gap * (n - 1)) / n >= 72 ? n : Math.min(n, 3);
        int rows = (n + cols - 1) / cols;
        int cw = Math.min(124, (width - 24 - gap * (cols - 1)) / cols);
        int ch = Math.min(176, (height - 52 - T - 36 - gap * (rows - 1)) / rows);
        int totalW = cols * cw + (cols - 1) * gap;
        int x0 = (width - totalW) / 2, y0 = 52 + T;
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Card(all.get(i), x0 + (i % cols) * (cw + gap), y0 + (i / cols) * (ch + gap), cw, ch));
        return out;
    }

    private void drawChoice(GuiGraphicsExtractor g, int mx, int my) {
        scaled(g, Component.translatable("jjk.quantumdeath.gui.choose"), width / 2, 10 + T, 1.5f, TEXT, true);
        scaled(g, Component.translatable("jjk.quantumdeath.gui.choose_sub"), width / 2, 27 + T, 0.8f, MUTED, true);
        if (!ClientTechnique.classId.isEmpty())
            scaled(g, Component.translatable("jjk.quantumdeath.gui.change_warning"), width / 2, 38 + T, 0.8f, 0xffd08050, true);
        for (Card card : cards()) {
            TechniqueClass c = card.cls;
            boolean hover = card.hit(mx, my), sel = c.id.equals(picked), current = c.id.equals(ClientTechnique.classId);
            int col = 0xff000000 | c.color;
            g.fill(card.x, card.y, card.x + card.w, card.y + card.h, hover || sel ? 0xff17161f : 0xff0e0d14);
            g.fillGradient(card.x, card.y + 3, card.x + card.w, card.y + 40, 0x30000000 | (c.color & 0xffffff), 0x00000000);
            g.fill(card.x, card.y, card.x + card.w, card.y + 3, col);
            g.outline(card.x, card.y, card.w, card.h, sel ? col : hover ? 0xff4a4656 : 0xff24222c);
            if (sel) g.outline(card.x - 1, card.y - 1, card.w + 2, card.h + 2, 0x80000000 | (c.color & 0xffffff));
            int cx = card.x + card.w / 2;
            boolean big = card.h >= 120;
            g.pose().pushMatrix();
            g.pose().translate(cx - (big ? 16 : 8), card.y + 7);
            if (big) g.pose().scale(2f, 2f);
            g.item(new ItemStack(c.icon.get()), 0, 0);
            g.pose().popMatrix();
            int ty = card.y + (big ? 45 : 27);
            Component name = Component.translatable(c.nameKey()).withStyle(ChatFormatting.BOLD);
            float ns = Math.min(1f, (card.w - 6) / (float) font.width(name));
            scaled(g, name, cx, ty, ns, TEXT, true);
            ty += 11;
            for (FormattedCharSequence line : font.split(Component.translatable(c.techniqueKey()), (int) ((card.w - 8) / 0.85f))) {
                g.pose().pushMatrix();
                g.pose().translate(cx, ty);
                g.pose().scale(0.85f, 0.85f);
                g.centeredText(font, line, 0, 0, col);
                g.pose().popMatrix();
                ty += 9;
            }
            ty += 3;
            int limit = card.y + card.h - (current ? 24 : 14);
            g.pose().pushMatrix();
            g.pose().translate(card.x + 5, ty);
            g.pose().scale(0.68f, 0.68f);
            int ly = 0;
            for (FormattedCharSequence line : font.split(Component.translatable(c.descKey()), (int) ((card.w - 10) / 0.68f))) {
                if (ty + (ly + 9) * 0.68f > limit) break;
                g.text(font, line, 0, ly, MUTED, false);
                ly += 10;
            }
            g.pose().popMatrix();
            Component style = Component.translatable(c.styleKey());
            scaled(g, style, cx, card.y + card.h - 10, Math.min(0.62f, (card.w - 6) / (float) font.width(style)), 0xffb0aabd, true);
            if (current) scaled(g, Component.translatable("jjk.quantumdeath.gui.current"), cx, card.y + card.h - 20, 0.7f, GOLD, true);
        }
    }

    // ------------------------------------------------------------------------------------------------ tree

    private int bottom() { return height - 34; }
    private int maxRow(TechniqueClass c) { return c.skills().stream().mapToInt(SkillDef::row).max().orElse(0); }
    private int colSpacing() { return Mth.clamp((width - 40) / 9, 30, 56); }
    /** Shrinks the whole tree (down to 60%) so that tall trees fit small windows; below that it scrolls. */
    private float zoom(TechniqueClass c) { return Mth.clamp((bottom() - TOP - 6) / (maxRow(c) * 40f + 40f), 0.6f, 1f); }
    private int rowSpacing(TechniqueClass c) { return Math.round(40 * zoom(c)); }
    private int nodeX(SkillDef s) { return width / 2 + (s.col() - 4) * colSpacing(); }
    private int nodeY(TechniqueClass c, SkillDef s) { return TOP + Math.round(18 * zoom(c)) + s.row() * rowSpacing(c) - (int) scroll; }
    private int size(SkillDef s) { return s.tier() >= 5 || s.cost() >= JjkBalance.Progression.TP_COST_MAXIMUM ? NODE + 4 : NODE; }

    private void clampScroll(TechniqueClass c) {
        int content = maxRow(c) * rowSpacing(c) + Math.round(40 * zoom(c));
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - (bottom() - TOP)));
    }

    private JjkClasses.Status status(SkillDef s) {
        return JjkClasses.status(s, ClientTechnique.level, ClientTechnique.tp, ClientTechnique.unlocked);
    }

    private @Nullable SkillDef hovered(TechniqueClass c, int mx, int my) {
        if (my < TOP || my > bottom()) return null;
        float k = zoom(c);
        for (SkillDef s : c.skills()) {
            int r = Math.round((size(s) / 2 + 2) * k);
            if (Math.abs(mx - nodeX(s)) <= r && Math.abs(my - nodeY(c, s)) <= r) return s;
        }
        return null;
    }

    private void drawTree(GuiGraphicsExtractor g, int mx, int my) {
        TechniqueClass c = ClientTechnique.cls();
        if (c == null) return;
        clampScroll(c);
        int col = 0xff000000 | c.color;
        // Header: class, level and XP, technique points
        scaled(g, Component.translatable(c.nameKey()), 12, 9 + T, 1.6f, col, false);
        g.text(font, Component.translatable(c.techniqueKey()), 12, 26 + T, MUTED);
        int barW = Math.min(180, width / 3);
        int bx = width / 2 - barW / 2;
        boolean max = ClientTechnique.level >= JjkBalance.Progression.MAX_LEVEL;
        g.centeredText(font, Component.translatable("jjk.quantumdeath.gui.level", ClientTechnique.level), width / 2, 9 + T, TEXT);
        g.fill(bx - 1, 21 + T, bx + barW + 1, 27 + T, 0xff000000);
        g.fill(bx, 22 + T, bx + barW, 26 + T, 0xff1c1a24);
        float xpFrac = max ? 1f : ClientTechnique.xp / (float) Math.max(1, ClientTechnique.xpNext);
        g.fillGradient(bx, 22 + T, bx + Math.round(barW * xpFrac), 26 + T, col, 0xff000000 | darken(c.color, 0.6f));
        scaled(g, max ? Component.translatable("jjk.quantumdeath.gui.max_level") : Component.translatable("jjk.quantumdeath.gui.xp", ClientTechnique.xp, ClientTechnique.xpNext),
                width / 2, 30 + T, 0.75f, MUTED, true);
        Component tp = Component.translatable("jjk.quantumdeath.gui.tp", ClientTechnique.tp);
        scaled(g, tp, width - 12 - Math.round(font.width(tp) * 1.4f), 11 + T, 1.4f, GOLD, false);
        g.fill(8, TOP - 6, width - 8, TOP - 5, 0x50000000 | (c.color & 0xffffff));

        // Tree
        g.enableScissor(0, TOP - 4, width, bottom());
        for (SkillDef s : c.skills())
            for (String req : s.requires()) {
                SkillDef parent = c.get(req);
                if (parent == null) continue;
                boolean pu = ClientTechnique.unlocked.contains(parent.id()), cu = ClientTechnique.unlocked.contains(s.id());
                int lc = pu && cu ? col : pu ? (status(s) == JjkClasses.Status.AVAILABLE ? GOLD : 0xff4a4858) : 0xff26242e;
                line(g, nodeX(parent), nodeY(c, parent), nodeX(s), nodeY(c, s), pu && cu ? 2 : 1, lc);
            }
        SkillDef hover = hovered(c, mx, my);
        for (SkillDef s : c.skills()) drawNode(g, c, s, s == hover);
        g.disableScissor();
        // Scroll hints when the tree does not fit.
        int content = maxRow(c) * rowSpacing(c) + Math.round(40 * zoom(c));
        if (scroll > 0) g.centeredText(font, "▲", width / 2, TOP - 4, 0xa0ffffff);
        if (scroll < content - (bottom() - TOP) - 1) g.centeredText(font, "▼", width / 2, bottom() - 9, 0xa0ffffff);

        // Legend
        int lx = 10, ly = height - 26;
        lx = legend(g, lx, ly, col, "unlocked");
        lx = legend(g, lx, ly, GOLD, "available");
        lx = legend(g, lx, ly, 0xffc07830, "no_tp");
        lx = legend(g, lx, ly, 0xff6a6a78, "no_prereq");
        legend(g, lx, ly, 0xffb04040, "no_level");
        scaled(g, Component.translatable("jjk.quantumdeath.gui.hint"), 10, height - 14, 0.7f, MUTED, false);

        if (hover != null) tooltip(g, c, hover, mx, my);
    }

    private int legend(GuiGraphicsExtractor g, int x, int y, int color, String key) {
        g.fill(x, y + 1, x + 6, y + 7, 0xff0e0d14);
        g.outline(x, y + 1, 6, 6, color);
        Component t = Component.translatable("jjk.quantumdeath.gui.state." + key);
        scaled(g, t, x + 9, y + 1, 0.7f, MUTED, false);
        return x + 14 + Math.round(font.width(t) * 0.7f);
    }

    private void drawNode(GuiGraphicsExtractor g, TechniqueClass c, SkillDef s, boolean hover) {
        g.pose().pushMatrix();
        g.pose().translate(nodeX(s), nodeY(c, s));
        float k = zoom(c);
        g.pose().scale(k, k);
        drawNodeLocal(g, c, s, hover);
        g.pose().popMatrix();
    }

    /** One node drawn around (0, 0). */
    private void drawNodeLocal(GuiGraphicsExtractor g, TechniqueClass c, SkillDef s, boolean hover) {
        int x = 0, y = 0, sz = size(s), r = sz / 2;
        JjkClasses.Status st = status(s);
        int border, fill, dim;
        switch (st) {
            case UNLOCKED -> { border = 0xff000000 | c.color; fill = 0xff000000 | darken(c.color, 0.22f); dim = 0; }
            case AVAILABLE -> {
                float pulse = 0.65f + 0.35f * Mth.sin(ClientTechnique.clientTicks * 0.25f);
                border = 0xff000000 | lerp(0x6a5420, GOLD & 0xffffff, pulse); fill = 0xff1c1708; dim = 0;
            }
            case NO_TP -> { border = 0xffc07830; fill = 0xff151010; dim = 0x50; }
            case NO_PREREQ -> { border = 0xff44444e; fill = 0xff0e0e12; dim = 0xb0; }
            case NO_LEVEL -> { border = 0xff8a3030; fill = 0xff120b0b; dim = 0x80; }
            default -> { border = 0xff2e2e36; fill = 0xff0b0b0e; dim = 0xc0; }
        }
        if (hover) border = 0xffffffff;
        if (s.active()) {
            g.fill(x - r, y - r, x + r, y + r, fill);
            g.outline(x - r, y - r, sz, sz, border);
            if (st == JjkClasses.Status.UNLOCKED) g.outline(x - r + 1, y - r + 1, sz - 2, sz - 2, 0x60000000 | (c.color & 0xffffff));
        } else {
            // Passives are diamonds.
            int d = Math.round(r * 0.8f);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().rotate((float) Math.PI / 4);
            g.fill(-d, -d, d, d, border);
            g.fill(-d + 1, -d + 1, d - 1, d - 1, fill);
            g.pose().popMatrix();
        }
        g.item(new ItemStack(s.icon().get()), x - 8, y - 8);
        if (dim > 0) g.fill(x - 8, y - 8, x + 8, y + 8, dim << 24);
        if (s.id().equals(ClientTechnique.selected)) {
            g.fill(x - r, y + r + 1, x + r, y + r + 2, 0xffffffff);
        }
        if (st == JjkClasses.Status.NO_PREREQ || st == JjkClasses.Status.LOCKED) lock(g, x + r - 5, y + r - 6);
        if (st == JjkClasses.Status.NO_LEVEL || st == JjkClasses.Status.LOCKED)
            scaled(g, Component.translatable("jjk.quantumdeath.gui.lv", s.level()), x, y + r + 3, 0.6f, st == JjkClasses.Status.NO_LEVEL ? 0xffe06060 : 0xff6a6a78, true);
        else if (colSpacing() >= 44 && zoom(c) >= 0.95f) {
            String name = Component.translatable(s.key()).getString();
            int maxW = (int) (colSpacing() * 1.7f / 0.6f);
            if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - font.width("…")) + "…";
            scaled(g, Component.literal(name), x, y + r + 3, 0.6f, st == JjkClasses.Status.UNLOCKED ? TEXT : MUTED, true);
        }
    }

    private void lock(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 2, x + 6, y + 6, 0xffb0b0bc);
        g.outline(x + 1, y, 4, 3, 0xffb0b0bc);
    }

    private void tooltip(GuiGraphicsExtractor g, TechniqueClass c, SkillDef s, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(s.key()).withColor(c.color).withStyle(ChatFormatting.BOLD));
        MutableComponent kind = Component.translatable(s.active() ? "jjk.quantumdeath.gui.active" : "jjk.quantumdeath.gui.passive");
        kind.append(Component.literal(" · ")).append(Component.translatable(s.tier() == 0 ? "jjk.quantumdeath.gui.starting" : "jjk.quantumdeath.gui.tier." + s.tier()));
        lines.add(kind.withStyle(ChatFormatting.DARK_GRAY));
        String desc = Component.translatable(s.descKey()).getString();
        for (String para : desc.split("\n")) lines.add(Component.literal(para).withStyle(ChatFormatting.GRAY));
        JjkClasses.Status st = status(s);
        boolean unlocked = st == JjkClasses.Status.UNLOCKED;
        if (s.tier() > 0 || !unlocked) {
            lines.add(Component.translatable("jjk.quantumdeath.gui.cost", s.cost()).withColor(unlocked || ClientTechnique.tp >= s.cost() ? GOLD : 0xffe06060));
            lines.add(Component.translatable("jjk.quantumdeath.gui.requires_level", s.level()).withColor(ClientTechnique.level >= s.level() ? 0xff70c070 : 0xffe06060));
        }
        for (String req : s.requires()) {
            SkillDef p = c.get(req);
            if (p == null) continue;
            boolean has = ClientTechnique.unlocked.contains(req);
            lines.add(Component.translatable("jjk.quantumdeath.gui.requires", Component.translatable(p.key())).withColor(has ? 0xff70c070 : 0xffe06060));
        }
        List<String> info = new ArrayList<>();
        int cd = s.cooldown().getAsInt();
        if (cd > 0) info.add(Component.translatable("jjk.quantumdeath.gui.cooldown", cd >= 1200 ? (cd / 1200) + " min" : String.format("%.1fs", cd / 20f)).getString());
        double e = s.energy().getAsDouble();
        if (e > 0) info.add(Component.translatable("jjk.quantumdeath.gui.energy", Math.round(e)).getString());
        if (e < 0) info.add(Component.translatable("jjk.quantumdeath.gui.energy_all").getString());
        double res = s.resource().getAsDouble();
        if (res != 0 && c.resourceKey != null)
            info.add(Component.translatable(res > 0 ? "jjk.quantumdeath.gui.resource_cost" : "jjk.quantumdeath.gui.resource_gain", Math.round(Math.abs(res)), Component.translatable(c.resourceKey)).getString());
        if (s.hold()) info.add(Component.translatable("jjk.quantumdeath.gui.hold").getString());
        if (!info.isEmpty()) lines.add(Component.literal(String.join("  ·  ", info)).withColor(0xff9fb8d0));
        lines.add(Component.translatable("jjk.quantumdeath.gui.status." + st.name().toLowerCase()).withColor(switch (st) {
            case UNLOCKED -> 0xff000000 | c.color;
            case AVAILABLE -> GOLD;
            default -> 0xffe06060;
        }));
        if (unlocked && s.active()) lines.add(Component.translatable(s.id().equals(ClientTechnique.selected) ? "jjk.quantumdeath.gui.selected" : "jjk.quantumdeath.gui.right_click").withStyle(ChatFormatting.DARK_GRAY));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component l : lines) wrapped.addAll(font.split(l, 230));
        g.setTooltipForNextFrame(wrapped, mx, my);
    }

    /** Test hooks: where a node / class card / the confirm button is drawn (gui coordinates). */
    public int[] nodeCenter(SkillDef s) { TechniqueClass c = ClientTechnique.cls(); return c == null ? new int[2] : new int[]{nodeX(s), nodeY(c, s)}; }
    public int[] cardCenter(String classId) {
        for (Card card : cards()) if (card.cls.id.equals(classId)) return new int[]{card.x + card.w / 2, card.y + card.h / 2};
        return new int[2];
    }
    public int[] confirmCenter() { return new int[]{width / 2, height - 18}; }
    public boolean choosing() { return choosing; }

    // ------------------------------------------------------------------------------------------------ input

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (ryuna.qdeath.client.yokai.BookTabs.click(minecraft, width, event.x(), event.y(), ryuna.qdeath.client.yokai.BookTabs.Tab.TECHNIQUES)) return true;
        if (super.mouseClicked(event, doubled)) return true;
        if (choosing) {
            for (Card card : cards())
                if (card.hit(event.x(), event.y()) && !card.cls.id.equals(ClientTechnique.classId)) {
                    picked = card.cls.id;
                    rebuildWidgets();
                    return true;
                }
            return false;
        }
        pressedTree = event.y() >= TOP && event.y() <= bottom();
        pressX = event.x();
        pressY = event.y();
        scrollAtPress = scroll;
        return pressedTree;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!choosing && pressedTree) {
            scroll = scrollAtPress - (event.y() - pressY);
            TechniqueClass c = ClientTechnique.cls();
            if (c != null) clampScroll(c);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (!choosing && pressedTree && Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) < 4) {
            TechniqueClass c = ClientTechnique.cls();
            SkillDef s = c == null ? null : hovered(c, (int) event.x(), (int) event.y());
            if (s != null) {
                JjkClasses.Status st = status(s);
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && st != JjkClasses.Status.UNLOCKED) ClientPlayNetworking.send(new JjkNet.Unlock(s.id()));
                else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && st == JjkClasses.Status.UNLOCKED && s.active()) ClientPlayNetworking.send(new JjkNet.Select(s.id()));
            }
        }
        pressedTree = false;
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (choosing) return false;
        scroll -= v * 20;
        TechniqueClass c = ClientTechnique.cls();
        if (c != null) clampScroll(c);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ drawing helpers

    private void scaled(GuiGraphicsExtractor g, Component text, int x, int y, float scale, int color, boolean centered) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (centered) g.centeredText(font, text, 0, 0, color);
        else g.text(font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int thick, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.fill(0, -thick / 2, Math.round(len), thick - thick / 2, color);
        g.pose().popMatrix();
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xff) * f), gg = (int) (((rgb >> 8) & 0xff) * f), b = (int) ((rgb & 0xff) * f);
        return (r << 16) | (gg << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xff, (b >> 16) & 0xff), gg = (int) Mth.lerp(t, (a >> 8) & 0xff, (b >> 8) & 0xff), bb = (int) Mth.lerp(t, a & 0xff, b & 0xff);
        return (r << 16) | (gg << 8) | bb;
    }
}
