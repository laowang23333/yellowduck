package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.util.SafePlayerDelivery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 副本统一奖励：服务端Roll、只读预览、持久化待发奖励、经验平分。 */
public final class DungeonRewardManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern RULE = Pattern.compile("\\[([^\\]]+)]");

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
     * 在进入奖励阶段时立刻把最终奖励写进SavedData。
     * 这样即使奖励界面期间崩服，重启后仍能回原位置并补发同一份真实奖励。
     */
    public static void stage(DungeonInstance instance, MinecraftServer server) {
        if (instance.rewardStaged) return;
        instance.rewardStaged = true;

        DungeonSavedData data = DungeonSavedData.get(server);
        List<UUID> eligible = new ArrayList<>(instance.rewardEligible);
        int totalXp = Math.max(0, instance.definition.experience());
        int each = eligible.isEmpty() ? 0 : totalXp / eligible.size();
        int remainder = eligible.isEmpty() ? 0 : totalXp % eligible.size();

        // 物品优先给仍有奖励资格的开本队长；队长中途主动离本后已经失去资格，
        // 此时顺延给仍有资格的第一位参与者。
        UUID itemRecipient = instance.rewardEligible.contains(instance.leaderId)
                ? instance.leaderId
                : (eligible.isEmpty() ? null : eligible.get(0));

        for (int i = 0; i < eligible.size(); i++) {
            UUID playerId = eligible.get(i);
            int xp = each + (i < remainder ? 1 : 0);
            List<ItemStack> items = playerId.equals(itemRecipient)
                    ? copy(instance.rolledRewards)
                    : List.of();
            UUID batchId = rewardBatchId(instance.id, playerId);
            data.addPendingReward(playerId, batchId, items, xp);
        }
    }

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
        NetworkHooks.openScreen(player,
                new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new RewardMenu(id, inv, instance.definition.displayName(),
                                instance.rolledRewards, totalXp, xpForViewer, recipient, isRecipient),
                        Component.literal("副本奖励")),
                buf -> RewardMenu.writeOpenData(buf, instance.definition.displayName(), instance.rolledRewards,
                        totalXp, xpForViewer, recipient, isRecipient));
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

    /** 副本正常关闭时，在线玩家在回到原位置后立即领取；离线玩家继续留在SavedData。 */
    public static void deliver(DungeonInstance instance, MinecraftServer server) {
        if (instance.rewardDelivered) return;
        instance.rewardDelivered = true;
        Set<UUID> recipients = new LinkedHashSet<>(instance.rewardEligible);
        for (UUID uuid : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            // 死亡界面中的玩家还没完成回程，不能此时把奖励塞进将被替换的玩家实体/背包。
            // 等 PlayerRespawnEvent 或下次登录完成回原位置后再从 SavedData 补发。
            if (player != null && player.isAlive() && !player.level().dimension().equals(DungeonManager.DUNGEON_LEVEL)) {
                deliverPending(player);
            }
        }
    }

    /**
     * 发放已经持久化的副本奖励。
     *
     * 旧逻辑要求“整批奖励必须一次性全部放进36格主背包”，背包不足时整批卡在SavedData里。
     * 新逻辑改为：能塞进背包的正常塞；剩余部分直接掉在玩家当前世界的脚下。
     * 这样奖励总量超过背包最大容量时也不会清空/覆盖玩家原背包，更不会看起来像奖励消失。
     */
    public static void deliverPending(ServerPlayer player) {
        DungeonSavedData data = DungeonSavedData.get(player.server);
        List<DungeonSavedData.PendingReward> batches = data.pendingRewards(player.getUUID());
        if (batches.isEmpty()) return;

        boolean granted = false;
        boolean droppedOverflow = false;

        for (DungeonSavedData.PendingReward pending : batches) {
            SafePlayerDelivery.DeliveryResult result = SafePlayerDelivery.deliverOrDropOverflow(
                    player,
                    "dungeon_reward",
                    pending.batchId(),
                    pending.items(),
                    pending.xp()
            );

            if (result == SafePlayerDelivery.DeliveryResult.GRANTED) {
                granted = true;
            } else if (result == SafePlayerDelivery.DeliveryResult.GRANTED_WITH_DROPS) {
                granted = true;
                droppedOverflow = true;
            } else if (SafePlayerDelivery.canAcknowledge(
                    player, "dungeon_reward", pending.batchId())) {
                data.acknowledgePendingReward(player.getUUID(), pending.batchId());
            }
        }

        if (granted) {
            player.sendSystemMessage(Component.literal("§a已安全发放你的副本奖励。"));
        }
        if (droppedOverflow) {
            player.sendSystemMessage(Component.literal(
                    "§e你的背包已满，放不下的副本奖励已经掉落在你脚下，请及时拾取。"));
        }
    }

    private static UUID rewardBatchId(UUID instanceId, UUID playerId) {
        String source = "yellowduck:dungeon_reward:" + instanceId + ":" + playerId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static List<ItemStack> copy(List<ItemStack> list) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : list) out.add(stack.copy());
        return out;
    }
}
