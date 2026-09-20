package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 副本柱子渲染器。
 *
 * 模型已经在 GLB 内完整拼接并嵌入贴图，不再由代码分别加载上下两段进行拼接。
 */
public class MeetStoneRenderer extends GltfBlockEntityRenderer<MeetStoneBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation("yellowduck", "meet_stone");

    public MeetStoneRenderer(BlockEntityRendererProvider.Context context) {
        super(context, MODEL, GltfRenderOptions.builder()
                .scale(0.0625F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }
}
