package com.yourname.yellowduck.client.event;

import com.yourname.yellowduck.client.NetcraftBossHud;
import com.yourname.yellowduck.client.SakuraNetcraftHud;
import com.yourname.yellowduck.client.SilkBossStatusHud;
import com.yourname.yellowduck.client.ToyBearNetcraftHud;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 使用 Forge HUD 渲染事件绘制 YellowDuck 的 NetCraft 风格 Boss 血条。
 *
 * 注意：这个类虽然文件仍放在旧的 mixin/client 目录，实际 Java package
 * 已移动到 com.yourname.yellowduck.client.event，避免被 Mixin 包规则拦截。
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class BossHealthBarMixin {
    private BossHealthBarMixin() {
    }

    @SubscribeEvent
    public static void yellowduck$renderBossHud(RenderGuiEvent.Post event) {
        SakuraNetcraftHud.render(event);
        ToyBearNetcraftHud.render(event);
        NetcraftBossHud.render(event);
        // 斯尔克原版式技能/Buff 图标行必须最后绘制，保证位于 Boss 血条之上。
        SilkBossStatusHud.render(event);
    }
}
