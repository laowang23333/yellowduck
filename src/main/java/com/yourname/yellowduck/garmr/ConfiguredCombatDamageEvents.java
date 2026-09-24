package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 让 yellowduck-entities.toml 的 attack_damage 真正进入脚本技能伤害。
 * 不改变百分比秒杀/献祭/小恶魔最大生命百分比等“机制伤害”。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ConfiguredCombatDamageEvents {
    private static final float SAKURA_SOURCE_ATTACK = 110.0F;

    private ConfiguredCombatDamageEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getSource().getEntity() instanceof GarmrBoss boss) {
            // P1 骷髅射手的投射物：attack_damage 直接作为实际单发伤害。
            if (event.getSource().getDirectEntity() instanceof GarmrProjectile) {
                event.setAmount((float) EntityTuningConfig.configured(
                        "garmr_p1_archer", "attack_damage", GarmrConfig.P1_ARCHER_ATTACK));
                return;
            }

            int action = boss.visualAction();

            // 普攻：配置里的 garmr.attack_damage 就是基础普攻伤害。
            if (action == GarmrBoss.ACT_BASIC) {
                float damage = (float) EntityTuningConfig.configured(
                        "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
                if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
                    damage *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
                }
                event.setAmount(damage);
                return;
            }

            // 吐息规则仍为“总计基础近战的 50%”，源码分 3 次结算。
            if (action == GarmrBoss.ACT_FIRE_BREATH || action == GarmrBoss.ACT_ICE_BREATH) {
                float basic = (float) EntityTuningConfig.configured(
                        "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
                event.setAmount(basic * 0.50F / 3.0F);
            }
            return;
        }

        // 小樱所有脚本魔法原本以 110 攻击为基准。
        // 统一按当前 attack_damage / 110 缩放，保留喷火、爆炸、喷发之间原本的比例。
        if (event.getSource().getEntity() instanceof SakurawitchEntity sakura) {
            float configured = (float) sakura.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (configured > 0.0F && SAKURA_SOURCE_ATTACK > 0.0F) {
                event.setAmount(event.getAmount() * configured / SAKURA_SOURCE_ATTACK);
            }
        }
    }
}
