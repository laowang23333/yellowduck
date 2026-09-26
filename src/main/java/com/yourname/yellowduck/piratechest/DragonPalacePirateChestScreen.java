package com.yourname.yellowduck.piratechest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 252 格海盗箱：5x9 可视窗口、滚动、搜索、创造栏分类分页。 */
public final class DragonPalacePirateChestScreen extends AbstractContainerScreen<DragonPalacePirateChestMenu> {
    private static final int PAGE_SIZE = 10;
    private List<DragonPalacePirateChestTabs> categories = List.of();
    private DragonPalacePirateChestTabs activeTab;
    private EditBox searchBox;
    private int page;
    private int scrollRow;
    private boolean dragging;
    private Button prevPage;
    private Button nextPage;
    private long contentSignature = Long.MIN_VALUE;

    public DragonPalacePirateChestScreen(DragonPalacePirateChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 195;
        this.imageHeight = 207;
        this.inventoryLabelY = 101;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (categories.isEmpty() && mc.level != null) {
            categories = DragonPalacePirateChestTabs.build(mc.level.enabledFeatures(), mc.level.registryAccess());
        }
        if (activeTab == null && !categories.isEmpty()) activeTab = categories.get(0);

        searchBox = new EditBox(font, leftPos + 82, topPos + 6, 80, 10, Component.literal("搜索"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setHint(Component.literal("搜索"));
        searchBox.setResponder(value -> { scrollRow = 0; refresh(); });
        addRenderableWidget(searchBox);

        prevPage = Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1); updatePageButtons();
        }).bounds(leftPos, topPos - 44, 20, 20).build();
        nextPage = Button.builder(Component.literal(">"), b -> {
            page = Math.min(pageCount() - 1, page + 1); updatePageButtons();
        }).bounds(leftPos + imageWidth - 20, topPos - 44, 20, 20).build();
        addRenderableWidget(prevPage);
        addRenderableWidget(nextPage);
        updatePageButtons();
        refresh();
        contentSignature = contentSignature();
    }

    private void updatePageButtons() {
        if (prevPage == null || nextPage == null) return;
        boolean many = pageCount() > 1;
        prevPage.visible = many; nextPage.visible = many;
        prevPage.active = page > 0;
        nextPage.active = page < pageCount() - 1;
    }

    private int pageCount() { return Math.max(1, (categories.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private void refresh() {
        menu.applyFilter(activeTab, searchBox == null ? "" : searchBox.getValue(), scrollRow);
        scrollRow = menu.scrollRow();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchBox != null) searchBox.tick();
        long now = contentSignature();
        if (now != contentSignature) {
            contentSignature = now;
            refresh();
        }
    }

    private long contentSignature() {
        long h = 1469598103934665603L;
        for (int i = 0; i < DragonPalacePirateChestMenu.SIZE; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            // 1.20.1 稳定写法：用物品注册 ID + 数量 + NBT 计算签名，
            // 不依赖新版本才有的 ItemStack hash API。
            h ^= net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(s.getItem());
            h *= 1099511628211L;
            h ^= s.getCount();
            h *= 1099511628211L;
            h ^= s.hasTag() ? s.getTag().hashCode() : 0;
            h *= 1099511628211L;
        }
        return h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xE51C2026);
        g.fill(x + 4, y + 4, x + imageWidth - 4, y + 16, 0xFF323842);
        g.drawString(font, Component.literal("🔎"), x + 68, y + 6, 0xFFE0E0E0, false);

        for (int r = 0; r < 5; r++) for (int c = 0; c < 9; c++) slotBg(g, x + 8 + c * 18, y + 17 + r * 18);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slotBg(g, x + 8 + c * 18, y + 111 + r * 18);
        for (int c = 0; c < 9; c++) slotBg(g, x + 8 + c * 18, y + 169);

        int tx = x + DragonPalacePirateChestMenu.SCROLL_X;
        int ty = y + DragonPalacePirateChestMenu.SCROLL_Y;
        g.fill(tx, ty, tx + 12, ty + 95, 0xFF101216);
        int knob = knobY();
        g.fill(tx + 1, knob, tx + 11, knob + 15, menu.maxScrollRow() > 0 ? 0xFFB4BBC6 : 0xFF4B5058);
    }

    private static void slotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF6E7278);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF20242A);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFFFD36A, false);
        g.drawString(font, playerInventoryTitle, 8, 101, 0xFFE0E0E0, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTabs(g, mouseX, mouseY);
        if (pageCount() > 1) {
            String label = (page + 1) + " / " + pageCount();
            g.drawCenteredString(font, label, leftPos + imageWidth / 2, topPos - 39, 0xFFFFFFFF);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        int end = Math.min(categories.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int row = local >= 5 ? 1 : 0;
            int col = local % 5;
            int x = leftPos + 24 + col * 29;
            int y = row == 0 ? topPos - 25 : topPos + imageHeight + 4;
            boolean selected = categories.get(i) == activeTab;
            g.fill(x, y, x + 26, y + 24, selected ? 0xFFB88A3E : 0xFF3B414B);
            g.renderItem(categories.get(i).icon(), x + 5, y + 4);
            if (mouseX >= x && mouseX < x + 26 && mouseY >= y && mouseY < y + 24) {
                g.renderTooltip(font, categories.get(i).name(), mouseX, mouseY);
            }
        }
    }

    private int tabAt(double mouseX, double mouseY) {
        int start = page * PAGE_SIZE;
        int end = Math.min(categories.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int row = local >= 5 ? 1 : 0;
            int col = local % 5;
            int x = leftPos + 24 + col * 29;
            int y = row == 0 ? topPos - 25 : topPos + imageHeight + 4;
            if (mouseX >= x && mouseX < x + 26 && mouseY >= y && mouseY < y + 24) return i;
        }
        return -1;
    }

    private int knobY() {
        int track = 95 - 15;
        if (menu.maxScrollRow() <= 0) return topPos + DragonPalacePirateChestMenu.SCROLL_Y;
        return topPos + DragonPalacePirateChestMenu.SCROLL_Y
                + Math.round(track * (scrollRow / (float) menu.maxScrollRow()));
    }

    private void setScroll(int row) {
        scrollRow = Mth.clamp(row, 0, menu.maxScrollRow());
        refresh();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.maxScrollRow() > 0 && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight) {
            setScroll(scrollRow + (delta < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            activeTab = categories.get(tab);
            scrollRow = 0;
            refresh();
            return true;
        }
        int tx = leftPos + DragonPalacePirateChestMenu.SCROLL_X;
        int ty = topPos + DragonPalacePirateChestMenu.SCROLL_Y;
        if (button == 0 && mouseX >= tx && mouseX < tx + 12 && mouseY >= ty && mouseY < ty + 95) {
            dragging = true;
            setScroll(rowForMouse(mouseY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            setScroll(rowForMouse(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int rowForMouse(double mouseY) {
        if (menu.maxScrollRow() <= 0) return 0;
        double p = (mouseY - (topPos + DragonPalacePirateChestMenu.SCROLL_Y) - 7.5D) / 80.0D;
        return Mth.clamp((int) Math.round(p * menu.maxScrollRow()), 0, menu.maxScrollRow());
    }
}
