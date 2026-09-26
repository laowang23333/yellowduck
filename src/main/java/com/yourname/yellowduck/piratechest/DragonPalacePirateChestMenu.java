package com.yourname.yellowduck.piratechest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

public final class DragonPalacePirateChestMenu extends AbstractContainerMenu {
    public static final int COLS = 9;
    public static final int ROWS = 5;
    public static final int WINDOW_SIZE = 45;
    public static final int SIZE = 252;
    public static final int GRID_X = 9;
    public static final int GRID_Y = 18;
    public static final int INV_X = 9;
    public static final int INV_Y = 112;
    public static final int HOTBAR_Y = 170;
    public static final int SCROLL_X = 175;
    public static final int SCROLL_Y = 18;
    public static final int SCROLL_H = 112;

    private final Container container;
    private int[] display = new int[0];
    private int scrollRow;
    private int maxScrollRow;

    private static boolean slotMoveBroken;
    private static final Field SLOT_X = findSlotField("f_40220_");
    private static final Field SLOT_Y = findSlotField("f_40221_");

    public DragonPalacePirateChestMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, containerAt(inv, data.readBlockPos()));
    }

    public DragonPalacePirateChestMenu(int id, Inventory inv, Container container) {
        super(DragonPalacePirateChestContent.DRAGON_PALACE_PIRATE_CHEST_MENU.get(), id);
        checkContainerSize(container, SIZE);
        this.container = container;
        container.startOpen(inv.player);

        for (int i = 0; i < SIZE; i++) {
            boolean visible = i < WINDOW_SIZE;
            addSlot(new WindowSlot(container, i,
                    visible ? GRID_X + (i % COLS) * 18 : -2000,
                    visible ? GRID_Y + (i / COLS) * 18 : -2000,
                    visible));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9,
                        INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }
        display = new int[SIZE];
        for (int i = 0; i < SIZE; i++) display[i] = i;
        applyWindow();
    }

    private static Container containerAt(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof DragonPalacePirateChestBlockEntity chest) return chest;
        return new SimpleContainer(SIZE);
    }

    public void applyFilter(DragonPalacePirateChestTabs category, String query, int requestedScrollRow) {
        display = DragonPalacePirateChestTabs.filter(container, category, query);
        int rows = (display.length + COLS - 1) / COLS;
        maxScrollRow = Math.max(0, rows - ROWS);
        scrollRow = Math.max(0, Math.min(requestedScrollRow, maxScrollRow));
        applyWindow();
    }

    private void applyWindow() {
        for (int i = 0; i < SIZE; i++) hideSlot(slots.get(i));
        int start = scrollRow * COLS;
        for (int visible = 0; visible < WINDOW_SIZE; visible++) {
            int idx = start + visible;
            if (idx >= display.length) break;
            Slot slot = slots.get(display[idx]);
            if (slot instanceof WindowSlot window) window.visible = true;
            moveSlot(slot, GRID_X + (visible % COLS) * 18, GRID_Y + (visible / COLS) * 18);
        }
    }

    private static void hideSlot(Slot slot) {
        if (slot instanceof WindowSlot window) window.visible = false;
        moveSlot(slot, -2000, -2000);
    }

    public int scrollRow() { return scrollRow; }
    public int maxScrollRow() { return maxScrollRow; }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < SIZE) moved = moveItemStackTo(stack, SIZE, SIZE + 36, true);
        else moved = moveItemStackTo(stack, 0, SIZE, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static void moveSlot(Slot slot, int x, int y) {
        if (slotMoveBroken || SLOT_X == null || SLOT_Y == null) return;
        try {
            SLOT_X.setInt(slot, x);
            SLOT_Y.setInt(slot, y);
        } catch (Throwable error) {
            slotMoveBroken = true;
        }
    }

    private static Field findSlotField(String name) {
        try {
            Field f = ObfuscationReflectionHelper.findField(Slot.class, name);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class WindowSlot extends Slot {
        private boolean visible;
        WindowSlot(Container container, int slot, int x, int y, boolean visible) {
            super(container, slot, x, y);
            this.visible = visible;
        }
        @Override public boolean isActive() { return visible; }
    }
}
