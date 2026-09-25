package com.yourname.yellowduck.garmr;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.config.EntityTuningConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
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
    /*
     * v2:
     * NetCraft 在某些装备组合下会在 LivingAttackEvent 就取消 YellowDuck 的攻击。
     * 如果第一层已经 cancel，LivingHurt/LivingDamage 根本不会触发。
     * 因此这里从 Attack -> Hurt -> Damage 三层连续兜底。
     */
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field delayedHitField;
    private static boolean delayedHitFieldResolved;
    private static int fallbackLogBudget = 12;
    private static int verifyLogBudget = 20;
    private static int reconcileLogBudget = 20;

    /** NetCraft 装备仍可减伤，但不能把 YellowDuck 加姆体系的合法攻击完全压成 0。 */
    private static final float MIN_FINAL_RATIO = 0.20F;
    private static final Map<UUID, PendingDamage> PENDING = new HashMap<>();
    private static final Map<UUID, PendingDamage> FALLBACK = new HashMap<>();
    /** 下一 tick 核对“实际生命+吸收”是否真的下降，彻底绕开其它 Mod 的后置改写。 */
    private static final Map<UUID, HealthCheck> VERIFY = new HashMap<>();
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
        scheduleHealthCheck(target, attacker, protectedFloor, "forced");
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
        if (PENDING.get(target.getUUID()) == pending
                && FALLBACK.get(target.getUUID()) != pending) {
            PENDING.remove(target.getUUID());
        }
        return hit;
    }

    static boolean isApplyingForcedHit() {
        return APPLYING_FORCED_HIT.get();
    }

    /**
     * 第一层：LivingAttackEvent。
     *
     * NetCraft 如果在这里直接 cancel，后续 LivingHurt/LivingDamage 都不会出现。
     * 但 GarmrAttackSyncEvents 会故意 cancel “普攻动画刚开始的第一次攻击”来延迟到命中帧，
     * 那一次必须继续保持取消，否则会造成普攻双重伤害。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreAttackEvent(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        long gameTime = player.level().getGameTime();

        // hurtMinion()/hurtMinionFromBossSource() 主动发起的最终伤害已经提前登记。
        PendingDamage forced = PENDING.get(player.getUUID());
        if (APPLYING_FORCED_HIT.get() && matches(forced, attacker, gameTime)) {
            if (event.isCanceled()) {
                FALLBACK.put(player.getUUID(), forced);
                event.setCanceled(false);
            }
            return;
        }

        if (!(attacker instanceof GarmrBoss boss) || !boss.isParticipant(player)) return;

        boolean delayedBasic = isApplyingDelayedBossHit();

        float expectedOriginalBasic = GarmrConfig.BASIC_DAMAGE;
        if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
            expectedOriginalBasic *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
        }

        /*
         * 这一次是 GarmrAttackSyncEvents 为了“动画先播、命中帧再出伤”主动 cancel 的原始普攻。
         * 不能把它救回来，否则玩家会先吃一次，命中帧再吃一次。
         */
        boolean animationProbe = boss.visualAction() == GarmrBoss.ACT_BASIC
                && !delayedBasic
                && Math.abs(event.getAmount() - expectedOriginalBasic) <= 0.01F;
        if (animationProbe) return;

        float finalDamage = expectedBossAttackDamage(
                boss, player, event.getAmount(), delayedBasic);
        if (finalDamage <= 0.0F) return;

        float floor = Math.max(0.1F, finalDamage * MIN_FINAL_RATIO
                * protectionMultiplier(boss, player));

        PENDING.put(player.getUUID(), new PendingDamage(
                boss.getUUID(),
                Math.max(0.1F, finalDamage),
                floor,
                gameTime
        ));
        scheduleHealthCheck(player, boss, floor, "attack");

        if (event.isCanceled()) {
            FALLBACK.put(player.getUUID(), PENDING.get(player.getUUID()));
            event.setCanceled(false);
        }
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
            scheduleHealthCheck(player, boss, floor, "hurt");
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
        if (event.isCanceled()) {
            FALLBACK.put(player.getUUID(), pending);
            event.setCanceled(false);
        }
        event.setAmount(Math.max(event.getAmount(), pending.floor()));
    }

    /** 最终生命扣除前设置同样的 20% 下限，NetCraft/原版装备仍然可以正常减伤。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreFinalDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        PendingDamage pending = PENDING.get(player.getUUID());
        if (!matches(pending, attacker, player.level().getGameTime())) return;

        if (event.isCanceled()) {
            FALLBACK.put(player.getUUID(), pending);
            event.setCanceled(false);
        }
        event.setAmount(Math.max(event.getAmount(), pending.floor()));
        PENDING.remove(player.getUUID());
        FALLBACK.remove(player.getUUID());
    }

    /**
     * 如果其它模组在同一 LOWEST 优先级、并且排在本类之后再次取消攻击，
     * 当次 hurt() 仍可能直接返回 false。ServerTick END 再检查一次：
     * 正常进入 LivingDamage 的攻击已经从 FALLBACK 删除；只剩真正被提前吞掉的攻击。
     */
    @SubscribeEvent
    public static void applyCanceledAttackFallback(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var iterator = FALLBACK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingDamage> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            PendingDamage pending = entry.getValue();

            if (player == null || !player.isAlive() || player.isRemoved()) {
                PENDING.remove(entry.getKey());
                iterator.remove();
                continue;
            }

            // 不允许旧记录跨 tick 继续扣血。
            long now = player.level().getGameTime();
            if (now < pending.gameTime()) continue;
            if (now > pending.gameTime() + 1L) {
                PENDING.remove(entry.getKey());
                iterator.remove();
                continue;
            }

            float remaining = pending.floor();
            float absorption = player.getAbsorptionAmount();
            if (absorption > 0.0F) {
                float absorbed = Math.min(absorption, remaining);
                player.setAbsorptionAmount(absorption - absorbed);
                remaining -= absorbed;
            }

            if (remaining > 0.0F) {
                float before = player.getHealth();
                player.setHealth(Math.max(0.0F, before - remaining));
                player.hurtMarked = true;

                if (fallbackLogBudget > 0) {
                    fallbackLogBudget--;
                    LOGGER.info("[GarmrDamageFix] NetCraft 提前取消攻击，已执行最终兜底：player={}, damage={}",
                            player.getGameProfile().getName(), pending.floor());
                }

                if (before > 0.0F && player.getHealth() <= 0.0F) {
                    player.die(player.damageSources().generic());
                }
            }

            PENDING.remove(entry.getKey());
            iterator.remove();
        }

        /*
         * v3 最终核对：
         * LivingDamageEvent 即使成功触发，也可能被 Mohist/NetCraft 在本监听器之后再次改写，
         * 因而“事件没取消”并不等于玩家真的扣了血。
         * 下一 tick 用服务端真实 health + absorption 对账；不足最低伤害时只补差额。
         */
        if (!VERIFY.isEmpty()) {
            var verifyIterator = VERIFY.entrySet().iterator();
            while (verifyIterator.hasNext()) {
                Map.Entry<UUID, HealthCheck> entry = verifyIterator.next();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                HealthCheck check = entry.getValue();

                if (player == null || !player.isAlive() || player.isRemoved()) {
                    verifyIterator.remove();
                    continue;
                }

                long now = player.level().getGameTime();
                if (now <= check.gameTime()) continue;
                if (now > check.gameTime() + 3L) {
                    verifyIterator.remove();
                    continue;
                }

                float currentEffective = effectiveHealth(player);
                float actuallyLost = Math.max(0.0F, check.beforeEffectiveHealth() - currentEffective);
                float missing = check.requiredFloor() - actuallyLost;

                if (missing > 0.01F) {
                    forceRemoveEffectiveHealth(player, missing);

                    if (reconcileLogBudget > 0) {
                        reconcileLogBudget--;
                        LOGGER.info(
                                "[GarmrDamageFix] 最终生命核对补伤：player={}, stage={}, expectedMin={}, actuallyLost={}, supplemented={}",
                                player.getGameProfile().getName(),
                                check.stage(),
                                check.requiredFloor(),
                                actuallyLost,
                                missing
                        );
                    }
                }

                verifyIterator.remove();
            }
        }
    }

    /**
     * 登记一笔伤害的“实际生命核对”。
     * 同一玩家同一 tick 可能同时经过 Attack/Hurt 两层，只取更高的最低伤害，避免重复计算。
     */
    private static void scheduleHealthCheck(
            ServerPlayer player,
            LivingEntity attacker,
            float requiredFloor,
            String stage
    ) {
        if (player == null || attacker == null || requiredFloor <= 0.0F) return;

        long gameTime = player.level().getGameTime();
        float before = effectiveHealth(player);
        HealthCheck previous = VERIFY.get(player.getUUID());

        if (previous != null && previous.gameTime() == gameTime) {
            VERIFY.put(player.getUUID(), new HealthCheck(
                    Math.max(previous.beforeEffectiveHealth(), before),
                    Math.max(previous.requiredFloor(), requiredFloor),
                    gameTime,
                    previous.stage() + "+" + stage
            ));
        } else {
            VERIFY.put(player.getUUID(), new HealthCheck(
                    before,
                    requiredFloor,
                    gameTime,
                    stage
            ));
        }

        if (verifyLogBudget > 0) {
            verifyLogBudget--;
            LOGGER.info(
                    "[GarmrDamageFix] 已登记实际生命核对：player={}, attacker={}, stage={}, hpPlusAbsorption={}, expectedMin={}",
                    player.getGameProfile().getName(),
                    attacker.getClass().getSimpleName(),
                    stage,
                    before,
                    requiredFloor
            );
        }
    }

    private static float effectiveHealth(ServerPlayer player) {
        return Math.max(0.0F, player.getHealth())
                + Math.max(0.0F, player.getAbsorptionAmount());
    }

    /**
     * 只补“缺失”的那一部分；优先扣吸收值，再扣真实生命。
     * 这是事件链全部结束后的最终保险，因此不再重新调用 hurt()，避免再次被同一套装备逻辑吞掉。
     */
    private static void forceRemoveEffectiveHealth(ServerPlayer player, float amount) {
        if (player == null || amount <= 0.0F) return;

        float remaining = amount;
        float absorption = Math.max(0.0F, player.getAbsorptionAmount());
        if (absorption > 0.0F) {
            float absorbed = Math.min(absorption, remaining);
            player.setAbsorptionAmount(absorption - absorbed);
            remaining -= absorbed;
        }

        if (remaining <= 0.0F) return;

        float before = player.getHealth();
        float after = Math.max(0.0F, before - remaining);
        player.setHealth(after);
        player.hurtMarked = true;

        if (before > 0.0F && after <= 0.0F) {
            player.die(player.damageSources().generic());
        }
    }

    /**
     * LivingAttackEvent 仍保留 hurt() 刚传入的原始 amount，因此最适合判断这一击本来是什么。
     */
    private static float expectedBossAttackDamage(
            GarmrBoss boss,
            ServerPlayer player,
            float incomingRaw,
            boolean delayedBasic
    ) {
        int action = boss.visualAction();

        if (delayedBasic && action == GarmrBoss.ACT_BASIC) {
            float raw = (float) EntityTuningConfig.configured(
                    "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
            if (boss.getEntityData().get(GarmrBoss.PHASE) == GarmrBoss.P3) {
                raw *= GarmrConfig.PHASE_THREE_BASIC_MULTIPLIER;
            }
            return Netcraft123CombatBridge.applyBossTierSuppression(player, raw);
        }

        float originalBreathTick = GarmrConfig.BREATH_DAMAGE / 3.0F;
        if ((action == GarmrBoss.ACT_FIRE_BREATH || action == GarmrBoss.ACT_ICE_BREATH)
                && Math.abs(incomingRaw - originalBreathTick) <= 0.01F) {
            float basic = (float) EntityTuningConfig.configured(
                    "garmr", "attack_damage", GarmrConfig.BASIC_DAMAGE);
            float raw = basic * 0.50F / 3.0F;
            return Netcraft123CombatBridge.applyBossTierSuppression(player, raw);
        }

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

        /*
         * 兼容旧版调用：熔岩守卫/亡灵战士/射手曾由 boss.damageNoKnockback() 代为出伤。
         * 这类伤害已经在调用点读取配置；这里保留传入值并只负责防止被装备事件清零。
         */
        return incomingRaw > 0.0F ? incomingRaw : -1.0F;
    }

    /**
     * 不改 GarmrAttackSyncEvents 的公开 API，只读取它的命中帧保护开关。
     */
    private static boolean isApplyingDelayedBossHit() {
        try {
            if (!delayedHitFieldResolved) {
                delayedHitFieldResolved = true;
                delayedHitField = GarmrAttackSyncEvents.class
                        .getDeclaredField("applyingDelayedHit");
                delayedHitField.setAccessible(true);
            }
            return delayedHitField != null && delayedHitField.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
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
    private record HealthCheck(
            float beforeEffectiveHealth,
            float requiredFloor,
            long gameTime,
            String stage
    ) {}
}
