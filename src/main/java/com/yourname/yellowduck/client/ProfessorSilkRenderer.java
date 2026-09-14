package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.ProfessorSilkBlockEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ProfessorSilkRenderer extends GltfBlockEntityRenderer<ProfessorSilkBlockEntity> {

    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "professor_silk");

    private static final float MODEL_SCALE = 0.15F;

    public ProfessorSilkRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(false)     // 模型动画时间轴坏了，不循环
                .build());
    }
}
