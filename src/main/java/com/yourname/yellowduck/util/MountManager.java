package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.AlpacaMountEntity;
import com.yourname.yellowduck.entity.BambooHorseEntity;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.RabbitMountEntity;
import com.yourname.yellowduck.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 坐骑服务端逻辑：3 秒召唤倒计时、乘骑、放生、唯一实体记录、玩家数据继承。 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class MountManager {
    private record Countdown(int ticks, String mountId) {}
    private static final Map<UUID, Countdown> COUNTDOWNS = new ConcurrentHashMap<>();

    private MountManager() {}

    public static void startMountCountdown(ServerPlayer player, String mountId) {
        String id = normalizeMountId(mountId);
        if (!MountData.hasMount(player, id)) {
            player.displayClientMessage(Component.literal("§c你还没有绑定这个坐骑。"), true);
            return;
        }
        if (player.isPassenger() || player.isVehicle()) {
            player.displayClientMessage(Component.literal("§e你当前正在使用坐骑。"), true);
            return;
        }

        // 服务端持久化唯一记录是权威状态：即使旧坐骑在 3000 格外或其它维度也不能重复召唤。
        MountSavedData.ActiveMount active = MountSavedData.get(player.server).getActive(player.getUUID());
        if (active != null) {
            player.displayClientMessage(Component.literal(
                    "§e你已经召唤了“" + displayName(active.mountId()) + "”。请先放生当前坐骑再召唤新的。"), true);
            return;
        }

        COUNTDOWNS.put(player.getUUID(), new Countdown(60, id));
        player.displayClientMessage(Component.literal("§b坐骑将在 §e3 §b秒后到来……"), true);
    }

    public static void releaseMount(ServerPlayer player, String mountId) {
        String id = normalizeMountId(mountId);
        MountSavedData data = MountSavedData.get(player.server);
        MountSavedData.ActiveMount active = data.getActive(player.getUUID());

        if (active == null || !active.mountId().equals(id)) {
            player.displayClientMessage(Component.literal(
                    "§7当前没有已召唤的" + displayName(id) + "。"), true);
            COUNTDOWNS.remove(player.getUUID());
            return;
        }

        // 先清除持久化记录。即使实体所在区块当前没加载，它以后重新加载时也会自动自毁。
        data.clear(player.getUUID());
        MountEntity loaded = findLoadedByUuid(player.server, active.entityId());
        if (loaded != null && player.getUUID().equals(loaded.getOwnerUUID())) {
            loaded.discard();
        }

        player.displayClientMessage(Component.literal(
                "§7" + displayName(id) + " 已放生，随时可以再次召唤。"), true);
        COUNTDOWNS.remove(player.getUUID());
    }

    public static MountEntity findOwnedMount(ServerPlayer player) {
        return findOwnedMount(player, null);
    }

    /**
     * 只按持久化 UUID 查当前唯一坐骑，不再用 256 格 AABB。
     * 如果对应区块未加载会返回 null，但唯一记录仍存在，因此不会允许重复召唤。
     */
    public static MountEntity findOwnedMount(ServerPlayer player, String mountId) {
        MountSavedData.ActiveMount active = MountSavedData.get(player.server).getActive(player.getUUID());
        if (active == null) return null;

        String requested = mountId == null ? null : normalizeMountId(mountId);
        if (requested != null && !requested.equals(active.mountId())) return null;

        MountEntity entity = findLoadedByUuid(player.server, active.entityId());
        if (entity == null || entity.isRemoved()) return null;
        return player.getUUID().equals(entity.getOwnerUUID()) ? entity : null;
    }

    /**
     * MountEntity 每秒调用一次。
     * 旧版本遗留、重复召唤、跨维度残留的坐骑只要重新加载，就会与 SavedData 权威 UUID 对比；
     * 不是当前唯一实体的直接清理。
     */
    public static boolean validateActiveMount(MountEntity mount) {
        if (mount == null || mount.level().isClientSide) return true;
        if (!(mount.level() instanceof ServerLevel level)) return true;
        UUID ownerId = mount.getOwnerUUID();
        if (ownerId == null) return true;

        MountSavedData.ActiveMount active = MountSavedData.get(level.getServer()).getActive(ownerId);
        String actualId = mountIdOf(mount);

        if (active == null
                || !active.entityId().equals(mount.getUUID())
                || !active.mountId().equals(actualId)) {
            mount.discard();
            return false;
        }
        return true;
    }

    /** 实体死亡/被清理时只清除与它自身匹配的唯一记录。 */
    public static void onMountRemoved(MountEntity mount) {
        if (mount == null || mount.level().isClientSide) return;
        if (!(mount.level() instanceof ServerLevel level)) return;
        UUID ownerId = mount.getOwnerUUID();
        if (ownerId == null) return;
        MountSavedData.get(level.getServer()).clearIfMatches(ownerId, mount.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || COUNTDOWNS.isEmpty()) return;

        // ConcurrentHashMap 不使用 iterator.remove，统一按 UUID 删除，避免混合端实现差异。
        for (var entry : COUNTDOWNS.entrySet()) {
            UUID uuid = entry.getKey();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
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

    private static void summonAndRide(ServerPlayer player, String mountId) {
        String id = normalizeMountId(mountId);
        if (player.isPassenger() || player.isVehicle()) return;
        if (!MountData.hasMount(player, id)) return;

        MountSavedData data = MountSavedData.get(player.server);
        if (data.hasActive(player.getUUID())) {
            player.displayClientMessage(Component.literal("§e你的坐骑已经在世界中了。"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        MountEntity mount;
        if ("rabbit".equals(id)) {
            mount = ModEntities.RABBIT_MOUNT.get().create(level);
        } else if ("alpaca".equals(id)) {
            mount = ModEntities.ALPACA_MOUNT.get().create(level);
        } else if ("bamboo_horse".equals(id)) {
            mount = ModEntities.BAMBOO_HORSE_MOUNT.get().create(level);
        } else if ("ghost_wolf_stars".equals(id)) {
            mount = ModEntities.MOUNT.get().create(level);
        } else {
            return;
        }
        if (mount == null) return;

        mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        mount.setOwner(player);

        // 只有真正成功加入世界后才登记唯一 UUID，避免生成被其它 Mod/插件取消后留下“幽灵激活记录”。
        if (!level.addFreshEntity(mount)) {
            player.displayClientMessage(Component.literal("§c坐骑生成失败，请稍后再试。"), true);
            return;
        }

        data.register(player.getUUID(), mount.getUUID(), id);
        player.startRiding(mount, true);
        player.displayClientMessage(Component.literal(
                "§a✦ " + displayName(id) + " 已到达！"), true);
    }

    private static MountEntity findLoadedByUuid(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof MountEntity mount && !mount.isRemoved()) {
                return mount;
            }
        }
        return null;
    }

    public static String mountIdOf(MountEntity mount) {
        if (mount instanceof BambooHorseEntity) return "bamboo_horse";
        if (mount instanceof RabbitMountEntity) return "rabbit";
        if (mount instanceof AlpacaMountEntity) return "alpaca";
        return "ghost_wolf_stars";
    }

    private static String normalizeMountId(String mountId) {
        if (mountId == null) return "";
        String id = mountId.trim().toLowerCase(Locale.ROOT);
        return "demon_tengu".equals(id) ? "ghost_wolf_stars" : id;
    }

    private static String displayName(String mountId) {
        return switch (normalizeMountId(mountId)) {
            case "rabbit" -> "玉兔";
            case "alpaca" -> "羊驼";
            case "bamboo_horse" -> "竹马";
            case "ghost_wolf_stars" -> "魔化天狗";
            default -> "坐骑";
        };
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        MountData.copyOnClone(event.getOriginal(), event.getEntity());
    }
}
