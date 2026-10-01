package ryuna.qdeath.client.rendering;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** Enderman geometry without the vanilla glowing-eye and carried-block render layers. */
public class PlainEndermanRenderer<T extends Mob> extends MobRenderer<T, EndermanRenderState, EndermanModel<EndermanRenderState>> {
    private final Identifier texture;
    public PlainEndermanRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.5f);
        this.texture = texture;
    }
    @Override public EndermanRenderState createRenderState() { return new EndermanRenderState(); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return texture; }
}
