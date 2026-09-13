package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.BigChestBlockEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class BigChestRenderer extends GltfBlockEntityRenderer<BigChestBlockEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "big_chest");

    public BigChestRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(1.0F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }
}
