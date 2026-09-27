package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.dungeon.RewardMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 副本结算 / 待领取邮箱界面。
 *
 * 结算模式完全由代码和现有小色块动态拼出，不使用整张死背景图：
 * - 左上按当前副本动态切换 Boss 专属元素横幅；
 * - 中间显示本场玩家输出 / 治疗 / 承伤；
 * - 右侧只显示本场真正 Roll 出来的掉落；
 * - 副本内没有领取按钮，奖励只能离本后从柱子邮箱领取。
 */
public final class RewardScreen extends AbstractContainerScreen<RewardMenu> {
    private static final ResourceLocation CHEST_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private static final ResourceLocation BANNER_SAKURA = banner("sakura");
    private static final ResourceLocation BANNER_CLEOPATRA = banner("cleopatra");
    private static final ResourceLocation BANNER_GARMR = banner("garmr");
    private static final ResourceLocation BANNER_CHANGE = banner("change");
    private static final ResourceLocation BANNER_SILK = banner("silk");

    private static final int LOGICAL_W = 720;
    private static final int LOGICAL_H = 405;
    private static final int LOOT_COLS = 5;
    private static final int LOOT_ROWS = 3;
    private static final int LOOT_PER_PAGE = LOOT_COLS * LOOT_ROWS;

    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;
    private int lootPage;
    private long openedGameTime;

    private int claimX;
    private int claimY;
    private final int claimW = 160;
    private final int claimH = 20;

    public RewardScreen(RewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (menu.mailboxMode()) {
            this.imageWidth = 176;
            this.imageHeight = 114 + 6 * 18;
            this.inventoryLabelY = this.imageHeight - 94;
        } else {
            this.imageWidth = LOGICAL_W;
            this.imageHeight = LOGICAL_H;
        }
    }

    @Override
    protected void init() {
        super.init();
        if (menu.mailboxMode()) {
            claimX = leftPos + 8;
            claimY = topPos + imageHeight - 28;
            return;
        }
        updateTransform();
        openedGameTime = minecraft != null && minecraft.level != null
                ? minecraft.level.getGameTime() : 0L;
    }

