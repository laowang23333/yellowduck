package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Mohist/Bukkit 最终伤害兼容层。
 *
 * Forge 的 LivingDamageEvent 发生后，Mohist 还会继续派发 Bukkit EntityDamageEvent。
 * WorldGuard / Residence / Essentials 等插件如果在 Bukkit 层取消副本怪物伤害，
 * Forge 侧会误以为伤害已经成立，而玩家最终却完全不掉血。
 *
 * 本桥只处理 yellowduck:dungeon 中、由当前副本怪物造成的实体伤害：
 * - 清除 Forge 阶段提前写入的 deathSeen，真正死亡仍由 LivingDeathEvent 重新记录；
 * - 若 Bukkit 最终层把该伤害取消，则在 MONITOR 最后恢复；
 * - 普通世界的领地、God、PvP 等插件保护完全不受影响。
 */
public final class DungeonBukkitDamageBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Listener LISTENER = new Listener() {};
    private static boolean registered;
    private static int logBudget = 30;

    private DungeonBukkitDamageBridge() {}

    public static synchronized boolean register() {
        if (registered) return true;

        PluginManager pm = Bukkit.getPluginManager();
        Plugin owner = enabledPlugin(pm, "Mohist");
        if (owner == null) owner = enabledPlugin(pm, "Residence");
        if (owner == null) owner = enabledPlugin(pm, "Essentials");
        if (owner == null) {
            for (Plugin plugin : pm.getPlugins()) {
                if (plugin != null && plugin.isEnabled()) {
                    owner = plugin;
                    break;
                }
            }
        }
        if (owner == null) return false;

        pm.registerEvent(
                EntityDamageEvent.class,
                LISTENER,
                EventPriority.MONITOR,
                (listener, event) -> onDamage((EntityDamageEvent) event),
                owner,
                false
        );

        registered = true;
        LOGGER.info("YellowDuck 副本 Mohist/Bukkit 最终伤害桥已启用（owner={}）。", owner.getName());
        return true;
    }

    private static Plugin enabledPlugin(PluginManager pm, String name) {
        Plugin plugin = pm.getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private static void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player bukkitPlayer)) return;
        if (!isDungeonWorld(bukkitPlayer.getWorld())) return;

        ServerPlayer player = serverPlayer(bukkitPlayer);
        if (player == null) return;
        DungeonInstance instance = DungeonManager.instanceOf(player);
        if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        net.minecraft.world.entity.Entity damager = handle(byEntity.getDamager());
        if (!isDungeonCombatSource(damager, instance)) return;

        /*
         * DungeonManager.fatalDamage() 在 Forge LivingDamageEvent 阶段只能看到“理论最终伤害”，
         * 还不知道 Bukkit 稍后会不会取消。因此在 Bukkit 最终层先撤销这个提前死亡标记。
         * 如果这一击真的杀死玩家，LivingDeathEvent 会在随后重新正确设置 deathSeen。
         */
        DungeonInstance.LifeState life = instance.life.get(player.getUUID());
        if (life != null && player.isAlive() && !player.isDeadOrDying()) {
            life.deathSeen = false;
        }

        boolean wasCancelled = event.isCancelled();
        double base = event.getDamage();
        double finalDamage = event.getFinalDamage();

        if (wasCancelled) {
            // 仅副本怪物伤害越过 Bukkit 插件保护；普通世界完全不动。
            event.setCancelled(false);
        }

        if (logBudget > 0) {
            logBudget--;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(damager.getType());
            LOGGER.info(
                    "[YellowDuckBukkitDamage] player={}, source={}, cause={}, cancelledByPlugin={}, base={}, final={}",
                    player.getGameProfile().getName(),
                    id == null ? damager.getClass().getSimpleName() : id.toString(),
                    event.getCause(),
                    wasCancelled,
                    base,
                    finalDamage
            );
        }
    }

    private static boolean isDungeonCombatSource(
            net.minecraft.world.entity.Entity entity,
            DungeonInstance instance
    ) {
        if (entity == null || instance == null) return false;

        if (entity.getPersistentData().hasUUID("YellowDuckDungeon")
                && instance.id.equals(entity.getPersistentData().getUUID("YellowDuckDungeon"))) {
            return true;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id != null && YellowDuckMod.MOD_ID.equals(id.getNamespace())) {
            return true;
        }

        if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            return isDungeonCombatSource(projectile.getOwner(), instance);
        }
        return false;
    }

    private static ServerPlayer serverPlayer(Player player) {
        Object handle = handleObject(player);
        return handle instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private static net.minecraft.world.entity.Entity handle(org.bukkit.entity.Entity entity) {
        Object handle = handleObject(entity);
        return handle instanceof net.minecraft.world.entity.Entity nms ? nms : null;
    }

    private static Object handleObject(Object bukkitEntity) {
        if (bukkitEntity == null) return null;
        try {
            Method getHandle = bukkitEntity.getClass().getMethod("getHandle");
            return getHandle.invoke(bukkitEntity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDungeonWorld(World world) {
        if (world == null) return false;
        try {
            var key = world.getKey();
            if (key != null
                    && "yellowduck".equalsIgnoreCase(key.getNamespace())
                    && "dungeon".equalsIgnoreCase(key.getKey())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String name = world.getName().toLowerCase(Locale.ROOT).replace('\\', '/');
        return name.equals("yellowduck:dungeon")
                || name.equals("yellowduck_dungeon")
                || name.endsWith("/yellowduck/dungeon")
                || name.endsWith("/yellowduck_dungeon");
    }
}
