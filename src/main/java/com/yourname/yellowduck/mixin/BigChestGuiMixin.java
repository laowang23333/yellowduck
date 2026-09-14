package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.menu.BigChestMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class BigChestGuiMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Shadow protected abstract void renderSlot(GuiGraphics g, Slot slot);

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void yellowduck$renderBigStack(GuiGraphics g, Slot slot, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!(self.getMenu() instanceof BigChestMenu)) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        int x = slot.x;
        int y = slot.y;

        g.renderItem(stack, x, y);

        // 画数量
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        String countText = String.valueOf(stack.getCount());
        int color = stack.getCount() > 999 ? 0xFF5555 : 0xFFFFFF;
        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, countText,
                x + 17 - mc.font.width(countText),
                y + 9, color, true);
        g.pose().popPose();

        // 耐久条
        if (stack.isBarVisible()) {
            int barWidth = stack.getBarWidth();
            int barColor = stack.getBarColor();
            g.fill(x + 2, y + 13, x + 2 + 13, y + 15, 0xFF000000);
            g.fill(x + 2, y + 13, x + 2 + barWidth, y + 15, 0xFF000000 | barColor);
        }

        ci.cancel();
    }
}
