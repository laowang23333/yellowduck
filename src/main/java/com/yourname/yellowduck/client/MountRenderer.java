package com.yourname.yellowduck.client;

import dev.phe.polymesh.client.GltfEntityRendererFactory;
import dev.phe.polymesh.client.GltfRenderOptions; // 新增导入
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class MountRenderer {

    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "mount");

    private static final float MODEL_SCALE = 0.05F;

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        event.registerEntityRenderer(entityType,
                GltfEntityRendererFactory.create(
                        MODEL_ID,
                        GltfRenderOptions.builder()
                                .scale(MODEL_SCALE)
                                .build()
                ));
    }
}
