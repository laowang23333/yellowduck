package com.yourname.yellowduck.client.dungeon;

import com.yourname.yellowduck.dungeon.RewardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 自定义副本奖励展示。使用 dungeon_reward.png，美术展示与真实奖励数据分离。 */
public final class RewardScreen extends AbstractContainerScreen<RewardMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("yellowduck", "textures/gui/party/dungeon_reward.png");
    private static final int W = 352;
    private static final int H = 418;
    private static final int COLS = 5;
    private static final int ROWS = 2;
    private static final int MAX_VISIBLE = COLS * ROWS;

    private float scale = 1.0F;
    private float ox;
    private float oy;

    public RewardScreen(RewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private void transform() {
        scale = Math.min(1.0F, Math.min((width - 16F) / W, (height - 16F) / H));
        scale = Math.max(.4F, scale);
        ox = (width - W * scale) / 2F;
        oy = (height - H * scale) / 2F;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        transform();
        float mx = (mouseX - ox) / scale;
        float my = (mouseY - oy) / scale;

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(TEXTURE, 0, 0, W, H, 0, 0, W, H, W, H);

        // 清掉视觉稿中的示例奖励，保留槽位边框和整体皮肤。
        g.fill(38, 91, 316, 251, 0xE7E1E3E5);
        if (menu.rewards().isEmpty()) {
            g.drawCenteredString(font, "本次没有获得物品奖励", W / 2, 166, 0xFF666666);
        } else {
            for (int i = 0; i < menu.rewards().size() && i < MAX_VISIBLE; i++) {
                int col = i % COLS;
                int row = i / COLS;
                int sx = 47 + col * 55;
                int sy = 103 + row * 67;
                // 重新绘制槽位底色，让实际物品不和设计稿示例图标叠在一起。
                g.fill(sx - 7, sy - 7, sx + 34, sy + 34, 0xFF8A8D90);
                g.fill(sx - 5, sy - 5, sx + 32, sy + 32, 0xFFE8E9EA);
                renderLargeItem(g, menu.rewards().get(i), sx, sy);
            }
            if (menu.rewards().size() > MAX_VISIBLE) {
                g.drawCenteredString(font, "另有 " + (menu.rewards().size() - MAX_VISIBLE) + " 组奖励已记录并照常发放",
                        W / 2, 249, 0xFF666666);
            }
        }

        // 清掉设计稿中的示例说明，写入服务端真实结算数据。
        g.fill(39, 273, 318, 349, 0xEBE4E6E7);
        String itemLine = menu.itemRecipientName().isBlank()
                ? "本次没有符合资格的物品接收者"
                : (menu.isItemRecipient() ? "物品奖励将在离本后发给你" : "物品奖励接收者：" + menu.itemRecipientName());
        g.drawString(font, itemLine, 48, 286, 0xFF303030, false);
        g.drawString(font, "副本总经验：" + menu.totalXp(), 48, 308, 0xFF303030, false);
        g.drawString(font, "你获得经验：" + menu.personalXp(), 48, 330, 0xFF168B36, false);

        g.pose().popPose();

        // 原版 Item Tooltip：奖励仍然只是渲染，不存在真正可拿取的 Slot。
        for (int i = 0; i < menu.rewards().size() && i < MAX_VISIBLE; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int sx = 47 + col * 55;
            int sy = 103 + row * 67;
            if (mx >= sx - 8 && mx < sx + 35 && my >= sy - 8 && my < sy + 35) {
                g.renderTooltip(font, menu.rewards().get(i), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderLargeItem(GuiGraphics g, ItemStack stack, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.55F, 1.55F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.renderItemDecorations(font, stack, 0, 0);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = (float) ((mouseX - ox) / scale);
        float my = (float) ((mouseY - oy) / scale);
        // dungeon_reward.png 的关闭按钮区域。
        if (mx >= 128 && mx < 286 && my >= 355 && my < 409) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            onClose();
            return true;
        }
        return true;
    }
}
