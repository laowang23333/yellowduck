package com.yourname.yellowduck.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mohist/Bukkit 层的副本隔离保护。
 *
 * 传送层不依赖 Residence 的 tp flag：第三方 /b、/back、/home、/tpa 等
 * 只要传送起点或终点涉及 yellowduck:dungeon，就会被取消；唯一例外是
 * YellowDuck 自己通过 DungeonTeleportGuard 标记的副本内部传送。
 *
 * Residence 本体由 Bukkit 的独立插件 ClassLoader 加载，因此领地事件全部通过
 * Residence 自己的 ClassLoader 反射注册，避免 Mod ClassLoader 直接引用 Residence 类后
 * 在 Mohist 上出现 NoClassDefFoundError。
 */
public final class DungeonBukkitProtection {
    private static final Listener LISTENER = new Listener() {};
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private static final Set<String> TELEPORT_COMMANDS = Set.of(
            "b", "back", "return", "deathback", "dback",
            "tp", "teleport", "tpo", "tphere", "tpohere", "tppos",
            "tpa", "tpaccept", "tpahere", "tpcancel",
            "home", "homes", "sethome", "delhome",
            "spawn", "warp", "warps", "rtp", "randomtp", "wild",
            "top", "jump", "j"
    );

    private static Plugin registeredWith;

    private DungeonBukkitProtection() {
    }

    /**
     * @return true 表示保护已经成功注册；Residence 还未启用时返回 false，稍后可重试。
     */
    public static synchronized boolean tryRegister() throws Exception {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin residence = pm.getPlugin("Residence");
        if (residence == null || !residence.isEnabled()) return false;
        if (registeredWith == residence) return true;

        pm.registerEvent(PlayerTeleportEvent.class, LISTENER, EventPriority.HIGHEST,
                (listener, event) -> onTeleport((PlayerTeleportEvent) event), residence, true);
        // PlayerPortalEvent 在部分 Bukkit 实现中使用独立 HandlerList，单独注册避免传送门漏网。
        pm.registerEvent(PlayerPortalEvent.class, LISTENER, EventPriority.HIGHEST,
                (listener, event) -> onTeleport((PlayerPortalEvent) event), residence, true);
        pm.registerEvent(PlayerCommandPreprocessEvent.class, LISTENER, EventPriority.HIGHEST,
                (listener, event) -> onCommand((PlayerCommandPreprocessEvent) event), residence, true);

        ClassLoader residenceLoader = residence.getClass().getClassLoader();
        registerResidenceAreaEvent(pm, residence, residenceLoader,
                "com.bekvon.bukkit.residence.event.ResidenceCreationEvent", "getPhysicalArea");
        registerResidenceAreaEvent(pm, residence, residenceLoader,
                "com.bekvon.bukkit.residence.event.ResidenceAreaAddEvent", "getPhysicalArea");
        registerResidenceAreaEvent(pm, residence, residenceLoader,
                "com.bekvon.bukkit.residence.event.ResidenceSubzoneCreationEvent", "getPhysicalArea");
        registerResidenceAreaEvent(pm, residence, residenceLoader,
                "com.bekvon.bukkit.residence.event.ResidenceSizeChangeEvent", "getNewArea");

        registeredWith = residence;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void registerResidenceAreaEvent(PluginManager pm, Plugin owner, ClassLoader loader,
                                                   String className, String areaGetter) throws Exception {
        Class<?> raw = Class.forName(className, true, loader);
        if (!Event.class.isAssignableFrom(raw)) return;
        Class<? extends Event> eventClass = (Class<? extends Event>) raw;
        Method getArea = raw.getMethod(areaGetter);
        Method setCancelled = raw.getMethod("setCancelled", boolean.class);
        Method getPlayer = raw.getMethod("getPlayer");

        pm.registerEvent(eventClass, LISTENER, EventPriority.HIGHEST, (listener, event) -> {
            try {
                Object area = getArea.invoke(event);
                if (!isDungeonResidenceArea(area)) return;
                setCancelled.invoke(event, true);
                Object player = getPlayer.invoke(event);
                if (player instanceof Player bukkitPlayer) warnClaim(bukkitPlayer);
            } catch (Throwable ignored) {
                // Residence API 小版本差异不应影响服务器主线程；命令层仍会继续阻止副本内圈地。
            }
        }, owner, true);
    }

    private static boolean isDungeonResidenceArea(Object area) {
        if (area == null) return false;
        try {
            Method getWorld = area.getClass().getMethod("getWorld");
            Object world = getWorld.invoke(area);
            return world instanceof World bukkitWorld && isDungeonWorld(bukkitWorld);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || DungeonTeleportGuard.isInternalTeleport()) return;

        Player player = event.getPlayer();
        boolean fromDungeon = isDungeonWorld(event.getFrom().getWorld());
        boolean toDungeon = isDungeonWorld(event.getTo().getWorld());

        // yellowduck:dungeon 是永久封闭副本维度：
        // 不看当前有没有队伍/Boss，也不给 OP 或第三方插件额外豁免。
        // 只要传送的起点或终点涉及副本世界，且不是 YellowDuck 通过
        // DungeonTeleportGuard 标记的内部副本传送，就直接取消。
        if (fromDungeon || toDungeon) {
            event.setCancelled(true);
            warn(player, "§c副本世界禁止外部传送，只能通过副本系统进入或离开。", 800L);
        }
    }

    private static void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String root = rootCommand(event.getMessage());
        if (root.isEmpty()) return;

        // 副本维度内一律禁止 Residence 命令，避免选区、创建、扩展等操作。
        if (isDungeonWorld(player.getWorld()) && (root.equals("res") || root.equals("residence"))) {
            event.setCancelled(true);
            warn(player, "§c副本世界禁止使用 Residence 圈地命令。", 800L);
            return;
        }

        // 只要玩家当前身处副本世界，常见外部传送指令直接在执行前拦截。
        // 不依赖“有没有活动副本”；未知插件指令仍由 PlayerTeleportEvent 兜底。
        if (isDungeonWorld(player.getWorld()) && TELEPORT_COMMANDS.contains(root)) {
            event.setCancelled(true);
            warn(player, "§c副本世界禁止外部传送指令，请通过副本系统正常离开。", 800L);
        }
    }

