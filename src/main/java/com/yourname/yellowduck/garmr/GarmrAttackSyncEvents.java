package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
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
 * 修复加姆普通攻击“先出伤、后看到攻击动画”的问题。
 *
 * 原逻辑在 beginTimedAction(ACT_BASIC) 后同一 tick 立即造成伤害。
 * 这里拦截这次普通攻击伤害，并延迟到攻击动作中段再结算。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrAttackSyncEvents {
    /** 22 tick 普攻动作的中段命中。 */
    private static final int BASIC_HIT_DELAY_TICKS =
            Math.max(1, GarmrConfig.BASIC_ACTION_TICKS / 2);

    /** 每个加姆同时最多只有一发待结算普通攻击。 */
    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();

    /**
     * 延迟伤害重新调用 GarmrBoss#damageNoKnockback 时会再次触发 LivingAttackEvent，
     * 用这个标记避免把已经延迟过的伤害再次拦截。
     */
    private static boolean applyingDelayedHit;

    private GarmrAttackSyncEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void delayBasicAttackDamage(LivingAttackEvent event) {
        if (applyingDelayedHit) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof GarmrBoss boss)) return;
        if (boss.level().isClientSide) return;

        // 只拦截 Boss 的近战普攻。
        // 吐息、献祭、小恶魔等其它 Garmr 伤害保持原机制。
        if (boss.visualAction() != GarmrBoss.ACT_BASIC) return;
        if (!boss.isParticipant(player)) return;

        event.setCanceled(true);

        int now = ((ServerLevel) boss.level()).getServer().getTickCount();
        PENDING.put(boss.getUUID(), new PendingHit(
                boss.getUUID(),
                player.getUUID(),
                Math.max(0.0F, event.getAmount()),
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
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(pending.targetId());
            if (boss == null || !boss.isAlive()) continue;
            if (player == null || !player.isAlive() || player.isRemoved()) continue;
            if (!boss.isParticipant(player)) continue;

            // 攻击动作被别的机制打断时，不再补一发“幽灵伤害”。
            if (boss.visualAction() != GarmrBoss.ACT_BASIC) continue;

            // 命中帧再检查一次距离：玩家已经躲开则这一击打空。
            double maxRange = GarmrConfig.MELEE_RANGE;
            if (boss.distanceToSqr(player) > maxRange * maxRange) continue;

            boss.faceTargetForAttack(player);

            applyingDelayedHit = true;
            try {
                boss.damageNoKnockback(player, pending.damage());
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

    private record PendingHit(UUID bossId, UUID targetId, float damage, int hitServerTick) {
    }
}
