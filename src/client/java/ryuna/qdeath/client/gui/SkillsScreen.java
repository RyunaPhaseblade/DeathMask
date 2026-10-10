package ryuna.qdeath.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.client.QuantumdeathClient;
import ryuna.qdeath.client.data.ClientSkillTreeData;
import ryuna.qdeath.client.jjk.ClientTechnique;
import ryuna.qdeath.client.yokai.BookTabs;
import ryuna.qdeath.client.yokai.ClientYokai;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillProgress;
import ryuna.qdeath.yokai.YokaiContent;

import java.util.ArrayList;
import java.util.List;

/**
 * General skills of the Wisdom of the Void, drawn like the sorcery tree: six branches of three connected nodes,
 * drag to pan, wheel to zoom, tooltips with effect, cost, prerequisites and the reason a node is locked. Left click
 * learns (a drag is never a click); the server decides. General points and technique points are shown apart.
 */
public final class SkillsScreen extends Screen {
    private static final int BG = 0xff060509, GOLD = 0xffe8c547, TEXT = 0xffd8d4e0, MUTED = 0xff8a8696, TP = 0xff7ad0e8;
    private static final int COL = 66, ROW = 58, NODE = 22;
    private static final int[] BRANCH_COLOR = {0xd04848, 0x48b0d0, 0xd09048, 0x9a6ad8, 0xd8b84a, 0x6ac890};
    private static final String[] BRANCH_KEY = {"health", "speed", "strength", "perception", "bond", "spirit"};

    private final ClientSkillTreeData data = ClientSkillTreeData.INSTANCE;
    private double panX, panY, pressX, pressY, panAtX, panAtY;
    private float zoom = 1f;
    private boolean pressedTree, dragged;

    public SkillsScreen() { super(Component.translatable("screen.quantumdeath.skills")); }

    @Override public boolean isPauseScreen() { return false; }

    /** Opens fitted to the window: the whole tree visible (down to 55%), panning and zoom from there. */
    @Override protected void init() {
        float fitY = (bottom() - top() - 34) / (2f * ROW + NODE + 44);
        float fitX = (width - 30) / (6f * COL);
        zoom = Mth.clamp(Math.min(fitX, fitY), 0.55f, 1f);
        panX = 0;
        panY = 0;
    }

    private int top() { return BookTabs.HEIGHT + 34; }
    private int bottom() { return height - 30; }
    private int originX() { return width / 2 + (int) Math.round(panX); }
    private int originY() { return top() + Math.round(34 * zoom) + 6 + (int) Math.round(panY); }

    public int nodeX(SkillId s) { return originX() + Math.round((s.branch() - 2.5f) * COL * zoom); }
    public int nodeY(SkillId s) { return originY() + Math.round(s.tier() * ROW * zoom); }

    private void clamp() {
        zoom = Mth.clamp(zoom, 0.55f, 1.8f);
        double spanX = Math.max(0, (6 * COL * zoom - (width - 40)) / 2 + 30);
        double spanY = Math.max(30, (3 * ROW * zoom + 40) - (bottom() - top()) + 30);
        panX = Mth.clamp(panX, -spanX, spanX);
        panY = Mth.clamp(panY, -spanY, 30);
    }

    private static ItemStack icon(SkillId s) {
        Item i = switch (s) {
            case HEALTH_I -> Items.MELON_SLICE;
            case HEALTH_II -> Items.GOLDEN_APPLE;
            case HEALTH_III -> Items.ENCHANTED_GOLDEN_APPLE;
            case SPEED_I -> Items.SUGAR;
            case SPEED_II -> Items.RABBIT_FOOT;
            case SPEED_III -> Items.FEATHER;
            case STRENGTH_I -> Items.WOODEN_SWORD;
            case STRENGTH_II -> Items.IRON_SWORD;
            case STRENGTH_III -> Items.DIAMOND_SWORD;
            case PERCEPTION_I -> Items.SPYGLASS;
            case PERCEPTION_II -> Items.ENDER_EYE;
            case PERCEPTION_III -> Items.SCULK_SENSOR;
            case BOND_I -> YokaiContent.AMULET;
            case BOND_II -> Items.LEAD;
            case BOND_III -> Items.AMETHYST_SHARD;
            case SPIRIT_I -> Items.WRITABLE_BOOK;
            case SPIRIT_II -> Items.BUNDLE;
            case SPIRIT_III -> Items.CHAINMAIL_BOOTS;
        };
        return new ItemStack(i);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, BG);
        g.fillGradient(0, 0, width, height / 2, 0x26d8b84a, 0x00000000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x30000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        clamp();
        BookTabs.draw(g, font, width, BookTabs.Tab.GENERAL, mx, my);
        header(g, mx, my);
        g.enableScissor(0, top(), width, bottom());
        branchTitles(g);
        for (SkillId s : SkillId.values())
            for (SkillId req : s.prerequisites()) {
                boolean pu = data.isUnlocked(req), cu = data.isUnlocked(s);
                int color = 0xff000000 | BRANCH_COLOR[s.branch()];
                int lc = pu && cu ? color : pu ? (data.isAvailable(s) ? GOLD : 0xff4a4858) : 0xff26242e;
                line(g, nodeX(req), nodeY(req), nodeX(s), nodeY(s), pu && cu ? 2 : 1, lc);
            }
        SkillId hover = hovered(mx, my);
        for (SkillId s : SkillId.values()) node(g, s, s == hover);
        g.disableScissor();
        legend(g);
        super.extractRenderState(g, mx, my, a);
        if (hover != null) tooltip(g, hover, mx, my);
    }

