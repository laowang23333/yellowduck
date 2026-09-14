package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// ⚠️ 请务必把下面这两行，换成你 BigChestRenderer.java 里面成功使用的 Import！⚠️
import com.yourname.yellowduck.client.GltfBlockEntityRenderer;
import com.yourname.yellowduck.client.GltfRenderOptions;

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
