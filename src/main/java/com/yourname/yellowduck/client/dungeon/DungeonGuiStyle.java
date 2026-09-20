package com.yourname.yellowduck.client.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 组队/副本 GUI 共用的灰金色视觉工具。 */
public final class DungeonGuiStyle {
    public static final int TEXT = 0xFF202020;
    public static final int MUTED = 0xFF666666;
    public static final int GOLD = 0xFFFFC928;
    public static final int GOLD_DARK = 0xFF9B6A09;
    public static final int GREEN = 0xFF1DB943;
    public static final int RED = 0xFFE14343;
    public static final int PANEL = 0xFFE8E9EB;
    public static final int PANEL_DARK = 0xFFD1D3D6;
    public static final int BORDER = 0xFF4B4D50;
    public static final int SHADOW = 0x66000000;

    private static final ResourceLocation BUTTON_GRAY =
            new ResourceLocation("yellowduck", "textures/gui/party/button_gray.png");
    private static final ResourceLocation BUTTON_GREEN =
            new ResourceLocation("yellowduck", "textures/gui/party/button_green.png");
    private static final ResourceLocation BUTTON_RED =
            new ResourceLocation("yellowduck", "textures/gui/party/button_red.png");
    private static final ResourceLocation BUTTON_GOLD =
            new ResourceLocation("yellowduck", "textures/gui/party/button_gold.png");

    private static final ResourceLocation BOSS_ICON_ATLAS =
            new ResourceLocation("yellowduck", "textures/gui/boss_map_icon.png");
    private static final int ATLAS_W = 1368;
    private static final int ATLAS_H = 1012;

    // 直接复用当前 Boss 血条使用的头像裁切区域。
    public static final IconRegion CLEOPATRA_ICON = new IconRegion(218, 821, 106, 95);
    public static final IconRegion SAKURA_ICON = new IconRegion(1083, 631, 103, 79);

    private DungeonGuiStyle() {}

    /**
     * 主窗口改为纯代码绘制，不再把 gui_tiles 的单个 16x16 方格铺满整个屏幕。
     * 这样不会再出现截图中密密麻麻的“表格格子”。
     */
    public static void tiledWindow(GuiGraphics g, int x, int y, int w, int h, boolean header) {
        // 外投影
        g.fill(x + 5, y + 6, x + w + 5, y + h + 6, 0x52000000);

        // 外边框 + 金属亮边
        g.fill(x, y, x + w, y + h, 0xFF43464B);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFF0F1F2);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFE5E7E9);

        // 四角金色固定件，接近之前 UI 图的感觉
        int c = 0xFFD69B18;
        g.fill(x + 3, y + 3, x + 9, y + 9, c);
        g.fill(x + w - 9, y + 3, x + w - 3, y + 9, c);
        g.fill(x + 3, y + h - 9, x + 9, y + h - 3, c);
        g.fill(x + w - 9, y + h - 9, x + w - 3, y + h - 3, c);

        if (header) {
            g.fill(x + 4, y + 4, x + w - 4, y + 32, 0xFFF7F7F8);
            g.fill(x + 4, y + 31, x + w - 4, y + 33, 0xFFB8BBC0);
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, SHADOW);
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
        g.fill(x + 3, y + 3, x + w - 3, y + 18, 0xFFF4F4F5);
        g.fill(x + 3, y + 18, x + w - 3, y + 19, 0xFFB8BABE);
    }

    public static void innerPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x28000000);
        g.fill(x, y, x + w, y + h, 0xFF777A7F);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFF3F4F5);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0xFF8C8F94 : 0xFFA3A6AA);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1,
                hovered ? 0xFFF1F2F3 : 0xFFE5E7E9);
    }

    /** 功能卡。灰色卡片 + 轻微立体边，悬停时用对应颜色描边。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h,
                            boolean hovered, boolean enabled, ButtonTone tone) {
        int accent = switch (tone) {
            case GREEN -> GREEN;
            case RED -> RED;
            case GOLD -> GOLD;
            case GRAY -> 0xFF74777C;
        };

        int border = enabled && hovered ? accent : 0xFF6C6F74;
        int body = enabled ? 0xFFD6D8DB : 0xFF9B9DA1;
        int bodyTop = enabled ? 0xFFE4E5E7 : 0xFFA9ABAE;

        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x35000000);
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, body);
        g.fill(x + 3, y + 3, x + w - 3, y + 28, bodyTop);

        // 侧边和底边阴影，做成之前图里那种厚卡片效果
        g.fill(x + w - 4, y + 3, x + w - 2, y + h - 3, 0x33000000);
        g.fill(x + 3, y + h - 5, x + w - 3, y + h - 2, 0x25000000);

        if (enabled && hovered) {
            g.fill(x + 2, y + 2, x + w - 2, y + 4, 0x55FFFFFF);
        }
    }

    public static void button(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                              String text, boolean hovered, ButtonTone tone, boolean enabled) {
        int textColor = enabled ? 0xFFFFFFFF : 0xFFB7B7B7;

        if (!enabled) {
            g.fill(x, y, x + w, y + h, 0xFF6A6C70);
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF4F5154);
        } else {
            ResourceLocation texture = switch (tone) {
                case GREEN -> BUTTON_GREEN;
                case RED -> BUTTON_RED;
                case GOLD -> BUTTON_GOLD;
                case GRAY -> BUTTON_GRAY;
            };
            g.blit(texture, x, y, 0, 0, w, h, 32, 16);

            int border = switch (tone) {
                case GREEN -> 0xFF12832F;
                case RED -> 0xFF9E2D2D;
                case GOLD -> 0xFF9B6A09;
                case GRAY -> 0xFF55585C;
            };
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);

            if (hovered) {
                g.fill(x + 2, y + 2, x + w - 2, y + 5, 0x55FFFFFF);
            }
        }

        g.drawCenteredString(mc.font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void bossIcon(GuiGraphics g, IconRegion region, int x, int y, int w, int h) {
        double sourceRatio = region.w() / (double) region.h();
        int drawW = w;
        int drawH = Math.max(1, (int) Math.round(drawW / sourceRatio));
        if (drawH > h) {
            drawH = h;
            drawW = Math.max(1, (int) Math.round(drawH * sourceRatio));
        }
        int drawX = x + (w - drawW) / 2;
        int drawY = y + (h - drawH) / 2;

        g.blit(BOSS_ICON_ATLAS, drawX, drawY, drawW, drawH,
                region.u(), region.v(), region.w(), region.h(), ATLAS_W, ATLAS_H);
    }

    public static void playerFace(GuiGraphics g, Minecraft mc, UUID uuid, int x, int y, int size) {
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(uuid);
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) skin = info.getSkinLocation();
        }
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    public enum ButtonTone { GREEN, RED, GOLD, GRAY }

    public record IconRegion(int u, int v, int w, int h) {}
}
