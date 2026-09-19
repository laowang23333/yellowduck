package com.yourname.yellowduck.mixin.client;

import com.yourname.yellowduck.silk.SilkNetcraftHud;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 直接监听 Forge HUD 渲染事件来绘制斯尔克血条。
 *
 * 不再 Mixin NetCraft 的 BossHealthBarRenderer，这样即使编译环境里
 * 没有 NetCraft 本体，也不会因为找不到目标类而导致编译失败。
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class BossHealthBarMixin {
    private BossHealthBarMixin() {
    }

    @SubscribeEvent
    public static void yellowduck$renderSilk(RenderGuiEvent.Post event) {
        SilkNetcraftHud.render(event);
    }
}
