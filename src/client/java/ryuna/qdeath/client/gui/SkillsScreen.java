package ryuna.qdeath.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import ryuna.qdeath.client.QuantumdeathClient;
import ryuna.qdeath.client.data.ClientSkillTreeData;
import ryuna.qdeath.client.rendering.SkillTreeRenderer;
import ryuna.qdeath.skills.SkillId;

import java.util.ArrayList;

/** Interactive screen. Rendering details live in {@link SkillTreeRenderer}. */
public final class SkillsScreen extends Screen {
    private static final Identifier TAB_SELECTED = Identifier.parse("advancements/tab_above_left_selected");
    private static final Identifier EXPERIENCE_BAR_BACKGROUND = Identifier.parse("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_BAR_PROGRESS = Identifier.parse("hud/experience_bar_progress");
    private final ClientSkillTreeData data = ClientSkillTreeData.INSTANCE;
    private final SkillTreeRenderer tree = new SkillTreeRenderer(data);
    private double panX;
    private double panY;
    private float zoom = 1.0f;
    private boolean dragging;
    private boolean pressedInContent;
    private double dragDistance;
    private double lastX;
    private double lastY;

    public SkillsScreen() { super(Component.translatable("screen.quantumdeath.skills")); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return super.mouseClicked(event, doubled);
        pressedInContent = insideContent(event.x(), event.y());
        dragging = pressedInContent;
        dragDistance = 0;
        lastX = event.x();
        lastY = event.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && pressedInContent && dragDistance <= 2.0) {
            hovered((int) event.x(), (int) event.y()).filter(data::isAvailable).ifPresent(QuantumdeathClient::requestUnlock);
        }
        dragging = false;
        pressedInContent = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (!dragging) return super.mouseDragged(event, offsetX, offsetY);
        double deltaX = event.x() - lastX;
        double deltaY = event.y() - lastY;
        dragDistance += Math.abs(deltaX) + Math.abs(deltaY);
        panX += deltaX;
        panY += deltaY;
        clampPan();
        lastX = event.x();
        lastY = event.y();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float oldZoom = zoom;
        float factor = (float) Math.pow(2.0, verticalAmount * 0.25);
        zoom = Mth.clamp(zoom * factor, tree.minZoom(width, height), tree.maxZoom(width, height));
        factor = zoom / oldZoom;
        panX -= (factor - 1.0) * (mouseX - width / 2.0 - panX);
        panY -= (factor - 1.0) * (mouseY - height / 2.0 - panY);
        clampPan();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        clampPan();
        tree.render(graphics, width, height);
        drawTab(graphics);
        drawHeader(graphics, mouseX, mouseY);
        graphics.enableScissor(tree.contentLeft(), tree.contentTop(), tree.contentRight(width), tree.contentBottom(height));
        tree.renderNodes(graphics, width, height, panX, panY, zoom);
        graphics.disableScissor();
        hovered(mouseX, mouseY).ifPresent(skill -> drawTooltip(graphics, skill, mouseX, mouseY));
    }

    private java.util.Optional<SkillId> hovered(int mouseX, int mouseY) {
        int radius = tree.radius(zoom);
        for (SkillId skill : SkillId.values()) {
            int dx = mouseX - tree.nodeX(width, skill, panX, zoom);
            int dy = mouseY - tree.nodeY(height, skill, panY, zoom);
            if (dx * dx + dy * dy <= radius * radius) return java.util.Optional.of(skill);
        }
        return java.util.Optional.empty();
    }

    private boolean insideContent(double mouseX, double mouseY) {
        return mouseX >= tree.contentLeft() && mouseX < tree.contentRight(width)
                && mouseY >= tree.contentTop() && mouseY < tree.contentBottom(height);
    }

    private void clampPan() {
        zoom = Mth.clamp(zoom, tree.minZoom(width, height), tree.maxZoom(width, height));
        panX = tree.clampPanX(width, panX, zoom);
        panY = tree.clampPanY(height, panY, zoom);
    }

    private void drawTab(GuiGraphicsExtractor graphics) {
        int x = SkillTreeRenderer.FRAME_MARGIN;
        int y = SkillTreeRenderer.FRAME_MARGIN;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAB_SELECTED, x, y, 28, 32);
        graphics.item(new net.minecraft.world.item.ItemStack(Items.NETHER_STAR), x + 6, y + 9);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int titleY = SkillTreeRenderer.FRAME_MARGIN + 31;
        graphics.text(font, tree.title(), SkillTreeRenderer.FRAME_MARGIN + 9, titleY, 0xff404040, false);

        var points = Component.translatable("screen.quantumdeath.points", data.points());
        int pointsX = width - SkillTreeRenderer.FRAME_MARGIN - 9 - font.width(points);
        graphics.text(font, points, pointsX, titleY, 0xff404040, false);

        int barWidth = Math.min(182, Math.max(80, width - 100));
        int barX = (width - barWidth) / 2;
        int barY = SkillTreeRenderer.FRAME_MARGIN + 15;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND, barX, barY, barWidth, 5);
        int progressWidth = Math.round(barWidth * data.spentProgress());
        if (progressWidth > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS, barWidth, 5, 0, 0, barX, barY, progressWidth, 5);
        }
        var spent = Component.translatable("screen.quantumdeath.spent_points", data.spentPoints(), data.totalSkills());
        graphics.centeredText(font, spent, width / 2, barY - 1, 0xffffffff);
        boolean overPoints = mouseX >= pointsX && mouseX <= width - SkillTreeRenderer.FRAME_MARGIN
                && mouseY >= titleY && mouseY <= titleY + font.lineHeight;
        boolean overBar = mouseX >= barX && mouseX <= barX + barWidth
                && mouseY >= barY - font.lineHeight && mouseY <= barY + 5;
        if (overPoints || overBar) {
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("screen.quantumdeath.available_points", data.points()));
            lines.add(Component.translatable("screen.quantumdeath.spent_points", data.spentPoints(), data.totalSkills()));
            graphics.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, SkillId skill, int mouseX, int mouseY) {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(skill.translationKey()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(skill.descriptionKey()));
        lines.add(Component.translatable("screen.quantumdeath.cost", 1));
        if (skill.prerequisite().isPresent() && !data.isUnlocked(skill.prerequisite().orElseThrow())) {
            lines.add(Component.translatable("screen.quantumdeath.requires", Component.translatable(skill.prerequisite().orElseThrow().translationKey())).withStyle(ChatFormatting.RED));
        }
        graphics.setTooltipForNextFrame(lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
    }
}
