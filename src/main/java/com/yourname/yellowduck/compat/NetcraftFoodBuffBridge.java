package com.yourname.yellowduck.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * 不把 42MB 的 NetCraft JAR 放进 YellowDuck 编译依赖，运行时直接接它公开的 FoodBuffCapability。
 *
 * NetCraft 1.4.23 的真实食物 Buff 类型：
 * SHARPNESS / POWER / SPELL / ARMOR / RESISTANCE。
 */
public final class NetcraftFoodBuffBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] BUFF_NAMES = {
            "SHARPNESS", "POWER", "SPELL", "ARMOR", "RESISTANCE"
    };

    /** NetCraft 第一份同类食物会从等级 3 开始。 */
    private static final int BASE_FOOD_BUFF_LEVEL = 3;
    private static boolean warned;

    private NetcraftFoodBuffBridge() {
    }

    /**
     * 给玩家全部 NetCraft 食物 Buff，但绝不做“等级 +1”式叠加。
     * 已有更强或剩余时间更长的效果会原样保留。
     */
    public static boolean applyAll(Player player, int durationTicks) {
        if (player == null || player.level().isClientSide || durationTicks <= 0) return false;
        if (!ModList.get().isLoaded("netcraft")) return false;

        try {
            Class<?> foodBuffTypeClass = Class.forName("com.jiufeng.netcraft.api.FoodBuffType");
            Class<?> capabilityClass = Class.forName("com.jiufeng.netcraft.capability.FoodBuffCapability");
            Class<?> dataClass = Class.forName("com.jiufeng.netcraft.capability.FoodBuffCapability$FoodBuffData");

            Method getCapability = capabilityClass.getMethod("get", Player.class);
            Object lazyObject = getCapability.invoke(null, player);
            if (!(lazyObject instanceof LazyOptional<?> lazy)) return false;

            Object data = lazy.resolve().orElse(null);
            if (data == null) return false;

            Method getLevel = dataClass.getMethod("getBuffLevel", foodBuffTypeClass);
            Method setLevel = dataClass.getMethod("setBuffLevel", foodBuffTypeClass, int.class);
            Method getRemaining = dataClass.getMethod("getBuffRemainingTime", foodBuffTypeClass);
            Method setRemaining = dataClass.getMethod("setBuffRemainingTime", foodBuffTypeClass, int.class);

            boolean changed = false;
            for (String name : BUFF_NAMES) {
                Object type = foodBuffTypeClass.getField(name).get(null);
                int oldLevel = ((Number) getLevel.invoke(data, type)).intValue();
                int oldRemaining = ((Number) getRemaining.invoke(data, type)).intValue();

                // 已过期但 NetCraft 还没跑到下一次清理 tick：按新 Buff 处理，避免复活旧高等级。
                if (oldRemaining <= 0) {
                    setLevel.invoke(data, type, BASE_FOOD_BUFF_LEVEL);
                    setRemaining.invoke(data, type, durationTicks);
                    changed = true;
                    continue;
                }

                // 不叠层：只保证至少达到 NetCraft 食物的基础等级 3。
                if (oldLevel < BASE_FOOD_BUFF_LEVEL) {
                    setLevel.invoke(data, type, BASE_FOOD_BUFF_LEVEL);
                    changed = true;
                }
                // 不缩短玩家本来更长的 Buff。
                if (oldRemaining < durationTicks) {
                    setRemaining.invoke(data, type, durationTicks);
                    changed = true;
                }
            }
            return changed;
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                LOGGER.warn("YellowDuck 无法接入 NetCraft FoodBuffCapability；兔兔饼仍会补满饱食度，但本次未写入 NetCraft 食物 Buff。", error);
            }
            return false;
        }
    }
}
