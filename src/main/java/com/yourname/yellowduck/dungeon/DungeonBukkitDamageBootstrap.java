package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 纯 Forge 启动器：只有检测到 Bukkit/Mohist 后才反射加载 Bukkit 兼容类。
 * 因此单机/纯 Forge 环境不会因为 compileOnly Bukkit API 而加载失败。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DungeonBukkitDamageBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;
    private static int retryTicks;

    private DungeonBukkitDamageBootstrap() {}

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        tryRegister();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (registered || event.phase != TickEvent.Phase.END) return;
        retryTicks++;
        if (retryTicks > 600 || retryTicks % 20 != 0) return;
        tryRegister();
    }

    private static void tryRegister() {
        if (registered) return;
        try {
            Class.forName("org.bukkit.Bukkit", false, DungeonBukkitDamageBootstrap.class.getClassLoader());
            Class<?> bridge = Class.forName(
                    "com.yourname.yellowduck.dungeon.DungeonBukkitDamageBridge",
                    true,
                    DungeonBukkitDamageBootstrap.class.getClassLoader()
            );
            Object result = bridge.getMethod("register").invoke(null);
            registered = Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException ignored) {
            // 纯 Forge / 单机：没有 Bukkit，这是正常情况。
            registered = true;
        } catch (Throwable t) {
            if (retryTicks <= 40) {
                LOGGER.warn("YellowDuck Bukkit 最终伤害桥暂未就绪，将稍后重试：{}", t.toString());
            }
        }
    }
}
