package ryuna.qdeath.client.jjk;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.SkillDef;
import ryuna.qdeath.jjk.TechniqueClass;

import java.util.List;

/**
 * The technique HUD, bottom-right: the indicators of the class, cursed energy and the class resource, the selected
 * technique, and a bar of every unlocked technique with its cooldown. A charge bar sits under the crosshair while
 * something charges, and Naoya's momentum draws speed lines at the screen edges.
 */
public final class TechniqueHud {
    private TechniqueHud() {}

    static final int ENERGY_COLOR = 0xff7a5cff;
    static final int PANEL = 0xb00b0b10, EDGE = 0xff2c2c36;

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientTechnique.flashTicks > 0) {
            int a = (ClientTechnique.flashColor >>> 24) * ClientTechnique.flashTicks / Math.max(1, ClientTechnique.flashMax);
            g.fill(0, 0, w, h, (a << 24) | (ClientTechnique.flashColor & 0xffffff));
        }
        TechniqueClass c = ClientTechnique.cls();
        if (c == null || mc.player.isSpectator()) return;
        Font font = mc.font;
        JjkNet.State s = ClientTechnique.state;
        List<SkillDef> actives = ClientTechnique.actives();
        int slot = 20;
        int panelW = Math.max(132, actives.size() * slot);
        int x0 = w - 6 - panelW;
        // On narrow screens the panel would cover the hotbar and the health/food rows: lift it above them.
        int bottom = x0 < w / 2 + 95 ? h - 54 : h - 6;
        int y = bottom - 20;

        // Technique bar
        for (int i = 0; i < actives.size(); i++) {
            SkillDef sk = actives.get(i);
            int sx = x0 + i * slot;
            boolean sel = sk.id().equals(ClientTechnique.selected);
            g.fill(sx, y, sx + 18, y + 18, PANEL);
            g.outline(sx, y, 18, 18, sel ? 0xff000000 | c.color : EDGE);
            if (sel) g.outline(sx - 1, y - 1, 20, 20, 0x80000000 | (c.color & 0xffffff));
            g.item(new ItemStack(sk.icon().get()), sx + 1, y + 1);
            float frac = ClientTechnique.cooldownFraction(sk.id());
            if (frac > 0) {
                int hh = Math.round(16 * frac);
                g.fill(sx + 1, y + 17 - hh, sx + 17, y + 17, 0xb0000000);
                int left = ClientTechnique.cooldownLeft(sk.id());
                String t = left >= 200 ? (left / 20) + "" : String.format("%.1f", left / 20f);
                if (left >= 1200) t = (left / 1200) + "m";
                g.pose().pushMatrix();
                g.pose().translate(sx + 9, y + 6);
                g.pose().scale(0.75f, 0.75f);
                g.centeredText(font, t, 0, 0, 0xffffffff);
                g.pose().popMatrix();
            }
        }
        int lock = ClientTechnique.cooldownLeft("jjk:switch");
        if (lock > 0) g.text(font, Component.translatable("jjk.quantumdeath.hud.sealed", lock / 20 + 1), x0, y - 10, 0xffff6060);

        // Selected technique + keys
        y -= 12;
        SkillDef sel = actives.stream().filter(a -> a.id().equals(ClientTechnique.selected)).findFirst().orElse(null);
        if (sel != null) {
            Component name = Component.translatable(sel.key());
            g.text(font, name, x0, y, 0xff000000 | c.color);
            String keys = "[" + JjkClient.USE.getTranslatedKeyMessage().getString() + "]" + (sel.hold() ? "⏵" : "")
                    + " [" + JjkClient.SECONDARY.getTranslatedKeyMessage().getString() + "] [" + JjkClient.NEXT.getTranslatedKeyMessage().getString() + "]";
            g.text(font, keys, x0 + panelW - font.width(keys), y, 0xff8a8a96);
        }

        // Pools
        y -= 8;
        bar(g, font, x0, y, panelW, s.energy(), s.energyMax(), ENERGY_COLOR, Component.translatable("jjk.quantumdeath.hud.energy"));
        if (c.resourceKey != null && s.resourceMax() > 0) {
            y -= 8;
            float frac = s.resource() / s.resourceMax();
            int col = frac < 0.15f && (ClientTechnique.clientTicks / 5) % 2 == 0 ? 0xffff3030 : 0xff000000 | c.resourceColor;
            bar(g, font, x0, y, panelW, s.resource(), s.resourceMax(), col, Component.translatable(c.resourceKey));
        }

        // Indicators
        for (JjkNet.Indicator ind : s.indicators()) {
            if (ind.key().startsWith("@")) continue;
            y -= 11;
            Component label = Component.translatable(ind.key(), ind.arg());
            g.fill(x0 - 2, y - 1, x0 + panelW + 1, y + 9, 0x70000000);
            g.text(font, label, x0, y, ind.color() | 0xff000000);
            if (ind.fill() >= 0) {
                int bw = 36, bx = x0 + panelW - bw;
                g.fill(bx, y + 3, bx + bw, y + 6, 0xff1a1a20);
                g.fill(bx, y + 3, bx + Math.round(bw * Math.min(1, ind.fill())), y + 6, ind.color() | 0xff000000);
            }
        }

        // Charge under the crosshair
        if (s.charge() > 0) {
            int cw = 50, cx = w / 2 - cw / 2, cy = h / 2 + 12;
            g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 4, 0xc0000000);
            g.fill(cx, cy, cx + Math.round(cw * s.charge()), cy + 3, s.charge() >= 1f ? 0xffffffff : 0xff000000 | c.color);
        }

        // Naoya: speed lines grow with momentum
        JjkNet.Indicator m = ClientTechnique.indicator("jjk.quantumdeath.ind.mach");
        if (m == null) m = ClientTechnique.indicator("jjk.quantumdeath.ind.momentum");
        if (m != null || ClientTechnique.indicator("jjk.quantumdeath.ind.frame") != null) speedLines(g, w, h, m == null ? 1f : parse(m.arg()));
    }

    private static float parse(String s) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return 1f; }
    }

    private static void speedLines(GuiGraphicsExtractor g, int w, int h, float momentum) {
        int lines = Math.min(14, 3 + Math.round((momentum - 1) * 4));
        int alpha = Math.min(150, 40 + Math.round((momentum - 1) * 45));
        long t = ClientTechnique.clientTicks;
        for (int i = 0; i < lines; i++) {
            int seed = (int) ((i * 7919 + t * 13) % 997);
            int y = (seed * h) / 997;
            int len = 20 + (seed % 40) + Math.round(momentum * 10);
            int col = (alpha << 24) | 0xf4f1e8;
            if (i % 2 == 0) g.fill(0, y, len, y + 1, col);
            else g.fill(w - len, y, w, y + 1, col);
        }
    }

    private static void bar(GuiGraphicsExtractor g, Font font, int x, int y, int width, float value, float max, int color, Component label) {
        int labelW = 34;
        g.pose().pushMatrix();
        g.pose().translate(x, y - 1);
        g.pose().scale(0.7f, 0.7f);
        g.text(font, label, 0, 0, 0xffb0b0bc);
        g.pose().popMatrix();
        int bx = x + labelW, bw = width - labelW - 30;
        g.fill(bx - 1, y - 1, bx + bw + 1, y + 4, 0xc0000000);
        g.fill(bx, y, bx + Math.round(bw * Math.min(1, value / Math.max(1, max))), y + 3, color);
        String num = Math.round(value) + "/" + Math.round(max);
        g.pose().pushMatrix();
        g.pose().translate(bx + bw + 3, y - 1);
        g.pose().scale(0.7f, 0.7f);
        g.text(font, num, 0, 0, 0xffd0d0dc);
        g.pose().popMatrix();
    }
}
