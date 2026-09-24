package com.yourname.yellowduck.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 头颅方块物品形态，使用 YellowDuck Native GLTF，不走 Polymesh。 */
public class BossHeadItem extends BlockItem {
    private final ResourceLocation model;

    public BossHeadItem(Block block, Properties properties, ResourceLocation model) {
        super(block, properties);
        this.model = model;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new NativeBossHeadItemRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels(), model);
                }
                return renderer;
            }
        });
    }

    private static final class NativeBossHeadItemRenderer extends BlockEntityWithoutLevelRenderer {
        private final ResourceLocation modelLocation;

        private NativeBossHeadItemRenderer(BlockEntityRenderDispatcher dispatcher,
                                           EntityModelSet models, ResourceLocation model) {
            super(dispatcher, models);
            this.modelLocation = new ResourceLocation(model.getNamespace(),
                    "models/gltf/" + model.getPath() + ".glb");
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                                 PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, int packedOverlay) {
            YellowGltfModel model = YellowGltfModelCache.getOrLoad(modelLocation);
            if (model == null) return;

            poseStack.pushPose();
            try {
                poseStack.translate(0.5D, 0.08D, 0.5D);
                // GUI/item transforms view the GLB from the opposite side of the
                // world-facing renderer, so turn it around to show the face.
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(0.1F, 0.1F, 0.1F);
                YellowGltfRenderUtil.renderModel(model, poseStack, buffer, packedLight,
                        0.0F, null, false);
            } finally {
                poseStack.popPose();
            }
        }
    }
}
