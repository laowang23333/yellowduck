package com.yourname.yellowduck.client;

import com.yourname.yellowduck.entity.MountEntity;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRendererFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class MountRenderer {

    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "mount");

    // 模型大小：0.05F → 0.10F
    private static final float MODEL_SCALE = 0.10F;

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        GltfRenderOptions options = GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .build();
        event.registerEntityRenderer(entityType,
                GltfEntityRendererFactory.create(MODEL_ID, options));
    }
}
