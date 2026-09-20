package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 战斗中右上角副本 HUD。使用 UI 包中的 dungeon_hud.png。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT)
public final class DungeonHudOverlay {
    private static final ResourceLocation DUNGEON_DIM = new ResourceLocation("yellowduck", "dungeon");
    private static final ResourceLocation HUD_TEXTURE =
            new ResourceLocation("yellowduck", "textures/gui/party/dungeon_hud.png");
    private static final int TEX_W = 388;
    private static final int TEX_H = 418;

    private DungeonHudOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!mc.level.dimension().location().equals(DUNGEON_DIM)) {
            if (DungeonHudClientState.active()) DungeonHudClientState.clear();
            return;
        }
        if (!DungeonHudClientState.active()) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // UI 原图 388x418；HUD 只占右上角，按屏幕高度自动缩到约 205~238px 高。
        int drawH = Math.min(238, Math.max(190, sh / 2));
        int drawW = Math.round(drawH * (TEX_W / (float) TEX_H));
        int x = Math.max(6, sw - drawW - 8);
        int y = 34;
        g.blit(HUD_TEXTURE, x, y, drawW, drawH, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        float sx = drawW / (float) TEX_W;
        float sy = drawH / (float) TEX_H;

        // 覆盖视觉稿中的演示文字/艳后示例，写入真实副本状态。
        fillLocal(g, x, y, sx, sy, 118, 59, 359, 166, 0xE72D3033);
        fillLocal(g, x, y, sx, sy, 118, 180, 359, 278, 0xE72D3033);
        fillLocal(g, x, y, sx, sy, 30, 299, 358, 395, 0xE72D3033);
        fillLocal(g, x, y, sx, sy, 28, 88, 111, 170, 0xFFE1E3E4);

        boolean sakura = DungeonHudClientState.dungeonName().contains("小樱");
        DungeonGuiStyle.IconRegion icon = sakura ? DungeonGuiStyle.SAKURA_ICON : DungeonGuiStyle.CLEOPATRA_ICON;
        int iconX = x + Math.round(39 * sx);
        int iconY = y + Math.round(94 * sy);
        int iconW = Math.max(34, Math.round(64 * sx));
        int iconH = Math.max(30, Math.round(59 * sy));
        DungeonGuiStyle.bossIcon(g, icon, iconX, iconY, iconW, iconH);

        int textX = x + Math.round(135 * sx);
        int titleY = y + Math.round(71 * sy);
        g.drawString(mc.font, DungeonHudClientState.dungeonName(), textX, titleY, 0xFFFFD45D, true);

        int seconds = Math.max(0, DungeonHudClientState.remainingSeconds());
        String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
        int infoY = y + Math.round(192 * sy);
        g.drawString(mc.font, "剩余时间：" + time, textX, infoY, 0xFFFFFFFF, true);
        g.drawString(mc.font, "团队复活：" + DungeonHudClientState.revives() + " / " + DungeonHudClientState.maxRevives(),
                textX, infoY + 14, 0xFFFF8A8A, true);
        g.drawString(mc.font, "目标：击败所有 Boss", textX, infoY + 28, 0xFFE6E6E6, true);

        int listX = x + Math.round(43 * sx);
        int listY = y + Math.round(315 * sy);
        g.drawString(mc.font, "[副本] 队伍状态", listX, listY, 0xFF46E4F2, true);
        int row = 0;
        for (MountNetwork.DungeonHudMember member : DungeonHudClientState.members()) {
            if (row >= 5) break;
            int color = switch (member.status()) {
                case 1 -> 0xFFFF6A6A;
                case 2 -> 0xFFAAAAAA;
                default -> 0xFF67E37E;
            };
            String mark = switch (member.status()) {
                case 1 -> "☠ ";
                case 2 -> "○ ";
                default -> "● ";
            };
            g.drawString(mc.font, mark + member.name(), listX, listY + 14 + row * 12, color, false);
            row++;
        }
    }

    private static void fillLocal(GuiGraphics g, int x, int y, float sx, float sy,
                                  int lx1, int ly1, int lx2, int ly2, int color) {
        g.fill(x + Math.round(lx1 * sx), y + Math.round(ly1 * sy),
                x + Math.round(lx2 * sx), y + Math.round(ly2 * sy), color);
    }
}
