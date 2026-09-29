package com.yourname.yellowduck.garmr;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 加姆脱战复位：
 * Boss 水平 5 格内连续 8 秒没有可战斗玩家时，回满血并恢复到第一阶段等待首击状态。
 * 复位后下一次玩家攻击仍由 GarmrBoss 原逻辑触发起飞。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrDisengageResetEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double PLAYER_RADIUS = 5.0D;
    private static final double PLAYER_RADIUS_SQ = PLAYER_RADIUS * PLAYER_RADIUS;
    private static final int RESET_AFTER_CHECKS = 8;

    private static final Map<UUID, Integer> EMPTY_CHECKS = new HashMap<>();
    private static final String RESET_PERMISSION_TAG = "YellowDuckGarmrDisengageResetAllowed";

    private static Method resetMethod;
    private static boolean resetMethodResolved;
    private static boolean reflectionErrorLogged;

    private GarmrDisengageResetEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        /*
         * 这里必须先把加姆 Boss 收集到独立 List，再执行脱战检测。
         *
         * 不能一边遍历 ServerLevel#getAllEntities()，一边直接调用 tickBoss：
         * tickBoss 触发脱战复位后，GarmrBoss#resetForNewFight 会清理阶段召唤物，
         * 清理过程会 discard 实体并修改 ServerLevel 底层实体表。
         *
         * Mohist / Forge 下继续使用原来的实体迭代器，会导致 fastutil
         * Int2ObjectLinkedOpenHashMap 的迭代器状态损坏，最终出现
         * ArrayIndexOutOfBoundsException 并直接炸服。
         */
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<GarmrBoss> bosses = new ArrayList<>();

            // 第一阶段：只读取实体表，期间绝不触发任何会新增/删除实体的逻辑。
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof GarmrBoss boss && boss.isAlive()) {
                    bosses.add(boss);
                }
            }

            // 第二阶段：实体表迭代已经结束，此时 Boss 复位/清召唤物不会破坏上面的迭代器。
            for (GarmrBoss boss : bosses) {
                if (!boss.isAlive() || boss.isRemoved()) continue;
                tickBoss(level, boss);
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

        Method method = resolveResetMethod();
        if (method == null) return;

        // 只有这里完成连续 8 秒无玩家检测后，才授权完整回满与阶段复位。
        boss.getPersistentData().putBoolean(RESET_PERMISSION_TAG, true);
        try {
            boss.setHealth(boss.getMaxHealth());
            method.invoke(boss, level);
        } catch (Throwable error) {
            if (!reflectionErrorLogged) {
                reflectionErrorLogged = true;
                LOGGER.error("[YellowDuck/Garmr] 5格脱战复位调用失败", error);
            }
        } finally {
            boss.getPersistentData().remove(RESET_PERMISSION_TAG);
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
