package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * NetCraft 1.4.23 兼容桥。
 *
 * 不把 netcraft-1.4.23.jar 作为 YellowDuck 编译依赖，
 * 运行时存在 NetCraft 时通过其公开 PlayerStatsCapability 读取玩家 TotalTier，
 * 并按 NetCraft 1.4.23 的原生 Boss T级压制公式补齐 YellowDuck Boss 缺失的一层。
 */
public final class Netcraft123CombatBridge {
    private static final float DEFAULT_TIER_SUPPRESSION_RATIO = 0.05F;

    private static boolean initTried;
    private static Method capabilityGet;
    private static Method lazyResolve;
    private static Method getTotalTier;
    private static Field tierSuppressionRatioField;

    private Netcraft123CombatBridge() {}

    /**
     * NetCraft 1.4.23 BossBase#setBaseTier(tier) 实际保存 tier * 6。
     * 当前 Garmr 继续沿用 YellowDuck 已有规则：
     * attack_level 优先；未配置时回退 netcraft_tier；再回退源码默认等级。
     */
    public static int configuredBossModTier() {
        double tier = EntityTuningConfig.configured(
                "garmr",
                "attack_level",
                EntityTuningConfig.configured(
                        "garmr",
                        "netcraft_tier",
                        GarmrConfig.BOSS_ATTACK_LEVEL
                )
        );
        return Math.max(0, (int) Math.round(tier) * 6);
    }

    /**
     * 完整复刻 NetCraft 1.4.23 ModEvents#applyTierSuppression 的核心公式：
     * Boss ModTier > 玩家 TotalTier 时，每差 1 点按 tierSuppressionRatio 增伤。
     *
     * 默认 ratio=0.05，并优先读取 NetCraft 当前服务器配置中的真实值。
     */
    public static float applyBossTierSuppression(Player player, float damage) {
        if (player == null || damage <= 0.0F) return damage;

        int playerTier = getPlayerTotalTier(player);
        if (playerTier < 0) {
            // NetCraft 不存在/能力尚未就绪时保持原伤害，绝不错误放大。
            return damage;
        }

        int bossTier = configuredBossModTier();
        int diff = bossTier - playerTier;
        if (diff <= 0) return damage;

        float ratio = readTierSuppressionRatio();
        return damage * (1.0F + diff * ratio);
    }

    /**
     * NetCraft 1.4.23 的普通 minion/elite T级公式（obf.v#a）：
     * - 怪等级高：每差 1 点 +5%
     * - 玩家等级高：每差 1 点 -1%，最多 -18%
     *
     * 用于 YellowDuck 自己承载的 Garmr 召唤物。
     */
    public static float applyMinionTierSuppression(
            Player player,
            float damage,
            String section,
            int fallbackTier
    ) {
        if (player == null || damage <= 0.0F) return damage;

        int playerTier = getPlayerTotalTier(player);
        if (playerTier < 0) return damage;

        int tier = Math.max(0, (int) Math.round(EntityTuningConfig.configured(
                section,
                "attack_level",
                fallbackTier
        )));
        int mobTier = tier * 6;
        int diff = mobTier - playerTier;

        if (diff > 0) {
            return damage * (1.0F + diff * 0.05F);
        }
        if (diff < 0) {
            float reduction = Math.min((-diff) * 0.01F, 0.18F);
            return damage * (1.0F - reduction);
        }
        return damage;
    }

    private static int getPlayerTotalTier(Player player) {
        ensureReflection();
        if (capabilityGet == null || lazyResolve == null || getTotalTier == null) {
            return -1;
        }

        try {
            Object lazy = capabilityGet.invoke(null, player);
            if (lazy == null) return -1;

            Object resolved = lazyResolve.invoke(lazy);
            if (!(resolved instanceof Optional<?> optional) || optional.isEmpty()) {
                return -1;
            }

            Object stats = optional.get();
            Object result = getTotalTier.invoke(stats);
            return result instanceof Number number ? number.intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static float readTierSuppressionRatio() {
        ensureReflection();
        if (tierSuppressionRatioField == null) {
            return DEFAULT_TIER_SUPPRESSION_RATIO;
        }

        try {
            Object configValue = tierSuppressionRatioField.get(null);
            if (configValue == null) return DEFAULT_TIER_SUPPRESSION_RATIO;

            Method get = configValue.getClass().getMethod("get");
            Object value = get.invoke(configValue);
            if (value instanceof Number number) {
                float ratio = number.floatValue();
                return Float.isFinite(ratio) && ratio >= 0.0F
                        ? ratio
                        : DEFAULT_TIER_SUPPRESSION_RATIO;
            }
        } catch (Throwable ignored) {
        }

        return DEFAULT_TIER_SUPPRESSION_RATIO;
    }

    private static synchronized void ensureReflection() {
        if (initTried) return;
        initTried = true;

        try {
            Class<?> capabilityClass =
                    Class.forName("com.jiufeng.netcraft.capability.PlayerStatsCapability");
            Class<?> statsInterface =
                    Class.forName("com.jiufeng.netcraft.capability.PlayerStatsCapability$PlayerStats");
            Class<?> lazyOptionalClass =
                    Class.forName("net.minecraftforge.common.util.LazyOptional");

            capabilityGet = capabilityClass.getMethod("get", Player.class);
            lazyResolve = lazyOptionalClass.getMethod("resolve");
            getTotalTier = statsInterface.getMethod("getTotalTier");

            Class<?> modConfig = Class.forName("com.jiufeng.netcraft.config.ModConfig");
            tierSuppressionRatioField = modConfig.getField("TIER_SUPPRESSION_RATIO");

            if (!Modifier.isStatic(tierSuppressionRatioField.getModifiers())) {
                tierSuppressionRatioField = null;
            }
        } catch (Throwable ignored) {
            capabilityGet = null;
            lazyResolve = null;
            getTotalTier = null;
            tierSuppressionRatioField = null;
        }
    }
}
