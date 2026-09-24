package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Garmr helper 共用一个 entity id，普通 EntityTuningConfig 只能命中 garmr_helper。
 * 这里按 variant 每秒重新同步一次各自独立配置，/yd reload 后无需重生怪物。
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
        double attack = EntityTuningConfig.configured(
                "garmr", "attack_damage", boss.getAttributeValue(Attributes.ATTACK_DAMAGE));
        set(boss.getAttribute(Attributes.ATTACK_DAMAGE), attack);

        int attackLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr", "attack_level",
                EntityTuningConfig.configured("garmr", "netcraft_tier", GarmrConfig.BOSS_ATTACK_LEVEL)));
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
            helper.setHealth((float)Math.max(0.1D, Math.min(maxHealth, maxHealth * ratio)));
        }

        set(helper.getAttribute(Attributes.ATTACK_DAMAGE),
                EntityTuningConfig.configured(section, "attack_damage",
                        helper.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        set(helper.getAttribute(Attributes.MOVEMENT_SPEED),
                EntityTuningConfig.configured(section, "movement_speed",
                        helper.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        set(helper.getAttribute(Attributes.ATTACK_SPEED),
                EntityTuningConfig.configured(section, "attack_speed",
                        helper.getAttributeValue(Attributes.ATTACK_SPEED)));
        set(helper.getAttribute(Attributes.ARMOR),
                EntityTuningConfig.configured(section, "armor",
                        helper.getAttributeValue(Attributes.ARMOR)));
        set(helper.getAttribute(Attributes.ARMOR_TOUGHNESS),
                EntityTuningConfig.configured(section, "armor_toughness",
                        helper.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
        set(helper.getAttribute(Attributes.KNOCKBACK_RESISTANCE),
                EntityTuningConfig.configured(section, "knockback_resistance",
                        helper.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        set(helper.getAttribute(Attributes.FOLLOW_RANGE),
                EntityTuningConfig.configured(section, "follow_range",
                        helper.getAttributeValue(Attributes.FOLLOW_RANGE)));

        helper.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE,
                (int)Math.round(EntityTuningConfig.configured(
                        section, "armor", helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE))));
        helper.getPersistentData().putInt(GarmrBoss.TAG_ATTACK_LEVEL,
                (int)Math.round(EntityTuningConfig.configured(
                        section, "attack_level", helper.getPersistentData().getInt(GarmrBoss.TAG_ATTACK_LEVEL))));
        helper.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE_LEVEL,
                (int)Math.round(EntityTuningConfig.configured(
                        section, "defense_level", helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE_LEVEL))));
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
        if (attr != null && Double.isFinite(value)
                && Math.abs(attr.getBaseValue() - value) > 1.0E-9D) {
            attr.setBaseValue(value);
        }
    }
}
