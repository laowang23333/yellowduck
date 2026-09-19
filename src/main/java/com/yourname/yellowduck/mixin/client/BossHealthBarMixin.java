package com.yourname.yellowduck.client.event;

import com.yourname.yellowduck.silk.SilkNetcraftHud;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 使用 Forge HUD 渲染事件绘制斯尔克血条。
 *
 * 注意：这个类虽然文件仍放在旧的 mixin/client 目录，实际 Java package
 * 已移动到 com.yourname.yellowduck.client.event，避免被 Mixin 包规则拦截。
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
