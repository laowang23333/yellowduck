package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 纯展示型副本奖励菜单：没有任何真实 Slot，因此客户端无法拿取预览物品。 */
public final class RewardMenu extends AbstractContainerMenu {
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
        if (rewards != null) for (ItemStack stack : rewards) copies.add(stack.copy());
        this.rewards = Collections.unmodifiableList(copies);
        this.totalXp = Math.max(0, totalXp);
        this.personalXp = Math.max(0, personalXp);
        this.itemRecipientName = itemRecipientName == null ? "" : itemRecipientName;
        this.itemRecipient = itemRecipient;
    }

    public static void writeOpenData(FriendlyByteBuf buf, String dungeonName, List<ItemStack> rewards,
                                     int totalXp, int personalXp, String recipientName, boolean recipient) {
        buf.writeUtf(dungeonName == null ? "副本" : dungeonName, 128);
        int count = Math.min(45, rewards == null ? 0 : rewards.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeItem(rewards.get(i));
        buf.writeVarInt(Math.max(0, totalXp));
        buf.writeVarInt(Math.max(0, personalXp));
        buf.writeUtf(recipientName == null ? "" : recipientName, 64);
        buf.writeBoolean(recipient);
    }

    private static List<ItemStack> readItems(FriendlyByteBuf buf) {
        int count = Math.min(45, Math.max(0, buf.readVarInt()));
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

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
