package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
// 👇 关键：补上这几个缺失的 Import
// ⚠️ 注意：下面这两个 Import 路径，请务必参考你 BigChestRenderer 里的写法！
// 通常是类似 com.tom.polymesh.client.renderer.GltfBlockEntityRenderer 或类似路径
import com.tom.polymesh.client.renderer.GltfBlockEntityRenderer; 
import com.tom.polymesh.client.renderer.GltfRenderOptions;

public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    private static final ResourceLocation MODEL_DOWN = new ResourceLocation("yellowduck", "models/gltf/meet_stone_1.glb");
    private static final ResourceLocation MODEL_UP = new ResourceLocation("yellowduck", "models/gltf/meet_stone_2.glb");

    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererDown;
    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererUp;

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        GltfRenderOptions options = GltfRenderOptions.builder()
                .scale(0.0625f) 
                .build();
        rendererDown = new GltfBlockEntityRenderer<>(context, MODEL_DOWN, options);
        rendererUp = new GltfBlockEntityRenderer<>(context, MODEL_UP, options);
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        rendererDown.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.0D, 0.0D); 
        rendererUp.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
