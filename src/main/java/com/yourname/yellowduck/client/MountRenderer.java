package com.yourname.yellowduck.client;

import dev.phe.polymesh.client.GltfEntityRendererFactory;
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class MountRenderer {

    // 模型 ID：对应 assets/yellowduck/models/gltf/mount.glb
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "mount");

    // 缩放：看不见就改这个数字
    private static final float MODEL_SCALE = 0.05F;

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        event.registerEntityRenderer(entityType,
                GltfEntityRendererFactory.create(MODEL_ID, MODEL_SCALE));
    }
}
