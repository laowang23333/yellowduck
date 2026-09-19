package com.yourname.yellowduck.silk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;

/** 通过 NetCraft 血条渲染事件绘制斯尔克，不依赖 NetCraft 本体编译包。 */
public final class SilkNetcraftHud {
    private SilkNetcraftHud() {}
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SilkBoss boss = mc.level.getEntitiesOfClass(SilkBoss.class, mc.player.getBoundingBox().inflate(64), e -> e.isAlive())
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (boss == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int x = width / 2 - 214, y = 20, barW = 427, barH = 18;
        float pct = Math.max(0, Math.min(1, boss.getHealth() / boss.getMaxHealth()));
        int color = boss.phase() == 1 ? 0xff8bd11c : boss.phase() == 2 ? 0xffffa51c : 0xffd73c4a;
        g.fill(x, y, x + barW, y + barH, 0xff3a2112);
        g.fill(x + 2, y + 2, x + 2 + (int)((barW - 4) * pct), y + barH - 2, color);
        g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE), x - 42, y - 7);
        String title = "疯狂教授斯尔克 · 第" + boss.phase() + "阶段";
        g.drawCenteredString(mc.font, Component.literal(title), width / 2, y - 15, 0xffffff);
        g.drawCenteredString(mc.font, Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", pct * 100)), width / 2, y + 4, 0xffffff);
    }
}
