package com.yourname.yellowduck.client.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.UUID;

/** 组队/副本 GUI 共用的暖色羊皮纸视觉工具。 */
public final class DungeonGuiStyle {
    public static final int TEXT = 0xFF4A2C1C;
    public static final int MUTED = 0xFF7E6655;
    public static final int GOLD = 0xFFD8A13B;
    public static final int GOLD_DARK = 0xFF7A481E;
    public static final int GREEN = 0xFF258B4B;
    public static final int RED = 0xFFB44949;
    public static final int PANEL = 0xFFF3E6C8;
    public static final int PANEL_DARK = 0xFFE1C99C;
    public static final int BORDER = 0xFF6E4425;
    public static final int SHADOW = 0x66000000;

    private static final ResourceLocation PARCHMENT_TILE =
            new ResourceLocation("yellowduck", "textures/gui/party/v2/parchment_tile.png");
    private static final ResourceLocation BOSS_ICON_ATLAS =
            new ResourceLocation("yellowduck", "textures/gui/boss_map_icon.png");
    private static final ResourceLocation SAKURA_HEAD =
            new ResourceLocation("yellowduck", "textures/gui/boss_head/sakura.png");

    private static final int ATLAS_W = 1368;
    private static final int ATLAS_H = 1012;

    public static final IconRegion CLEOPATRA_ICON = new IconRegion(218, 821, 106, 95);
    public static final IconRegion GARMR_ICON = new IconRegion(396, 2, 106, 95);
    public static final IconRegion CHANGE_ICON = new IconRegion(218, 624, 106, 95);
    public static final IconRegion SILK_ICON = new IconRegion(1152, 2, 106, 95);
    public static final IconRegion SAKURA_ICON = new IconRegion(1083, 631, 103, 79);

    private DungeonGuiStyle() {}

