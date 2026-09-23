package com.yourname.yellowduck.util;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家物品/经验的崩服安全发放器。
 *
 * 安全原则：
 * 1. 每批发放都有唯一 batchId。
 * 2. 背包内容和领取凭证都写在玩家 NBT 中。
 * 3. 当前 JVM 额外记录本次运行已发批次，避免断线/Clone 等路径重复发奖。
 * 4. 通关奖励溢出到世界时，每一个掉落实体使用由 batchId + 序号计算出的确定性 UUID。
 *    如果服务端恰好在“掉落已保存、玩家领取凭证尚未保存”的窗口崩溃，
 *    重启后重放同一批奖励时会复用同一 UUID，已存在的掉落不会再生成第二份。
 * 5. 发放前先在内存中规划最终背包，再生成溢出实体；全部成功后才一次性提交背包/经验/领取凭证。
 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SafePlayerDelivery {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String RECEIPT_ROOT = "yellowduck_delivery_receipts";
    private static final String DROP_BATCH_TAG = "YellowDuckDeliveryBatch";
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
     * 副本通关奖励专用。
     *
     * 与旧实现的关键区别：
     * - 旧实现先真实修改背包、再生成随机 UUID 掉落，最后才写领取凭证；
     * - 现在先在副本内存中规划背包和溢出，再使用确定性 UUID 生成溢出实体；
     * - 全部掉落都已存在后，才提交背包、经验和领取凭证。
     *
     * 因此即使恰好在最危险的崩服窗口重启，同一 batchId 的掉落也不会再复制一套。
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

        DeliveryPlan plan = planMainInventoryWithOverflow(player, items);

        if (!spawnOverflowIdempotently(player, key, plan.overflow())) {
            // 世界/插件拒绝生成掉落时不提交背包也不写领取凭证，
            // 保留 SavedData 中的原奖励，之后仍可安全重试。
            return DeliveryResult.NO_SPACE;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            inventory.setItem(i, plan.mainInventory().get(i));
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

        return plan.overflow().isEmpty()
                ? DeliveryResult.GRANTED
                : DeliveryResult.GRANTED_WITH_DROPS;
    }

    /**
     * 生成溢出掉落。
     *
     * 每个掉落 UUID = nameUUID("yellowduck:reward_drop:" + batchKey + ":" + index)。
     * 同一批奖励无论重试多少次都只对应同一组实体 UUID。
     *
     * 如果本次新建过程中有任意实体被其它 Mod/插件拒绝生成，
     * 会回滚“本次刚生成”的实体并返回 false；之前崩服留下、已经存在的实体不动。
     */
    private static boolean spawnOverflowIdempotently(ServerPlayer player, String batchKey,
                                                      List<ItemStack> overflow) {
        if (overflow == null || overflow.isEmpty()) {
            return true;
        }

        ServerLevel level = player.serverLevel();
        List<ItemEntity> spawnedThisAttempt = new ArrayList<>();

        for (int i = 0; i < overflow.size(); i++) {
            ItemStack piece = overflow.get(i);
            if (piece == null || piece.isEmpty()) {
                continue;
            }

            UUID entityId = overflowEntityId(batchKey, i);
            Entity existing = level.getEntity(entityId);

            if (existing instanceof ItemEntity) {
                // 上次崩服已经把这一份掉落保存进世界：直接视为已生成。
                continue;
            }

            if (existing != null) {
                LOGGER.error("副本奖励确定性掉落 UUID 与其它实体冲突：batch={} uuid={}",
                        batchKey, entityId);
                rollbackSpawned(spawnedThisAttempt);
                return false;
            }

            ItemEntity entity = new ItemEntity(
                    level,
                    player.getX(),
                    player.getY() + 0.20D,
                    player.getZ(),
                    piece.copy()
            );
            entity.setUUID(entityId);
            entity.setPickUpDelay(10);
            entity.getPersistentData().putString(DROP_BATCH_TAG, batchKey);

            double dx = (player.getRandom().nextDouble() - 0.5D) * 0.12D;
            double dz = (player.getRandom().nextDouble() - 0.5D) * 0.12D;
            entity.setDeltaMovement(dx, 0.12D, dz);

            if (!level.addFreshEntity(entity)) {
                // 如果失败原因其实只是“同 UUID 已经存在”，同样视为幂等成功。
                Entity afterFailure = level.getEntity(entityId);
                if (afterFailure instanceof ItemEntity) {
                    continue;
                }

                rollbackSpawned(spawnedThisAttempt);
                LOGGER.warn("副本奖励掉落实体生成被拒绝，整批暂不提交：batch={} uuid={}",
                        batchKey, entityId);
                return false;
            }

            spawnedThisAttempt.add(entity);
        }

        return true;
    }

    private static void rollbackSpawned(List<ItemEntity> spawned) {
        for (ItemEntity entity : spawned) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static UUID overflowEntityId(String batchKey, int index) {
        String source = "yellowduck:reward_drop:" + batchKey + ":" + index;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 在不修改真实背包的情况下，先算出：
     * - 发放完成后的 0~35 主背包；
     * - 放不进去、需要掉地上的合法 ItemStack 列表。
     */
    private static DeliveryPlan planMainInventoryWithOverflow(ServerPlayer player, List<ItemStack> incoming) {
        Inventory inventory = player.getInventory();
        List<ItemStack> planned = new ArrayList<>(MAIN_INVENTORY_SIZE);

        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            planned.add(inventory.getItem(i).copy());
        }

        List<ItemStack> overflow = new ArrayList<>();

        if (incoming != null) {
            for (ItemStack original : incoming) {
                if (original == null || original.isEmpty()) {
                    continue;
                }

                ItemStack remaining = original.copy();
                int max = Math.max(1,
                        Math.min(inventory.getMaxStackSize(), remaining.getMaxStackSize()));

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

                while (!remaining.isEmpty()) {
                    int move = Math.min(max, remaining.getCount());
                    ItemStack piece = remaining.copy();
                    piece.setCount(move);
                    overflow.add(piece);
                    remaining.shrink(move);
                }
            }
        }

        return new DeliveryPlan(planned, overflow);
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

    private record DeliveryPlan(List<ItemStack> mainInventory, List<ItemStack> overflow) {
    }
}
