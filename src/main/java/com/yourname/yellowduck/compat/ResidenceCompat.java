package com.yourname.yellowduck.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Mohist/Residence 兼容桥。纯反射实现，因此纯 Forge/单机没有 Bukkit 时也不会硬依赖 Bukkit 类。
 */
public final class ResidenceCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warned;

    private ResidenceCompat() {}

    public static boolean canOpen(ServerPlayer player, BlockPos pos) {
        return check(player, pos, "container", null, "use");
    }

    public static boolean canBreak(ServerPlayer player, BlockPos pos) {
        return check(player, pos, "destroy", "build", "build")
                && check(player, pos, "container", null, null);
    }

    private static boolean check(ServerPlayer player, BlockPos pos,
                                 String primary, String fallback, String defaultFlag) {
        try {
            if (player.hasPermissions(2)) return true;

            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            if (pluginManager == null) return true;

            Method isPluginEnabled = pluginManager.getClass().getMethod("isPluginEnabled", String.class);
            Object enabled = isPluginEnabled.invoke(pluginManager, "Residence");
            if (!(enabled instanceof Boolean b) || !b) return true;

            Object residencePlugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "Residence");
            if (residencePlugin == null) return true;
            ClassLoader loader = residencePlugin.getClass().getClassLoader();

            Object bukkitPlayer = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUUID());
            if (bukkitPlayer == null) return false;

            Object world = bukkitPlayer.getClass().getMethod("getWorld").invoke(bukkitPlayer);
            Class<?> worldClass = Class.forName("org.bukkit.World", true, loader);
            Class<?> locationClass = Class.forName("org.bukkit.Location", true, loader);
            Constructor<?> locationCtor = locationClass.getConstructor(
                    worldClass, double.class, double.class, double.class);
            Object location = locationCtor.newInstance(world,
                    pos.getX() + 0.5D, (double) pos.getY(), pos.getZ() + 0.5D);

            if (isResidenceOwner(loader, bukkitPlayer, location)) return true;

            Class<?> flagPermissions = Class.forName(
                    "com.bekvon.bukkit.residence.protection.FlagPermissions", true, loader);
            Object perms = invokeStaticByName(flagPermissions, "getPerms", location, bukkitPlayer);
            if (perms == null) return false;

            Class<?> flagsClass = Class.forName(
                    "com.bekvon.bukkit.residence.containers.Flags", true, loader);
            Object flag = flagField(flagsClass, primary);
            if (flag == null && fallback != null) flag = flagField(flagsClass, fallback);
            if (flag == null) return false;

            boolean defaultValue = true;
            if (defaultFlag != null) {
                Object inherited = flagField(flagsClass, defaultFlag);
                if (inherited != null) {
                    defaultValue = playerHas(perms, bukkitPlayer, inherited, true);
                }
            }
            return playerHas(perms, bukkitPlayer, flag, defaultValue);
        } catch (ClassNotFoundException noBukkit) {
            return true;
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.warn("[YellowDuck] Residence 权限桥检查失败，本次操作按拒绝处理。", error);
            }
            return false;
        }
    }

    private static boolean isResidenceOwner(ClassLoader loader, Object player, Object location) {
        try {
            Class<?> api = Class.forName("com.bekvon.bukkit.residence.api.ResidenceApi", true, loader);
            Object manager = api.getMethod("getResidenceManager").invoke(null);
            if (manager == null) return false;
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("isOwnerOfLocation") || method.getParameterCount() != 2) continue;
                Class<?>[] p = method.getParameterTypes();
                if (!p[0].isInstance(player) || !p[1].isInstance(location)) continue;
                Object result = method.invoke(manager, player, location);
                return result instanceof Boolean b && b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object invokeStaticByName(Class<?> type, String name, Object... args) throws Exception {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] params = method.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                if (args[i] != null && !params[i].isInstance(args[i])) { ok = false; break; }
            }
            if (ok) return method.invoke(null, args);
        }
        return null;
    }

    private static boolean playerHas(Object perms, Object player, Object flag, boolean def) throws Exception {
        for (Method method : perms.getClass().getMethods()) {
            if (!method.getName().equals("playerHas") || method.getParameterCount() != 3) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p[2] != boolean.class) continue;
            if (!p[0].isInstance(player) || !p[1].isInstance(flag)) continue;
            Object result = method.invoke(perms, player, flag, def);
            return result instanceof Boolean b && b;
        }
        return false;
    }

    private static Object flagField(Class<?> flagsClass, String name) {
        try {
            Field field = flagsClass.getField(name);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