    private static boolean isDungeonWorld(World world) {
        if (world == null) return false;
        try {
            NamespacedKey key = world.getKey();
            if (key != null
                    && "yellowduck".equalsIgnoreCase(key.getNamespace())
                    && "dungeon".equalsIgnoreCase(key.getKey())) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Mohist 对自定义维度的 Bukkit world name 在不同构建里可能有不同格式，做兼容兜底。
        String name = world.getName().toLowerCase(Locale.ROOT).replace('\\', '/');
        return name.equals("yellowduck:dungeon")
                || name.equals("yellowduck_dungeon")
                || name.endsWith("/yellowduck/dungeon")
                || name.endsWith("/yellowduck_dungeon");
    }

    private static String rootCommand(String message) {
        if (message == null) return "";
        String text = message.trim();
        if (text.startsWith("/")) text = text.substring(1);
        if (text.isEmpty()) return "";
        int space = text.indexOf(' ');
        String root = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        int namespace = root.lastIndexOf(':');
        if (namespace >= 0 && namespace + 1 < root.length()) root = root.substring(namespace + 1);
        return root;
    }

    private static void warnClaim(Player player) {
        if (player != null) warn(player, "§c副本世界禁止创建、扩展或划分 Residence 领地。", 800L);
    }

    private static void warn(Player player, String message, long cooldownMs) {
        long now = System.currentTimeMillis();
        long last = LAST_MESSAGE.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMs) return;
        LAST_MESSAGE.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}
