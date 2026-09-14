package com.yourname.yellowduck.block;

import com.yourname.yellowduck.CountHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BigChestBlockEntity extends BlockEntity implements Container {
    public static final int SIZE = 54;
    public static final int MAX_STACK = 256;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public BigChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public int getContainerSize() { return SIZE; }

    @Override public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // 超 64 的堆叠压缩：count=1 + NBT(BigChestRealCount)，避免网络 byte 截断
        ItemStack stored = CountHelper.compress(stack);
        items.set(slot, stored);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public int getMaxStackSize() { return MAX_STACK; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemStack cur = items.get(slot);
        if (cur.isEmpty()) return true;
        // 用"真实数量"比较，且忽略我们自己的 realCount key
        if (!sameItemIgnoringRealCount(cur, stack)) return false;
        int curReal = CountHelper.getRealCount(cur);
        int addReal = CountHelper.getRealCount(stack);
        return curReal + addReal <= MAX_STACK;
    }

    private static boolean sameItemIgnoringRealCount(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        CompoundTag ta = a.getTag();
        CompoundTag tb = b.getTag();
        if (ta == null && tb == null) return true;
        CompoundTag ca = ta == null ? new CompoundTag() : ta.copy();
        CompoundTag cb = tb == null ? new CompoundTag() : tb.copy();
        ca.remove(CountHelper.REAL_COUNT_KEY);
        cb.remove(CountHelper.REAL_COUNT_KEY);
        return ca.equals(cb);
    }

    @Override public void clearContent() { items.clear(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }
}
