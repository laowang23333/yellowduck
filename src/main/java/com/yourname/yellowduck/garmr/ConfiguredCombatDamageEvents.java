package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 脚本技能配置伤害桥。
 *
 * Garmr：
 * - 普攻不再在这里重写，直接由 GarmrAttackSyncEvents 在命中帧读取配置。
 * - 吐息在 NetCraft LivingHurt 处理前写入配置伤害，并补 Boss T级压制。
 *
 * Sakura：
 * - 继续按当前 attack_damage / 110 缩放原技能比例。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ConfiguredCombatDamageEvents {
    private static final float SAKURA_SOURCE_ATTACK = 110.0F;
    private static final float ORIGINAL_BREATH_TICK_DAMAGE =
            GarmrConfig.BREATH_DAMAGE / 3.0F;

    private ConfiguredCombatDamageEvents() {}

    /**
     * 必须早于 NetCraft 默认 NORMAL 优先级处理。
     * 这样 NetCraft 看到的是 YellowDuck 配置后的真实基础伤害，
     * 再继续执行它自己的装备防御。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGarmrHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getSource().getEntity() instanceof GarmrBoss boss)) return;

        int action = boss.visualAction();

        /*
         * 普攻：
         * 已由 GarmrAttackSyncEvents 读取配置并补 T级压制。
         * 这里绝不能再次重写，否则会把命中帧计算结果覆盖回去。
         */
        if (action == GarmrBoss.ACT_BASIC) {
            return;
        }

        /*
         * 吐息原源码每次恰好送入 BREATH_DAMAGE / 3。
         * 加这个原始值检查，避免亡灵夫人/召唤物刚好在 Boss 吐息动画期间
         * 借 boss.damageNoKnockback() 出伤时被误改成吐息伤害。
         */
        if ((action == GarmrBoss.ACT_FIRE_BREATH
                || action == GarmrBoss.ACT_ICE_BREATH)
                && Math.abs(event.getAmount() - ORIGINAL_BREATH_TICK_DAMAGE) < 0.001F) {

            float basic = (float) EntityTuningConfig.configured(
                    "garmr",
                    "attack_damage",
                    GarmrConfig.BASIC_DAMAGE
            );

            float damage = basic * 0.50F / 3.0F;
            damage = Netcraft123CombatBridge.applyBossTierSuppression(
                    player,
                    damage
            );

            event.setAmount(damage);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSakuraHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getSource().getEntity() instanceof SakurawitchEntity sakura) {
            float configured = (float) sakura.getAttributeValue(
                    Attributes.ATTACK_DAMAGE
            );

            if (configured > 0.0F && SAKURA_SOURCE_ATTACK > 0.0F) {
                event.setAmount(
                        event.getAmount()
                                * configured
                                / SAKURA_SOURCE_ATTACK
                );
            }
        }
    }
}
