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

/**
 * 副本奖励只读预览 / 待领取邮箱菜单。
 *
 * 菜单本身不注册任何真实 Slot，所有物品都只是显示数据。
 * 邮箱模式只有一个服务端按钮：领取全部奖励。
 */
public final class RewardMenu extends AbstractContainerMenu {
    private static final int MAX_PREVIEW_STACKS = 54;
    public static final int ACTION_CLAIM_MAILBOX = 1;

    private final String dungeonName;
    private final List<ItemStack> rewards;
    private final int totalXp;
    private final int personalXp;
    private final String itemRecipientName;
    private final boolean itemRecipient;
    private final boolean mailboxMode;

    public RewardMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory,
                buf.readUtf(128),
                readItems(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readBoolean());
    }

    public RewardMenu(int id, Inventory inventory, String dungeonName, List<ItemStack> rewards,
                      int totalXp, int personalXp, String itemRecipientName,
                      boolean itemRecipient, boolean mailboxMode) {
        super(ModMenuTypes.DUNGEON_REWARD.get(), id);
        this.dungeonName = dungeonName == null ? "副本" : dungeonName;

        List<ItemStack> copies = new ArrayList<>();
        if (rewards != null) {
            for (int i = 0; i < rewards.size() && i < MAX_PREVIEW_STACKS; i++) {
                copies.add(rewards.get(i).copy());
            }
        }
        this.rewards = Collections.unmodifiableList(copies);
        this.totalXp = Math.max(0, totalXp);
        this.personalXp = Math.max(0, personalXp);
        this.itemRecipientName = itemRecipientName == null ? "" : itemRecipientName;
        this.itemRecipient = itemRecipient;
        this.mailboxMode = mailboxMode;
    }

    public static void writeOpenData(FriendlyByteBuf buf, String dungeonName, List<ItemStack> rewards,
                                     int totalXp, int personalXp, String recipientName,
                                     boolean recipient, boolean mailboxMode) {
        buf.writeUtf(dungeonName == null ? "副本" : dungeonName, 128);
        int count = Math.min(MAX_PREVIEW_STACKS, rewards == null ? 0 : rewards.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeItem(rewards.get(i));
        buf.writeVarInt(Math.max(0, totalXp));
        buf.writeVarInt(Math.max(0, personalXp));
        buf.writeUtf(recipientName == null ? "" : recipientName, 64);
        buf.writeBoolean(recipient);
        buf.writeBoolean(mailboxMode);
    }

    private static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int count = Math.min(MAX_PREVIEW_STACKS, Math.max(0, buf.readVarInt()));
        List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(buf.readItem());
        return out;
    }

    public String dungeonName() { return dungeonName; }
    public List<ItemStack> rewards() { return rewards; }
    public int totalXp() { return totalXp; }
    public int personalXp() { return personalXp; }
    public String itemRecipientName() { return itemRecipientName; }
    public boolean isItemRecipient() { return itemRecipient; }
    public boolean mailboxMode() { return mailboxMode; }

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
}
