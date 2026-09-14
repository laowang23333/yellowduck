package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MeetStoneDownRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    // 直接加载下半部分模型（已嵌贴图）
    private static final ResourceLocation MODEL = new ResourceLocation("yellowduck", "meet_stone_1");

    public MeetStoneDownRenderer(BlockEntityRendererProvider.Context context) {
        super(context, MODEL, GltfRenderOptions.builder()
                .scale(0.0625F) 
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }
}
