package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
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
 * 副本奖励只读预览菜单。
 *
 * 服务端菜单不注册任何真实 Slot，奖励物品只作为显示数据同步给客户端。
 * 同时吞掉所有容器点击/拖拽/Shift移动/双击收集等操作，避免任何方式把预览物品变成真实物品。
 */
public final class RewardMenu extends AbstractContainerMenu {
    private static final int MAX_PREVIEW_STACKS = 54;

    private final String dungeonName;
    private final List<ItemStack> rewards;
    private final int totalXp;
    private final int personalXp;
    private final String itemRecipientName;
    private final boolean itemRecipient;

    public RewardMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory,
                buf.readUtf(128),
                readItems(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readBoolean());
    }

    public RewardMenu(int id, Inventory inventory, String dungeonName, List<ItemStack> rewards,
                      int totalXp, int personalXp, String itemRecipientName, boolean itemRecipient) {
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
    }

    public static void writeOpenData(FriendlyByteBuf buf, String dungeonName, List<ItemStack> rewards,
                                     int totalXp, int personalXp, String recipientName, boolean recipient) {
        buf.writeUtf(dungeonName == null ? "副本" : dungeonName, 128);
        int count = Math.min(MAX_PREVIEW_STACKS, rewards == null ? 0 : rewards.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeItem(rewards.get(i));
        buf.writeVarInt(Math.max(0, totalXp));
        buf.writeVarInt(Math.max(0, personalXp));
        buf.writeUtf(recipientName == null ? "" : recipientName, 64);
        buf.writeBoolean(recipient);
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

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Shift 点击永远不能移动任何物品。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 服务端最终防线：左/右键、Shift、数字键、丢弃、双击收集、快捷交换等全部无效。
     * 菜单没有任何真实 Slot，因此也不存在“放入奖励箱”的目标。
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // intentionally no-op
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
