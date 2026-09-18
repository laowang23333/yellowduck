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
import net.minecraft.world.inventory.ClickType;
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
     * 数字键/副手交换会绕过普通 removeItem() 的“取出数量”限制。
     * 如果箱内这一格正处于超量堆叠状态，就禁止直接整组交换出去，
     * 避免 127 个物品被原样塞进快捷栏或副手。
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // 先把已经跑到鼠标/玩家背包里的非法大堆拆开。
        // 这一步放在 super.clicked() 之前很重要：否则玩家按 Q、点界面外等操作
        // 可能先把“4 把镐子一组”之类的非法 ItemStack 直接生成成掉落物。
        normalizeExternalStacks(player);

        if (slotId >= 0
                && slotId < BigChestBlockEntity.SIZE
                && clickType == ClickType.SWAP) {
            ItemStack chestStack = slots.get(slotId).getItem();
            if (!chestStack.isEmpty()
                    && chestStack.getCount() > chestStack.getMaxStackSize()) {
                return;
            }
        }

        super.clicked(slotId, button, clickType, player);

        // 所有 GUI 点击完成后再兜底检查一次。
        // 海盗箱允许 127 只存在于前 54 个 BigSlot；鼠标和玩家背包必须恢复
        // ItemStack 自身的正常上限（镐子=1、珍珠=16、普通方块=64）。
        normalizeExternalStacks(player);
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
            // 海盗箱 -> 玩家背包：不能把海盗箱里的“超量堆叠”原样带出去。
            // 必须按该物品自己的原版最大堆叠数拆开：
            //   127 个普通物品 -> 64 + 63
            //   31 个末影珍珠   -> 16 + 15
            //   2 把不可堆叠工具 -> 1 + 1
            if (!moveBigChestStackToPlayer(source)) {
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
     * Shift+左键从海盗箱搬到玩家背包。
     *
     * 海盗箱内部允许 127 堆叠，但这种超量堆叠绝不能进入玩家背包。
     * 因此这里不再依赖原版 moveItemStackTo()，而是显式使用
     * ItemStack#getMaxStackSize() 作为玩家背包中的硬上限。
     */
    private boolean moveBigChestStackToPlayer(ItemStack source) {
        boolean moved = false;
        int vanillaMax = Math.max(1, source.getMaxStackSize());

        // 第一阶段：先补满玩家背包里已有的同类物品堆。
        for (int i = BigChestBlockEntity.SIZE; i < slots.size() && !source.isEmpty(); i++) {
            Slot targetSlot = slots.get(i);
            ItemStack target = targetSlot.getItem();

            if (target.isEmpty()
                    || !ItemStack.isSameItemSameTags(target, source)
                    || !targetSlot.mayPlace(source)) {
                continue;
            }

            int slotLimit = Math.min(vanillaMax, targetSlot.getMaxStackSize(source));
            if (target.getCount() >= slotLimit) {
                continue;
            }

            int amount = Math.min(slotLimit - target.getCount(), source.getCount());
            target.grow(amount);
            source.shrink(amount);
            targetSlot.setChanged();
            moved = true;
        }

        // 第二阶段：再放入空的玩家背包槽，每格都严格不超过原版上限。
        for (int i = slots.size() - 1;
             i >= BigChestBlockEntity.SIZE && !source.isEmpty();
             i--) {
            Slot targetSlot = slots.get(i);

            if (targetSlot.hasItem() || !targetSlot.mayPlace(source)) {
                continue;
            }

            int slotLimit = Math.min(vanillaMax, targetSlot.getMaxStackSize(source));
            if (slotLimit <= 0) {
                continue;
            }

            int amount = Math.min(slotLimit, source.getCount());
            ItemStack placed = source.copy();
            placed.setCount(amount);

            targetSlot.set(placed);
            targetSlot.setChanged();
            source.shrink(amount);
            moved = true;
        }

        return moved;
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

    /**
     * 海盗箱的 127 堆叠只允许存在于海盗箱自己的 54 个槽位。
     * 鼠标光标和玩家背包属于“箱外”，任何超过物品原版上限的 ItemStack
     * 都会立即被拆成正常大小。
     *
     * 例如：
     *  - 4 把镐子 -> 1 + 1 + 1 + 1
     *  - 127 个石头 -> 64 + 63
     *  - 31 个末影珍珠 -> 16 + 15
     */
    private void normalizeExternalStacks(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        normalizeCarriedStack(player);
        normalizePlayerInventory(player);
    }

    /** 把鼠标光标上的非法大堆拆开，只保留一组正常上限，其余放回背包。 */
    private void normalizeCarriedStack(Player player) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }

        int max = Math.max(1, carried.getMaxStackSize());
        if (carried.getCount() <= max) {
            return;
        }

        int overflow = carried.getCount() - max;
        carried.setCount(max);
        setCarried(carried);
        distributeOverflow(player, carried, overflow);
    }

    /** 扫描玩家全部 Inventory 槽位，把任何超过物品自身上限的堆拆开。 */
    private void normalizePlayerInventory(Player player) {
        Inventory inventory = player.getInventory();

        // 先收集所有溢出量，再统一回填。不能边扫描边 add，
        // 否则新加进去的物品可能又被当前循环重复处理。
        java.util.List<ItemStack> overflowStacks = new java.util.ArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            int max = Math.max(1, stack.getMaxStackSize());
            if (stack.getCount() <= max) {
                continue;
            }

            int overflow = stack.getCount() - max;
            stack.setCount(max);
            inventory.setChanged();

            while (overflow > 0) {
                int amount = Math.min(max, overflow);
                ItemStack split = stack.copy();
                split.setCount(amount);
                overflowStacks.add(split);
                overflow -= amount;
            }
        }

        for (ItemStack overflow : overflowStacks) {
            putInInventoryOrDrop(player, overflow);
        }
    }

    /**
     * 将指定溢出数量按该物品自己的正常堆叠上限拆分后放进玩家背包。
     * 背包满时以正常大小的掉落物丢在脚下，绝不生成 4 把镐子一组这种非法实体。
     */
    private void distributeOverflow(Player player, ItemStack template, int count) {
        int max = Math.max(1, template.getMaxStackSize());

        while (count > 0) {
            int amount = Math.min(max, count);
            ItemStack split = template.copy();
            split.setCount(amount);
            putInInventoryOrDrop(player, split);
            count -= amount;
        }
    }

    private void putInInventoryOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        // Inventory#add 会按物品自己的正常最大堆叠数合并/寻找空槽。
        // 这里传入的 stack 本身已经保证 <= getMaxStackSize()。
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void removed(Player player) {
        // 关闭界面前先拆一次，避免 super.removed() 把非法鼠标堆直接塞回背包/丢地上。
        normalizeExternalStacks(player);
        super.removed(player);
        // super.removed() 可能把鼠标物品归还给玩家，再检查一次作为最终保险。
        normalizeExternalStacks(player);
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
