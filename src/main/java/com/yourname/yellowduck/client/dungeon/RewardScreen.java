package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.dungeon.RewardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 原版6行大箱子外观的副本奖励预览。
 *
 * 注意：屏幕里的“槽位”只是 generic_54.png 的背景图，RewardMenu 没有真实 Slot。
 * 奖励物品由 renderItem 直接画出来，因此任何点击、Shift、拖拽、数字键、双击都无法取出或放入物品。
 */
public final class RewardScreen extends AbstractContainerScreen<RewardMenu> {
    private static final ResourceLocation CHEST_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int MAX_VISIBLE = ROWS * COLS;

    public RewardScreen(RewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + ROWS * 18; // 原版6行大箱子：222
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CHEST_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "副本奖励 - " + menu.dungeonName(), 8, 6, 0x404040, false);
        graphics.drawString(font, "仅展示，无法取出或放入", 8, inventoryLabelY, 0xB00020, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 原版大箱子上半部分共有 6 x 9 = 54 个视觉槽位。
        // 这里直接画真实Roll结果，不创建任何可交互Slot。
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

        // Tooltip 也只读取同步过来的副本奖励数据，不经过容器Slot。
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

    /** 鼠标所有按钮都只消费事件，不向容器发送任何Slot点击。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    /**
     * 只允许 Esc / 物品栏键关闭界面；数字键、丢弃键等全部吞掉，避免触发容器快捷操作。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return true;
    }
}
