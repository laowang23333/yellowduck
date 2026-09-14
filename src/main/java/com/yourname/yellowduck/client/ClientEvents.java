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
        // 小黄鸭 Boss 渲染器
        event.registerEntityRenderer(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossRenderer::new);
        
        // 狮子狗坐骑渲染器
        MountRenderer.register(event, ModEntities.MOUNT.get());
        
        // 大箱子方块实体渲染器
        event.registerBlockEntityRenderer(
                ModBlockEntities.BIG_CHEST.get(),
                BigChestRenderer::new);

        // 👇 副本柱子：只注册“总调度器”，解决上下重叠/覆盖问题！
        event.registerBlockEntityRenderer(
                ModBlockEntities.MEET_STONE.get(),
                MeetStoneMainRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 大箱子 GUI
            MenuScreens.register(ModMenuTypes.BIG_CHEST.get(), BigChestScreen::new);
        });
    }
}
