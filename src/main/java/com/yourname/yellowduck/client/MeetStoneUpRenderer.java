package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneUpRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    // 直接加载上半部分模型（已嵌贴图）
    private static final ResourceLocation MODEL = new ResourceLocation("yellowduck", "meet_stone_2");

    public MeetStoneUpRenderer(BlockEntityRendererProvider.Context context) {
        super(context, MODEL, GltfRenderOptions.builder()
                .scale(0.0625F) 
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // ⚠️ 核心拼接：把上半部分往上提一格
        // 如果进游戏发现上半截卡在底座里，把 1.0D 改成 1.2D 或 1.5D
        // 如果发现上半截悬空了，把 1.0D 改成 0.8D
        poseStack.translate(0.0D, 1.0D, 0.0D);
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
