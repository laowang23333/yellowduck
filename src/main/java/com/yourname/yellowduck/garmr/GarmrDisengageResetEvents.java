package com.yourname.yellowduck.garmr;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 加姆脱战复位：
 * Boss 水平 5 格内连续 5 秒没有可战斗玩家时，回满血并恢复到第一阶段等待首击状态。
 * 复位后下一次玩家攻击仍由 GarmrBoss 原逻辑触发起飞。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrDisengageResetEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double PLAYER_RADIUS = 5.0D;
    private static final double PLAYER_RADIUS_SQ = PLAYER_RADIUS * PLAYER_RADIUS;
    private static final int RESET_AFTER_CHECKS = 5;

    private static final Map<UUID, Integer> EMPTY_CHECKS = new HashMap<>();

    private static Method resetMethod;
    private static boolean resetMethodResolved;
    private static boolean reflectionErrorLogged;

    private GarmrDisengageResetEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof GarmrBoss boss && boss.isAlive()) {
                    tickBoss(level, boss);
                }
            }
        }

        // 清理已经不存在的 Boss 计时，避免长期开服时 UUID 表增长。
        if (!EMPTY_CHECKS.isEmpty() && event.getServer().getTickCount() % 200 == 0) {
            Iterator<UUID> it = EMPTY_CHECKS.keySet().iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                boolean found = false;
                for (ServerLevel level : event.getServer().getAllLevels()) {
                    if (level.getEntity(id) instanceof GarmrBoss boss && boss.isAlive()) {
                        found = true;
                        break;
                    }
                }
                if (!found) it.remove();
            }
        }
    }

    private static void tickBoss(ServerLevel level, GarmrBoss boss) {
        int phase = boss.getEntityData().get(GarmrBoss.PHASE);

        // 第一阶段地面等待首击时已经是“复位完成”状态，不继续累计脱战时间。
        if (phase == GarmrBoss.P1_GROUND) {
            EMPTY_CHECKS.remove(boss.getUUID());
            return;
        }

        if (hasNearbyCombatPlayer(level, boss)) {
            EMPTY_CHECKS.remove(boss.getUUID());
            return;
        }

        int checks = EMPTY_CHECKS.getOrDefault(boss.getUUID(), 0) + 1;
        if (checks < RESET_AFTER_CHECKS) {
            EMPTY_CHECKS.put(boss.getUUID(), checks);
            return;
        }

        EMPTY_CHECKS.remove(boss.getUUID());

        // 先回满，再调用 Boss 自己现有的完整复位逻辑：
        // 清召唤物、清仇恨/索命、落地、回 P1_GROUND、重置技能计时。
        boss.setHealth(boss.getMaxHealth());

        Method method = resolveResetMethod();
        if (method == null) return;

        try {
            method.invoke(boss, level);
        } catch (Throwable error) {
            if (!reflectionErrorLogged) {
                reflectionErrorLogged = true;
                LOGGER.error("[YellowDuck/Garmr] 5格脱战复位调用失败", error);
            }
        }
    }

    /**
     * 只看 X/Z 水平距离。
     * 加姆第一阶段会飞到空中约 6 格；如果按三维距离判断，玩家正站在 Boss 脚下也会被误认成“5格内没人”。
     */
    private static boolean hasNearbyCombatPlayer(ServerLevel level, GarmrBoss boss) {
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive()
                    || player.isRemoved()
                    || player.isCreative()
                    || player.isSpectator()
                    || !boss.isParticipant(player)) {
                continue;
            }

            double dx = player.getX() - boss.getX();
            double dz = player.getZ() - boss.getZ();
            if (dx * dx + dz * dz <= PLAYER_RADIUS_SQ) {
                return true;
            }
        }
        return false;
    }

    private static synchronized Method resolveResetMethod() {
        if (resetMethodResolved) return resetMethod;
        resetMethodResolved = true;

        try {
            resetMethod = GarmrBoss.class.getDeclaredMethod(
                    "resetForNewFight", ServerLevel.class);
            resetMethod.setAccessible(true);
        } catch (Throwable error) {
            LOGGER.error("[YellowDuck/Garmr] 找不到 resetForNewFight(ServerLevel)，脱战复位无法启用", error);
            resetMethod = null;
        }
        return resetMethod;
    }
}
