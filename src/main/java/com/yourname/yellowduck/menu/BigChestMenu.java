package com.yourname.yellowduck.menu;

import com.yourname.yellowduck.block.BigChestBlockEntity;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BigChestMenu extends AbstractContainerMenu {
    private final Container container;

    public BigChestMenu(int id, Inventory playerInv) {
        this(id, playerInv, new SimpleContainer(BigChestBlockEntity.SIZE));
    }

    public BigChestMenu(int id, Inventory playerInv, Container container) {
        super(ModMenuTypes.BIG_CHEST.get(), id);
        this.container = container;
        container.startOpen(playerInv.player);

        for (int row = 0; row < 6; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new BigSlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));

        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
    }

    @Override public boolean stillValid(Player p) { return container.stillValid(p); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            result = inSlot.copy();
            if (index < 54) {
                if (!moveItemStackTo(inSlot, 54, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(inSlot, 0, 54, false)) return ItemStack.EMPTY;
            }
            if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static class BigSlot extends Slot {
        public BigSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public int getMaxStackSize() { return BigChestBlockEntity.MAX_STACK; }
    }
}
