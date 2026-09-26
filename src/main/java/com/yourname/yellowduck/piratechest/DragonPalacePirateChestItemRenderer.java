package com.yourname.yellowduck.piratechest;

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

/**
 * 龙宫海盗箱 3D 物品渲染器。
 * GUI、地面、展示框和手持全部直接渲染 dragon_palace_pirate_chest.glb。
 */
@OnlyIn(Dist.CLIENT)
public final class DragonPalacePirateChestItemRenderer
        extends BlockEntityWithoutLevelRenderer {

    private static DragonPalacePirateChestItemRenderer instance;

    private YellowGltfModel model;
    private DragonPalacePirateChestRenderer.Bounds bounds;

    private DragonPalacePirateChestItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    public static DragonPalacePirateChestItemRenderer getInstance() {
        if (instance == null) {
            instance = new DragonPalacePirateChestItemRenderer();
        }
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

            // GUI 中稍微倾斜，让箱体上下层能完整看见。
            if (displayContext == ItemDisplayContext.GUI) {
                pose.mulPose(Axis.XP.rotationDegrees(22.5F));
                pose.mulPose(Axis.YP.rotationDegrees(205.0F));
            } else if (displayContext == ItemDisplayContext.GROUND) {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            } else if (displayContext == ItemDisplayContext.FIXED) {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            }

            float contextScale = switch (displayContext) {
                case GUI -> 0.92F;
                case GROUND -> 0.56F;
                case FIXED -> 0.80F;
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.68F;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.62F;
                case HEAD -> 0.70F;
                default -> 0.75F;
            };

            float fit = contextScale / bounds.maxExtent();
            pose.scale(fit, fit, fit);
            pose.translate(
                    -bounds.centerX(),
                    -bounds.centerY(),
                    -bounds.centerZ()
            );

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

        model = YellowGltfModelCache.getOrLoad(
                DragonPalacePirateChestRenderer.MODEL
        );
        if (model == null) return false;

        bounds = DragonPalacePirateChestRenderer.calculateBounds(model);
        return bounds != null && bounds.maxExtent() > 1.0E-6F;
    }
}
