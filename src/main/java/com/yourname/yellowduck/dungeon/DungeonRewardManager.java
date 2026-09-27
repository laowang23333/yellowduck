package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.util.SafePlayerDelivery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 副本统一奖励：服务端Roll、配置预览、持久化邮箱、经验平分。 */
public final class DungeonRewardManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern RULE = Pattern.compile("\\[([^\\]]+)]");

    /**
     * 本次服务端运行期间已经从邮箱成功领取的批次。
     * SavedData 会继续暂存到玩家领取凭证跨重启确认成功，避免崩服窗口复制；
     * UI/开本判断则立刻把这些批次视为已领取。
     */
    private static final Set<UUID> MAILBOX_CLAIMED_THIS_RUNTIME = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<ItemStack>> CONFIG_PREVIEW_CACHE = new ConcurrentHashMap<>();

    private DungeonRewardManager() {}

    public static List<ItemStack> roll(DungeonInstance instance) {
        List<ItemStack> out = new ArrayList<>();
        Matcher matcher = RULE.matcher(instance.definition.itemRules() == null ? "" : instance.definition.itemRules());
        while (matcher.find()) {
            String[] p = matcher.group(1).split("\\|");
            if (p.length != 4) continue;
            try {
                ResourceLocation id = new ResourceLocation(p[0].trim().toLowerCase(Locale.ROOT));
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null || item == Items.AIR) continue;
                int min = Math.max(1, Integer.parseInt(p[1].trim()));
                int max = Math.max(min, Integer.parseInt(p[2].trim()));
                double chance = Math.max(0D, Math.min(1D, Double.parseDouble(p[3].trim())));
                if (Math.random() > chance) continue;
                int count = min + (int) (Math.random() * (max - min + 1));
                while (count > 0) {
                    int n = Math.min(count, item.getMaxStackSize());
                    out.add(new ItemStack(item, n));
                    count -= n;
                }
            } catch (Exception ex) {
                LOGGER.warn("副本奖励条目无法解析：{}", matcher.group());
            }
        }
        return out;
    }

    /**
     * 只用于组队界面的“奖励预览”。
     * 读取配置文件 item 规则并显示物品种类，不提前 Roll 数量和概率。
     */
    public static List<ItemStack> configuredPreview(DungeonDefinition definition, int limit) {
        if (definition == null || limit <= 0) return List.of();
        String rules = definition.itemRules();
        if (rules == null || rules.isBlank()) return List.of();

        String cacheKey = definition.id() + "\u0000" + rules;
        List<ItemStack> cached = CONFIG_PREVIEW_CACHE.computeIfAbsent(cacheKey, key -> {
            Map<ResourceLocation, ItemStack> unique = new LinkedHashMap<>();
            Matcher matcher = RULE.matcher(rules);
            while (matcher.find()) {
                String[] p = matcher.group(1).split("\\|");
                if (p.length != 4) continue;
                try {
                    ResourceLocation id = new ResourceLocation(p[0].trim().toLowerCase(Locale.ROOT));
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item == null || item == Items.AIR || unique.containsKey(id)) continue;
                    ItemStack stack = new ItemStack(item);
                    stack.setCount(1);
                    unique.put(id, stack);
                } catch (Exception ignored) {
                    // 配置解析错误由真正 Roll 时统一打印警告；GUI 预览只跳过非法条目。
                }
            }
            return new ArrayList<>(unique.values());
        });

        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < cached.size() && i < limit; i++) out.add(cached.get(i).copy());
        return out;
    }

    /**
     * 通关时把经验和物品拆成不同批次。
     * 经验仍按原规则自动发放；真正的物品奖励只进入物品接收者的待领取邮箱。
     */
    public static void stage(DungeonInstance instance, MinecraftServer server) {
        if (instance.rewardStaged) return;
        instance.rewardStaged = true;

        DungeonSavedData data = DungeonSavedData.get(server);
        List<UUID> eligible = new ArrayList<>(instance.rewardEligible);
        int totalXp = Math.max(0, instance.definition.experience());
        int each = eligible.isEmpty() ? 0 : totalXp / eligible.size();
        int remainder = eligible.isEmpty() ? 0 : totalXp % eligible.size();

        UUID itemRecipient = instance.rewardEligible.contains(instance.leaderId)
                ? instance.leaderId
                : (eligible.isEmpty() ? null : eligible.get(0));

        // 经验批次没有物品，因此不会进入邮箱，也不会阻止开启下一场副本。
        for (int i = 0; i < eligible.size(); i++) {
            UUID playerId = eligible.get(i);
            int xp = each + (i < remainder ? 1 : 0);
            if (xp > 0) {
                data.addPendingReward(playerId, xpBatchId(instance.id, playerId), List.of(), xp);
            }
        }

        // 物品只进入接收者邮箱。没有 Roll 到物品时不创建空邮箱。
        if (itemRecipient != null && !instance.rolledRewards.isEmpty()) {
            data.addPendingReward(itemRecipient, mailboxBatchId(instance.id, itemRecipient),
                    copy(instance.rolledRewards), 0);
        }
    }

    /**
     * 通关结算界面。
     * 只展示本场实际 Roll 的奖励和战斗统计，副本内没有任何领取入口。
     */
    public static void openPreview(ServerPlayer player, DungeonInstance instance) {
        List<UUID> eligible = new ArrayList<>(instance.rewardEligible);
        int totalXp = Math.max(0, instance.definition.experience());
        int personalXp = 0;
        int index = eligible.indexOf(player.getUUID());
        if (index >= 0 && !eligible.isEmpty()) {
            int each = totalXp / eligible.size();
            int remainder = totalXp % eligible.size();
            personalXp = each + (index < remainder ? 1 : 0);
        }

        UUID itemRecipient = instance.rewardEligible.contains(instance.leaderId)
                ? instance.leaderId
                : (eligible.isEmpty() ? null : eligible.get(0));
        String recipientName = itemRecipient == null ? "" : resolvePlayerName(player, itemRecipient);
        boolean isRecipient = itemRecipient != null && itemRecipient.equals(player.getUUID());

        final int xpForViewer = personalXp;
        final String recipient = recipientName;
        final List<RewardMenu.CombatRow> rows = buildCombatRows(player, instance);
        final List<ItemStack> displayRewards = compactDisplayRewards(instance.rolledRewards);
        final int fightSeconds = Math.max(0, instance.fightTicks / 20);
        final int previewSeconds = Math.max(0,
                instance.definition.rewardPreviewSeconds() - Math.max(0, instance.stateTicks / 20));
        final String dungeonId = instance.definition.id();
        final String bossEntityId = instance.definition.bossEntity();

        NetworkHooks.openScreen(player,
                new SimpleMenuProvider(
                        (id, inv, p) -> new RewardMenu(id, inv,
                                instance.definition.displayName(), dungeonId, bossEntityId,
                                displayRewards, totalXp, xpForViewer, recipient, isRecipient, false,
                                fightSeconds, previewSeconds, rows),
                        Component.literal("副本结算")),
                buf -> RewardMenu.writeOpenData(buf,
                        instance.definition.displayName(), dungeonId, bossEntityId,
                        displayRewards, totalXp, xpForViewer, recipient, isRecipient, false,
                        fightSeconds, previewSeconds, rows));
    }

    /** 打开玩家自己的待领取邮箱。 */
    public static void openMailbox(ServerPlayer player) {
        if (player == null) return;
        if (player.level().dimension().equals(DungeonManager.DUNGEON_LEVEL)) {
            player.sendSystemMessage(Component.literal("§e请先离开副本，再领取待领取邮箱中的奖励。"));
            return;
        }

        List<ItemStack> items = mailboxItems(player, 54);
        if (items.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7当前没有待领取的副本物品奖励。"));
            return;
        }

        NetworkHooks.openScreen(player,
                new SimpleMenuProvider(
                        (id, inv, p) -> new RewardMenu(id, inv,
                                "待领取邮箱", "", "", items,
                                0, 0, player.getGameProfile().getName(), true, true,
                                0, 0, List.of()),
                        Component.literal("待领取邮箱")),
                buf -> RewardMenu.writeOpenData(buf,
                        "待领取邮箱", "", "", items,
                        0, 0, player.getGameProfile().getName(), true, true,
                        0, 0, List.of()));
    }

    /**
     * 领取邮箱中的全部物品批次。
     * 背包能放下的进背包，放不下的由 SafePlayerDelivery 安全掉在玩家脚下。
     */
    public static boolean claimMailbox(ServerPlayer player) {
        if (player == null) return false;
        if (player.level().dimension().equals(DungeonManager.DUNGEON_LEVEL)) {
            player.sendSystemMessage(Component.literal("§c副本世界中不能领取邮箱奖励，请返回正常世界后再领取。"));
            return false;
        }

        DungeonSavedData data = DungeonSavedData.get(player.server);
        List<DungeonSavedData.PendingReward> batches = data.pendingRewards(player.getUUID());
        boolean found = false;
        boolean granted = false;
        boolean droppedOverflow = false;
        boolean failed = false;

        for (DungeonSavedData.PendingReward pending : batches) {
            if (pending.items().isEmpty()) continue;

            if (MAILBOX_CLAIMED_THIS_RUNTIME.contains(pending.batchId())) continue;
            if (SafePlayerDelivery.canAcknowledge(player, "dungeon_reward", pending.batchId())) {
                data.acknowledgePendingReward(player.getUUID(), pending.batchId());
                continue;
            }

            found = true;
            SafePlayerDelivery.DeliveryResult result = SafePlayerDelivery.deliverOrDropOverflow(
                    player,
                    "dungeon_reward",
                    pending.batchId(),
                    pending.items(),
                    pending.xp()
            );

            if (result == SafePlayerDelivery.DeliveryResult.GRANTED) {
                granted = true;
                MAILBOX_CLAIMED_THIS_RUNTIME.add(pending.batchId());
            } else if (result == SafePlayerDelivery.DeliveryResult.GRANTED_WITH_DROPS) {
                granted = true;
                droppedOverflow = true;
                MAILBOX_CLAIMED_THIS_RUNTIME.add(pending.batchId());
            } else if (result == SafePlayerDelivery.DeliveryResult.ALREADY_RECEIVED) {
                MAILBOX_CLAIMED_THIS_RUNTIME.add(pending.batchId());
            } else {
                failed = true;
            }
        }

        if (!found && !hasMailboxRewards(player)) {
            player.sendSystemMessage(Component.literal("§7当前没有待领取的副本物品奖励。"));
            return true;
        }
        if (granted) {
            player.sendSystemMessage(Component.literal("§a副本邮箱奖励已领取。"));
        }
        if (droppedOverflow) {
            player.sendSystemMessage(Component.literal(
                    "§e背包空间不足，放不下的奖励已经安全掉落在你脚下，请及时拾取。"));
        }
        if (failed) {
            player.sendSystemMessage(Component.literal(
                    "§c部分奖励暂时无法安全发放，仍保存在邮箱中，请稍后重试。"));
        }
        return !failed;
    }

    /** 当前玩家是否还有会阻止下一场副本的物品邮箱。 */
    public static boolean hasMailboxRewards(ServerPlayer player) {
        if (player == null) return false;
        DungeonSavedData data = DungeonSavedData.get(player.server);
        for (DungeonSavedData.PendingReward pending : data.pendingRewards(player.getUUID())) {
            if (pending.items().isEmpty()) continue;
            if (MAILBOX_CLAIMED_THIS_RUNTIME.contains(pending.batchId())) continue;
            if (SafePlayerDelivery.canAcknowledge(player, "dungeon_reward", pending.batchId())) {
                data.acknowledgePendingReward(player.getUUID(), pending.batchId());
                continue;
            }
            return true;
        }
        return false;
    }

    /** UI 左下角显示的待领取物品堆数。 */
    public static int mailboxStackCount(ServerPlayer player) {
        return mailboxItems(player, Integer.MAX_VALUE).size();
    }

    /** UI/邮箱预览用，不改变 SavedData 中的真实物品。 */
    public static List<ItemStack> mailboxItems(ServerPlayer player, int maxStacks) {
        if (player == null || maxStacks <= 0) return List.of();
        DungeonSavedData data = DungeonSavedData.get(player.server);
        List<ItemStack> out = new ArrayList<>();
        for (DungeonSavedData.PendingReward pending : data.pendingRewards(player.getUUID())) {
            if (pending.items().isEmpty()) continue;
            if (MAILBOX_CLAIMED_THIS_RUNTIME.contains(pending.batchId())) continue;
            if (SafePlayerDelivery.canAcknowledge(player, "dungeon_reward", pending.batchId())) {
                data.acknowledgePendingReward(player.getUUID(), pending.batchId());
                continue;
            }
            for (ItemStack stack : pending.items()) {
                if (stack == null || stack.isEmpty()) continue;
                out.add(stack.copy());
                if (out.size() >= maxStacks) return out;
            }
        }
        return out;
    }

    private static String resolvePlayerName(ServerPlayer viewer, UUID uuid) {
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        if (viewer.server.getProfileCache() != null) {
            var cached = viewer.server.getProfileCache().get(uuid);
            if (cached.isPresent() && cached.get().getName() != null) return cached.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private static List<RewardMenu.CombatRow> buildCombatRows(ServerPlayer viewer, DungeonInstance instance) {
        List<RewardMenu.CombatRow> rows = new ArrayList<>();
        for (UUID uuid : instance.participants) {
            rows.add(new RewardMenu.CombatRow(
                    uuid,
                    resolvePlayerName(viewer, uuid),
                    stat(instance.damageDealt, uuid),
                    stat(instance.healingDone, uuid),
                    stat(instance.damageTaken, uuid)
            ));
        }
        rows.sort(Comparator
                .comparingDouble(RewardMenu.CombatRow::total).reversed()
                .thenComparing(RewardMenu.CombatRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static double stat(Map<UUID, Double> map, UUID uuid) {
        double value = map.getOrDefault(uuid, 0.0D);
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }


    /** 结算界面把同物品同NBT的多堆合并，只改变显示，不改变邮箱里的真实奖励堆。 */
    private static List<ItemStack> compactDisplayRewards(List<ItemStack> rewards) {
        List<ItemStack> out = new ArrayList<>();
        if (rewards == null) return out;
        for (ItemStack stack : rewards) {
            if (stack == null || stack.isEmpty()) continue;
            ItemStack same = null;
            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    same = existing;
                    break;
                }
            }
            if (same == null) {
                out.add(stack.copy());
            } else {
                long total = (long) same.getCount() + stack.getCount();
                same.setCount((int) Math.min(Integer.MAX_VALUE, total));
            }
        }
        return out;
    }

    /**
     * 副本正常关闭/登录/重生时只自动发经验批次。
     * 含物品的批次永久留在邮箱，直到玩家主动点击领取。
     */
    public static void deliver(DungeonInstance instance, MinecraftServer server) {
        if (instance.rewardDelivered) return;
        instance.rewardDelivered = true;
        Set<UUID> recipients = new LinkedHashSet<>(instance.rewardEligible);
        for (UUID uuid : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.isAlive() && !player.level().dimension().equals(DungeonManager.DUNGEON_LEVEL)) {
                deliverPending(player);
            }
        }
    }

    public static void deliverPending(ServerPlayer player) {
        DungeonSavedData data = DungeonSavedData.get(player.server);
        List<DungeonSavedData.PendingReward> batches = data.pendingRewards(player.getUUID());
        if (batches.isEmpty()) return;

        boolean grantedXp = false;

        for (DungeonSavedData.PendingReward pending : batches) {
            // 物品批次只允许邮箱主动领取。
            if (!pending.items().isEmpty()) {
                if (SafePlayerDelivery.canAcknowledge(player, "dungeon_reward", pending.batchId())) {
                    data.acknowledgePendingReward(player.getUUID(), pending.batchId());
                }
                continue;
            }

            SafePlayerDelivery.DeliveryResult result = SafePlayerDelivery.deliverOrDropOverflow(
                    player,
                    "dungeon_reward",
                    pending.batchId(),
                    List.of(),
                    pending.xp()
            );

            if (result == SafePlayerDelivery.DeliveryResult.GRANTED) {
                grantedXp = pending.xp() > 0 || grantedXp;
            } else if (SafePlayerDelivery.canAcknowledge(
                    player, "dungeon_reward", pending.batchId())) {
                data.acknowledgePendingReward(player.getUUID(), pending.batchId());
            }
        }

        if (grantedXp) {
            player.sendSystemMessage(Component.literal("§a已发放本次副本经验奖励。"));
        }
        if (hasMailboxRewards(player)) {
            player.sendSystemMessage(Component.literal("§6副本物品奖励已进入待领取邮箱，请在副本组队界面领取。"));
        }
    }

    private static UUID xpBatchId(UUID instanceId, UUID playerId) {
        String source = "yellowduck:dungeon_reward_xp:" + instanceId + ":" + playerId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID mailboxBatchId(UUID instanceId, UUID playerId) {
        String source = "yellowduck:dungeon_reward_mailbox:" + instanceId + ":" + playerId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static List<ItemStack> copy(List<ItemStack> list) {
        List<ItemStack> out = new ArrayList<>();
        if (list != null) {
            for (ItemStack stack : list) {
                if (stack != null && !stack.isEmpty()) out.add(stack.copy());
            }
        }
        return out;
    }
}
