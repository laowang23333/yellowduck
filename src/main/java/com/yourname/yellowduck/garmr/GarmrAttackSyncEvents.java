package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 加姆普通攻击动画/命中帧同步。
 *
 * v2：只拦截 GarmrBoss 真正的基础普攻，避免把亡灵夫人等借
 * boss.damageNoKnockback() 造成的伤害误当成 Boss 普攻取消。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrAttackSyncEvents {
    private static final int BASIC_HIT_DELAY_TICKS =
            Math.max(1, GarmrConfig.BASIC_ACTION_TICKS / 2);

    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();
    private static boolean applyingDelayedHit;

    private GarmrAttackSyncEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void delayBasicAttackDamage(LivingAttackEvent event) {
        if (applyingDelayedHit) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof GarmrBoss boss)) return;
        if (boss.level().isClientSide) return;
        if (boss.visualAction() != GarmrBoss.ACT_BASIC) return;
        if (!boss.isParticipant(player)) return;

        float expectedOriginal = GarmrConfig.BASIC_DAMAGE;
        if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
            expectedOriginal *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
        }

        // 不是 GarmrBoss#tickBreathAndBasic 发出的原始普攻，就不要拦。
        if (Math.abs(event.getAmount() - expectedOriginal) > 0.01F) return;

        event.setCanceled(true);

        float configuredDamage = (float) EntityTuningConfig.configured(
                "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
        if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
            configuredDamage *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
        }

        int now = ((ServerLevel) boss.level()).getServer().getTickCount();
        PENDING.put(boss.getUUID(), new PendingHit(
                boss.getUUID(), player.getUUID(),
                Math.max(0.0F, configuredDamage),
                now + BASIC_HIT_DELAY_TICKS
        ));
    }

    @SubscribeEvent
    public static void applyDelayedHit(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;

        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, PendingHit>> it = PENDING.entrySet().iterator();

        while (it.hasNext()) {
            PendingHit pending = it.next().getValue();
            if (now < pending.hitServerTick()) continue;
            it.remove();

            GarmrBoss boss = findBoss(event, pending.bossId());
            ServerPlayer player =
                    event.getServer().getPlayerList().getPlayer(pending.targetId());

            if (boss == null || !boss.isAlive()) continue;
            if (player == null || !player.isAlive() || player.isRemoved()) continue;
            if (!boss.isParticipant(player)) continue;
            if (boss.visualAction() != GarmrBoss.ACT_BASIC) continue;

            double maxRange = GarmrConfig.MELEE_RANGE;
            if (boss.distanceToSqr(player) > maxRange * maxRange) continue;

            boss.faceTargetForAttack(player);

            float finalRawDamage =
                    Netcraft123CombatBridge.applyBossTierSuppression(
                            player, pending.damage());

            applyingDelayedHit = true;
            try {
                boss.damageNoKnockback(player, finalRawDamage);
            } finally {
                applyingDelayedHit = false;
            }
        }
    }

    private static GarmrBoss findBoss(TickEvent.ServerTickEvent event, UUID id) {
        if (id == null) return null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof GarmrBoss boss) return boss;
        }
        return null;
    }

    private record PendingHit(
            UUID bossId, UUID targetId, float damage, int hitServerTick) {}
}
