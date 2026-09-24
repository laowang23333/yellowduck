package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Garmr helper 共用一个 entity id，普通 EntityTuningConfig 只能命中 garmr_helper。
 * 这里按 variant 每秒重新同步一次各自独立配置，/yd reload 后无需重生怪物。
 *
 * v1.0.2：
 * 任何可选属性都先检查 AttributeInstance，绝不再对未注册属性调用
 * LivingEntity#getAttributeValue，避免 "Can't find attribute ..." 直接崩服。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrVariantConfigSyncEvents {
    private GarmrVariantConfigSyncEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity raw : level.getAllEntities()) {
                if (raw instanceof GarmrBoss boss) {
                    syncBoss(boss);
                } else if (raw instanceof GarmrHelperEntity helper) {
                    syncHelper(helper);
                }
            }
        }
    }

    private static void syncBoss(GarmrBoss boss) {
        AttributeInstance attackAttr = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        double currentAttack = attackAttr != null
                ? attackAttr.getBaseValue()
                : GarmrConfig.BASIC_DAMAGE;

        double attack = EntityTuningConfig.configured(
                "garmr", "attack_damage", currentAttack);
        set(attackAttr, attack);

        int attackLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr", "attack_level",
                EntityTuningConfig.configured(
                        "garmr", "netcraft_tier", GarmrConfig.BOSS_ATTACK_LEVEL)));

        int defenseLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr", "defense_level", GarmrConfig.BOSS_DEFENSE_LEVEL));

        boss.setBaseTier(Math.max(0, attackLevel));
        boss.setBaseDamage((int) Math.round(attack));
        boss.setBaseDefense(Math.max(0, defenseLevel));
    }

    private static void syncHelper(GarmrHelperEntity helper) {
        String section = section(helper);
        if (section == null) return;

        double oldMax = helper.getMaxHealth();
        float oldHealth = helper.getHealth();
        double ratio = oldMax > 0.0D ? oldHealth / oldMax : 1.0D;

        double maxHealth = EntityTuningConfig.configured(section, "max_health", oldMax);
        AttributeInstance max = helper.getAttribute(Attributes.MAX_HEALTH);
        if (max != null && Math.abs(max.getBaseValue() - maxHealth) > 1.0E-9D) {
            max.setBaseValue(maxHealth);
            helper.setHealth((float) Math.max(
                    0.1D,
                    Math.min(maxHealth, maxHealth * ratio)
            ));
        }

        syncAttribute(helper, section, "attack_damage", Attributes.ATTACK_DAMAGE);
        syncAttribute(helper, section, "movement_speed", Attributes.MOVEMENT_SPEED);
        syncAttribute(helper, section, "attack_speed", Attributes.ATTACK_SPEED);
        syncAttribute(helper, section, "armor", Attributes.ARMOR);
        syncAttribute(helper, section, "armor_toughness", Attributes.ARMOR_TOUGHNESS);
        syncAttribute(helper, section, "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE);
        syncAttribute(helper, section, "follow_range", Attributes.FOLLOW_RANGE);
        syncAttribute(helper, section, "attack_knockback", Attributes.ATTACK_KNOCKBACK);

        /*
         * Garmr 自己的战斗事件使用这三个 TAG 进行固定防御和等级结算。
         * 即使某个 vanilla Attribute 没有注册，这里仍然可以正常工作。
         */
        helper.getPersistentData().putInt(
                GarmrBoss.TAG_DEFENSE,
                (int) Math.round(EntityTuningConfig.configured(
                        section,
                        "armor",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE)
                ))
        );

        helper.getPersistentData().putInt(
                GarmrBoss.TAG_ATTACK_LEVEL,
                (int) Math.round(EntityTuningConfig.configured(
                        section,
                        "attack_level",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_ATTACK_LEVEL)
                ))
        );

        helper.getPersistentData().putInt(
                GarmrBoss.TAG_DEFENSE_LEVEL,
                (int) Math.round(EntityTuningConfig.configured(
                        section,
                        "defense_level",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE_LEVEL)
                ))
        );
    }

    private static void syncAttribute(
            GarmrHelperEntity helper,
            String section,
            String key,
            Attribute attribute
    ) {
        AttributeInstance instance = helper.getAttribute(attribute);

        // 关键修复：该实体没有注册此属性时直接跳过，绝不能 getAttributeValue(attribute)。
        if (instance == null) return;

        double configured = EntityTuningConfig.configured(
                section,
                key,
                instance.getBaseValue()
        );
        set(instance, configured);
    }

    private static String section(GarmrHelperEntity helper) {
        return switch (helper.getVariant()) {
            case GarmrHelperEntity.LAVA_GUARD -> "garmr_lava_guard";
            case GarmrHelperEntity.P1_ARCHER -> "garmr_p1_archer";
            case GarmrHelperEntity.ANUBIS -> "garmr_anubis";
            case GarmrHelperEntity.LADY_ICE -> "garmr_ice_lady";
            case GarmrHelperEntity.LADY_FIRE -> "garmr_fire_lady";
            case GarmrHelperEntity.DEVIL -> "garmr_little_devil";
            case GarmrHelperEntity.DEATH_GUARD -> "garmr_death_guard";
            default -> null;
        };
    }

    private static void set(AttributeInstance attr, double value) {
        if (attr != null
                && Double.isFinite(value)
                && Math.abs(attr.getBaseValue() - value) > 1.0E-9D) {
            attr.setBaseValue(value);
        }
    }
}
