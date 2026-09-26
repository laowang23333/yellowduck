package com.yourname.yellowduck.rabbitbox;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 兔兔宝箱客户端 Native GLTF 渲染注册。 */
@Mod.EventBusSubscriber(
        modid = YellowDuckMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class RabbitBoxClient {
    private RabbitBoxClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                RabbitBoxContent.JADE_RABBIT_BOX_BE.get(),
                RabbitBoxRenderer::new
        );
    }
}
