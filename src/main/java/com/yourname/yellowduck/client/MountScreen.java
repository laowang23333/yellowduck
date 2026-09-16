package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.network.MountNetwork;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** 奶块式通用坐骑收藏界面：只显示已拥有坐骑蛋，右侧直接渲染真实 GLB 实体。 */
public class MountScreen extends Screen {
    private int panelLeft;
    private int panelTop;
    private int listLeft;

    private MountEntity previewEntity;
    private MountCatalog.MountDefinition selected;

    public MountScreen() {
        super(Component.literal("坐骑"));
    }

    @Override
    protected void init() {
        panelLeft = Math.max(8, this.width / 2 - 235);
        panelTop = Math.max(8, this.height / 2 - 135);
        listLeft = panelLeft + 18;

        createPreviewEntity();

        addRenderableWidget(Button.builder(Component.literal("乘骑"), b -> {
            if (selected != null && isOwned(selected.id())) {
                MountNetwork.CHANNEL.sendToServer(new MountNetwork.MountActionPacket(0));
                this.onClose();
            }
        }).bounds(panelLeft + 300, panelTop + 218, 92, 22).build());

        addRenderableWidget(Button.builder(Component.literal("放生"), b -> {
            if (selected != null && isOwned(selected.id())) {
                MountNetwork.CHANNEL.sendToServer(new MountNetwork.MountActionPacket(1));
            }
        }).bounds(panelLeft + 398, panelTop + 218, 92, 22).build());
    }

    private void createPreviewEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            previewEntity = ModEntities.MOUNT.get().create(mc.level);
            if (previewEntity != null) {
                previewEntity.setNoGravity(true);
                previewEntity.setYRot(180.0F);
                previewEntity.yRotO = 180.0F;
                previewEntity.setXRot(0.0F);
                previewEntity.xRotO = 0.0F;
            }
        } catch (Throwable ignored) {
            previewEntity = null;
        }
    }

    @Override
    public void removed() {
        if (previewEntity != null) {
            previewEntity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            previewEntity = null;
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        final int left = panelLeft;
        final int top = panelTop;
        final int right = left + 505;
        final int bottom = top + 250;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF101C38);
        graphics.fill(left, top, right, bottom, 0xF20A1630);
        graphics.fill(left + 1, top + 1, right - 1, top + 34, 0xFF142B55);
        graphics.fill(left + 1, top + 35, left + 248, bottom - 1, 0xC80B1730);
        graphics.fill(left + 249, top + 35, right - 1, bottom - 1, 0xC80D1A38);
        graphics.fill(left, top, right, top + 1, 0xFF6DA8FF);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF6DA8FF);
        graphics.fill(left, top, left + 1, bottom, 0xFF315E9D);
        graphics.fill(right - 1, top, right, bottom, 0xFF315E9D);
        graphics.fill(left + 248, top + 35, left + 249, bottom, 0xFF294E84);

        graphics.drawString(this.font, Component.literal("坐骑"), left + 16, top + 12, 0xFFEAF4FF, false);
        graphics.drawString(this.font, Component.literal("我的坐骑"), left + 18, top + 46, 0xFFBFD9FF, false);
        graphics.drawString(this.font,
                Component.literal("已拥有 " + ownedCount() + "/" + MountCatalog.all().size()),
                left + 145, top + 46, 0xFF748DB2, false);

        if (selected == null) {
            for (MountCatalog.MountDefinition mount : MountCatalog.all()) {
                if (isOwned(mount.id())) {
                    selected = mount;
                    break;
                }
            }
        }

        drawOwnedMounts(graphics, mouseX, mouseY);

        int px = left + 260;
        int py = top + 43;
        graphics.fill(px, py, right - 10, bottom - 43, 0xB30A1530);
        graphics.fill(px, py, right - 10, py + 1, 0xFF315E9D);
        graphics.fill(px, bottom - 44, right - 10, bottom - 43, 0xFF315E9D);
        graphics.drawString(this.font,
                Component.literal(selected == null ? "未选择" : selected.name()),
                px + 12, py + 10, 0xFFF1F6FF, false);
        graphics.drawString(this.font, Component.literal("3D 预览"), right - 68, py + 10, 0xFF8EAAD2, false);

        renderActualGlbPreview(graphics, mouseX, mouseY);

        graphics.drawCenteredString(this.font, Component.literal("拖动鼠标查看模型"),
                px + 120, bottom - 61, 0xFF718AB0);
        graphics.drawString(this.font, Component.literal("点击坐骑蛋可切换预览"),
                left + 18, bottom - 15, 0xFF718AB0, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderActualGlbPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (previewEntity == null || Minecraft.getInstance().level == null || selected == null) return;
        try {
            // 这是 Minecraft 的真实实体 GUI 渲染入口；MountRenderer 内部使用 Polymesh GLB。
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    panelLeft + 380,
                    panelTop + 150,
                    72,
                    mouseX,
                    mouseY,
                    previewEntity
            );
        } catch (Throwable ignored) {
            graphics.drawCenteredString(this.font, Component.literal("3D 模型加载中…"),
                    panelLeft + 380, panelTop + 150, 0xFF9DB6D8);
        }
    }

    private void drawOwnedMounts(GuiGraphics graphics, int mouseX, int mouseY) {
        int slotSize = 68;
        int gap = 8;
        int startY = panelTop + 62;
        int index = 0;

        for (MountCatalog.MountDefinition mount : MountCatalog.all()) {
            if (!isOwned(mount.id())) continue;

            int col = index % 3;
            int row = index / 3;
            int x = listLeft + col * (slotSize + gap);
            int y = startY + row * (slotSize + 22);
            boolean hover = mouseX >= x && mouseX <= x + slotSize
                    && mouseY >= y && mouseY <= y + slotSize;
            boolean active = selected != null && selected.id().equals(mount.id());

            graphics.fill(x, y, x + slotSize, y + slotSize,
                    active ? 0xFF213B68 : 0xFF111F3A);
            graphics.fill(x, y, x + slotSize, y + 2,
                    active ? 0xFFFFC95C : 0xFF294E84);
            if (hover) graphics.fill(x, y, x + slotSize, y + slotSize, 0x442D70B8);

            RenderSystem.enableBlend();
            graphics.blit(mount.eggTexture(), x + 6, y + 5, 0, 0, 56, 56, 56, 56);
            RenderSystem.disableBlend();
            graphics.drawCenteredString(this.font, Component.literal(mount.name()),
                    x + slotSize / 2, y + slotSize + 5,
                    active ? 0xFFEAF4FF : 0xFFB6C9E6);
            index++;
        }
    }

    private int ownedCount() {
        int count = 0;
        for (MountCatalog.MountDefinition mount : MountCatalog.all()) {
            if (isOwned(mount.id())) count++;
        }
        return count;
    }

    private boolean isOwned(String id) {
        return MountClientState.has(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slotSize = 68;
        int gap = 8;
        int startY = panelTop + 62;
        int index = 0;

        for (MountCatalog.MountDefinition mount : MountCatalog.all()) {
            if (!isOwned(mount.id())) continue;

            int col = index % 3;
            int row = index / 3;
            int x = listLeft + col * (slotSize + gap);
            int y = startY + row * (slotSize + 22);
            if (mouseX >= x && mouseX <= x + slotSize
                    && mouseY >= y && mouseY <= y + slotSize) {
                selected = mount;
                return true;
            }
            index++;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
