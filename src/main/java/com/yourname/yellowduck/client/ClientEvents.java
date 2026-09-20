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
        // 小樱 Boss 渲染器
        SakurawitchRenderer.register(event, ModEntities.SAKURA_WITCH.get());

        // 小樱布偶熊
        ToyBearRenderer.register(event, ModEntities.TOY_BEAR.get());
        
        // 小黄鸭 Boss 渲染器
        event.registerEntityRenderer(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossRenderer::new);

        // 鬼狼星坐骑渲染器
        MountRenderer.register(event, ModEntities.MOUNT.get());

        // 羊驼坐骑渲染器
        AlpacaMountRenderer.register(event, ModEntities.ALPACA_MOUNT.get());
        RabbitMountRenderer.register(event, ModEntities.RABBIT_MOUNT.get());
        event.registerEntityRenderer(ModEntities.BAMBOO_HORSE_MOUNT.get(), BambooHorseRenderer::new);

        // 大箱子方块实体渲染器
        event.registerBlockEntityRenderer(
                ModBlockEntities.BIG_CHEST.get(),
                BigChestRenderer::new);

        // 副本柱子：直接渲染已经完整拼接好的单个 GLB 模型
        event.registerBlockEntityRenderer(
                ModBlockEntities.MEET_STONE.get(),
                MeetStoneRenderer::new);

        // Professor Silk 方块实体渲染器
        event.registerBlockEntityRenderer(
                ModBlockEntities.PROFESSOR_SILK.get(),
                ProfessorSilkRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 大箱子 GUI
            MenuScreens.register(ModMenuTypes.BIG_CHEST.get(), BigChestScreen::new);
        });
    }
}
