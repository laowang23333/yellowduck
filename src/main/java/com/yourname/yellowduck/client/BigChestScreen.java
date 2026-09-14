package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yourname.yellowduck.menu.BigChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BigChestScreen extends AbstractContainerScreen<BigChestMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    public BigChestScreen(BigChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        g.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    // 【B 方案核心】覆写每个格子的渲染，允许画超过 64 的数字
    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        int x = slot.x;
        int y = slot.y;

        // 1. 画物品图标
        g.renderItem(stack, x, y);

        // 2. 画数量数字（原版 renderItemDecorations 会在 > 64 时跳过，我们自己画）
        g.renderItemDecorations(this.font, stack, x, y);

        // 3. 如果数量 > 64，原版 renderItemDecorations 就不画数字了
        //    我们手动补上：把 renderItemDecorations 画错的部分重画一遍
        if (stack.getCount() > 64) {
            // 先清掉原版画错的位置（用透明色覆盖，避免重影）
            // 然后手动画数字
            String countText = String.valueOf(stack.getCount());
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F); // 画在最上层
            g.drawString(this.font, countText, x + 17 - this.font.width(countText), y + 9, 0xFFFFFF, true);
            g.pose().popPose();
        }

        // 4. 画耐久条
        if (stack.isBarVisible()) {
            RenderSystem.disableBlend();
            int barWidth = stack.getBarWidth();
            int barColor = stack.getBarColor();
            g.fill(x + 2, y + 13, x + 2 + 13, y + 15, 0xFF000000);
            g.fill(x + 2, y + 13, x + 2 + barWidth, y + 15, 0xFF000000 | barColor);
            RenderSystem.enableBlend();
        }
    }
}
