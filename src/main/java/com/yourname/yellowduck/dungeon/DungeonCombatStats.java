package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 副本结算统计。
 *
 * 输出使用 LivingDamageEvent 的最终伤害值，因此能自然兼容 NetCraft 装备、技能、减伤等
 * 已经完成计算后的实际伤害。承伤同理。
 * 治疗统计使用 Forge 的实际 LivingHealEvent，并裁掉超出最大生命的无效治疗。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DungeonCombatStats {
    private static final String DUNGEON_TAG = "YellowDuckDungeon";

    private DungeonCombatStats() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        // 队员承伤：只统计真正处于战斗阶段的玩家。
        if (victim instanceof ServerPlayer player) {
            DungeonInstance instance = DungeonManager.instanceOf(player);
            if (instance != null && instance.state == DungeonInstance.State.FIGHTING) {
                add(instance.damageTaken, player.getUUID(), event.getAmount());
            }
        }

        // 队员输出：只统计打到本场副本实体的最终有效伤害，不计 PvP/自伤。
        ServerPlayer attacker = resolvePlayer(event.getSource());
        if (attacker == null || victim instanceof Player) return;
        DungeonInstance instance = DungeonManager.instanceOf(attacker);
        if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;
        if (!belongsToInstance(victim, instance)) return;

        add(instance.damageDealt, attacker.getUUID(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHeal(LivingHealEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DungeonInstance instance = DungeonManager.instanceOf(player);
        if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;

        float missing = Math.max(0.0F, player.getMaxHealth() - player.getHealth());
        double effective = Math.min(event.getAmount(), missing);
        if (effective > 0.0D) add(instance.healingDone, player.getUUID(), effective);
    }

    private static ServerPlayer resolvePlayer(DamageSource source) {
        Entity owner = source.getEntity();
        if (owner instanceof ServerPlayer player) return player;

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static boolean belongsToInstance(LivingEntity entity, DungeonInstance instance) {
        if (entity == null || instance == null) return false;
        if (instance.mainBossId != null && instance.mainBossId.equals(entity.getUUID())) return true;
        if (entity.getPersistentData().hasUUID(DUNGEON_TAG)) {
            return instance.id.equals(entity.getPersistentData().getUUID(DUNGEON_TAG));
        }
        return false;
    }

    private static void add(java.util.Map<UUID, Double> map, UUID player, double value) {
        if (player == null || !Double.isFinite(value) || value <= 0.0D) return;
        map.merge(player, value, Double::sum);
    }
}
