package ryuna.qdeath.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * One model class for every Hell creature: geometry comes from {@link ryuna.qdeath.client.model.HellModelLayers},
 * motion from an {@link Anim} in {@link HellAnims}. Parts are looked up by name; missing parts are simply skipped.
 */
public class HellModel extends EntityModel<HellRenderState> {
    @FunctionalInterface
    public interface Anim { void apply(HellModel m, HellRenderState s); }

    private final Function<String, @Nullable ModelPart> lookup;
    private final Anim anim;
    /** Torso lean accumulated during one frame by human poses (see DeadAnims). */
    public float lean;

    public HellModel(ModelPart root, Anim anim, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
        this.lookup = root.createPartLookup();
        this.anim = anim;
    }

    public @Nullable ModelPart p(String name) { return lookup.apply(name); }

    @Override
    public void setupAnim(HellRenderState state) {
        super.setupAnim(state);
        anim.apply(this, state);
    }

    // ------------------------------------------------------------------ helpers used by HellAnims
    public static final float DEG = Mth.DEG_TO_RAD;

    public void look(String head, HellRenderState s, float factor) {
        ModelPart h = p(head);
        if (h == null) return;
        h.yRot += s.yRot * DEG * factor;
        h.xRot += s.xRot * DEG * factor;
    }

    public void walkPair(String right, String left, HellRenderState s, float amp) {
        float swing = Mth.cos(s.walkAnimationPos * 0.6662f) * amp * s.walkAnimationSpeed;
        ModelPart r = p(right), l = p(left);
        if (r != null) r.xRot += swing;
        if (l != null) l.xRot -= swing;
    }

    public void quad(HellRenderState s, float amp) {
        float swing = Mth.cos(s.walkAnimationPos * 0.6662f) * amp * s.walkAnimationSpeed;
        for (int i = 0; i < 4; i++) {
            ModelPart leg = p("leg" + i);
            if (leg != null) leg.xRot += (i == 0 || i == 3) ? swing : -swing;
        }
    }

    public void rotX(String name, float v) { ModelPart m = p(name); if (m != null) m.xRot += v; }
    public void rotY(String name, float v) { ModelPart m = p(name); if (m != null) m.yRot += v; }
    public void rotZ(String name, float v) { ModelPart m = p(name); if (m != null) m.zRot += v; }
    public void moveY(String name, float v) { ModelPart m = p(name); if (m != null) m.y += v; }
    public void move(String name, float x, float y, float z) { ModelPart m = p(name); if (m != null) { m.x = x; m.y = y; m.z = z; } }
    public void show(String name, boolean visible) { ModelPart m = p(name); if (m != null) m.visible = visible; }
    public void stretch(String name, float y) { ModelPart m = p(name); if (m != null) m.yScale = y; }
    public void scale(String name, float x, float y, float z) { ModelPart m = p(name); if (m != null) { m.xScale = x; m.yScale = y; m.zScale = z; } }

    /** Sine bump over an animation: 0 at start and end, 1 in the middle. -1 progress (idle) gives 0. */
    public static float bump(float p) { return p < 0 ? 0 : Mth.sin(p * Mth.PI); }
}