    /** 大窗口：羊皮纸纹理、双层棕金边框、四角装饰和顶部标题区。 */
    public static void tiledWindow(GuiGraphics g, int x, int y, int w, int h, boolean header) {
        g.fill(x + 7, y + 8, x + w + 7, y + h + 8, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xFF4E301D);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC8934F);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFFF7ECCD);

        tile(g, x + 5, y + 5, w - 10, h - 10);

        // 双层细边，视觉上更接近预览图的棕金描边。
        lineRect(g, x + 5, y + 5, w - 10, h - 10, 0xFF8D5A2B);
        lineRect(g, x + 8, y + 8, w - 16, h - 16, 0xFFE2B968);

        // 四角装饰。
        corner(g, x + 5, y + 5, false, false);
        corner(g, x + w - 17, y + 5, true, false);
        corner(g, x + 5, y + h - 17, false, true);
        corner(g, x + w - 17, y + h - 17, true, true);

        if (header) {
            g.fill(x + 10, y + 10, x + w - 10, y + 42, 0x55FFF9E6);
            g.fill(x + 14, y + 41, x + w - 14, y + 42, 0xFFBC8A47);
        }
    }

    private static void tile(GuiGraphics g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy += 16) {
            for (int xx = x; xx < x + w; xx += 16) {
                int tw = Math.min(16, x + w - xx);
                int th = Math.min(16, y + h - yy);
                g.blit(PARCHMENT_TILE, xx, yy, 0, 0, tw, th, 16, 16);
            }
        }
    }

    private static void corner(GuiGraphics g, int x, int y, boolean flipX, boolean flipY) {
        // 小型卷角/金属角花，全部用几何图形组成，避免整张界面画死。
        int dx = flipX ? -1 : 1;
        int dy = flipY ? -1 : 1;
        int ox = flipX ? x + 12 : x;
        int oy = flipY ? y + 12 : y;
        g.fill(Math.min(ox, ox + dx * 10), oy, Math.max(ox, ox + dx * 10) + 1, oy + 2, 0xFFD4A451);
        g.fill(ox, Math.min(oy, oy + dy * 10), ox + 2, Math.max(oy, oy + dy * 10) + 1, 0xFFD4A451);
        g.fill(Math.min(ox, ox + dx * 6), Math.min(oy, oy + dy * 6),
                Math.max(ox, ox + dx * 6) + 1, Math.max(oy, oy + dy * 6) + 1, 0x33552E16);
    }

    private static void lineRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x30000000);
        g.fill(x, y, x + w, y + h, 0xFF8A5A2C);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE7CF9F);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFF8EFD8);
    }

    public static void innerPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x22000000);
        g.fill(x, y, x + w, y + h, 0xFFC79E66);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFF8EFD9);
    }

    public static void sectionHeader(GuiGraphics g, Minecraft mc, int x, int y, int w, String text) {
        g.fill(x, y + 16, x + w, y + 17, 0x55845A30);
        g.drawString(mc.font, text, x + 7, y + 4, TEXT, false);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int border = hovered ? 0xFFD6A34F : 0xFFC8A878;
        int body = hovered ? 0xFFFFF5DD : 0xFFF8EED6;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        g.fill(x + 2, y + 2, x + w - 2, y + 4, 0x66FFFFFF);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h,
                            boolean hovered, boolean enabled, ButtonTone tone) {
        int accent = toneColor(tone);
        int border = enabled && hovered ? accent : 0xFFC19862;
        int body = enabled ? 0xFFF8E9C8 : 0xFFD5C8B1;
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x28000000);
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, body);
        if (enabled && hovered) g.fill(x + 3, y + 3, x + w - 3, y + 6, 0x66FFFFFF);
    }

    public static void button(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                              String text, boolean hovered, ButtonTone tone, boolean enabled) {
        int base = toneColor(tone);
        int dark = toneDark(tone);
        if (!enabled) {
            base = 0xFF8E8577;
            dark = 0xFF5E574F;
        }
        if (hovered && enabled) base = lighten(base, 24);

        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x33000000);
        g.fill(x, y, x + w, y + h, dark);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, base);
        g.fill(x + 3, y + 3, x + w - 3, y + 5, enabled ? 0x66FFFFFF : 0x22FFFFFF);
        g.fill(x + 3, y + h - 5, x + w - 3, y + h - 3, 0x33000000);
        g.drawCenteredString(mc.font, text, x + w / 2, y + (h - 8) / 2,
                enabled ? 0xFFFFFFFF : 0xFFD7D0C8);
    }

    public static void statusPill(GuiGraphics g, Minecraft mc, int x, int y, String text, boolean good) {
        int w = mc.font.width(text) + 12;
        int border = good ? 0xFF34754B : 0xFF9B4B43;
        int fill = good ? 0xFFE4F1E6 : 0xFFF4E1DB;
        g.fill(x, y, x + w, y + 17, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 16, fill);
        g.drawString(mc.font, text, x + 6, y + 5, good ? 0xFF24703F : 0xFF9A3C35, false);
    }

    public static void rewardSlot(GuiGraphics g, int x, int y, int size) {
        g.fill(x + 2, y + 3, x + size + 2, y + size + 3, 0x33000000);
        g.fill(x, y, x + size, y + size, 0xFF7E5231);
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF3E2D29);
        g.fill(x + 4, y + 4, x + size - 4, y + size - 4, 0xFF654539);
    }

    public static void bossIconForDungeon(GuiGraphics g, String dungeonId, int x, int y, int w, int h) {
        String id = dungeonId == null ? "" : dungeonId.toLowerCase(Locale.ROOT);
        if (id.contains("sakura")) {
            g.blit(SAKURA_HEAD, x, y, 0, 0, w, h, w, h);
            return;
        }
        IconRegion region = id.contains("garmr") || id.contains("hellhound") ? GARMR_ICON
                : id.contains("change") ? CHANGE_ICON
                : id.contains("silk") || id.contains("professor") ? SILK_ICON
                : CLEOPATRA_ICON;
        bossIcon(g, region, x, y, w, h);
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

    private static int toneColor(ButtonTone tone) {
        return switch (tone) {
            case GREEN -> 0xFF287A43;
            case RED -> 0xFF9E3E45;
            case GOLD -> 0xFFA86E2E;
            case GRAY -> 0xFF6F4B35;
        };
    }

    private static int toneDark(ButtonTone tone) {
        return switch (tone) {
            case GREEN -> 0xFF174C2A;
            case RED -> 0xFF63252B;
            case GOLD -> 0xFF6B431D;
            case GRAY -> 0xFF3E2C23;
        };
    }

    private static int lighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >>> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public enum ButtonTone { GREEN, RED, GOLD, GRAY }

    public record IconRegion(int u, int v, int w, int h) {}
}
