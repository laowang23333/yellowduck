package com.yourname.yellowduck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class CountHelper {
    public static final String REAL_COUNT_KEY = "BigChestRealCount";
    public static final int MAX_REAL = 256;

    private CountHelper() {}

    /** 存储时压缩：count > 64 → count = 1 + NBT 记录真实数量 */
    public static ItemStack compress(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() <= 64) {
            return stack;
        }
        ItemStack copy = stack.copy();
        CompoundTag tag = copy.getOrCreateTag();
        tag.putInt(REAL_COUNT_KEY, stack.getCount());
        copy.setCount(1);
        return copy;
    }

    /** 读真实数量（用于显示） */
    public static int getRealCount(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(REAL_COUNT_KEY)) {
            return tag.getInt(REAL_COUNT_KEY);
        }
        return stack.getCount();
    }

    /** 是否是压缩堆叠 */
    public static boolean isCompressed(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag()
                && stack.getTag().contains(REAL_COUNT_KEY);
    }
}
