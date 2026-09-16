package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 坐骑服务端逻辑：3 秒召唤倒计时、乘骑、召回/放生、玩家数据继承。 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class MountManager {
    private static final Map<UUID, Integer> COUNTDOWNS = new ConcurrentHashMap<>();

    private MountManager() {}

    public static void startMountCountdown(ServerPlayer player) {
        if (!MountData.hasGhostWolf(player)) {
            player.displayClientMessage(Component.literal("§c你还没有绑定鬼狼星坐骑。"), true);
            return;
        }
        if (player.isPassenger() || player.isVehicle()) {
            player.displayClientMessage(Component.literal("§e你当前正在使用坐骑。"), true);
            return;
        }
        if (findOwnedMount(player) != null) {
            player.displayClientMessage(Component.literal("§e你的鬼狼星已经在世界中了。"), true);
            return;
        }
        COUNTDOWNS.put(player.getUUID(), 60);
        player.displayClientMessage(Component.literal("§b鬼狼星将在 §e3 §b秒后到来……"), true);
    }

    public static void releaseMount(ServerPlayer player) {
        MountEntity mount = findOwnedMount(player);
        if (mount != null) {
            mount.discard();
            player.displayClientMessage(Component.literal("§7鬼狼星已放生，随时可以再次召唤。"), true);
        } else {
            player.displayClientMessage(Component.literal("§7当前没有已召唤的鬼狼星。"), true);
        }
        COUNTDOWNS.remove(player.getUUID());
    }

    public static MountEntity findOwnedMount(ServerPlayer player) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return null;
        for (MountEntity mount : serverLevel.getEntitiesOfClass(
                MountEntity.class, player.getBoundingBox().inflate(256.0D))) {
            if (mount.isOwner(player) && !mount.isRemoved()) return mount;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || COUNTDOWNS.isEmpty()) return;

        COUNTDOWNS.entrySet().removeIf(entry -> {
            ServerPlayer player = findOnlinePlayer(entry.getKey());
            if (player == null || !player.isAlive()) return true;

            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                if (left == 40 || left == 20) {
                    player.displayClientMessage(
                            Component.literal("§b鬼狼星召唤倒计时：§e" + (left / 20)), true);
                }
                return false;
            }

            summonAndRide(player);
            return true;
        });
    }

    private static ServerPlayer findOnlinePlayer(UUID uuid) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    private static void summonAndRide(ServerPlayer player) {
        if (player.isPassenger() || player.isVehicle()) return;
        if (!MountData.hasGhostWolf(player) || findOwnedMount(player) != null) return;

        ServerLevel level = player.serverLevel();
        MountEntity mount = ModEntities.MOUNT.get().create(level);
        if (mount == null) return;

        mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        mount.setOwner(player);
        level.addFreshEntity(mount);
        player.startRiding(mount, true);
        player.displayClientMessage(Component.literal("§a✦ 鬼狼星已到达！"), true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        MountData.copyOnClone(event.getOriginal(), event.getEntity());
    }
}
