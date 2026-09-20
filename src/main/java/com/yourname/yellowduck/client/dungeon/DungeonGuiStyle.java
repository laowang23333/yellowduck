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

    private static final ResourceLocation BUTTON_GRAY = new ResourceLocation("yellowduck", "textures/gui/party/button_gray.png");
    private static final ResourceLocation BUTTON_GREEN = new ResourceLocation("yellowduck", "textures/gui/party/button_green.png");
    private static final ResourceLocation BUTTON_RED = new ResourceLocation("yellowduck", "textures/gui/party/button_red.png");
    private static final ResourceLocation BUTTON_GOLD = new ResourceLocation("yellowduck", "textures/gui/party/button_gold.png");
    private static final ResourceLocation TILES = new ResourceLocation("yellowduck", "textures/gui/party/gui_tiles.png");
    private static final ResourceLocation BOSS_ICON_ATLAS =
            new ResourceLocation("yellowduck", "textures/gui/boss_map_icon.png");
    private static final int ATLAS_W = 1368;
    private static final int ATLAS_H = 1012;

    // 直接复用当前 Boss 血条使用的头像裁切区域。
    public static final IconRegion CLEOPATRA_ICON = new IconRegion(218, 821, 106, 95);
    public static final IconRegion SAKURA_ICON = new IconRegion(1083, 631, 103, 79);

    private DungeonGuiStyle() {}

    public static void tiledWindow(GuiGraphics g, int x, int y, int w, int h, boolean header) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, SHADOW); g.fill(x, y, x + w, y + h, BORDER);
        for (int yy=y+2; yy<y+h-2; yy+=16) for (int xx=x+2; xx<x+w-2; xx+=16) {
            int dw=Math.min(16,x+w-2-xx), dh=Math.min(16,y+h-2-yy); g.blit(TILES,xx,yy,0,0,dw,dh,64,64);
        }
        if (header) g.fill(x+3,y+3,x+w-3,y+20,0xFFF4F4F5);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, SHADOW);
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
        g.fill(x + 3, y + 3, x + w - 3, y + 18, 0xFFF4F4F5);
        g.fill(x + 3, y + 18, x + w - 3, y + 19, 0xFFB8BABE);
    }

    public static void innerPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8A8C90);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFF1F2F3);
    }

    public static void button(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                              String text, boolean hovered, ButtonTone tone, boolean enabled) {
        int border = enabled ? (hovered ? GOLD_DARK : 0xFF6C6E72) : 0xFF777777;
        int fill;
        int textColor = enabled ? 0xFFFFFFFF : 0xFF9A9A9A;
        if (!enabled) fill = 0xFF4F5154;
        else fill = switch (tone) {
            case GREEN -> hovered ? 0xFF25C94E : 0xFF18A83C;
            case RED -> hovered ? 0xFFEF5555 : 0xFFD33E3E;
            case GOLD -> hovered ? 0xFFFFD34C : 0xFFE8AA17;
            case GRAY -> hovered ? 0xFF7A7D81 : 0xFF676A6E;
        };
        ResourceLocation texture = switch (tone) {
            case GREEN -> BUTTON_GREEN; case RED -> BUTTON_RED; case GOLD -> BUTTON_GOLD; case GRAY -> BUTTON_GRAY;
        };
        if (enabled) {
            g.blit(texture, x, y, 0, 0, w, h, 32, 16);
            if (hovered) g.fill(x + 2, y + 2, x + w - 2, y + 4, 0x55FFFFFF);
        } else {
            g.fill(x, y, x + w, y + h, border); g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
        }
        g.drawCenteredString(mc.font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void bossIcon(GuiGraphics g, IconRegion region, int x, int y, int w, int h) {
        // 保持血条头像原始比例，避免小樱帽子或艳后头像被拉扁。
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
