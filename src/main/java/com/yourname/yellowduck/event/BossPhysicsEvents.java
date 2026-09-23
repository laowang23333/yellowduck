package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.entity.MountEntity;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boss 物理规则统一入口。
 *
 * 1. YellowDuck Boss 自身不接受 vanilla knockback。
 * 2. YellowDuck Boss/召唤物伤害玩家时，记录受击前速度；
 *    服务器 tick 结束时恢复，从而同时消除：
 *    - vanilla hurt knockback
 *    - 技能在 hurt() 之后额外写入的上抛速度
 *
 * 黑洞持续吸人、抓取传送等“不造成伤害的技能移动”不会触发 LivingAttackEvent，
 * 因此不会被这里取消。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossPhysicsEvents {
    private static final Map<UUID, Vec3> PLAYER_MOTION_BEFORE_BOSS_HIT = new LinkedHashMap<>();

    private BossPhysicsEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isYellowDuckBossDamage(event.getSource())) {
            return;
        }

        // 同一个服务器 tick 被连续命中时，只保存第一次受击前的真实速度。
        PLAYER_MOTION_BEFORE_BOSS_HIT.putIfAbsent(
                player.getUUID(),
                player.getDeltaMovement()
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        // 玩家攻击 Boss 时：Boss 不被武器/伤害击退。
        if (isMainBoss(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        // Boss 攻击玩家时：直接取消 vanilla knockback。
        if (event.getEntity() instanceof ServerPlayer player
                && PLAYER_MOTION_BEFORE_BOSS_HIT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PLAYER_MOTION_BEFORE_BOSS_HIT.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Vec3> entry : PLAYER_MOTION_BEFORE_BOSS_HIT.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved()) {
                continue;
            }

            player.setDeltaMovement(entry.getValue());
            player.hurtMarked = true;
        }

        PLAYER_MOTION_BEFORE_BOSS_HIT.clear();
    }

    private static boolean isMainBoss(Entity entity) {
        return entity instanceof NetcraftBossBase
                || entity instanceof SakurawitchEntity
                || entity instanceof ToyBearEntity
                || entity instanceof TwoPhaseBossEntity;
    }

    /**
     * 当前和以后所有 YellowDuck 实体来源伤害都按“Boss 技能伤害无击退”处理。
     * 坐骑明确排除，避免未来坐骑扩展攻击能力时被误判成 Boss。
     */
    private static boolean isYellowDuckBossDamage(DamageSource source) {
        return isYellowDuckCombatEntity(source.getEntity())
                || isYellowDuckCombatEntity(source.getDirectEntity());
    }

    private static boolean isYellowDuckCombatEntity(Entity entity) {
        if (entity == null || entity instanceof MountEntity) {
            return false;
        }

        if (isMainBoss(entity)) {
            return true;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && YellowDuckMod.MOD_ID.equals(id.getNamespace());
    }
}
