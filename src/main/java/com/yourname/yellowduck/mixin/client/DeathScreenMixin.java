package com.yourname.yellowduck.mixin.client;

import com.yourname.yellowduck.client.SilkReviveClientState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 在斯尔克“心智腐蚀”复活锁期间禁用死亡界面的“原地复活”按钮。
 *
 * 使用 Forge ScreenEvent，而不是直接 Mixin Minecraft DeathScreen#render，
 * 避免 1.20.1 的 Mixin obfuscation mapping 编译错误。
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class DeathScreenMixin {
    private DeathScreenMixin() {
    }

    @SubscribeEvent
    public static void yellowduck$onScreenInit(ScreenEvent.Init.Post event) {
        disableReviveButton(event.getScreen());
    }

    @SubscribeEvent
    public static void yellowduck$onScreenRender(ScreenEvent.Render.Pre event) {
        disableReviveButton(event.getScreen());
    }

    private static void disableReviveButton(Screen screen) {
        if (!(screen instanceof DeathScreen deathScreen) || !SilkReviveClientState.locked()) {
            return;
        }

        for (GuiEventListener listener : deathScreen.children()) {
            if (listener instanceof Button button
                    && button.getMessage().getString().contains("原地复活")) {
                button.active = false;
            }
        }
    }
}
