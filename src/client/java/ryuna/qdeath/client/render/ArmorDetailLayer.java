package ryuna.qdeath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import ryuna.qdeath.Quantumdeath;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the 3D details of the mod's armor sets (see {@link ArmorDetailModels}) over the vanilla armor layers, posed like
 * the wearer's own model, with an emissive pass for flames, embers and glowing eyes.
 */
public class ArmorDetailLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final String SLOTS = "hclf";
    /** Equipment asset name -> one model per slot (head, chest, legs, feet), each showing only its own parts. */
    private final Map<String, DetailModel[]> models = new HashMap<>();

    public ArmorDetailLayer(RenderLayerParent<S, M> parent, EntityModelSet modelSet) {
        super(parent);
        ArmorDetailModels.LAYERS.forEach((name, layer) -> {
            DetailModel[] perSlot = new DetailModel[4];
            for (int i = 0; i < 4; i++) perSlot[i] = new DetailModel(modelSet.bakeLayer(layer), SLOTS.charAt(i));
            models.put(name, perSlot);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer.getModel() instanceof HumanoidModel<?>)
                helper.register(new ArmorDetailLayer(renderer, context.getModelSet()));
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
        if (state.isBaby || state.isInvisible) return;
        piece(poseStack, collector, lightCoords, state, state.headEquipment, EquipmentSlot.HEAD, 0);
        piece(poseStack, collector, lightCoords, state, state.chestEquipment, EquipmentSlot.CHEST, 1);
        piece(poseStack, collector, lightCoords, state, state.legsEquipment, EquipmentSlot.LEGS, 2);
        piece(poseStack, collector, lightCoords, state, state.feetEquipment, EquipmentSlot.FEET, 3);
    }

    private void piece(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, ItemStack stack, EquipmentSlot equipmentSlot, int slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty() || equippable.slot() != equipmentSlot) return;
        Identifier asset = equippable.assetId().get().identifier();
        if (!asset.getNamespace().equals(Quantumdeath.MOD_ID)) return;
        DetailModel[] perSlot = models.get(asset.getPath());
        if (perSlot == null || !perSlot[slot].hasParts) return;
        String name = asset.getPath();
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, perSlot[slot], state, false, collector, poseStack,
                RenderTypes.armorCutoutNoCull(Quantumdeath.id("textures/entity/equipment/details/" + name + ".png")),
                lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, perSlot[slot], state, false, collector.order(1), poseStack,
                RenderTypes.eyes(Quantumdeath.id("textures/entity/equipment/details/" + name + "_glow.png")),
                lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }

    /** The detail geometry of one set, with only the parts of one slot visible. */
    private static final class DetailModel extends Model<HumanoidRenderState> {
        final boolean hasParts;

        DetailModel(ModelPart root, char slot) {
            super(root, RenderTypes::armorCutoutNoCull);
            boolean any = false;
            // ponytail: detail names are "<slot><index>" with indices below 100 (see tools/gen_items.py)
            for (String part : new String[]{"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
                ModelPart p = root.getChild(part);
                for (int i = 0; i < 100; i++)
                    for (char c : SLOTS.toCharArray())
                        if (p.hasChild(c + "" + i)) {
                            p.getChild(c + "" + i).visible = c == slot;
                            any |= c == slot;
                        }
            }
            hasParts = any;
        }
    }
}
