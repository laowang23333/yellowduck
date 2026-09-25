package com.yourname.yellowduck.event;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 修复 Mohist/其它 Mod 留下的非法玩家生命数值。
 * NaN absorption 会让 vanilla 伤害公式继续传播 NaN，最终表现为“任何攻击都不正常扣血”。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class PlayerNumericSanityEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> WARNED_ABSORPTION = new HashSet<>();
    private static final Set<UUID> WARNED_HEALTH = new HashSet<>();

    private PlayerNumericSanityEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;

        float absorption = player.getAbsorptionAmount();
        if (!Float.isFinite(absorption)) {
            player.setAbsorptionAmount(0.0F);
            if (WARNED_ABSORPTION.add(player.getUUID())) {
                LOGGER.warn("[YellowDuckNumericFix] 玩家 {} 的 absorption 为非法值 {}，已恢复为 0。",
                        player.getGameProfile().getName(), absorption);
            }
        }

        float health = player.getHealth();
        if (!Float.isFinite(health)) {
            float max = player.getMaxHealth();
            float repaired = Float.isFinite(max) && max > 0.0F ? max : 20.0F;
            player.setHealth(repaired);
            player.hurtMarked = true;
            if (WARNED_HEALTH.add(player.getUUID())) {
                LOGGER.warn("[YellowDuckNumericFix] 玩家 {} 的 health 为非法值 {}，已恢复为 {}。",
                        player.getGameProfile().getName(), health, repaired);
            }
        }
    }
}
