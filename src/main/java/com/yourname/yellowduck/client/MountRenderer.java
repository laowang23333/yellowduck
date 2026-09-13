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

    // ============================================================
    // 🎚️ 调试用缩放值：如果模型看不见，就改这个数字！
    //   1.0    = 原尺寸
    //   0.1    = 缩小 10 倍
    //   0.01   = 缩小 100 倍
    //   10.0   = 放大 10 倍
    //   100.0  = 放大 100 倍
    // ============================================================
    private static final float MODEL_SCALE = 0.05F;

    // 模型名称（对应 assets/yellowduck/models/gltf/ 文件夹下的文件名，不带 .glb 后缀）
    private static final String MODEL_NAME = "mount";

    /**
     * 在 ClientEvents 中调用此方法注册实体渲染器
     */
    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        event.registerEntityRenderer(entityType,
                GltfEntityRendererFactory.create(
                        new ResourceLocation("yellowduck", MODEL_NAME),
                        MODEL_SCALE
                )
        );
    }
}
