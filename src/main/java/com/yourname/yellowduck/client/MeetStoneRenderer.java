package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    // 👇 调换了！父类渲染原本的下半部分(1)，内部类渲染原本的上半部分(2)
    private static final ResourceLocation MODEL_PARENT = new ResourceLocation("yellowduck", "meet_stone_1");
    private static final ResourceLocation MODEL_INNER = new ResourceLocation("yellowduck", "meet_stone_2");

    private static final GltfRenderOptions OPTIONS = GltfRenderOptions.builder()
            .scale(0.0625F)
            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
            .build();

    private final InnerRenderer innerRenderer;

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        // 父类渲染 MODEL_PARENT (也就是meet_stone_1)
        super(context, MODEL_PARENT, OPTIONS);
        // 内部类实例负责渲染 MODEL_INNER (也就是meet_stone_2)
        innerRenderer = new InnerRenderer(context);
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. 先渲染父类（meet_stone_1）
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        
        // 2. 再渲染内部类（meet_stone_2），向上提一格
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.0D, 0.0D);
        innerRenderer.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static class InnerRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
        public InnerRenderer(BlockEntityRendererProvider.Context context) {
            super(context, MODEL_INNER, OPTIONS);
        }
    }
}
