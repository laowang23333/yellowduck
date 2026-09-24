package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Garmr Boss / helper 运行时配置同步。 */
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
        syncMaxHealth(boss, EntityTuningConfig.configured(
                "garmr", "max_health", boss.getMaxHealth()));

        AttributeInstance attackAttr = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        double currentAttack = attackAttr != null
                ? attackAttr.getBaseValue() : GarmrConfig.BASIC_DAMAGE;
        double attack = EntityTuningConfig.configured(
                "garmr", "attack_damage", currentAttack);
        set(attackAttr, attack);

        syncAttribute(boss, "garmr", "knockback_resistance",
                Attributes.KNOCKBACK_RESISTANCE);
        syncAttribute(boss, "garmr", "follow_range",
                Attributes.FOLLOW_RANGE);

        int attackLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr", "attack_level",
                EntityTuningConfig.configured(
                        "garmr", "netcraft_tier",
                        GarmrConfig.BOSS_ATTACK_LEVEL)));

        int defenseLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr", "defense_level", GarmrConfig.BOSS_DEFENSE_LEVEL));

        boss.setBaseTier(Math.max(0, attackLevel));
        boss.setBaseDamage((int) Math.round(attack));
        boss.setBaseDefense(Math.max(0, defenseLevel));
    }

    private static void syncHelper(GarmrHelperEntity helper) {
        String section = section(helper);
        if (section == null) return;

        syncMaxHealth(helper, EntityTuningConfig.configured(
                section, "max_health", helper.getMaxHealth()));

        syncAttribute(helper, section, "attack_damage", Attributes.ATTACK_DAMAGE);
        syncAttribute(helper, section, "movement_speed", Attributes.MOVEMENT_SPEED);
        syncAttribute(helper, section, "attack_speed", Attributes.ATTACK_SPEED);
        syncAttribute(helper, section, "armor", Attributes.ARMOR);
        syncAttribute(helper, section, "armor_toughness", Attributes.ARMOR_TOUGHNESS);
        syncAttribute(helper, section, "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE);
        syncAttribute(helper, section, "follow_range", Attributes.FOLLOW_RANGE);
        syncAttribute(helper, section, "attack_knockback", Attributes.ATTACK_KNOCKBACK);

        helper.getPersistentData().putInt(
                GarmrBoss.TAG_DEFENSE,
                (int) Math.round(EntityTuningConfig.configured(
                        section, "armor",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE))));

        helper.getPersistentData().putInt(
                GarmrBoss.TAG_ATTACK_LEVEL,
                (int) Math.round(EntityTuningConfig.configured(
                        section, "attack_level",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_ATTACK_LEVEL))));

        helper.getPersistentData().putInt(
                GarmrBoss.TAG_DEFENSE_LEVEL,
                (int) Math.round(EntityTuningConfig.configured(
                        section, "defense_level",
                        helper.getPersistentData().getInt(GarmrBoss.TAG_DEFENSE_LEVEL))));
    }

    private static void syncMaxHealth(LivingEntity entity, double maxHealth) {
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null || !Double.isFinite(maxHealth) || maxHealth <= 0.0D) return;
        if (Math.abs(attr.getBaseValue() - maxHealth) <= 1.0E-9D) return;

        double oldMax = Math.max(1.0D, entity.getMaxHealth());
        double ratio = entity.getHealth() / oldMax;
        attr.setBaseValue(maxHealth);
        entity.setHealth((float) Math.max(
                0.1D, Math.min(maxHealth, maxHealth * ratio)));
    }

    private static void syncAttribute(
            LivingEntity entity,
            String section,
            String key,
            Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;

        double configured = EntityTuningConfig.configured(
                section, key, instance.getBaseValue());
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
