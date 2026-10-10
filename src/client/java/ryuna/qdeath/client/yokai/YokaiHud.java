package ryuna.qdeath.client.yokai;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.yokai.YokaiNet;

/** Compact HUD panels: the tracking eye's last observation, and Resonance's companion status. Top left. */
public final class YokaiHud {
    private YokaiHud() {}

    private static final int BG = 0xa0080610, EDGE = 0xff3a2f4a, TEXT = 0xffd8d4e0, MUTED = 0xff8a8696;

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        Font font = mc.font;
        int y = 6;
        YokaiNet.Tracking t = ClientYokai.tracking;
        if (t.active()) y = tracking(g, font, t, y) + 4;
        if (ClientYokai.node(SkillId.BOND_III) && !ClientYokai.companions.isEmpty()) resonance(g, font, y);
    }

    private static int tracking(GuiGraphicsExtractor g, Font font, YokaiNet.Tracking t, int y) {
        long age = Math.max(0, (t.now() - t.observedAt()) / 20 + (ClientYokai.clientTicks - ClientYokai.trackingAt) / 20);
        boolean live = t.status().equals("observing");
        String dim = t.dimension().contains(":") ? t.dimension().substring(t.dimension().indexOf(':') + 1) : t.dimension();
        Component l1 = Component.translatable("yokai.quantumdeath.hud.tracking", t.target());
        Component l2 = Component.literal(t.x() + " " + t.y() + " " + t.z() + "  ·  " + dim);
        Component l3 = Component.translatable("yokai.quantumdeath.tracking." + t.status()).append(live ? Component.empty()
                : Component.literal("  ·  ").append(Component.translatable("yokai.quantumdeath.hud.age", age)));
        int w = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))) + 12;
        int h = 3 * 10 + 6;
        g.fill(4, y, 4 + w, y + h, BG);
        g.outline(4, y, w, h, EDGE);
        // The eye's state: steady while it sees, pulsing amber while it searches, grey once frozen or lost.
        int dot = live ? 0xffe03a2a : t.status().equals("searching") ? ((ClientYokai.clientTicks / 6) % 2 == 0 ? 0xffe0a030 : 0xff6a5020) : 0xff6a6a78;
        g.fill(8, y + 5, 12, y + 9, dot);
        g.text(font, l1, 15, y + 4, TEXT, false);
        g.text(font, l2, 8, y + 14, live ? TEXT : MUTED, false);
        g.text(font, l3, 8, y + 24, live ? 0xff70c070 : 0xffd0a060, false);
        return y + h;
    }

    private static void resonance(GuiGraphicsExtractor g, Font font, int y) {
        int w = 120;
        int h = 6 + ClientYokai.companions.size() * 13;
        g.fill(4, y, 4 + w, y + h, BG);
        g.outline(4, y, w, h, EDGE);
        int ly = y + 4;
        for (YokaiNet.Status s : ClientYokai.companions) {
            Component name = s.name().isEmpty() ? Component.translatable("yokai.quantumdeath." + s.species()) : Component.literal(s.name());
            float frac = s.max() > 0 ? Math.max(0, Math.min(1, s.health() / s.max())) : 0;
            boolean low = frac < 0.25f && s.state().equals("deployed");
            int col = s.state().equals("stored") ? 0xff9fb8d0 : low ? ((ClientYokai.clientTicks / 8) % 2 == 0 ? 0xffff4040 : 0xff902020) : TEXT;
            g.text(font, font.plainSubstrByWidth(name.getString(), 70), 8, ly, col, false);
            g.fill(80, ly + 2, 118, ly + 6, 0xff1c1a24);
            g.fill(80, ly + 2, 80 + Math.round(38 * frac), ly + 6, low ? 0xffd03030 : s.state().equals("stored") ? 0xff6a88a8 : 0xff60b060);
            if (s.state().equals("stored")) g.text(font, "◇", 72, ly, 0xff9fb8d0, false);
            ly += 13;
        }
    }
}
