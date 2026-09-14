package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    // ⚠️ 注意：和 BigChestRenderer 一样，路径只写模型名，不含 models/gltf/ 和 .glb
    private static final ResourceLocation MODEL_DOWN = new ResourceLocation("yellowduck", "meet_stone_1");
    private static final ResourceLocation MODEL_UP = new ResourceLocation("yellowduck", "meet_stone_2");

    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererDown;
    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererUp;

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        GltfRenderOptions options = GltfRenderOptions.builder()
                .scale(0.0625F) 
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
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
