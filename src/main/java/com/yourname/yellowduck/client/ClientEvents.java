package com.yourname.yellowduck.client;

import com.yourname.yellowduck.registry.ModBlockEntities;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 👇 补上了这一段！小黄鸭 Boss 的渲染器
        event.registerEntityRenderer(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossRenderer::new);
        
        // 坐骑
        MountRenderer.register(event, ModEntities.MOUNT.get());
        
        // 大箱子方块实体
        event.registerBlockEntityRenderer(
                ModBlockEntities.BIG_CHEST.get(),
                BigChestRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 大箱子 GUI
            MenuScreens.register(ModMenuTypes.BIG_CHEST.get(), BigChestScreen::new);
        });
    }
}
