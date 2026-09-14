package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    private static final ResourceLocation MODEL_DOWN = new ResourceLocation("yellowduck", "meet_stone_1");
    private static final ResourceLocation MODEL_UP = new ResourceLocation("yellowduck", "meet_stone_2");

    private static final GltfRenderOptions OPTIONS = GltfRenderOptions.builder()
            .scale(0.0625F)
            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
            .build();

    private final DownRenderer downRenderer;

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        // 父类渲染上半部分（MODEL_UP）
        super(context, MODEL_UP, OPTIONS);
        // 内部类实例负责渲染下半部分
        downRenderer = new DownRenderer(context);
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. 先渲染下半部分（原位不动）
        downRenderer.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        
        // 2. 再渲染上半部分（父类），向上提一格
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.0D, 0.0D);
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    // 静态内部类：继承抽象的 GltfBlockEntityRenderer，负责渲染下层模型
    private static class DownRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
        public DownRenderer(BlockEntityRendererProvider.Context context) {
            super(context, MODEL_DOWN, OPTIONS);
        }
    }
}
