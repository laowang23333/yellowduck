package com.yourname.yellowduck.piratechest;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DragonPalacePirateChestClient {
    private DragonPalacePirateChestClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                DragonPalacePirateChestContent.DRAGON_PALACE_PIRATE_CHEST_BE.get(),
                DragonPalacePirateChestRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                DragonPalacePirateChestContent.DRAGON_PALACE_PIRATE_CHEST_MENU.get(),
                DragonPalacePirateChestScreen::new));
    }
}
