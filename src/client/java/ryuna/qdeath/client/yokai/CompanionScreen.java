package ryuna.qdeath.client.yokai;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ryuna.qdeath.yokai.Species;
import ryuna.qdeath.yokai.YokaiNet;

/**
 * Orders for one's own shikigami. Follow and stay come with Pacts; defend, passive, the special action and tracking
 * need Orders. The screen only asks: the server checks everything again.
 */
public final class CompanionScreen extends Screen {
    private final YokaiNet.OpenCompanion data;

    public CompanionScreen(YokaiNet.OpenCompanion data) {
        super(Component.translatable("yokai.quantumdeath.companion.title"));
        this.data = data;
    }

    public int entity() { return data.entity(); }

    @Override public boolean isPauseScreen() { return false; }

    private void send(String action, String arg) {
        ClientPlayNetworking.send(new YokaiNet.Order(data.entity(), action, arg));
        if (action.equals("ride") || action.equals("storage")) onClose();
    }

    @Override protected void init() {
        int cx = width / 2, y = height / 2 - 30, bw = 96;
        add("follow", cx - bw - 2, y, bw, true, null);
        add("stay", cx + 2, y, bw, true, null);
        Component needOrders = Component.translatable("yokai.quantumdeath.companion.need_orders");
        add("defend", cx - bw - 2, y + 22, bw, data.orders(), needOrders);
        add("passive", cx + 2, y + 22, bw, data.orders(), needOrders);
        Species s = Species.byId(data.species());
        int row = y + 44;
        if (s == Species.TANUKI || s == Species.TENGU) {
            add("special_" + s.id, "special", cx - bw - 2, row, bw * 2 + 4, data.orders(), needOrders);
            row += 22;
        }
        if (s == Species.ITTAN_MOMEN) { add("ride", cx - bw - 2, row, bw * 2 + 4, true, null); row += 22; }
        if (s == Species.CHEST) { add("storage", cx - bw - 2, row, bw * 2 + 4, true, null); row += 22; }
        if (s == Species.SWARM_EYE) {
            if (data.tracking()) {
                add("untrack", cx - bw - 2, row, bw * 2 + 4, true, null);
                row += 22;
            } else {
                for (String name : data.targets()) {
                    addRenderableWidget(Button.builder(Component.translatable("yokai.quantumdeath.companion.track", name), b -> send("track", name))
                            .bounds(cx - bw - 2, row, bw * 2 + 4, 20).build()).active = data.orders();
                    row += 22;
                }
                if (data.targets().isEmpty()) {
                    Button none = addRenderableWidget(Button.builder(Component.translatable("yokai.quantumdeath.companion.no_targets"), b -> {})
                            .bounds(cx - bw - 2, row, bw * 2 + 4, 20).build());
                    none.active = false;
                    none.setTooltip(Tooltip.create(Component.translatable("yokai.quantumdeath.companion.no_targets_tip")));
                    row += 22;
                }
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 50, row + 6, 100, 20).build());
    }

    private void add(String action, int x, int y, int w, boolean active, Component why) { add(action, action, x, y, w, active, why); }

    private void add(String labelKey, String action, int x, int y, int w, boolean active, Component why) {
        Button b = addRenderableWidget(Button.builder(Component.translatable("yokai.quantumdeath.companion." + labelKey), btn -> send(action, ""))
                .bounds(x, y, w, 20).build());
        b.active = active;
        if (!active && why != null) b.setTooltip(Tooltip.create(why));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        int cx = width / 2, y = height / 2 - 70;
        g.fill(cx - 110, y - 6, cx + 110, height / 2 + 30 + 22 * 4, 0xc0080610);
        Species s = Species.byId(data.species());
        Component name = data.name().isEmpty() && s != null ? Component.translatable(s.key()) : Component.literal(data.name());
        g.centeredText(font, name, cx, y, s == null ? 0xffffffff : 0xff000000 | s.color);
        float frac = data.max() > 0 ? data.health() / data.max() : 0;
        g.fill(cx - 60, y + 14, cx + 60, y + 18, 0xff1c1a24);
        g.fill(cx - 60, y + 14, cx - 60 + Math.round(120 * frac), y + 18, frac < 0.25f ? 0xffd03030 : 0xff60b060);
        g.centeredText(font, Component.translatable("yokai.quantumdeath.companion.order", Component.translatable("yokai.quantumdeath.companion." + data.order())),
                cx, y + 24, 0xff8a8696);
        super.extractRenderState(g, mx, my, a);
    }
}
