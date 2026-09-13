package com.yourname.yellowduck.menu;

import com.yourname.yellowduck.block.BigChestBlockEntity;
import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BigChestMenu extends AbstractContainerMenu {
    private final Container container;

    // 客户端打开 GUI 时用的（从网络读 BlockPos）
    public BigChestMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        this(id, playerInv, getContainer(playerInv, data));
    }

    private static Container getContainer(Inventory inv, FriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        if (inv.player.level().getBlockEntity(pos) instanceof BigChestBlockEntity be) {
            return be;
        }
        return new SimpleContainer(BigChestBlockEntity.SIZE);
    }

    // 服务端打开 GUI 时用的（直接传 Container）
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
                // 注意：这里改成了自定义的方法 moveItemStackToCustom
                if (!moveItemStackToCustom(inSlot, 54, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // 注意：这里改成了自定义的方法 moveItemStackToCustom
                if (!moveItemStackToCustom(inSlot, 0, 54, false)) return ItemStack.EMPTY;
            }
            if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    // ==========================================
    // 新增：自定义快速移动逻辑，突破原版64限制
    // ==========================================
    protected boolean moveItemStackToCustom(ItemStack stack, int startIndex, int endIndex, boolean reverseOrder) {
        boolean flag = false;
        int i = startIndex;
        if (reverseOrder) {
            i = endIndex - 1;
        }

        if (stack.isStackable()) {
            while (!stack.isEmpty()) {
                if (reverseOrder) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot = this.slots.get(i);
                ItemStack itemstack = slot.getItem();
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameTags(stack, itemstack)) {
                    int j = itemstack.getCount() + stack.getCount();
                    int maxSize = slot.getMaxStackSize(itemstack); // 读取我们 BigSlot 里的 256
                    if (j <= maxSize) {
                        stack.setCount(0);
                        itemstack.setCount(j);
                        slot.setChanged();
                        flag = true;
                    } else if (itemstack.getCount() < maxSize) {
                        stack.shrink(maxSize - itemstack.getCount());
                        itemstack.setCount(maxSize);
                        slot.setChanged();
                        flag = true;
                    }
                }

                if (reverseOrder) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        if (!stack.isEmpty()) {
            if (reverseOrder) {
                i = endIndex - 1;
            } else {
                i = startIndex;
            }

            while (true) {
                if (reverseOrder) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();
                if (itemstack1.isEmpty() && slot1.mayPlace(stack)) {
                    int maxSize = slot1.getMaxStackSize(stack);
                    if (stack.getCount() > maxSize) {
                        slot1.setByPlayer(stack.split(maxSize));
                    } else {
                        slot1.setByPlayer(stack.split(stack.getCount()));
                    }
                    slot1.setChanged();
                    flag = true;
                    break;
                }

                if (reverseOrder) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        return flag;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static class BigSlot extends Slot {
        public BigSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        
        @Override 
        public int getMaxStackSize() { 
            return BigChestBlockEntity.MAX_STACK; 
        }
        
        // 新增：让原版逻辑也能读到正确的最大值
        @Override 
        public int getMaxStackSize(ItemStack stack) { 
            return BigChestBlockEntity.MAX_STACK; 
        }
    }
}
