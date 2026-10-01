package ryuna.qdeath.client.rendering;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidgetType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import ryuna.qdeath.client.data.ClientSkillTreeData;
import ryuna.qdeath.client.data.SkillNodeLayout;
import ryuna.qdeath.skills.SkillId;

/** Draws the vanilla-advancement styled frame, edges, icons and node states. */
public final class SkillTreeRenderer {
    private static final Identifier WINDOW_TEXTURE = Identifier.parse("textures/gui/advancements/window.png");
    private static final Identifier BACKGROUND_TEXTURE = Identifier.parse("textures/gui/advancements/backgrounds/stone.png");
    public static final int FRAME_MARGIN = 8;
    public static final int CONTENT_PADDING_LEFT = 17;
    public static final int CONTENT_PADDING_RIGHT = 17;
    public static final int CONTENT_PADDING_TOP = 54;
    public static final int CONTENT_PADDING_BOTTOM = 17;
    private static final int BRANCH_SPACING = 78;
    private static final int TIER_SPACING = 38;
    private static final int NODE_RADIUS = 13;

    private final ClientSkillTreeData data;

    public SkillTreeRenderer(ClientSkillTreeData data) { this.data = data; }

    public int contentLeft() { return CONTENT_PADDING_LEFT; }
    public int contentTop() { return CONTENT_PADDING_TOP; }
    public int contentRight(int screenWidth) { return screenWidth - CONTENT_PADDING_RIGHT; }
    public int contentBottom(int screenHeight) { return screenHeight - CONTENT_PADDING_BOTTOM; }
    public int contentWidth(int screenWidth) { return contentRight(screenWidth) - contentLeft(); }
    public int contentHeight(int screenHeight) { return contentBottom(screenHeight) - contentTop(); }

    public float minZoom(int screenWidth, int screenHeight) {
        float treeWidth = (SkillId.values().length == 0 ? 0 : 2 * BRANCH_SPACING + NODE_RADIUS * 2 + 64);
        float treeHeight = 2 * TIER_SPACING + NODE_RADIUS * 2 + 64;
        return Math.max(0.5f, Math.min(1.0f, Math.min(contentWidth(screenWidth) / treeWidth, contentHeight(screenHeight) / treeHeight)));
    }
    public float maxZoom(int screenWidth, int screenHeight) { return Math.max(1.5f, minZoom(screenWidth, screenHeight) * 2.0f); }

    public int nodeX(int screenWidth, SkillId skill, double panX, float zoom) {
        return screenWidth / 2 + (int) Math.round(panX + (skill.branch() - 1) * BRANCH_SPACING * zoom);
    }
    public int nodeY(int screenHeight, SkillId skill, double panY, float zoom) {
        return screenHeight / 2 + (int) Math.round(panY + (skill.tier() - 1) * TIER_SPACING * zoom);
    }
    public int radius(float zoom) { return Math.max(10, Math.round(NODE_RADIUS * zoom)); }

    public double clampPanX(int screenWidth, double panX, float zoom) {
        double extent = BRANCH_SPACING * zoom + radius(zoom) + 16;
        double min = contentLeft() - screenWidth / 2.0 + extent;
        double max = contentRight(screenWidth) - screenWidth / 2.0 - extent;
        return min > max ? 0 : Mth.clamp(panX, min, max);
    }

    public double clampPanY(int screenHeight, double panY, float zoom) {
        double extent = TIER_SPACING * zoom + radius(zoom) + 16;
        double min = contentTop() - screenHeight / 2.0 + extent;
        double max = contentBottom(screenHeight) - screenHeight / 2.0 - extent;
        return min > max ? 0 : Mth.clamp(panY, min, max);
    }

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        final int halfWidth = 126;
        final int halfHeight = 70;
        final int middleWidth = screenWidth - FRAME_MARGIN * 2 - halfWidth * 2;
        final int middleHeight = screenHeight - FRAME_MARGIN * 2 - halfHeight * 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0xff000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, contentLeft(), contentTop(), 0, 0, contentWidth(screenWidth), contentHeight(screenHeight), 16, 16);

        // Draw the advancement window as corners and repeatable edges instead of stretching its 252x140 texture.
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, FRAME_MARGIN, FRAME_MARGIN + 28, 0, 0, halfWidth, halfHeight - 28, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, screenWidth - FRAME_MARGIN - halfWidth, FRAME_MARGIN + 28, halfWidth, 0, halfWidth, halfHeight - 28, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, FRAME_MARGIN + halfWidth, FRAME_MARGIN + 28, halfWidth - 1, 0, middleWidth, halfHeight, 2, halfHeight, 256, 256);

        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, FRAME_MARGIN, FRAME_MARGIN + halfHeight, 0, halfHeight - 1, halfWidth, middleHeight, halfWidth, 2, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, screenWidth - FRAME_MARGIN - halfWidth, FRAME_MARGIN + halfHeight, halfWidth, halfHeight - 1, halfWidth, middleHeight, halfWidth, 2, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, FRAME_MARGIN + halfWidth, screenHeight - FRAME_MARGIN - halfHeight, halfWidth - 1, halfHeight, middleWidth, halfHeight, 2, halfHeight, 256, 256);

        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, FRAME_MARGIN, screenHeight - FRAME_MARGIN - halfHeight, 0, halfHeight, halfWidth, halfHeight, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, screenWidth - FRAME_MARGIN - halfWidth, screenHeight - FRAME_MARGIN - halfHeight, halfWidth, halfHeight, halfWidth, halfHeight, 256, 256);
    }

    public void renderNodes(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, double panX, double panY, float zoom) {
        for (SkillId skill : SkillId.values()) skill.prerequisite().ifPresent(parent -> drawConnection(graphics, screenWidth, screenHeight, parent, skill, panX, panY, zoom));
        for (SkillId skill : SkillId.values()) drawNode(graphics, screenWidth, screenHeight, SkillNodeLayout.of(skill), panX, panY, zoom);
    }

    private void drawConnection(GuiGraphicsExtractor graphics, int width, int height, SkillId from, SkillId to, double panX, double panY, float zoom) {
        int x = nodeX(width, from, panX, zoom);
        int y1 = nodeY(height, from, panY, zoom);
        int y2 = nodeY(height, to, panY, zoom);
        int color = data.isUnlocked(from) ? 0xff62a35f : data.isAvailable(to) ? 0xff8f9f4f : 0xff5a5a5a;
        graphics.fill(x - 1, Math.min(y1, y2), x + 2, Math.max(y1, y2), color);
    }

    private void drawNode(GuiGraphicsExtractor graphics, int width, int height, SkillNodeLayout node, double panX, double panY, float zoom) {
        int x = nodeX(width, node.skill(), panX, zoom), y = nodeY(height, node.skill(), panY, zoom), radius = radius(zoom);
        var frame = data.isUnlocked(node.skill()) ? AdvancementWidgetType.OBTAINED.boxSprite() : AdvancementWidgetType.UNOBTAINED.boxSprite();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, x - radius, y - radius, radius * 2, radius * 2);
        if (!data.isUnlocked(node.skill()) && data.isAvailable(node.skill())) {
            graphics.fill(x - radius + 3, y - radius + 3, x + radius - 3, y + radius - 3, 0x557f6a20);
        }
        graphics.item(node.icon(), x - 8, y - 8);
    }

    public Component title() { return Component.translatable("screen.quantumdeath.skills"); }
}
