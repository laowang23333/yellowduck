package com.yourname.yellowduck.client.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 副本进行中的简洁 HUD。
 *
 * 不再绘制右上角副本面板、Boss 实时血量、团队复活次数和“击败 Boss”提示。
 * 副本进行中只在屏幕最上方正中间显示剩余时间。
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class DungeonHudOverlay {
    private static final int TOP_Y = 5;

    private DungeonHudOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!DungeonHudClientState.active()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        int seconds = Math.max(0, DungeonHudClientState.remainingSeconds());
        String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
        Component text = Component.literal("剩余时间 " + time);

        g.drawCenteredString(
                mc.font,
                text,
                screenWidth / 2,
                TOP_Y,
                0xFFFFFFFF
        );
    }
}
