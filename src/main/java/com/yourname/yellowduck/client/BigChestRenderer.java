package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.block.BigChestBlock;
import com.yourname.yellowduck.block.BigChestBlockEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 海盗箱 GLTF 渲染器。
 *
 * Polymesh 的 GltfBlockEntityRenderer 不会自动读取 BlockState 的 FACING，
 * 所以这里在调用父类渲染前，以方块中心为轴旋转模型。
 */
public class BigChestRenderer extends GltfBlockEntityRenderer<BigChestBlockEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "big_chest");

    public BigChestRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(1.0F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }

    @Override
    public void render(BigChestBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(BigChestBlock.FACING);

        // big_chest.glb 的默认正面为 SOUTH。
        // FACING 表示锁扣/正面朝向，因此把模型旋转到保存的方向。
        float rotation = switch (facing) {
            case SOUTH -> 0.0F;
            case EAST -> 90.0F;
            case NORTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };

        poseStack.pushPose();
        if (rotation != 0.0F) {
            // GLTF 模型尺寸为约 1x1x1，以方块中心旋转，避免旋转后模型偏离方块中心。
            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));
            poseStack.translate(-0.5D, 0.0D, -0.5D);
        }

        super.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
