package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.dungeon.RewardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 原版6行大箱子外观的副本奖励预览 / 待领取邮箱。
 *
 * 物品仍然只是显示数据，不存在真实容器 Slot。邮箱模式通过独立服务端按钮一次性领取全部奖励。
 */
public final class RewardScreen extends AbstractContainerScreen<RewardMenu> {
    private static final ResourceLocation CHEST_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int MAX_VISIBLE = ROWS * COLS;

    private int claimX;
    private int claimY;
    private int claimW = 160;
    private int claimH = 20;

    public RewardScreen(RewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        claimX = leftPos + 8;
        claimY = topPos + imageHeight - 28;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CHEST_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.mailboxMode()) {
            graphics.drawString(font, "待领取邮箱", 8, 6, 0x4A2D1B, false);
            graphics.drawString(font, "背包放不下的奖励会安全掉在脚下", 8, inventoryLabelY, 0x8A4F1D, false);
        } else {
            graphics.drawString(font, "副本奖励 - " + menu.dungeonName(), 8, 6, 0x404040, false);
            graphics.drawString(font, "本页仅展示，物品已进入接收者的待领取邮箱", 8, inventoryLabelY, 0x8A4F1D, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int visible = Math.min(MAX_VISIBLE, menu.rewards().size());
        for (int i = 0; i < visible; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = leftPos + 8 + col * 18;
            int y = topPos + 18 + row * 18;
            ItemStack stack = menu.rewards().get(i);
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }

        if (menu.mailboxMode()) {
            boolean hover = mouseX >= claimX && mouseX < claimX + claimW
                    && mouseY >= claimY && mouseY < claimY + claimH;
            int border = hover ? 0xFFF2C15B : 0xFF6C421F;
            int body = hover ? 0xFFB98234 : 0xFF8D5C2A;
            graphics.fill(claimX, claimY, claimX + claimW, claimY + claimH, border);
            graphics.fill(claimX + 2, claimY + 2, claimX + claimW - 2, claimY + claimH - 2, body);
            graphics.fill(claimX + 3, claimY + 3, claimX + claimW - 3, claimY + 5, 0x55FFFFFF);
            graphics.drawCenteredString(font, "领取全部奖励", claimX + claimW / 2, claimY + 6, 0xFFFFFFFF);
        }

        for (int i = 0; i < visible; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = leftPos + 8 + col * 18;
            int y = topPos + 18 + row * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, menu.rewards().get(i), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.mailboxMode()
                && mouseX >= claimX && mouseX < claimX + claimW
                && mouseY >= claimY && mouseY < claimY + claimH) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RewardMenu.ACTION_CLAIM_MAILBOX);
            }
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
