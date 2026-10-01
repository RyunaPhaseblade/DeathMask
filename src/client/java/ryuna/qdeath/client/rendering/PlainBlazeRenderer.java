package ryuna.qdeath.client.rendering;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** Blaze geometry under ordinary world lighting, without vanilla Blaze effects. */
public class PlainBlazeRenderer<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, BlazeModel> {
    private final Identifier texture;
    public PlainBlazeRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context, new BlazeModel(context.bakeLayer(ModelLayers.BLAZE)), 0.5f);
        this.texture = texture;
    }
    @Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
    @Override public Identifier getTextureLocation(LivingEntityRenderState state) { return texture; }
}
