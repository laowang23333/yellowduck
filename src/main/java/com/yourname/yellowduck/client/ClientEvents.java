package com.yourname.yellowduck.client;

import com.yourname.yellowduck.client.dungeon.PartyScreen;
import com.yourname.yellowduck.client.dungeon.RewardScreen;
import com.yourname.yellowduck.registry.ModBlockEntities;
import com.yourname.yellowduck.registry.ModEntities;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "yellowduck", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        SakurawitchRenderer.register(event, ModEntities.SAKURA_WITCH.get());
        ToyBearRenderer.register(event, ModEntities.TOY_BEAR.get());
        event.registerEntityRenderer(ModEntities.ROOT_VINE.get(), RootVineRenderer::new);
        event.registerEntityRenderer(ModEntities.TWO_PHASE_BOSS.get(), TwoPhaseBossRenderer::new);
        MountRenderer.register(event, ModEntities.MOUNT.get());
        AlpacaMountRenderer.register(event, ModEntities.ALPACA_MOUNT.get());
        RabbitMountRenderer.register(event, ModEntities.RABBIT_MOUNT.get());
        event.registerEntityRenderer(ModEntities.BAMBOO_HORSE_MOUNT.get(), BambooHorseRenderer::new);

        event.registerBlockEntityRenderer(ModBlockEntities.BIG_CHEST.get(), BigChestRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MEET_STONE.get(), MeetStoneRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PROFESSOR_SILK.get(), ProfessorSilkRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOSS_HEAD.get(), BossHeadRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.BIG_CHEST.get(), BigChestScreen::new);
            MenuScreens.register(ModMenuTypes.PARTY.get(), PartyScreen::new);
            MenuScreens.register(ModMenuTypes.DUNGEON_REWARD.get(), RewardScreen::new);

            ItemBlockRenderTypes.setRenderLayer(
                    com.yourname.yellowduck.praytree.PrayTreeContent.PRAY_SAPLING.get(),
                    RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    com.yourname.yellowduck.praytree.PrayTreeContent.PRAY_LEAVES.get(),
                    RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(
                    com.yourname.yellowduck.praytree.PrayTreeContent.PRAY_RIBBON.get(),
                    RenderType.cutout());
        });
    }
}
