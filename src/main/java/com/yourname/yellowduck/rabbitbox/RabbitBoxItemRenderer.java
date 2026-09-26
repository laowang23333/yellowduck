package com.yourname.yellowduck.rabbitbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 兔兔宝箱 3D 物品渲染器。模型只有约 500 顶点，GUI 使用也很轻。 */
@OnlyIn(Dist.CLIENT)
public final class RabbitBoxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static RabbitBoxItemRenderer instance;

    private YellowGltfModel model;
    private RabbitBoxRenderer.Bounds bounds;

    private RabbitBoxItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    public static RabbitBoxItemRenderer getInstance() {
        if (instance == null) instance = new RabbitBoxItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (!ensureModel()) return;

        int renderLight = switch (displayContext) {
            case GUI, GROUND, FIXED -> LightTexture.FULL_BRIGHT;
            default -> packedLight;
        };

        pose.pushPose();
        try {
            pose.translate(0.5D, 0.5D, 0.5D);

            if (displayContext == ItemDisplayContext.GUI) {
                pose.mulPose(Axis.XP.rotationDegrees(14.0F));
                pose.mulPose(Axis.YP.rotationDegrees(205.0F));
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            }

            float visualScale = switch (displayContext) {
                case GUI -> 0.93F;
                case GROUND -> 0.58F;
                case FIXED -> 0.82F;
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.72F;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.67F;
                case HEAD -> 0.72F;
                default -> 0.78F;
            };

            float fit = visualScale / Math.max(1.0E-6F, bounds.maxExtent());
            pose.scale(fit, fit, fit);
            pose.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

            YellowGltfRenderUtil.renderModel(
                    model,
                    pose,
                    buffers,
                    renderLight,
                    0.0F,
                    null,
                    false
            );
        } finally {
            pose.popPose();
        }
    }

    private boolean ensureModel() {
        if (model != null && bounds != null) return true;
        model = YellowGltfModelCache.getOrLoad(RabbitBoxRenderer.MODEL);
        if (model == null) return false;
        bounds = RabbitBoxRenderer.calculateBounds(model);
        return bounds != null && bounds.maxExtent() > 1.0E-6F;
    }
}
