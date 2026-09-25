package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** 加姆副本中跨实体的死亡、伤害、减速与阿努比斯 Buff 事件。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrCombatEvents {
    private static final String SLOW_UNTIL = "GarmrDeathGuardSlowUntil";
    private static final UUID SLOW_MODIFIER_ID = UUID.fromString("4f0c669c-4e08-4ca9-bf26-e31e737ad65a");

    private GarmrCombatEvents() {}

    /**
     * 冰/火幽灵按规则为无敌：玩家和普通实体不能直接打掉。
     * 相反属性吐息由 GarmrBoss 直接 discard，对应“被 Boss 相反属性扇形 AOE 秒杀”。
     */
    @SubscribeEvent
    public static void blockLadyDirectDamage(LivingAttackEvent event) {
        // 本类主动重新结算的小怪伤害会再次经过 LivingAttackEvent；不能二次拦截造成递归。
        if (GarmrOutgoingDamageFixEvents.isApplyingForcedHit()) return;

        if (GarmrBoss.ROLE_LADY.equals(event.getEntity().getPersistentData().getString(GarmrBoss.TAG_ROLE))) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;

        String role = attacker.getPersistentData().getString(GarmrBoss.TAG_ROLE);
        GarmrBoss boss = ownerBoss(attacker);
        if (boss == null || !boss.isParticipant(player)) return;
        if (!(attacker instanceof GarmrHelperEntity helper)) return;

        // 逻辑承载实体的原版攻击统一改为配置伤害，并用真正的小怪作为伤害来源。
        if (GarmrBoss.ROLE_CORE_ADD.equals(role)) {
            event.setCanceled(true);
            float raw = (float) EntityTuningConfig.configured(
                    "garmr_lava_guard", "attack_damage", GarmrConfig.LAVA_GUARD_ATTACK);
            GarmrOutgoingDamageFixEvents.hurtMinion(
                    boss, helper, player, raw,
                    "garmr_lava_guard", GarmrConfig.LAVA_GUARD_ATTACK_LEVEL);
        } else if (GarmrBoss.ROLE_P1_SKELETON.equals(role)) {
            event.setCanceled(true);
            float raw = (float) EntityTuningConfig.configured(
                    "garmr_p1_archer", "attack_damage", GarmrConfig.P1_ARCHER_ATTACK);
            GarmrOutgoingDamageFixEvents.hurtMinion(
                    boss, helper, player, raw,
                    "garmr_p1_archer", GarmrConfig.P1_ARCHER_ATTACK_LEVEL);
        } else if (GarmrBoss.ROLE_DEATH_GUARD.equals(role)) {
            event.setCanceled(true);
            float raw = (float) EntityTuningConfig.configured(
                    "garmr_death_guard", "attack_damage", GarmrConfig.DEATH_GUARD_ATTACK);
            GarmrOutgoingDamageFixEvents.hurtMinion(
                    boss, helper, player, raw,
                    "garmr_death_guard", GarmrConfig.DEATH_GUARD_ATTACK_LEVEL);
            applyExactSlow(player);
        } else if (GarmrBoss.ROLE_LADY.equals(role) || GarmrBoss.ROLE_DEVIL.equals(role)) {
            // 幽灵伤害由每秒 AOE 逻辑结算；小恶魔只负责接触自爆。
            event.setCanceled(true);
        }
    }

    /**
     * 阿努比斯祝福/守护，以及图片给出的召唤物防御值。
     */
    @SubscribeEvent
    public static void damageModifiers(LivingDamageEvent event) {
        float amount = event.getAmount();

        // P1 祝福：被祝福玩家造成伤害 +100%。
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker.level() instanceof ServerLevel) {
            GarmrBoss boss = findBossFor(attacker);
            if (boss != null && boss.isBlessed(attacker)) {
                amount *= GarmrConfig.ANUBIS_BLESS_DAMAGE_MULTIPLIER;
            }
        }

        // 逻辑承载体使用表格防御值。幽灵的“无敌”已经在 LivingAttackEvent 中单独处理。
        int defense = event.getEntity().getPersistentData().getInt(GarmrBoss.TAG_DEFENSE);
        if (defense > 0) {
            amount = Math.max(0.1F, amount - defense);
        }

        // P2 守护 / P3 分身：受到伤害 -50%。索命、献祭与分身死亡使用 OUT_OF_WORLD，不参与减伤。
        if (event.getEntity() instanceof ServerPlayer victim
                && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            GarmrBoss boss = findBossFor(victim);
            if (boss != null && boss.hasAnubisProtection(victim)) {
                amount *= GarmrConfig.ANUBIS_PROTECTION_MULTIPLIER;
            }
        }

        event.setAmount(amount);
    }

    /** 玩家死亡后在原地生成图片中的“亡灵战士”。 */
    @SubscribeEvent
    public static void playerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        GarmrBoss boss = findBossFor(player);
        if (boss == null) return;

        GarmrHelperEntity guard = GarmrContent.HELPER.get().create(level);
        if (guard == null) return;
        guard.setVariant(GarmrHelperEntity.DEATH_GUARD);
        guard.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        guard.setPersistenceRequired();
        guard.getPersistentData().putString(GarmrBoss.TAG_ROLE, GarmrBoss.ROLE_DEATH_GUARD);
        guard.getPersistentData().putUUID(GarmrBoss.TAG_OWNER, boss.getUUID());
        int guardDefense = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "armor", GarmrConfig.DEATH_GUARD_DEFENSE));
        int guardAttackLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "attack_level", GarmrConfig.DEATH_GUARD_ATTACK_LEVEL));
        int guardDefenseLevel = (int) Math.round(EntityTuningConfig.configured(
                "garmr_death_guard", "defense_level", GarmrConfig.DEATH_GUARD_DEFENSE_LEVEL));
        guard.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE, guardDefense);
        guard.getPersistentData().putInt(GarmrBoss.TAG_ATTACK_LEVEL, guardAttackLevel);
        guard.getPersistentData().putInt(GarmrBoss.TAG_DEFENSE_LEVEL, guardDefenseLevel);
        if (boss.getPersistentData().hasUUID("YellowDuckDungeon")) {
            guard.getPersistentData().putUUID("YellowDuckDungeon",
                    boss.getPersistentData().getUUID("YellowDuckDungeon"));
        }

        AttributeInstance damage = guard.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(EntityTuningConfig.configured(
                "garmr_death_guard", "attack_damage", GarmrConfig.DEATH_GUARD_ATTACK));
        AttributeInstance health = guard.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double maxHealth = EntityTuningConfig.configured(
                    "garmr_death_guard", "max_health", GarmrConfig.DEATH_GUARD_HEALTH);
            health.setBaseValue(maxHealth);
            guard.setHealth((float) maxHealth);
        }
        AttributeInstance armor = guard.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(guardDefense);
        AttributeInstance knockback = guard.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(EntityTuningConfig.configured(
                "garmr_death_guard", "knockback_resistance", 1.0D));
        level.addFreshEntity(guard);
    }

    /** 精确 -50% 移速，10 秒；重复命中只刷新持续时间。 */
    private static void applyExactSlow(ServerPlayer player) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getModifier(SLOW_MODIFIER_ID) == null) {
            movement.addTransientModifier(new AttributeModifier(
                    SLOW_MODIFIER_ID,
                    "Garmr death guard 50% slow",
                    GarmrConfig.DEATH_GUARD_SLOW_MULTIPLIER,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        player.getPersistentData().putLong(SLOW_UNTIL,
                player.level().getGameTime() + GarmrConfig.DEATH_GUARD_SLOW_TICKS);
    }

    @SubscribeEvent
    public static void removeExpiredSlow(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().contains(SLOW_UNTIL)) return;
        if (player.level().getGameTime() < player.getPersistentData().getLong(SLOW_UNTIL)) return;

        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(SLOW_MODIFIER_ID);
        player.getPersistentData().remove(SLOW_UNTIL);
    }

    private static GarmrBoss findBossFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        return level.getEntitiesOfClass(GarmrBoss.class,
                        player.getBoundingBox().inflate(96.0D), b -> b.isAlive() && b.isParticipant(player))
                .stream().findFirst().orElse(null);
    }

    private static GarmrBoss ownerBoss(Entity helper) {
        if (!(helper.level() instanceof ServerLevel level)) return null;
        if (!helper.getPersistentData().hasUUID(GarmrBoss.TAG_OWNER)) return null;
        Entity owner = level.getEntity(helper.getPersistentData().getUUID(GarmrBoss.TAG_OWNER));
        return owner instanceof GarmrBoss boss && boss.isAlive() ? boss : null;
    }
}