    private void updateTransform() {
        uiScale = Math.min(1.0F,
                Math.min((width - 12.0F) / LOGICAL_W, (height - 12.0F) / LOGICAL_H));
        uiScale = Math.max(0.35F, uiScale);
        uiX = (width - LOGICAL_W * uiScale) / 2.0F;
        uiY = (height - LOGICAL_H * uiScale) / 2.0F;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.mailboxMode()) {
            graphics.blit(CHEST_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.mailboxMode()) return;
        graphics.drawString(font, "待领取邮箱", 8, 6, 0x4A2D1B, false);
        graphics.drawString(font, "背包放不下的奖励会安全掉在脚下", 8, inventoryLabelY, 0x8A4F1D, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (menu.mailboxMode()) {
            renderMailbox(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderBackground(graphics);
        updateTransform();
        float mx = (mouseX - uiX) / uiScale;
        float my = (mouseY - uiY) / uiScale;

        graphics.pose().pushPose();
        graphics.pose().translate(uiX, uiY, 0.0F);
        graphics.pose().scale(uiScale, uiScale, 1.0F);

        drawResultWindow(graphics, mx, my, partialTick);

        graphics.pose().popPose();
        drawLootTooltip(graphics, mouseX, mouseY, mx, my);
    }

    private void renderMailbox(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int visible = Math.min(54, menu.rewards().size());
        for (int i = 0; i < visible; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = leftPos + 8 + col * 18;
            int y = topPos + 18 + row * 18;
            ItemStack stack = menu.rewards().get(i);
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }

        boolean hover = mouseX >= claimX && mouseX < claimX + claimW
                && mouseY >= claimY && mouseY < claimY + claimH;
        DungeonGuiStyle.button(graphics, minecraft, claimX, claimY, claimW, claimH,
                "领取全部奖励", hover, DungeonGuiStyle.ButtonTone.GOLD, true);

        for (int i = 0; i < visible; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = leftPos + 8 + col * 18;
            int y = topPos + 18 + row * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, menu.rewards().get(i), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawResultWindow(GuiGraphics g, float mx, float my, float partialTick) {
        // 外框：只使用纯色块叠层，不依赖整张背景图。
        g.fill(4, 6, LOGICAL_W - 4, LOGICAL_H - 6, 0xD9000000);
        g.fill(6, 8, LOGICAL_W - 6, LOGICAL_H - 8, 0xFFB77A2C);
        g.fill(8, 10, LOGICAL_W - 8, LOGICAL_H - 10, 0xFF281B25);
        g.fill(10, 12, LOGICAL_W - 10, LOGICAL_H - 12, 0xF214111B);
        g.fill(11, 13, LOGICAL_W - 11, 15, 0xFF5F3D85);

        // 顶部标题牌。
        int titleW = 310;
        int titleX = (LOGICAL_W - titleW) / 2;
        g.fill(titleX - 5, 4, titleX + titleW + 5, 43, 0xFF2E1729);
        g.fill(titleX - 3, 6, titleX + titleW + 3, 41, 0xFFD09236);
        g.fill(titleX, 9, titleX + titleW, 38, 0xFF24131F);
        g.fill(titleX + 3, 12, titleX + titleW - 3, 15, 0xFF704A8C);
        g.drawCenteredString(font, "◆  副本结算 - " + menu.dungeonName() + "  ◆",
                LOGICAL_W / 2, 19, 0xFFFFD987);

        drawBossPanel(g, partialTick);
        drawTeamSummary(g);
        drawContributionPanel(g);
        drawLootPanel(g, mx, my);
        drawBottom(g, mx, my);
    }

    private void drawBossPanel(GuiGraphics g, float partialTick) {
        int x = 14, y = 49, w = 390, h = 99;
        int[] colors = elementColors();
        panel(g, x, y, w, h, colors[0]);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xEE100E18);

        // Boss 专属横幅只负责元素氛围；“已击败 / Boss名 / 经验”仍由代码动态绘制。
        // 资源统一为 512x128，铺满整个 Boss 信息框，不再创建/渲染实体，避免朝向和动画兼容问题。
        g.blit(bossBanner(), x + 4, y + 4, w - 8, h - 8,
                0.0F, 0.0F, 512, 128, 512, 128);

        // 右侧压暗，保证动态文字在每张横幅上都清晰。
        g.fill(x + 166, y + 4, x + w - 4, y + h - 4, 0x7A090911);
        g.fill(x + 224, y + 4, x + w - 4, y + h - 4, 0x66000000);
        g.fill(x + 4, y + 4, x + w - 4, y + 6, 0x55FFFFFF);

        int tx = x + 185;
        g.fill(tx, y + 12, tx + 54, y + 29, 0xFF641C27);
        g.fill(tx + 1, y + 13, tx + 53, y + 28, 0xFF9A2836);
        g.drawCenteredString(font, "已击败", tx + 27, y + 17, 0xFFFFE7D4);
        g.drawString(font, bossDisplayName(), tx, y + 37, 0xFFFFF1DB, false);
        g.drawString(font, "本场挑战已完成", tx, y + 53, 0xFFC9BCD2, false);
        g.drawString(font, "个人经验  +" + menu.personalXp(), tx, y + 69, 0xFFFFC85D, false);
    }

    private void drawTeamSummary(GuiGraphics g) {
        int x = 412, y = 49, w = 294, h = 99;
        panel(g, x, y, w, h, 0xFF7A5422);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xEE11131C);

        double damage = menu.combatRows().stream().mapToDouble(RewardMenu.CombatRow::damage).sum();
        double healing = menu.combatRows().stream().mapToDouble(RewardMenu.CombatRow::healing).sum();
        double taken = menu.combatRows().stream().mapToDouble(RewardMenu.CombatRow::taken).sum();

        int cellW = 72;
        statCell(g, x + 3, y + 9, cellW, "战斗时长", formatTime(menu.fightSeconds()), 0xFFFFD276);
        statCell(g, x + 75, y + 9, cellW, "队伍总输出", formatNumber(damage), 0xFFFFC06A);
        statCell(g, x + 147, y + 9, cellW, "队伍总治疗", formatNumber(healing), 0xFF7EEA85);
        statCell(g, x + 219, y + 9, cellW, "队伍总承伤", formatNumber(taken), 0xFF8BC6FF);
    }

    private void statCell(GuiGraphics g, int x, int y, int w, String label, String value, int valueColor) {
        g.drawCenteredString(font, label, x + w / 2, y + 11, 0xFFB9B5C4);
        g.drawCenteredString(font, value, x + w / 2, y + 42, valueColor);
        g.fill(x + w - 1, y + 4, x + w, y + 78, 0x443F3950);
    }

    private void drawContributionPanel(GuiGraphics g) {
        int x = 14, y = 156, w = 430, h = 188;
        panel(g, x, y, w, h, 0xFF9C6929);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xF2111219);
        g.fill(x + 3, y + 3, x + w - 3, y + 29, 0xFF181821);
        g.drawString(font, "⚔  成员贡献排行", x + 12, y + 10, 0xFFFFCF67, false);

        int headerY = y + 34;
        g.drawString(font, "玩家", x + 52, headerY, 0xFFAAA6B4, false);
        g.drawString(font, "输出", x + 184, headerY, 0xFFAAA6B4, false);
        g.drawString(font, "治疗", x + 248, headerY, 0xFFAAA6B4, false);
        g.drawString(font, "承伤", x + 309, headerY, 0xFFAAA6B4, false);
        g.drawString(font, "总贡献", x + 367, headerY, 0xFFAAA6B4, false);

        List<RewardMenu.CombatRow> rows = menu.combatRows().stream()
                .sorted(Comparator.comparingDouble(RewardMenu.CombatRow::total).reversed())
                .limit(5)
                .toList();
        double maxDamage = rows.stream().mapToDouble(RewardMenu.CombatRow::damage).max().orElse(1.0D);
        double maxHealing = rows.stream().mapToDouble(RewardMenu.CombatRow::healing).max().orElse(1.0D);
        double maxTaken = rows.stream().mapToDouble(RewardMenu.CombatRow::taken).max().orElse(1.0D);

        for (int i = 0; i < rows.size(); i++) {
            RewardMenu.CombatRow row = rows.get(i);
            int ry = y + 48 + i * 27;
            boolean top = i == 0;
            int border = top ? 0xFFB47A25 : 0xFF343241;
            int body = top ? 0x663A2B13 : 0x6613151E;
            g.fill(x + 5, ry, x + w - 5, ry + 25, border);
            g.fill(x + 6, ry + 1, x + w - 6, ry + 24, body);

            int rankColor = switch (i) {
                case 0 -> 0xFFFFD568;
                case 1 -> 0xFFBFD7F1;
                case 2 -> 0xFFD69B65;
                default -> 0xFF9A96A4;
            };
            g.drawCenteredString(font, Integer.toString(i + 1), x + 17, ry + 9, rankColor);
            DungeonGuiStyle.playerFace(g, minecraft, row.playerId(), x + 29, ry + 3, 19);

            g.drawString(font, trimName(row.name(), 90), x + 53, ry + 4, 0xFFF3EDF7, false);
            g.drawString(font, roleText(row, rows), x + 53, ry + 14, roleColor(row, rows), false);

            drawMetric(g, x + 176, ry + 3, 55, row.damage(), maxDamage, 0xFFE95454);
            drawMetric(g, x + 240, ry + 3, 55, row.healing(), maxHealing, 0xFF4DD86A);
            drawMetric(g, x + 303, ry + 3, 55, row.taken(), maxTaken, 0xFF579DE8);
            g.drawCenteredString(font, formatNumber(row.total()), x + 392, ry + 8,
                    top ? 0xFFFFD36A : 0xFFD7E4FF);
        }

        if (rows.isEmpty()) {
            g.drawCenteredString(font, "本场没有可显示的战斗统计", x + w / 2, y + 105, 0xFF9B96A6);
        }
    }

    private void drawMetric(GuiGraphics g, int x, int y, int w,
                            double value, double max, int color) {
        g.drawCenteredString(font, formatNumber(value), x + w / 2, y, 0xFFE7E2EA);
        int barY = y + 13;
        g.fill(x + 4, barY, x + w - 4, barY + 4, 0xFF33333B);
        int inner = Math.max(0, w - 8);
        int fill = max <= 0.0D ? 0 : (int) Math.round(inner * Math.min(1.0D, value / max));
        if (fill > 0) g.fill(x + 4, barY, x + 4 + fill, barY + 4, color);
    }

    private void drawLootPanel(GuiGraphics g, float mx, float my) {
        int x = 452, y = 156, w = 254, h = 188;
        panel(g, x, y, w, h, 0xFF9C6929);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xF2111219);
        g.fill(x + 3, y + 3, x + w - 3, y + 29, 0xFF181821);
        g.drawString(font, "▣  本次掉落", x + 12, y + 10, 0xFFFFCF67, false);
        g.drawString(font, "共 " + totalItemCount() + " 件", x + w - 62, y + 10, 0xFFB9B5C4, false);

        int pages = Math.max(1, (menu.rewards().size() + LOOT_PER_PAGE - 1) / LOOT_PER_PAGE);
        if (lootPage >= pages) lootPage = pages - 1;
        int start = lootPage * LOOT_PER_PAGE;
        int slot = 42;
        int gap = 5;
        int startX = x + 11;
        int startY = y + 37;

        for (int local = 0; local < LOOT_PER_PAGE; local++) {
            int index = start + local;
            int col = local % LOOT_COLS;
            int row = local / LOOT_COLS;
            int sx = startX + col * (slot + gap);
            int sy = startY + row * (slot + gap);
            if (index >= menu.rewards().size()) {
                g.fill(sx, sy, sx + slot, sy + slot, 0x55302A36);
                g.fill(sx + 2, sy + 2, sx + slot - 2, sy + slot - 2, 0x66131319);
                continue;
            }
            ItemStack stack = menu.rewards().get(index);
            int border = rarityColor(stack.getRarity());
            g.fill(sx + 1, sy + 2, sx + slot + 1, sy + slot + 2, 0x55000000);
            g.fill(sx, sy, sx + slot, sy + slot, border);
            g.fill(sx + 2, sy + 2, sx + slot - 2, sy + slot - 2, 0xFF24202B);
            g.fill(sx + 4, sy + 4, sx + slot - 4, sy + slot - 4, 0xFF15131A);
            g.renderItem(stack, sx + 13, sy + 10);
            g.renderItemDecorations(font, stack, sx + 13, sy + 10);
        }

        if (menu.rewards().isEmpty()) {
            g.drawCenteredString(font, "本次未掉落物品", x + w / 2, y + 100, 0xFF9995A2);
        }

        if (pages > 1) {
            int py = y + h - 18;
            boolean leftHover = inside(mx, my, x + 79, py, 22, 14);
            boolean rightHover = inside(mx, my, x + 153, py, 22, 14);
            g.fill(x + 79, py, x + 101, py + 14, leftHover ? 0xFF6D487F : 0xFF3B2B46);
            g.fill(x + 153, py, x + 175, py + 14, rightHover ? 0xFF6D487F : 0xFF3B2B46);
            g.drawCenteredString(font, "<", x + 90, py + 3, 0xFFFFFFFF);
            g.drawCenteredString(font, ">", x + 164, py + 3, 0xFFFFFFFF);
            g.drawCenteredString(font, (lootPage + 1) + "/" + pages, x + 127, py + 3, 0xFFBEB8C9);
        }
    }

    private void drawBottom(GuiGraphics g, float mx, float my) {
        int closeX = 122, closeY = 354, closeW = 250, closeH = 30;
        boolean closeHover = inside(mx, my, closeX, closeY, closeW, closeH);
        String close = remainingSeconds() > 0 ? "关闭 (" + remainingSeconds() + ")" : "关闭";
        DungeonGuiStyle.button(g, minecraft, closeX, closeY, closeW, closeH,
                close, closeHover, DungeonGuiStyle.ButtonTone.GRAY, true);

        int x = 452, y = 351, w = 254, h = 39;
        g.fill(x, y, x + w, y + h, 0xFF55346B);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xEE1B1524);
        g.fill(x + 4, y + 4, x + 8, y + h - 4, 0xFF9B5AC4);
        g.drawString(font, "奖励已发送至待领取邮箱", x + 14, y + 8, 0xFFEAD9FF, false);
        String sub = menu.isItemRecipient()
                ? "离开副本后，在副本柱子处领取。"
                : "物品由 " + menu.itemRecipientName() + " 在柱子邮箱领取。";
        g.drawString(font, trimName(sub, w - 24), x + 14, y + 23, 0xFFAAA2B6, false);
    }

