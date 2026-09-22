package com.yourname.yellowduck.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家物品/经验的崩服安全发放器。
 *
 * 核心规则：
 * 1. 每批发放都有唯一 batchId。
 * 2. 真正写入背包/经验后，把领取凭证写进玩家 PersistentData。
 *    背包、经验和该凭证会一起进入玩家 NBT。
 * 3. 当前运行期间不立刻从 DungeonSavedData 删除该批记录。
 *    即使服务端在玩家数据与世界 SavedData 之间崩溃，也仍可通过领取凭证判断是否已经发过。
 * 4. 下次服务器进程重新启动后，旧领取凭证会用于安全确认并清理 SavedData 中的待发批次。
 * 5. 普通 deliver() 仍保持“整批必须能放入背包”的旧安全规则，供副本死亡物品恢复使用。
 * 6. 副本通关奖励使用 deliverOrDropOverflow()：能放入背包的先放入，放不下的直接生成在玩家脚下。
 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SafePlayerDelivery {
    private static final String RECEIPT_ROOT = "yellowduck_delivery_receipts";
    private static final int MAIN_INVENTORY_SIZE = 36;

    private static final Set<String> ISSUED_THIS_RUNTIME = ConcurrentHashMap.newKeySet();

    private SafePlayerDelivery() {
    }

    public enum DeliveryResult {
        GRANTED,
        GRANTED_WITH_DROPS,
        ALREADY_RECEIVED,
        NO_SPACE
    }

    /**
     * 原有的整批安全发放。
     * 主要给副本死亡物品恢复使用：背包放不下时整批不动，也不丢到副本地面。
     */
    public static DeliveryResult deliver(ServerPlayer player, String namespace, UUID batchId,
                                         List<ItemStack> items, int xp) {
        if (player == null || batchId == null) {
            return DeliveryResult.ALREADY_RECEIVED;
        }

        String key = receiptKey(namespace, batchId);
        // 当前 JVM 内刚发过、但玩家尚未落盘时也必须视为已领取。
        // 这样玩家死亡 Clone、断线重连等过程中即使 PersistentData 暂时变化，也不会同服重复发放。
        if (hasReceipt(player, key) || ISSUED_THIS_RUNTIME.contains(key)) {
            return DeliveryResult.ALREADY_RECEIVED;
        }

        List<ItemStack> planned = planMainInventory(player, items);
        if (planned == null) {
            return DeliveryResult.NO_SPACE;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            inventory.setItem(i, planned.get(i));
        }
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }

        markReceipt(player, key);
        ISSUED_THIS_RUNTIME.add(key);

        inventory.setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        return DeliveryResult.GRANTED;
    }

    /**
     * 副本通关奖励专用：
     * - 不再要求整批奖励全部塞得进 36 格主背包；
     * - 逐个把能放下的奖励加入现有背包，不会重建/覆盖玩家整个背包；
     * - 放不下的剩余物品拆成合法堆叠，直接掉落在玩家脚下；
     * - 同一 batchId 仍使用领取凭证避免同服重复发奖。
     */
    public static DeliveryResult deliverOrDropOverflow(ServerPlayer player, String namespace, UUID batchId,
                                                       List<ItemStack> items, int xp) {
        if (player == null || batchId == null) {
            return DeliveryResult.ALREADY_RECEIVED;
        }

        String key = receiptKey(namespace, batchId);
        if (hasReceipt(player, key) || ISSUED_THIS_RUNTIME.contains(key)) {
            return DeliveryResult.ALREADY_RECEIVED;
        }

        Inventory inventory = player.getInventory();
        boolean droppedAny = false;

        if (items != null) {
            for (ItemStack original : items) {
                if (original == null || original.isEmpty()) {
                    continue;
                }

                ItemStack remaining = original.copy();

                // 使用原版背包插入逻辑：先合并同类堆叠，再使用空格。
                // Inventory#add 会从 remaining 中扣除已经成功放进背包的数量。
                inventory.add(remaining);

                if (!remaining.isEmpty()) {
                    droppedAny = true;
                    dropAtPlayerFeet(player, remaining);
                }
            }
        }

        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }

        // 物品已经全部进入“背包或世界掉落实体”后才写领取凭证。
        markReceipt(player, key);
        ISSUED_THIS_RUNTIME.add(key);

        inventory.setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }

        return droppedAny ? DeliveryResult.GRANTED_WITH_DROPS : DeliveryResult.GRANTED;
    }

    /**
     * 把溢出奖励拆成该物品允许的最大堆叠数，生成在玩家脚边。
     * 不设置 owner 锁，玩家整理背包后可以像普通掉落物一样重新拾取。
     */
    private static void dropAtPlayerFeet(ServerPlayer player, ItemStack overflow) {
        ItemStack remaining = overflow.copy();

        while (!remaining.isEmpty()) {
            int max = Math.max(1, remaining.getMaxStackSize());
            int move = Math.min(max, remaining.getCount());

            ItemStack piece = remaining.copy();
            piece.setCount(move);
            remaining.shrink(move);

            ItemEntity entity = new ItemEntity(
                    player.level(),
                    player.getX(),
                    player.getY() + 0.20D,
                    player.getZ(),
                    piece
            );
            entity.setPickUpDelay(10);

            // 只给一点点散开速度，避免几十个堆叠完全重合看不见。
            double dx = (player.getRandom().nextDouble() - 0.5D) * 0.12D;
            double dz = (player.getRandom().nextDouble() - 0.5D) * 0.12D;
            entity.setDeltaMovement(dx, 0.12D, dz);

            player.level().addFreshEntity(entity);
        }
    }

    public static boolean canAcknowledge(ServerPlayer player, String namespace, UUID batchId) {
        if (player == null || batchId == null) {
            return false;
        }
        String key = receiptKey(namespace, batchId);
        return hasReceipt(player, key) && !ISSUED_THIS_RUNTIME.contains(key);
    }

    private static List<ItemStack> planMainInventory(ServerPlayer player, List<ItemStack> incoming) {
        Inventory inventory = player.getInventory();
        List<ItemStack> planned = new ArrayList<>(MAIN_INVENTORY_SIZE);
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            planned.add(inventory.getItem(i).copy());
        }

        if (incoming == null || incoming.isEmpty()) {
            return planned;
        }

        for (ItemStack original : incoming) {
            if (original == null || original.isEmpty()) {
                continue;
            }

            ItemStack remaining = original.copy();
            int max = Math.max(1, Math.min(inventory.getMaxStackSize(), remaining.getMaxStackSize()));

            if (max > 1) {
                for (int i = 0; i < MAIN_INVENTORY_SIZE && !remaining.isEmpty(); i++) {
                    ItemStack target = planned.get(i);
                    if (target.isEmpty()
                            || !ItemStack.isSameItemSameTags(target, remaining)) {
                        continue;
                    }

                    int limit = Math.max(1, Math.min(target.getMaxStackSize(), max));
                    if (target.getCount() >= limit) {
                        continue;
                    }

                    int move = Math.min(limit - target.getCount(), remaining.getCount());
                    target.grow(move);
                    remaining.shrink(move);
                }
            }

            for (int i = 0; i < MAIN_INVENTORY_SIZE && !remaining.isEmpty(); i++) {
                if (!planned.get(i).isEmpty()) {
                    continue;
                }

                int move = Math.min(max, remaining.getCount());
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                planned.set(i, placed);
                remaining.shrink(move);
            }

            if (!remaining.isEmpty()) {
                return null;
            }
        }

        return planned;
    }

    /**
     * 玩家死亡/重生会创建新的 ServerPlayer 实例。
     * 领取凭证必须跟着复制，否则同一服务端运行期间死亡后可能再次领取同一批奖励。
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        if (!original.contains(RECEIPT_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        event.getEntity().getPersistentData().put(
                RECEIPT_ROOT,
                original.getCompound(RECEIPT_ROOT).copy()
        );
    }

    private static String receiptKey(String namespace, UUID batchId) {
        String ns = namespace == null ? "default" : namespace.trim().toLowerCase(Locale.ROOT);
        if (ns.isEmpty()) {
            ns = "default";
        }
        return ns + ":" + batchId;
    }

    private static boolean hasReceipt(ServerPlayer player, String key) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(RECEIPT_ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        return persistent.getCompound(RECEIPT_ROOT).contains(key, Tag.TAG_LONG);
    }

    private static void markReceipt(ServerPlayer player, String key) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag receipts = persistent.contains(RECEIPT_ROOT, Tag.TAG_COMPOUND)
                ? persistent.getCompound(RECEIPT_ROOT)
                : new CompoundTag();
        receipts.putLong(key, System.currentTimeMillis());
        persistent.put(RECEIPT_ROOT, receipts);
    }
}
