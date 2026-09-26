package com.yourname.yellowduck.statue;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 嫦娥雕像客户端注册。 */
@Mod.EventBusSubscriber(
        modid = YellowDuckMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ChangeStatueClient {
    private ChangeStatueClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ChangeStatueContent.CHANGE_STATUE_BE.get(),
                ChangeStatueRenderer::new
        );
    }
}
