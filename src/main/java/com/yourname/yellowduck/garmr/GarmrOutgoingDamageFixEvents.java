package com.yourname.yellowduck.garmr;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 加姆体系的最终出伤桥。
 *
 * YellowDuck 的加姆不是 NetCraft 原生实体；如果先把 YellowDuck 配置伤害送进
 * NetCraft 的玩家装备事件，再继续走原版护甲，某些高阶装备会把伤害重复压到 0。
 * 这里仍然读取 NetCraft TotalTier 做等级修正，但在最终伤害阶段锁定本次应结算值，
 * 避免同一笔 YellowDuck 伤害被错误重复防御。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class GarmrOutgoingDamageFixEvents {
    /** NetCraft 装备仍可减伤，但不能把 YellowDuck 加姆体系的合法攻击完全压成 0。 */
    private static final float MIN_FINAL_RATIO = 0.20F;
    private static final Map<UUID, PendingDamage> PENDING = new HashMap<>();
    private static final ThreadLocal<Boolean> APPLYING_FORCED_HIT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private GarmrOutgoingDamageFixEvents() {}

    /** 召唤物统一出口：使用真正召唤物作为攻击者，并读取该分组的 attack_level。 */
    public static boolean hurtMinion(
            GarmrBoss boss,
            LivingEntity attacker,
            ServerPlayer target,
            float rawDamage,
            String section,
            int fallbackTier
    ) {
        if (boss == null || attacker == null || target == null || !target.isAlive() || rawDamage <= 0.0F) {
            return false;
        }

        float finalDamage = Netcraft123CombatBridge.applyMinionTierSuppression(
                target,
                rawDamage,
                section,
                fallbackTier
        );
        return hurtLocked(boss, attacker, target, finalDamage);
    }

    /**
     * 射手投射物当前没有保存具体射手 UUID；仍以 Boss 作为 vanilla 来源，
     * 但按射手自己的配置和 T 级锁定最终伤害。
     */
    public static boolean hurtMinionFromBossSource(
            GarmrBoss boss,
            ServerPlayer target,
            float rawDamage,
            String section,
            int fallbackTier
    ) {
        if (boss == null || target == null || !target.isAlive() || rawDamage <= 0.0F) return false;
        float finalDamage = Netcraft123CombatBridge.applyMinionTierSuppression(
                target,
                rawDamage,
                section,
                fallbackTier
        );
        return hurtLocked(boss, boss, target, finalDamage);
    }

    private static boolean hurtLocked(
            GarmrBoss boss, LivingEntity attacker, ServerPlayer target, float finalDamage) {
        if (finalDamage <= 0.0F) return false;

        float protectedFloor = Math.max(0.1F, finalDamage * MIN_FINAL_RATIO
                * protectionMultiplier(boss, target));
        PendingDamage pending = new PendingDamage(
                attacker.getUUID(),
                Math.max(0.1F, finalDamage),
                protectedFloor,
                target.level().getGameTime()
        );
        PENDING.put(target.getUUID(), pending);

        Vec3 oldMotion = target.getDeltaMovement();
        boolean previous = APPLYING_FORCED_HIT.get();
        boolean hit;
        APPLYING_FORCED_HIT.set(Boolean.TRUE);
        try {
            hit = target.hurt(target.damageSources().mobAttack(attacker), pending.damage());
        } finally {
            APPLYING_FORCED_HIT.set(previous);
        }
        if (hit) {
            target.setDeltaMovement(oldMotion);
            target.hurtMarked = true;
        }

        // 正常命中会在 LivingDamageEvent 中消费；被盾/无敌帧直接拦截时在这里清理。
        if (PENDING.get(target.getUUID()) == pending) {
            PENDING.remove(target.getUUID());
        }
        return hit;
    }

    static boolean isApplyingForcedHit() {
        return APPLYING_FORCED_HIT.get();
    }

    /**
     * Boss 本体以及 GarmrBoss 内部代替夫人/小恶魔发出的伤害在这里识别。
     * HIGHEST 阶段先记录“这一击应该结算多少”，不依赖后续事件对 amount 的修改。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureBossDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof GarmrBoss boss)) return;
        if (!boss.isParticipant(player)) return;

        long gameTime = player.level().getGameTime();
        PendingDamage already = PENDING.get(player.getUUID());
        if (matches(already, boss, gameTime)) {
            // 由 hurtMinionFromBossSource 主动登记的射手伤害，不要再按 Boss 技能重算。
            return;
        }

        float finalDamage = expectedBossSourceDamage(boss, player, event.getAmount());
        if (finalDamage > 0.0F) {
            float floor = Math.max(0.1F, finalDamage * MIN_FINAL_RATIO
                    * protectionMultiplier(boss, player));
            PENDING.put(player.getUUID(), new PendingDamage(
                    boss.getUUID(), Math.max(0.1F, finalDamage), floor, gameTime));
        }
    }

    /**
     * NetCraft 的装备事件通常位于 LivingHurtEvent；LOWEST 再恢复本次已计算好的值，
     * 防止它在进入原版护甲阶段前已经被压成 0。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreHurtAmount(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        PendingDamage pending = PENDING.get(player.getUUID());
        if (!matches(pending, attacker, player.level().getGameTime())) return;
        if (event.isCanceled()) event.setCanceled(false);
        event.setAmount(Math.max(event.getAmount(), pending.floor()));
    }

    /** 最终生命扣除前设置同样的 20% 下限，NetCraft/原版装备仍然可以正常减伤。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restoreFinalDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        PendingDamage pending = PENDING.get(player.getUUID());
        if (!matches(pending, attacker, player.level().getGameTime())) return;

        event.setAmount(Math.max(event.getAmount(), pending.floor()));
        PENDING.remove(player.getUUID());
    }

    private static float expectedBossSourceDamage(GarmrBoss boss, ServerPlayer player, float incomingRaw) {
        int action = boss.visualAction();

        if (action == GarmrBoss.ACT_BASIC) {
            float raw = (float) EntityTuningConfig.configured(
                    "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
            if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
                raw *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
            }
            return Netcraft123CombatBridge.applyBossTierSuppression(player, raw);
        }

        if (action == GarmrBoss.ACT_FIRE_BREATH || action == GarmrBoss.ACT_ICE_BREATH) {
            float basic = (float) EntityTuningConfig.configured(
                    "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
            float raw = basic * 0.50F / 3.0F;
            return Netcraft123CombatBridge.applyBossTierSuppression(player, raw);
        }

        // P3 小恶魔爆炸由 GarmrBoss 代为 hurt；原始值固定为玩家最大生命的 20%。
        float devilRaw = player.getMaxHealth() * GarmrConfig.DEVIL_EXPLOSION_MAX_HEALTH_RATIO;
        if (Math.abs(incomingRaw - devilRaw) <= Math.max(0.05F, devilRaw * 0.02F)
                && hasOwnedDevilNear(boss, player)) {
            return Netcraft123CombatBridge.applyMinionTierSuppression(
                    player,
                    devilRaw,
                    "garmr_little_devil",
                    GarmrConfig.DEVIL_ATTACK_LEVEL
            );
        }

        // 3.5 格内的亡灵夫人 AOE 也由 GarmrBoss 代为 hurt；补回夫人自己的等级和配置。
        GarmrHelperEntity lady = nearestOwnedLady(boss, player);
        if (lady != null) {
            int type = lady.getPersistentData().getInt(GarmrBoss.TAG_LADY_TYPE);
            String section = type == GarmrBoss.BREATH_FIRE
                    ? "garmr_fire_lady" : "garmr_ice_lady";
            int born = lady.getPersistentData().getInt(GarmrBoss.TAG_LADY_SPAWN_TICK);
            int elapsedSeconds = Math.max(0, (boss.tickCount - born) / 20);
            float raw = (float) EntityTuningConfig.configured(
                    section, "attack_damage", GarmrConfig.LADY_BASE_DAMAGE);
            raw *= 1.0F + elapsedSeconds * GarmrConfig.LADY_DAMAGE_GROWTH_PER_SECOND;
            return Netcraft123CombatBridge.applyMinionTierSuppression(
                    player, raw, section, GarmrConfig.LADY_ATTACK_LEVEL);
        }

        return -1.0F;
    }

    private static float protectionMultiplier(GarmrBoss boss, ServerPlayer player) {
        return boss != null && player != null && boss.hasAnubisProtection(player)
                ? GarmrConfig.ANUBIS_PROTECTION_MULTIPLIER
                : 1.0F;
    }

    private static boolean hasOwnedDevilNear(GarmrBoss boss, ServerPlayer player) {
        double r = GarmrConfig.DEVIL_EXPLOSION_RADIUS + 0.5D;
        for (GarmrHelperEntity helper : player.level().getEntitiesOfClass(
                GarmrHelperEntity.class,
                player.getBoundingBox().inflate(r),
                e -> e.isAlive() && e.getVariant() == GarmrHelperEntity.DEVIL)) {
            if (ownedBy(helper, boss) && player.distanceToSqr(helper) <= r * r) return true;
        }
        return false;
    }

    private static GarmrHelperEntity nearestOwnedLady(GarmrBoss boss, ServerPlayer player) {
        double r = GarmrConfig.LADY_AOE_RADIUS + 0.15D;
        GarmrHelperEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (GarmrHelperEntity helper : player.level().getEntitiesOfClass(
                GarmrHelperEntity.class,
                player.getBoundingBox().inflate(r),
                GarmrHelperEntity::isLady)) {
            if (!helper.isAlive() || !ownedBy(helper, boss)) continue;
            double d2 = player.distanceToSqr(helper);
            if (d2 <= r * r && d2 < best) {
                best = d2;
                nearest = helper;
            }
        }
        return nearest;
    }

    private static boolean ownedBy(GarmrHelperEntity helper, GarmrBoss boss) {
        return helper.getPersistentData().hasUUID(GarmrBoss.TAG_OWNER)
                && boss.getUUID().equals(helper.getPersistentData().getUUID(GarmrBoss.TAG_OWNER));
    }

    private static boolean matches(PendingDamage pending, LivingEntity attacker, long gameTime) {
        return pending != null
                && pending.gameTime() == gameTime
                && pending.attackerId().equals(attacker.getUUID());
    }

    private record PendingDamage(UUID attackerId, float damage, float floor, long gameTime) {}
}
