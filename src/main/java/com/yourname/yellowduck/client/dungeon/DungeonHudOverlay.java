package com.yourname.yellowduck.client.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 副本进行中的右下角小型动态 HUD。 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class DungeonHudOverlay {
    private DungeonHudOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!DungeonHudClientState.active()) return;
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = 214, h = 92, x = mc.getWindow().getGuiScaledWidth() - w - 8;
        int y = mc.getWindow().getGuiScaledHeight() - h - 8;
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xE5222529);
        g.fill(x + 1, y + 1, x + w - 1, y + 20, 0xFF34383D);
        g.drawCenteredString(mc.font, DungeonHudClientState.dungeonName(), x + w / 2, y + 6, 0xFFFFD34D);

        float max = DungeonHudClientState.bossMaxHealth();
        float hp = DungeonHudClientState.bossHealth();
        int barX = x + 10, barY = y + 27, barW = w - 20;
        g.fill(barX, barY, barX + barW, barY + 9, 0xFF15171A);
        int fill = max <= 0 ? 0 : Math.min(barW, Math.round(barW * Math.max(0, Math.min(1, hp / max))));
        g.fill(barX, barY, barX + fill, barY + 9, 0xFFE62F39);
        if (max > 0) g.drawCenteredString(mc.font, Math.round(hp) + " / " + Math.round(max), x + w / 2, y + 28, 0xFFFFFFFF);

        int seconds = DungeonHudClientState.remainingSeconds();
        String time = String.format("%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
        g.drawString(mc.font, Component.literal("剩余时间：" + time), x + 10, y + 44, 0xFFFFFFFF, false);
        g.drawString(mc.font, Component.literal("团队复活：" + DungeonHudClientState.revives() + " / " + DungeonHudClientState.maxRevives()), x + 10, y + 59, 0xFFFFFFFF, false);
        g.drawString(mc.font, Component.literal("击败 Boss！"), x + 10, y + 74, 0xFFFFD34D, false);
    }
}
