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

    @Override
    public boolean stillValid(Player p) {
        return container.stillValid(p);
    }

    /**
     * Shift-click 自动搬运。
     *
     * 原版 AbstractContainerMenu.moveItemStackTo() 会把
     * ItemStack.getMaxStackSize()（普通物品通常是 64）也算进合并上限，
     * 所以即使海盗箱 Slot 允许 127，已有 64+ 的箱子堆也不会继续自动合并。
     *
     * 这里对“玩家背包 -> 海盗箱”使用自己的合并逻辑，直接以海盗箱的
     * MAX_STACK=127 为上限。这样 shift-click 可以自动把物品继续并入
     * 64 以上、127 以下的箱内堆。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = sourceSlot.getItem();
        ItemStack result = source.copy();

        if (index < BigChestBlockEntity.SIZE) {
            // 海盗箱 -> 玩家背包：按玩家背包自己的 64 上限拆分。
            if (!moveItemStackTo(source, BigChestBlockEntity.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 -> 海盗箱：一次 Shift+左键要把“整组”尽可能放完。
            // 例如箱内已有 100 个 + 玩家手里 64 个：
            //   第一个箱格变成 127
            //   剩余 37 个自动进入下一个空箱格
            // 而不是把 37 个留在玩家背包。
            if (!movePlayerStackToBigChest(source)) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        return result;
    }

    /**
     * Shift+左键从玩家背包向海盗箱搬运“全部来源数量”。
     *
     * 海盗箱单格上限为 127：
     * 1) 先填充所有同物品且未满的格子；
     * 2) 当前格到 127 后，继续寻找下一个空格；
     * 3) 直到来源 ItemStack 完全清空或海盗箱没有空间。
     */
    private boolean movePlayerStackToBigChest(ItemStack source) {
        boolean moved = false;

        // 第一阶段：把来源全部尽可能并入已有同物品堆。
        for (int i = 0; i < BigChestBlockEntity.SIZE && !source.isEmpty(); i++) {
            Slot targetSlot = slots.get(i);
            ItemStack target = targetSlot.getItem();

            if (target.isEmpty()
                    || !ItemStack.isSameItemSameTags(target, source)
                    || target.getCount() >= BigChestBlockEntity.MAX_STACK
                    || !targetSlot.mayPlace(source)) {
                continue;
            }

            int room = BigChestBlockEntity.MAX_STACK - target.getCount();
            int amount = Math.min(room, source.getCount());

            // 直接增长目标，不经过原版 64 的目标上限。
            target.grow(amount);
            targetSlot.setChanged();
            source.shrink(amount);
            moved = true;
        }

        // 第二阶段：已有堆都填满后，自动开新格子继续放。
        // 例如剩余 37 个，就在下一个空格放入 37；
        // 如果剩余 200 个，则依次放 127 + 73。
        while (!source.isEmpty()) {
            Slot emptySlot = null;

            for (int i = 0; i < BigChestBlockEntity.SIZE; i++) {
                Slot candidate = slots.get(i);
                if (!candidate.hasItem() && candidate.mayPlace(source)) {
                    emptySlot = candidate;
                    break;
                }
            }

            if (emptySlot == null) {
                break;
            }

            int amount = Math.min(BigChestBlockEntity.MAX_STACK, source.getCount());
            ItemStack placed = source.copy();
            placed.setCount(amount);

            // BigSlot 的最大数量就是 127，因此这里不会被截成 64。
            emptySlot.set(placed);
            emptySlot.setChanged();
            source.shrink(amount);
            moved = true;
        }

        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static class BigSlot extends Slot {
        public BigSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return BigChestBlockEntity.MAX_STACK;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return BigChestBlockEntity.MAX_STACK;
        }
    }
}
