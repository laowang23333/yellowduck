package com.yourname.yellowduck.item;

import com.yourname.yellowduck.network.MountNetwork;
import com.yourname.yellowduck.util.MountData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 坐骑蛋：第一次使用绑定到玩家坐骑图鉴，之后由 M 键坐骑界面管理。 */
public class MountSummonItem extends Item {
    private final String mountId;

    public MountSummonItem(Properties properties, String mountId) {
        super(properties.stacksTo(1));
        this.mountId = mountId;
    }

    private static String nameOf(String id) { return "rabbit".equals(id) ? "玉兔" : ("alpaca".equals(id) ? "羊驼" : ("bamboo_horse".equals(id) ? "竹马" : "魔化天狗")); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!MountData.hasMount(player, mountId)) {
                MountData.bindMount(player, mountId);
                if (player instanceof ServerPlayer serverPlayer) {
                    MountNetwork.syncTo(serverPlayer);
                }
                String name = nameOf(mountId);
                player.displayClientMessage(Component.literal("§a✦ " + name + " 已绑定到你的坐骑图鉴！"), true);
                player.displayClientMessage(Component.literal("§7按 M 打开坐骑界面，选择乘骑或放生。"), false);
            } else {
                String name = nameOf(mountId);
                player.displayClientMessage(Component.literal("§b" + name + " 已经绑定。按 M 打开坐骑界面。"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
