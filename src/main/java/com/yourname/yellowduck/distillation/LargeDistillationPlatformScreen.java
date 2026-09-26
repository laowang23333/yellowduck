package com.yourname.yellowduck.distillation;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.ModList;

public final class LargeDistillationPlatformScreen extends AbstractContainerScreen<LargeDistillationPlatformMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("yellowduck", "textures/gui/container/large_distillation_platform.png");

    public LargeDistillationPlatformScreen(LargeDistillationPlatformMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 194;
        imageHeight = 187;
        inventoryLabelY = 145;
    }

    @Override protected void init() {
        super.init();
        if (ModList.get().isLoaded("jei")) leftPos = Math.max(0, leftPos - 40);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 194, 187);

        // 原版界面：先盖住完整火焰/进度箭头，再按实时进度恢复对应区域。
        g.fill(leftPos+110, topPos+61, leftPos+124, topPos+75, 0xFFC6C6C6);
        g.fill(leftPos+132, topPos+60, leftPos+156, topPos+76, 0xFFC6C6C6);

        int burn = menu.getBurnTime(), burnTotal = menu.getBurnTimeTotal();
        if (burn > 0 && burnTotal > 0) {
            int h = scaledProgress(burn, burnTotal, 14);
            if (h > 0) {
                int off = 14-h;
                g.blit(TEXTURE, leftPos+110, topPos+61+off, 110, 61+off, 14, h, 194, 187);
            }
        }
        int cook = menu.getCookTime(), total = menu.getCookTimeTotal();
        if (cook > 0 && total > 0) {
            int w = scaledProgress(cook, total, 24);
            if (w > 0) g.blit(TEXTURE, leftPos+132, topPos+60, 132, 60, w, 16, 194, 187);
        }
    }

    private static int scaledProgress(int value, int total, int pixels) {
        if (value <= 0 || total <= 0) return 0;
        return (int)Math.min(pixels, ((long)pixels * value + total - 1L) / total);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        zone(g, "gui.yellowduck.distillation.zone_crops", 35, 52);
        zone(g, "gui.yellowduck.distillation.zone_bottles", 95, 52);
        zone(g, "gui.yellowduck.distillation.zone_output", 153, 52);
        zone(g, "gui.yellowduck.distillation.zone_fuel", 43, 78);
    }

    private void zone(GuiGraphics g, String key, int cx, int y) {
        Component c = Component.translatable(key);
        g.drawString(font, c, cx-font.width(c)/2, y, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
