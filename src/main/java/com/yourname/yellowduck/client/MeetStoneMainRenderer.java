package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MeetStoneMainRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    private final List<GltfBlockEntityRenderer<MeetStoneBlockEntity>> renderers = new ArrayList<>();

    public MeetStoneMainRenderer(BlockEntityRendererProvider.Context context) {
        // 父类随便给一个模型初始化，我们只用它里面的子渲染器
        super(context, new ResourceLocation("yellowduck", "meet_stone_1"), GltfRenderOptions.builder()
                .scale(0.0625F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
        
        // 把上下两个渲染器装进一个列表里
        renderers.add(new MeetStoneDownRenderer(context));
        renderers.add(new MeetStoneUpRenderer(context));
    }

    @Override
    public void render(MeetStoneBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 遍历列表，一次性把上下两截都渲染出来
        for (GltfBlockEntityRenderer<MeetStoneBlockEntity> renderer : renderers) {
            renderer.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }
}
