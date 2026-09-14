package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    // 分别指向带贴图的模型1（下半部分）和模型2（上半部分）
    private static final ResourceLocation MODEL_DOWN = new ResourceLocation("yellowduck", "models/gltf/meet_stone_1.glb");
    private static final ResourceLocation MODEL_UP = new ResourceLocation("yellowduck", "models/gltf/meet_stone_2.glb");

    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererDown;
    private final GltfBlockEntityRenderer<MeetStoneBlockEntity> rendererUp;

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        // ⚠️ 极其关键的缩放！奶块模型是 1:16 比例，不缩放模型比山还大！
        GltfRenderOptions options = GltfRenderOptions.builder()
                .scale(0.0625f) // 如果进游戏巨大无比，把这个数字改成 0.03f
                .build();
        rendererDown = new GltfBlockEntityRenderer<>(context, MODEL_DOWN, options);
        rendererUp = new GltfBlockEntityRenderer<>(context, MODEL_UP, options);
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. 渲染下半部分（放在原地）
        rendererDown.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        
        // 2. 渲染上半部分（往上提一格）
        poseStack.pushPose();
        // ⚠️ 关键微调点：如果进游戏发现上半截陷进底座了，就把 1.0D 改成 1.2D（往上提）；如果悬空了，就改成 0.8D（往下压）
        poseStack.translate(0.0D, 1.0D, 0.0D); 
        rendererUp.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
