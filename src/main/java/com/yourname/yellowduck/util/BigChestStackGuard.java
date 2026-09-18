package com.yourname.yellowduck.util;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 海盗箱特殊堆叠的“箱外保险”。
 *
 * 海盗箱内部允许单格 127，但玩家背包/快捷栏/副手/鼠标光标必须始终遵守
 * 物品自己的 getMaxStackSize()。这层保护不依赖 GUI 点击路径，所以即使
 * Mohist/插件/拾取物品等路径把不可堆叠物品临时合成了 x2、x3，也会在
 * 当前服务端 tick 结束时立即拆回正常数量。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BigChestStackGuard {
    private BigChestStackGuard() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }

        normalizeOutsideChest(event.player);
    }

    /**
     * 只整理玩家自身的物品，不会扫描/修改海盗箱 BlockEntity，
     * 因此海盗箱内部的 127 特殊堆叠完全保留。
     */
    public static void normalizeOutsideChest(Player player) {
        boolean changed = false;

        // 鼠标光标也属于“箱外”。
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                int max = normalMax(carried);
                if (carried.getCount() > max) {
                    int overflow = carried.getCount() - max;
                    carried.setCount(max);
                    player.containerMenu.setCarried(carried);

                    ItemStack template = carried.copy();
                    template.setCount(1);
                    putOverflowBackSafely(player, template, overflow);
                    changed = true;
                }
            }
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> overflowStacks = new ArrayList<>();

        // getContainerSize() 包括主背包、盔甲和副手。任何箱外槽位都不能超上限。
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            int max = normalMax(stack);
            if (stack.getCount() <= max) {
                continue;
            }

            int overflow = stack.getCount() - max;
            stack.setCount(max);
            changed = true;

            while (overflow > 0) {
                int amount = Math.min(max, overflow);
                ItemStack split = stack.copy();
                split.setCount(amount);
                overflowStacks.add(split);
                overflow -= amount;
            }
        }

        // 不能用 Inventory#add 做最后兜底，因为当前问题就是某些 Mohist/插件
        // 拾取路径可能把本来不可堆叠的物品重新并到一起。这里自己严格按原版上限放。
        for (ItemStack overflow : overflowStacks) {
            insertStrictOrDrop(player, overflow);
        }

        if (changed || !overflowStacks.isEmpty()) {
            inventory.setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
        }
    }

    private static void putOverflowBackSafely(Player player, ItemStack template, int count) {
        int max = normalMax(template);

        while (count > 0) {
            int amount = Math.min(max, count);
            ItemStack split = template.copy();
            split.setCount(amount);
            insertStrictOrDrop(player, split);
            count -= amount;
        }
    }

    /**
     * 严格放回玩家主背包/快捷栏（槽 0~35）。
     * 不会把物品塞进盔甲槽，也绝不会让某格超过 ItemStack#getMaxStackSize()。
     */
    public static void insertStrictOrDrop(Player player, ItemStack input) {
        if (input.isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack remaining = input.copy();
        int max = normalMax(remaining);

        // 先补已有同类堆。max=1 的镐子/武器自然不会进入这一段。
        if (max > 1) {
            for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
                ItemStack target = inventory.getItem(i);
                if (target.isEmpty()
                        || !ItemStack.isSameItemSameTags(target, remaining)
                        || target.getCount() >= max) {
                    continue;
                }

                int amount = Math.min(max - target.getCount(), remaining.getCount());
                target.grow(amount);
                remaining.shrink(amount);
            }
        }

        // 再找主背包/快捷栏空格，每格严格不超过该物品正常上限。
        for (int i = 0; i < 36 && !remaining.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                continue;
            }

            int amount = Math.min(max, remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(amount);
            inventory.setItem(i, placed);
            remaining.shrink(amount);
        }

        // 背包真的满了就按正常上限一组组丢出，不制造非法大堆 ItemEntity。
        while (!remaining.isEmpty()) {
            int amount = Math.min(max, remaining.getCount());
            ItemStack dropped = remaining.copy();
            dropped.setCount(amount);
            remaining.shrink(amount);
            player.drop(dropped, false);
        }
    }

    private static int normalMax(ItemStack stack) {
        return Math.max(1, stack.getMaxStackSize());
    }
}