    private void header(GuiGraphicsExtractor g, int mx, int my) {
        int y = BookTabs.HEIGHT + 6;
        scaled(g, Component.translatable("yokai.quantumdeath.book.general"), 12, y, 1.4f, GOLD, false);
        scaled(g, Component.translatable("yokai.quantumdeath.book.general_sub"), 12, y + 15, 0.75f, MUTED, false);
        // The two currencies, never mixed: general points (vanilla levels) and technique points (class levels).
        Component gp = Component.translatable("yokai.quantumdeath.book.general_points", data.points());
        Component tp = ClientTechnique.classId.isEmpty() ? Component.translatable("yokai.quantumdeath.book.technique_points_none")
                : Component.translatable("yokai.quantumdeath.book.technique_points", ClientTechnique.tp);
        int gx = width - 12 - Math.round(font.width(gp) * 1.2f);
        scaled(g, gp, gx, y, 1.2f, GOLD, false);
        scaled(g, tp, width - 12 - Math.round(font.width(tp) * 0.8f), y + 15, 0.8f, TP, false);
        Component spent = Component.translatable("screen.quantumdeath.spent_points", data.spentPoints(), data.totalCost());
        scaled(g, spent, width / 2, y + 2, 0.8f, MUTED, true);
        if (ClientYokai.override) scaled(g, Component.translatable("yokai.quantumdeath.book.demo"), width / 2, y + 13, 0.7f, 0xffe07040, true);
        if (my >= y && my <= y + 25 && mx >= gx - 4) {
            List<Component> lines = List.of(Component.translatable("yokai.quantumdeath.book.points_tip_1").withStyle(ChatFormatting.GOLD),
                    Component.translatable("yokai.quantumdeath.book.points_tip_2").withStyle(ChatFormatting.GRAY),
                    Component.translatable("yokai.quantumdeath.book.points_tip_3").withStyle(ChatFormatting.AQUA));
            g.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mx, my);
        }
        g.fill(8, top() - 2, width - 8, top() - 1, 0x50d8b84a);
    }

    private void branchTitles(GuiGraphicsExtractor g) {
        for (int b = 0; b < SkillId.BRANCHES; b++) {
            int x = originX() + Math.round((b - 2.5f) * COL * zoom);
            Component t = Component.translatable("yokai.quantumdeath.book.branch." + BRANCH_KEY[b]);
            float s = Math.min(0.75f * Math.max(zoom, 0.8f), (COL * zoom - 4) / (float) font.width(t));
            scaled(g, t, x, originY() - Math.round(30 * zoom), s, 0xff000000 | BRANCH_COLOR[b], true);
            if (b == 3) g.fill(x - Math.round(COL * zoom / 2), originY() - Math.round(34 * zoom), x - Math.round(COL * zoom / 2) + 1,
                    originY() + Math.round(2 * ROW * zoom + 20), 0x40d8b84a);
        }
    }

    private void node(GuiGraphicsExtractor g, SkillId s, boolean hover) {
        g.pose().pushMatrix();
        g.pose().translate(nodeX(s), nodeY(s));
        g.pose().scale(zoom, zoom);
        int r = NODE / 2, color = BRANCH_COLOR[s.branch()];
        SkillProgress.Status st = data.status(s);
        int border, fill, dim;
        switch (st) {
            case UNLOCKED -> { border = 0xff000000 | color; fill = 0xff000000 | darken(color, 0.22f); dim = 0; }
            case AVAILABLE -> {
                float pulse = 0.65f + 0.35f * Mth.sin(ClientYokai.clientTicks * 0.25f);
                border = 0xff000000 | lerp(0x6a5420, GOLD & 0xffffff, pulse); fill = 0xff1c1708; dim = 0;
            }
            case NO_POINTS -> { border = 0xffc07830; fill = 0xff151010; dim = 0x50; }
            default -> { border = 0xff44444e; fill = 0xff0e0e12; dim = 0xb0; }
        }
        if (hover) border = 0xffffffff;
        g.fill(-r, -r, r, r, fill);
        g.outline(-r, -r, NODE, NODE, border);
        if (st == SkillProgress.Status.UNLOCKED) g.outline(-r + 1, -r + 1, NODE - 2, NODE - 2, 0x60000000 | color);
        g.item(icon(s), -8, -8);
        if (dim > 0) g.fill(-8, -8, 8, 8, dim << 24);
        if (st == SkillProgress.Status.NO_PREREQ) {
            g.fill(r - 5, r - 4, r + 1, r, 0xffb0b0bc);
            g.outline(r - 4, r - 6, 4, 3, 0xffb0b0bc);
        }
        int cost = data.cost(s);
        if (st != SkillProgress.Status.UNLOCKED) scaled(g, Component.literal(String.valueOf(cost)), r - 2, -r - 1, 0.6f, st == SkillProgress.Status.AVAILABLE ? GOLD : MUTED, false);
        if (zoom >= 0.9f) {
            String name = Component.translatable(s.translationKey()).getString();
            int maxW = (int) (COL * 1.5f / 0.6f);
            if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - font.width("…")) + "…";
            scaled(g, Component.literal(name), 0, r + 3, 0.6f, st == SkillProgress.Status.UNLOCKED ? TEXT : MUTED, true);
        }
        g.pose().popMatrix();
    }

    private void legend(GuiGraphicsExtractor g) {
        int lx = 10, ly = height - 24;
        lx = legend(g, lx, ly, 0xffd8b84a, "unlocked");
        lx = legend(g, lx, ly, GOLD, "available");
        lx = legend(g, lx, ly, 0xffc07830, "no_points");
        legend(g, lx, ly, 0xff6a6a78, "no_prereq");
        scaled(g, Component.translatable("yokai.quantumdeath.book.hint"), 10, height - 12, 0.7f, MUTED, false);
    }

    private int legend(GuiGraphicsExtractor g, int x, int y, int color, String key) {
        g.fill(x, y + 1, x + 6, y + 7, 0xff0e0d14);
        g.outline(x, y + 1, 6, 6, color);
        Component t = Component.translatable("yokai.quantumdeath.book.state." + key);
        scaled(g, t, x + 9, y + 1, 0.7f, MUTED, false);
        return x + 14 + Math.round(font.width(t) * 0.7f);
    }

    private void tooltip(GuiGraphicsExtractor g, SkillId s, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        int color = 0xff000000 | BRANCH_COLOR[s.branch()];
        lines.add(Component.translatable(s.translationKey()).withColor(color).withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("yokai.quantumdeath.book.branch." + BRANCH_KEY[s.branch()]).append(Component.literal(" · "))
                .append(Component.translatable("yokai.quantumdeath.book.tier", s.tier() + 1)).withStyle(ChatFormatting.DARK_GRAY));
        for (String para : Component.translatable(s.descriptionKey()).getString().split("\n")) lines.add(Component.literal(para).withStyle(ChatFormatting.GRAY));
        SkillProgress.Status st = data.status(s);
        boolean unlocked = st == SkillProgress.Status.UNLOCKED;
        lines.add(Component.translatable("yokai.quantumdeath.book.cost", data.cost(s)).withColor(unlocked || data.points() >= data.cost(s) ? GOLD : 0xffe06060));
        for (SkillId req : s.prerequisites())
            lines.add(Component.translatable("screen.quantumdeath.requires", Component.translatable(req.translationKey()))
                    .withColor(data.isUnlocked(req) ? 0xff70c070 : 0xffe06060));
        lines.add(Component.translatable("yokai.quantumdeath.book.status." + st.name().toLowerCase(), data.cost(s), data.points()).withColor(switch (st) {
            case UNLOCKED -> color;
            case AVAILABLE -> GOLD;
            default -> 0xffe06060;
        }));
        if (st == SkillProgress.Status.AVAILABLE) lines.add(Component.translatable("yokai.quantumdeath.book.click_learn").withStyle(ChatFormatting.DARK_GRAY));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component l : lines) wrapped.addAll(font.split(l, 230));
        g.setTooltipForNextFrame(wrapped, mx, my);
    }

    // ------------------------------------------------------------------------------------------------ input

    private @Nullable SkillId hovered(int mx, int my) {
        if (my < top() || my > bottom()) return null;
        int r = Math.round((NODE / 2 + 2) * zoom);
        for (SkillId s : SkillId.values()) if (Math.abs(mx - nodeX(s)) <= r && Math.abs(my - nodeY(s)) <= r) return s;
        return null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (BookTabs.click(minecraft, width, event.x(), event.y(), BookTabs.Tab.GENERAL)) return true;
        if (super.mouseClicked(event, doubled)) return true;
        pressedTree = event.y() >= top() && event.y() <= bottom();
        dragged = false;
        pressX = event.x();
        pressY = event.y();
        panAtX = panX;
        panAtY = panY;
        return pressedTree;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressedTree) return super.mouseDragged(event, dx, dy);
        if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) >= 4) dragged = true;
        if (dragged) {
            panX = panAtX + (event.x() - pressX);
            panY = panAtY + (event.y() - pressY);
            clamp();
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (pressedTree && !dragged && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            SkillId s = hovered((int) event.x(), (int) event.y());
            if (s != null && data.status(s) == SkillProgress.Status.AVAILABLE) QuantumdeathClient.requestUnlock(s);
        }
        pressedTree = false;
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        float old = zoom;
        zoom = (float) (zoom * Math.pow(1.15, v));
        clamp();
        // Zoom about the cursor.
        double k = zoom / old;
        panX -= (k - 1) * (mx - originX());
        panY -= (k - 1) * (my - originY());
        clamp();
        return true;
    }

    // ------------------------------------------------------------------------------------------------ helpers

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
