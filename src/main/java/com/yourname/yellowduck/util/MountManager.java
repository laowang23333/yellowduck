package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.AlpacaMountEntity;
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
    private record Countdown(int ticks, String mountId) {}
    private static final Map<UUID, Countdown> COUNTDOWNS = new ConcurrentHashMap<>();

    private MountManager() {}

    public static void startMountCountdown(ServerPlayer player, String mountId) {
        mountId = MountData.canonicalizeMountId(mountId);
        if (!isKnownMountId(mountId) || !MountData.hasMount(player, mountId)) {
            player.displayClientMessage(Component.literal("§c你还没有绑定这个坐骑。"), true);
            return;
        }
        if (player.isPassenger() || player.isVehicle()) {
            player.displayClientMessage(Component.literal("§e你当前正在使用坐骑。"), true);
            return;
        }
        if (findOwnedMount(player) != null) {
            player.displayClientMessage(Component.literal("§e你的坐骑已经在世界中了。"), true);
            return;
        }
        COUNTDOWNS.put(player.getUUID(), new Countdown(60, mountId));
        player.displayClientMessage(Component.literal("§b坐骑将在 §e3 §b秒后到来……"), true);
    }

    public static void releaseMount(ServerPlayer player, String mountId) {
        mountId = MountData.canonicalizeMountId(mountId);
        if (!isKnownMountId(mountId)) return;

        MountEntity mount = findOwnedMount(player, mountId);
        String name = getMountName(mountId);
        if (mount != null) {
            mount.discard();
            player.displayClientMessage(Component.literal("§7" + name + " 已放生，随时可以再次召唤。"), true);
        } else {
            player.displayClientMessage(Component.literal("§7当前没有已召唤的" + name + "。"), true);
        }
        COUNTDOWNS.remove(player.getUUID());
    }

    public static MountEntity findOwnedMount(ServerPlayer player) {
        return findOwnedMount(player, null);
    }

    public static MountEntity findOwnedMount(ServerPlayer player, String mountId) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return null;

        String canonicalId = mountId == null ? null : MountData.canonicalizeMountId(mountId);
        if (canonicalId != null && !isKnownMountId(canonicalId)) return null;

        for (MountEntity mount : serverLevel.getEntitiesOfClass(
                MountEntity.class, player.getBoundingBox().inflate(256.0D))) {
            if (!mount.isOwner(player) || mount.isRemoved()) continue;
            if (canonicalId == null) return mount;

            boolean alpaca = mount instanceof AlpacaMountEntity;
            if (MountData.ALPACA_ID.equals(canonicalId) && alpaca) return mount;
            if (MountData.DEMON_TENGU_ID.equals(canonicalId) && !alpaca) return mount;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || COUNTDOWNS.isEmpty()) return;

        for (var iterator = COUNTDOWNS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            ServerPlayer player = findOnlinePlayer(uuid);
            if (player == null || !player.isAlive()) {
                COUNTDOWNS.remove(uuid);
                continue;
            }

            Countdown countdown = entry.getValue();
            int left = countdown.ticks() - 1;
            if (left > 0) {
                COUNTDOWNS.put(uuid, new Countdown(left, countdown.mountId()));
                if (left == 40 || left == 20) {
                    player.displayClientMessage(
                            Component.literal("§b坐骑召唤倒计时：§e" + (left / 20)), true);
                }
            } else {
                summonAndRide(player, countdown.mountId());
                COUNTDOWNS.remove(uuid);
            }
        }
    }

    private static ServerPlayer findOnlinePlayer(UUID uuid) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    private static void summonAndRide(ServerPlayer player, String mountId) {
        mountId = MountData.canonicalizeMountId(mountId);
        if (player.isPassenger() || player.isVehicle()) return;
        if (!isKnownMountId(mountId)
                || !MountData.hasMount(player, mountId)
                || findOwnedMount(player, mountId) != null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        MountEntity mount;
        if (MountData.ALPACA_ID.equals(mountId)) {
            mount = ModEntities.ALPACA_MOUNT.get().create(level);
        } else if (MountData.DEMON_TENGU_ID.equals(mountId)) {
            mount = ModEntities.MOUNT.get().create(level);
        } else {
            return;
        }
        if (mount == null) return;

        mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        mount.setOwner(player);
        level.addFreshEntity(mount);
        player.startRiding(mount, true);
        player.displayClientMessage(Component.literal(
                "§a✦ " + getMountName(mountId) + " 已到达！"), true);
    }

    private static boolean isKnownMountId(String mountId) {
        return MountData.DEMON_TENGU_ID.equals(mountId) || MountData.ALPACA_ID.equals(mountId);
    }

    private static String getMountName(String mountId) {
        return MountData.ALPACA_ID.equals(mountId) ? "羊驼" : "魔化天狗";
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        MountData.copyOnClone(event.getOriginal(), event.getEntity());
    }
}
