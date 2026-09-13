package com.yourname.yellowduck.client;

import com.yourname.yellowduck.registry.ModBlockEntities;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterMenuScreensEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MountRenderer.register(event, ModEntities.MOUNT.get());
        event.registerBlockEntityRenderer(
                ModBlockEntities.BIG_CHEST.get(),
                BigChestRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.BIG_CHEST.get(), BigChestScreen::new);
    }
}
