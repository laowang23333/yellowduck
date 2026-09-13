package com.yourname.yellowduck.client;

import com.yourname.yellowduck.menu.BigChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BigChestScreen extends AbstractContainerScreen<BigChestMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("yellowduck", "textures/gui/big_chest.png");

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
}
