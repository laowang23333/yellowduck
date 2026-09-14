package com.yourname.yellowduck.client;

import com.yourname.yellowduck.CountHelper;
import com.yourname.yellowduck.menu.BigChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 渲染前：把压缩堆叠（count=1 + NBT）临时展开成真实数量，让原版 renderSlot 能画出来
        List<ItemStack> expanded = new ArrayList<>();
        List<Integer> savedCounts = new ArrayList<>();

        for (Slot slot : this.menu.slots) {
            ItemStack stack = slot.getItem();
            if (CountHelper.isCompressed(stack)) {
                expanded.add(stack);
                savedCounts.add(stack.getCount());
                stack.setCount(CountHelper.getRealCount(stack));
            }
        }

        try {
            super.render(g, mouseX, mouseY, partialTick);
        } finally {
            // 渲染后立刻还原，避免污染同步/交互
            for (int i = 0; i < expanded.size(); i++) {
                expanded.get(i).setCount(savedCounts.get(i));
            }
        }
    }
}
