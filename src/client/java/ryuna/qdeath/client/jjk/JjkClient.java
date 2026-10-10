package ryuna.qdeath.client.jjk;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.jjk.JjkContent;
import ryuna.qdeath.jjk.JjkNet;
import ryuna.qdeath.jjk.naoya.Naoya;
import ryuna.qdeath.jjk.naoya.ProjectionPath;
import ryuna.qdeath.moditems.modItems;

/** Client side of the cursed techniques: keys, packets, HUD, renderers and the Projection plan preview. */
public final class JjkClient {
    private JjkClient() {}

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Quantumdeath.id("techniques"));
    public static final KeyMapping USE = key("use", InputConstants.KEY_R);
    public static final KeyMapping SECONDARY = key("secondary", InputConstants.KEY_V);
    public static final KeyMapping NEXT = key("next", InputConstants.KEY_Z);
    public static final KeyMapping PREVIOUS = key("previous", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TREE = key("tree", InputConstants.KEY_J);

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.quantumdeath.technique_" + name, code, CATEGORY));
    }

    private static boolean held;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(JjkNet.Progress.TYPE, (p, ctx) -> ClientTechnique.progress(p));
        ClientPlayNetworking.registerGlobalReceiver(JjkNet.State.TYPE, (p, ctx) -> ClientTechnique.state(p));
        ClientPlayNetworking.registerGlobalReceiver(JjkNet.Flash.TYPE, (p, ctx) -> {
            ClientTechnique.flashColor = p.color();
            ClientTechnique.flashTicks = ClientTechnique.flashMax = Math.max(1, p.ticks());
        });
        EntityRendererRegistry.register(JjkContent.PROJECTILE, ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        EntityRendererRegistry.register(JjkContent.BODY, TechniqueBodyRenderer::new);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Quantumdeath.id("techniques"), TechniqueHud::extract);
        ClientTickEvents.END_CLIENT_TICK.register(JjkClient::tick);
    }

    private static void send(byte action) { ClientPlayNetworking.send(new JjkNet.Input(action)); }

    public static void openTree(Minecraft mc) { mc.setScreenAndShow(new TechniqueScreen()); }

    private static void tick(Minecraft mc) {
        ClientTechnique.clientTicks++;
        if (ClientTechnique.flashTicks > 0) ClientTechnique.flashTicks--;
        if (mc.player == null || mc.getConnection() == null) { held = false; return; }
        boolean hasClass = !ClientTechnique.classId.isEmpty();
        boolean down = hasClass && USE.isDown() && mc.gui.screen() == null;
        boolean clicked = false;
        while (USE.consumeClick()) clicked = true;
        if (down != held) {
            held = down;
            send(down ? JjkNet.PRESS : JjkNet.RELEASE);
        } else if (clicked && !down && hasClass && mc.gui.screen() == null) {
            // A tap shorter than a tick: press and release together.
            send(JjkNet.PRESS);
            send(JjkNet.RELEASE);
        }
        while (SECONDARY.consumeClick()) if (hasClass) send(JjkNet.SECONDARY);
        while (NEXT.consumeClick()) if (hasClass) send(InputConstants.isKeyDown(InputConstants.KEY_LALT) ? JjkNet.PREV : JjkNet.NEXT);
        while (PREVIOUS.consumeClick()) if (hasClass) send(JjkNet.PREV);
        while (TREE.consumeClick()) {
            // The book opens the path; once a technique is chosen the key opens it too.
            if (hasClass || mc.player.getInventory().contains(new net.minecraft.world.item.ItemStack(modItems.WISDOM_OF_THE_VOID))) openTree(mc);
            else mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jjk.quantumdeath.msg.need_book"));
        }
        if (hasClass && mc.gui.screen() == null && Naoya.PROJECTION.equals(ClientTechnique.selected) && ClientTechnique.clientTicks % 3 == 0) preview(mc);
    }

    private static final DustParticleOptions CLEAR = new DustParticleOptions(0xe8e2c8, 0.6f);
    private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xff3030, 0.8f);
    private static final DustParticleOptions TURN = new DustParticleOptions(0xffa030, 0.7f);

    /** Naoya: draws the frames the next Projection would follow, red where the plan would break. */
    private static void preview(Minecraft mc) {
        var ind = ClientTechnique.indicator("@naoya");
        if (ind == null || ClientTechnique.indicator("jjk.quantumdeath.ind.frame") != null) return;
        String[] f = ind.arg().split(";");
        if (f.length < 6) return;
        try {
            double distance = Double.parseDouble(f[0]), stepUp = Double.parseDouble(f[1]);
            boolean chain = f[2].equals("1");
            Vec3 last = new Vec3(Double.parseDouble(f[3]), 0, Double.parseDouble(f[4]));
            double maxTurn = Double.parseDouble(f[5]);
            var p = mc.player;
            Vec3 dir = ProjectionPath.horizontal(p.getLookAngle());
            boolean badTurn = chain && Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.dot(last))))) > maxTurn;
            ProjectionPath.Plan plan = ProjectionPath.plan(p.level(), p, p.position(), dir, distance, 24, stepUp);
            for (int i = 4; i <= 24; i += 4) {
                Vec3 v = plan.frames()[i];
                boolean broken = !plan.clear() && i >= plan.blockedAt();
                p.level().addParticle(broken ? BLOCKED : badTurn ? TURN : CLEAR, v.x, v.y + 0.15, v.z, 0, 0, 0);
            }
            if (!plan.clear()) {
                Vec3 v = plan.last();
                p.level().addParticle(BLOCKED, v.x, v.y + 1.0, v.z, 0, 0, 0);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
