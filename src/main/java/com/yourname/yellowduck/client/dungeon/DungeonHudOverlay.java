package com.yourname.yellowduck.client.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 副本进行中的动态 HUD。
 *
 * 使用固定逻辑尺寸绘制，再整体缩放到约 82%，
 * 这样标题、血条和文字会一起缩小，不会出现“框变小但字体还是很大”的问题。
 * HUD 固定在屏幕右上角，避免遮挡右下角快捷栏/状态区域。
 */
@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT)
public final class DungeonHudOverlay {
    private static final int BASE_WIDTH = 214;
    private static final int BASE_HEIGHT = 92;
    private static final int MARGIN = 8;

    /** 原 HUD 的 82%，实际约 175 x 75 像素。 */
    private static final float HUD_SCALE = 0.82F;

    private DungeonHudOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!DungeonHudClientState.active()) return;

        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // 先按最终显示尺寸计算右上角锚点，再在局部坐标系里画原始 HUD。
        int displayedWidth = Math.round(BASE_WIDTH * HUD_SCALE);
        int x = screenWidth - displayedWidth - MARGIN;
        int y = MARGIN;

        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(HUD_SCALE, HUD_SCALE, 1.0F);

        try {
            int w = BASE_WIDTH;
            int h = BASE_HEIGHT;

            // 阴影 + 主体
            g.fill(2, 2, w + 2, h + 2, 0x66000000);
            g.fill(0, 0, w, h, 0xE5222529);

            // 标题栏
            g.fill(1, 1, w - 1, 20, 0xFF34383D);
            g.drawCenteredString(
                    mc.font,
                    DungeonHudClientState.dungeonName(),
                    w / 2,
                    6,
                    0xFFFFD34D
            );

            // Boss 血条
            float max = DungeonHudClientState.bossMaxHealth();
            float hp = DungeonHudClientState.bossHealth();

            int barX = 10;
            int barY = 27;
            int barW = w - 20;

            g.fill(barX, barY, barX + barW, barY + 9, 0xFF15171A);

            int fill = max <= 0
                    ? 0
                    : Math.min(
                            barW,
                            Math.round(barW * Math.max(0.0F, Math.min(1.0F, hp / max)))
                    );

            g.fill(barX, barY, barX + fill, barY + 9, 0xFFE62F39);

            if (max > 0) {
                g.drawCenteredString(
                        mc.font,
                        Math.round(hp) + " / " + Math.round(max),
                        w / 2,
                        28,
                        0xFFFFFFFF
                );
            }

            // 副本实时信息
            int seconds = DungeonHudClientState.remainingSeconds();
            String time = String.format(
                    "%02d:%02d",
                    Math.max(0, seconds) / 60,
                    Math.max(0, seconds) % 60
            );

            g.drawString(
                    mc.font,
                    Component.literal("剩余时间：" + time),
                    10,
                    44,
                    0xFFFFFFFF,
                    false
            );

            g.drawString(
                    mc.font,
                    Component.literal(
                            "团队复活："
                                    + DungeonHudClientState.revives()
                                    + " / "
                                    + DungeonHudClientState.maxRevives()
                    ),
                    10,
                    59,
                    0xFFFFFFFF,
                    false
            );

            g.drawString(
                    mc.font,
                    Component.literal("击败 Boss！"),
                    10,
                    74,
                    0xFFFFD34D,
                    false
            );
        } finally {
            g.pose().popPose();
        }
    }
}