    private void drawLootTooltip(GuiGraphics g, int mouseX, int mouseY, float mx, float my) {
        int x = 452, y = 156;
        int slot = 42, gap = 5;
        int startX = x + 11, startY = y + 37;
        int start = lootPage * LOOT_PER_PAGE;
        for (int local = 0; local < LOOT_PER_PAGE; local++) {
            int index = start + local;
            if (index >= menu.rewards().size()) break;
            int col = local % LOOT_COLS;
            int row = local / LOOT_COLS;
            int sx = startX + col * (slot + gap);
            int sy = startY + row * (slot + gap);
            if (inside(mx, my, sx, sy, slot, slot)) {
                g.renderTooltip(font, menu.rewards().get(index), mouseX, mouseY);
                return;
            }
        }
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x55000000);
        g.fill(x, y, x + w, y + h, accent);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A222D);
    }

    private int[] elementColors() {
        String id = menu.dungeonId().toLowerCase(Locale.ROOT);
        if (id.contains("sakura")) return new int[]{0xFFBD5B91, 0xCC35162A, 0xBB8B2D66};
        if (id.contains("cleopatra")) return new int[]{0xFFC58D35, 0xCC2F2414, 0xBBA46A24};
        if (id.contains("change")) return new int[]{0xFF7CAAD9, 0xCC16283A, 0xBB486F9A};
        if (id.contains("silk") || id.contains("professor")) return new int[]{0xFF8E6750, 0xCC2B211D, 0xBB674531};
        return new int[]{0xFF8B4EB2, 0xCC1B1026, 0xBB59227B};
    }

    private static ResourceLocation banner(String name) {
        return new ResourceLocation("yellowduck", "textures/gui/reward/boss_banner/" + name + ".png");
    }

    private ResourceLocation bossBanner() {
        String id = menu.dungeonId().toLowerCase(Locale.ROOT);
        if (id.contains("sakura")) return BANNER_SAKURA;
        if (id.contains("cleopatra")) return BANNER_CLEOPATRA;
        if (id.contains("garmr") || id.contains("hellhound")) return BANNER_GARMR;
        if (id.contains("change")) return BANNER_CHANGE;
        if (id.contains("silk") || id.contains("professor")) return BANNER_SILK;
        return BANNER_GARMR;
    }

    private String bossDisplayName() {
        String id = menu.dungeonId().toLowerCase(Locale.ROOT);
        if (id.contains("sakura")) return "精英魔女小樱";
        if (id.contains("cleopatra")) return "埃及艳后";
        if (id.contains("garmr")) return "地狱双头犬加姆";
        if (id.contains("change")) return "嫦娥";
        if (id.contains("silk") || id.contains("professor")) return "疯狂教授斯尔克";
        return menu.dungeonName();
    }

    private String roleText(RewardMenu.CombatRow row, List<RewardMenu.CombatRow> rows) {
        double maxHeal = rows.stream().mapToDouble(RewardMenu.CombatRow::healing).max().orElse(0.0D);
        double maxTaken = rows.stream().mapToDouble(RewardMenu.CombatRow::taken).max().orElse(0.0D);
        double maxDamage = rows.stream().mapToDouble(RewardMenu.CombatRow::damage).max().orElse(0.0D);
        if (row.healing() > 0.0D && row.healing() >= maxHeal && row.healing() >= row.damage() * 0.35D) return "治疗之光";
        if (row.taken() > 0.0D && row.taken() >= maxTaken && row.taken() >= row.damage() * 0.60D) return "坚毅守护";
        if (row.damage() > 0.0D && row.damage() >= maxDamage) return "输出之星";
        return "全能战士";
    }

    private int roleColor(RewardMenu.CombatRow row, List<RewardMenu.CombatRow> rows) {
        String role = roleText(row, rows);
        if (role.contains("治疗")) return 0xFF65E678;
        if (role.contains("守护")) return 0xFF8EC5FF;
        if (role.contains("输出")) return 0xFFFF716A;
        return 0xFF86B7E8;
    }

    private String trimName(String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private int totalItemCount() {
        int total = 0;
        for (ItemStack stack : menu.rewards()) total += Math.max(0, stack.getCount());
        return total;
    }

    private int remainingSeconds() {
        int base = menu.previewSecondsRemaining();
        if (base <= 0 || minecraft == null || minecraft.level == null) return base;
        long elapsed = Math.max(0L, minecraft.level.getGameTime() - openedGameTime) / 20L;
        return Math.max(0, base - (int) elapsed);
    }

    private static String formatTime(int seconds) {
        int m = Math.max(0, seconds) / 60;
        int s = Math.max(0, seconds) % 60;
        return m + "分" + String.format(Locale.ROOT, "%02d", s) + "秒";
    }

    private static String formatNumber(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return "0";
        if (value >= 1_000_000.0D) return compact(value / 1_000_000.0D) + "M";
        if (value >= 1_000.0D) return compact(value / 1_000.0D) + "K";
        if (value >= 100.0D) return Long.toString(Math.round(value));
        return compact(value);
    }

    private static String compact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int rarityColor(Rarity rarity) {
        if (rarity == Rarity.EPIC) return 0xFFB050D4;
        if (rarity == Rarity.RARE) return 0xFF4D86D9;
        if (rarity == Rarity.UNCOMMON) return 0xFF5BB95D;
        return 0xFF7D7484;
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.mailboxMode()) {
            if (mouseX >= claimX && mouseX < claimX + claimW
                    && mouseY >= claimY && mouseY < claimY + claimH) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RewardMenu.ACTION_CLAIM_MAILBOX);
                }
                return true;
            }
            return true;
        }

        float mx = (float) ((mouseX - uiX) / uiScale);
        float my = (float) ((mouseY - uiY) / uiScale);
        if (inside(mx, my, 122, 354, 250, 30)) {
            onClose();
            return true;
        }

        int pages = Math.max(1, (menu.rewards().size() + LOOT_PER_PAGE - 1) / LOOT_PER_PAGE);
        int pageY = 156 + 188 - 18;
        if (pages > 1 && inside(mx, my, 452 + 79, pageY, 22, 14)) {
            lootPage = Math.max(0, lootPage - 1);
            return true;
        }
        if (pages > 1 && inside(mx, my, 452 + 153, pageY, 22, 14)) {
            lootPage = Math.min(pages - 1, lootPage + 1);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!menu.mailboxMode()) {
            int pages = Math.max(1, (menu.rewards().size() + LOOT_PER_PAGE - 1) / LOOT_PER_PAGE);
            if (delta < 0) lootPage = Math.min(pages - 1, lootPage + 1);
            else if (delta > 0) lootPage = Math.max(0, lootPage - 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return true;
    }
}
