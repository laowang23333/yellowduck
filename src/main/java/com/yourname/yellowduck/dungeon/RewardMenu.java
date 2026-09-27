package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 副本结算 / 待领取邮箱菜单。
 *
 * 菜单没有任何真实 Slot，所有奖励物品都只是服务端同步到客户端的显示副本。
 * 结算模式不能领取；只有离开副本后打开邮箱模式才有领取按钮。
 */
public final class RewardMenu extends AbstractContainerMenu {
    private static final int MAX_PREVIEW_STACKS = 54;
    private static final int MAX_COMBAT_ROWS = 16;
    public static final int ACTION_CLAIM_MAILBOX = 1;

    private final String dungeonName;
    private final String dungeonId;
    private final String bossEntityId;
    private final List<ItemStack> rewards;
    private final int totalXp;
    private final int personalXp;
    private final String itemRecipientName;
    private final boolean itemRecipient;
    private final boolean mailboxMode;
    private final int fightSeconds;
    private final int previewSecondsRemaining;
    private final List<CombatRow> combatRows;

    public RewardMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory,
                buf.readUtf(128),
                buf.readUtf(128),
                buf.readUtf(192),
                readItems(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                readCombatRows(buf));
    }

    public RewardMenu(int id, Inventory inventory,
                      String dungeonName, String dungeonId, String bossEntityId,
                      List<ItemStack> rewards,
                      int totalXp, int personalXp, String itemRecipientName,
                      boolean itemRecipient, boolean mailboxMode,
                      int fightSeconds, int previewSecondsRemaining,
                      List<CombatRow> combatRows) {
        super(ModMenuTypes.DUNGEON_REWARD.get(), id);
        this.dungeonName = dungeonName == null ? "副本" : dungeonName;
        this.dungeonId = dungeonId == null ? "" : dungeonId;
        this.bossEntityId = bossEntityId == null ? "" : bossEntityId;

        List<ItemStack> copies = new ArrayList<>();
        if (rewards != null) {
            for (int i = 0; i < rewards.size() && i < MAX_PREVIEW_STACKS; i++) {
                ItemStack stack = rewards.get(i);
                if (stack != null && !stack.isEmpty()) copies.add(stack.copy());
            }
        }
        this.rewards = Collections.unmodifiableList(copies);
        this.totalXp = Math.max(0, totalXp);
        this.personalXp = Math.max(0, personalXp);
        this.itemRecipientName = itemRecipientName == null ? "" : itemRecipientName;
        this.itemRecipient = itemRecipient;
        this.mailboxMode = mailboxMode;
        this.fightSeconds = Math.max(0, fightSeconds);
        this.previewSecondsRemaining = Math.max(0, previewSecondsRemaining);
        this.combatRows = Collections.unmodifiableList(
                combatRows == null ? List.of() : new ArrayList<>(combatRows.subList(0, Math.min(MAX_COMBAT_ROWS, combatRows.size())))
        );
    }

    public static void writeOpenData(FriendlyByteBuf buf,
                                     String dungeonName, String dungeonId, String bossEntityId,
                                     List<ItemStack> rewards,
                                     int totalXp, int personalXp, String recipientName,
                                     boolean recipient, boolean mailboxMode,
                                     int fightSeconds, int previewSecondsRemaining,
                                     List<CombatRow> combatRows) {
        buf.writeUtf(dungeonName == null ? "副本" : dungeonName, 128);
        buf.writeUtf(dungeonId == null ? "" : dungeonId, 128);
        buf.writeUtf(bossEntityId == null ? "" : bossEntityId, 192);

        int count = Math.min(MAX_PREVIEW_STACKS, rewards == null ? 0 : rewards.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeItem(rewards.get(i));

        buf.writeVarInt(Math.max(0, totalXp));
        buf.writeVarInt(Math.max(0, personalXp));
        buf.writeUtf(recipientName == null ? "" : recipientName, 64);
        buf.writeBoolean(recipient);
        buf.writeBoolean(mailboxMode);
        buf.writeVarInt(Math.max(0, fightSeconds));
        buf.writeVarInt(Math.max(0, previewSecondsRemaining));

        int rows = Math.min(MAX_COMBAT_ROWS, combatRows == null ? 0 : combatRows.size());
        buf.writeVarInt(rows);
        for (int i = 0; i < rows; i++) {
            CombatRow row = combatRows.get(i);
            buf.writeUUID(row.playerId());
            buf.writeUtf(row.name() == null ? "" : row.name(), 64);
            buf.writeDouble(safe(row.damage()));
            buf.writeDouble(safe(row.healing()));
            buf.writeDouble(safe(row.taken()));
        }
    }

    private static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int count = Math.min(MAX_PREVIEW_STACKS, Math.max(0, buf.readVarInt()));
        List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(buf.readItem());
        return out;
    }

    private static List<CombatRow> readCombatRows(FriendlyByteBuf buf) {
        int count = Math.min(MAX_COMBAT_ROWS, Math.max(0, buf.readVarInt()));
        List<CombatRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new CombatRow(
                    buf.readUUID(),
                    buf.readUtf(64),
                    safe(buf.readDouble()),
                    safe(buf.readDouble()),
                    safe(buf.readDouble())
            ));
        }
        return rows;
    }

    private static double safe(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    public String dungeonName() { return dungeonName; }
    public String dungeonId() { return dungeonId; }
    public String bossEntityId() { return bossEntityId; }
    public List<ItemStack> rewards() { return rewards; }
    public int totalXp() { return totalXp; }
    public int personalXp() { return personalXp; }
    public String itemRecipientName() { return itemRecipientName; }
    public boolean isItemRecipient() { return itemRecipient; }
    public boolean mailboxMode() { return mailboxMode; }
    public int fightSeconds() { return fightSeconds; }
    public int previewSecondsRemaining() { return previewSecondsRemaining; }
    public List<CombatRow> combatRows() { return combatRows; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!mailboxMode || id != ACTION_CLAIM_MAILBOX || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        DungeonRewardManager.claimMailbox(serverPlayer);
        serverPlayer.closeContainer();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // 没有真实 Slot，任何容器物品操作都无效。
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    public record CombatRow(UUID playerId, String name, double damage, double healing, double taken) {
        public double total() {
            return damage + healing + taken;
        }
    }
}
