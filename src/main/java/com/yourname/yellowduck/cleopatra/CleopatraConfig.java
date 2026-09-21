package com.yourname.yellowduck.cleopatra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 艳后战斗系统可调数值。
 *
 * 不再使用 ForgeConfigSpec。
 * 所有参数仍然保存在 config/yellowduck-entities.toml 的 [cleopatra_battle] 中，
 * 由 EntityTuningConfig 与其它生物属性一起原子读取。
 */
public final class CleopatraConfig {
    public static final String SECTION = "cleopatra_battle";

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    // 艳后本体
    public static DoubleValue bossHealth, bossAttack, bossMeleeDefense, bossRangedDefense, bossMagicDefense, bossReduction;
    public static DoubleValue hatredRange, hatredPerScan, autoDeathRatio, meleeRange, bossTurnSpeed;
    public static IntValue hatredScanInterval, hatredLostTicks, normalCd, normalDamageDelay, normalAnimTicks;
    public static IntValue volleyFirstCd, volleyCd, volleyTargets, volleyAnimTicks;
    public static DoubleValue volleyDamage;
    public static IntValue scorpionFirstCd, scorpionCd, scorpionCount, scorpionAnimTicks;
    public static DoubleValue scorpionSpawnDistance, sandworm75, sandworm50, sandwormSpawnOffset;
    public static IntValue sandwormSummonAnimTicks;

    // 沙虫
    public static DoubleValue sandwormHealth, sandwormBulletDamage, sandwormRange;
    public static IntValue sandwormAttackCd, sandwormRetryCd, sandwormTargets, sandwormAttackAnimTicks, sandwormBulletDelay, sicknessDuration;

    // 蝎子与毒池
    public static DoubleValue scorpionHealth, scorpionSpeed, scorpionKnockbackResistance, scorpionArrivalDistance, scorpionExplosionRange;
    public static IntValue scorpionChargeTicks, scorpionOwnerRefreshTicks;
    public static DoubleValue poolNormalRatio, poolSicknessRatio, poolRadius;
    public static IntValue poolLife, poolDamageInterval;

    // 三蛇
    public static DoubleValue snakeHealth, snakeAttributeAttack, snakeAttack, snakeMeleeDefense, snakeRangedDefense, snakeMagicDefense, snakeReduction;
    public static DoubleValue snakeTargetRange, snakePackRange, snakeDamageMultPerDeath, snakeSacrificeRange;
    public static IntValue snakeSacrificeTargets, snakeNormalCd, snakeAttackAnimTicks, snakeAppearTicks;
    public static IntValue snakeRingCd, snakeRingAttempts;
    public static DoubleValue snakeRingRandomDiameter, snakeRingGroundYRange;
    public static IntValue snakeBombFirstCd, snakeBombCd, bombEffectDuration, bombFuseTicks, layerDuration, layerMaxStacks;
    public static DoubleValue bombCandidateRadius, bombDamageMaxHealthMultiplier, bombRadius, maxStackDamageMultiplier;
    public static IntValue bombFarthestPoolSize, tankAvoidPercent;
    public static DoubleValue ringDamage, ringTriggerRadius, ringDamageRadius, ringSnakeExistRadius;

    // 三蛇召唤器
    public static IntValue goldPadSearchRadius, goldPadVerticalRange;
    public static IntValue summonPoisonTick, summonFireTick, summonIceTick, summonerDiscardTick;

    static {
        bossHealth = d("boss_health", 20000, 1, 1e9, "艳后最大生命值。默认：20000。");
        bossAttack = d("boss_normal_attack_damage", 70, 0, 1e9, "艳后普通攻击伤害。默认：70。");
        bossMeleeDefense = d("boss_melee_defense", 60, 0, 1e9, "艳后近战防御值。数值越高，受到的近战伤害越低。默认：60。");
        bossRangedDefense = d("boss_ranged_defense", 10, 0, 1e9, "艳后远程防御值。数值越高，受到的远程伤害越低。默认：10。");
        bossMagicDefense = d("boss_magic_defense", 10, 0, 1e9, "艳后魔法防御值。数值越高，受到的魔法伤害越低。默认：10。");
        bossReduction = d("boss_fixed_reduction", 0.5, 0, 0.99, "艳后固定减伤比例。0.5=额外减少50%伤害。默认：0.5。");
        hatredRange = d("boss_hatred_range", 3, 0, 500, "艳后建立和维持仇恨的范围，单位：格。默认：3。");
        hatredPerScan = d("boss_hatred_per_scan", 10, 0, 1e9, "艳后每次扫描时给范围内玩家增加的原始仇恨值。默认：10。");
        hatredScanInterval = i("boss_hatred_scan_interval_ticks", 4, 1, 120000, "艳后仇恨扫描间隔，单位：tick。20 tick=1秒。默认：4。");
        hatredLostTicks = i("boss_hatred_lost_ticks", 40, 1, 120000, "范围内持续没有有效玩家多少 tick 后清空仇恨并恢复状态。默认：40。");
        autoDeathRatio = d("boss_auto_death_health_ratio", 0.10, 0, 1, "艳后生命值低于该比例时进入死亡流程。0.10=10%。默认：0.10。");
        meleeRange = d("boss_melee_range", 3, 0, 100, "艳后普通攻击判定距离，单位：格。默认：3。");
        bossTurnSpeed = d("boss_turn_speed_degrees_per_tick", 12.0, 0.1, 180.0, "艳后每 tick 最大转身角度。数值越小转向越平滑。默认：12。");
        normalCd = i("normal_attack_cooldown_ticks", 40, 1, 120000, "艳后普通攻击冷却，单位：tick。默认：40（2秒）。");
        normalDamageDelay = i("normal_attack_damage_delay_ticks", 10, 0, 120000, "普通攻击播放动画后延迟多少 tick 结算伤害。默认：10。");
        normalAnimTicks = i("normal_attack_animation_ticks", 22, 1, 120000, "普通攻击状态持续时间，单位：tick。默认：22。");
        volleyFirstCd = i("volley_first_cooldown_ticks", 200, 0, 120000, "艳后第一次毒弹齐射前的冷却，单位：tick。默认：200（10秒）。");
        volleyCd = i("volley_cooldown_ticks", 400, 1, 120000, "艳后毒弹齐射后续冷却，单位：tick。默认：400（20秒）。");
        volleyDamage = d("volley_damage", 40, 0, 1e9, "艳后毒弹齐射每颗毒弹的伤害。默认：40。");
        volleyTargets = i("volley_max_targets", 3, 1, 100, "艳后一次毒弹齐射最多选择的玩家数量。默认：3。");
        volleyAnimTicks = i("volley_animation_ticks", 22, 1, 120000, "艳后毒弹齐射动画状态持续时间，单位：tick。默认：22。");
        scorpionFirstCd = i("scorpion_first_cooldown_ticks", 300, 0, 120000, "艳后第一次召唤蝎子前的冷却，单位：tick。默认：300（15秒）。");
        scorpionCd = i("scorpion_cooldown_ticks", 600, 1, 120000, "艳后召唤蝎子的后续冷却，单位：tick。默认：600（30秒）。");
        scorpionCount = i("scorpion_count", 3, 0, 100, "艳后每次召唤的蝎子数量。默认：3。");
        scorpionSpawnDistance = d("scorpion_spawn_distance", 10, 0, 1000, "蝎子生成点距离艳后的基础距离，单位：格。默认：10。");
        scorpionAnimTicks = i("scorpion_summon_animation_ticks", 20, 1, 120000, "艳后召唤蝎子时的动画状态持续时间，单位：tick。默认：20。");
        sandworm75 = d("sandworm_first_health_ratio", 0.75, 0, 1, "第一次召唤沙虫的生命比例。0.75=剩余75%生命时触发。默认：0.75。");
        sandworm50 = d("sandworm_second_health_ratio", 0.50, 0, 1, "第二次召唤沙虫的生命比例。0.50=剩余50%生命时触发。默认：0.50。");
        sandwormSpawnOffset = d("sandworm_spawn_offset", 3, 0, 1000, "沙虫生成位置距离艳后的偏移距离，单位：格。默认：3。");
        sandwormSummonAnimTicks = i("sandworm_summon_animation_ticks", 22, 1, 120000, "艳后召唤沙虫时的动画状态持续时间，单位：tick。默认：22。");
        sandwormHealth = d("sandworm_health", 1000, 1, 1e9, "沙虫最大生命值。默认：1000。");
        sandwormBulletDamage = d("sandworm_bullet_damage", 30, 0, 1e9, "沙虫每颗毒弹造成的伤害。默认：30。");
        sandwormRange = d("sandworm_target_range", 50, 1, 500, "沙虫寻找攻击目标的范围，单位：格。默认：50。");
        sandwormAttackCd = i("sandworm_attack_cooldown_ticks", 100, 1, 120000, "沙虫两轮攻击之间的冷却，单位：tick。默认：100（5秒）。");
        sandwormRetryCd = i("sandworm_empty_target_retry_ticks", 20, 1, 120000, "沙虫没有找到有效目标时，多久后再次搜索目标，单位：tick。默认：20。");
        sandwormTargets = i("sandworm_max_targets", 3, 1, 100, "沙虫每轮毒弹最多同时攻击的玩家数量。默认：3。");
        sandwormAttackAnimTicks = i("sandworm_attack_animation_ticks", 30, 1, 120000, "沙虫一次攻击状态持续时间，单位：tick。默认：30。");
        sandwormBulletDelay = i("sandworm_bullet_delay_ticks", 16, 0, 120000, "沙虫进入攻击动画后，延迟多少 tick 发射毒弹。默认：16。");
        sicknessDuration = i("venom_sickness_duration_ticks", 200, 1, 120000, "沙虫毒弹附加“毒素侵袭”效果的持续时间，单位：tick。默认：200（10秒）。");
        scorpionHealth = d("scorpion_health", 300, 1, 1e9, "蝎子最大生命值。默认：300。");
        scorpionSpeed = d("scorpion_speed", 0.34, 0, 10, "蝎子移动速度。默认：0.34。");
        scorpionKnockbackResistance = d("scorpion_knockback_resistance", 0.4, 0, 1, "蝎子击退抗性。0=无抗性，1=完全抗击退。默认：0.4。");
        scorpionArrivalDistance = d("scorpion_arrival_distance", 1.2, 0, 100, "蝎子距离目标位置多少格时开始自爆蓄力。默认：1.2。");
        scorpionExplosionRange = d("scorpion_explosion_radius", 1.5, 0, 100, "蝎子自爆伤害判定范围，单位：格。默认：1.5。");
        scorpionChargeTicks = i("scorpion_charge_ticks", 12, 1, 120000, "蝎子到达目标位置后自爆前的蓄力时间，单位：tick。默认：12。");
        scorpionOwnerRefreshTicks = i("scorpion_owner_refresh_ticks", 5, 1, 120000, "蝎子刷新追踪目标位置的间隔，单位：tick。默认：5。");
        poolLife = i("pool_life_ticks", 600, 1, 120000, "毒池存在时间，单位：tick。默认：600（30秒）。");
        poolDamageInterval = i("pool_damage_interval_ticks", 20, 1, 120000, "毒池伤害结算间隔，单位：tick。默认：20（1秒）。");
        poolRadius = d("pool_radius", 1.0, 0, 100, "毒池伤害判定半径，单位：格。默认：1。");
        poolNormalRatio = d("pool_normal_max_health_ratio", 0.30, 0, 100, "玩家没有“毒素侵袭”时，毒池伤害占玩家最大生命值的比例。0.30=30%。默认：0.30。");
        poolSicknessRatio = d("pool_sickness_max_health_ratio", 1.0, 0, 100, "玩家带有“毒素侵袭”时，毒池伤害占玩家最大生命值的比例。1.0=100%。默认：1.0。");
        snakeHealth = d("snake_health", 20000, 1, 1e9, "毒蛇、火焰蛇、寒冰蛇的最大生命值。默认：20000。");
        snakeAttributeAttack = d("snake_attribute_attack_damage", 30, 0, 1e9, "三蛇基础攻击力属性。默认：30。");
        snakeAttack = d("snake_skill_attack_damage", 45, 0, 1e9, "三蛇普通技能攻击的基础伤害。默认：45。");
        snakeMeleeDefense = d("snake_melee_defense", 60, 0, 1e9, "三蛇近战防御值。默认：60。");
        snakeRangedDefense = d("snake_ranged_defense", 10, 0, 1e9, "三蛇远程防御值。默认：10。");
        snakeMagicDefense = d("snake_magic_defense", 10, 0, 1e9, "三蛇魔法防御值。默认：10。");
        snakeReduction = d("snake_fixed_reduction", 0.5, 0, 0.99, "三蛇固定减伤比例。0.5=额外减少50%伤害。默认：0.5。");
        snakeTargetRange = d("snake_target_range", 50, 0, 1000, "三蛇选择攻击目标的范围，单位：格。默认：50。");
        snakePackRange = d("snake_pack_link_range", 150, 0, 1000, "三蛇之间同步战斗目标和仇恨的范围，单位：格。默认：150。");
        snakeNormalCd = i("snake_normal_attack_cooldown_ticks", 60, 1, 120000, "三蛇普通攻击冷却，单位：tick。默认：60（3秒）。");
        snakeAttackAnimTicks = i("snake_attack_animation_ticks", 20, 1, 120000, "三蛇普通攻击动画状态持续时间，单位：tick。默认：20。");
        snakeAppearTicks = i("snake_appear_ticks", 30, 1, 120000, "三蛇出生动画状态持续时间，单位：tick。默认：30。");
        snakeRingCd = i("snake_ring_cooldown_ticks", 300, 1, 120000, "三蛇生成元素圈的冷却，单位：tick。默认：300（15秒）。");
        snakeRingAttempts = i("snake_ring_ground_attempts", 8, 1, 100, "生成元素圈时随机寻找可用地面的最大尝试次数。默认：8。");
        snakeRingRandomDiameter = d("snake_ring_random_diameter", 80, 0, 1000, "元素圈随机生成区域的直径，单位：格。默认：80。");
        snakeRingGroundYRange = d("snake_ring_ground_vertical_range", 5, 0, 100, "元素圈寻找地面时允许的上下搜索范围，单位：格。默认：5。");
        snakeBombFirstCd = i("snake_first_bomb_cooldown_ticks", 300, 0, 120000, "三蛇第一次给玩家放置炸弹前的冷却，单位：tick。默认：300（15秒）。");
        snakeBombCd = i("snake_bomb_cooldown_ticks", 600, 1, 120000, "三蛇放置炸弹的后续冷却，单位：tick。默认：600（30秒）。");
        bombEffectDuration = i("bomb_effect_duration_ticks", 400, 1, 120000, "玩家身上炸弹状态效果的总持续时间，单位：tick。默认：400（20秒）。");
        bombFuseTicks = i("bomb_fuse_ticks", 300, 1, 120000, "炸弹标记生成后多少 tick 引爆。默认：300（15秒）。");
        bombCandidateRadius = d("bomb_candidate_radius", 40, 0, 1000, "三蛇选择炸弹目标时搜索玩家的范围，单位：格。默认：40。");
        bombFarthestPoolSize = i("bomb_random_farthest_player_count", 3, 1, 100, "炸弹从距离较远的前多少名玩家中随机选择目标。默认：3。");
        tankAvoidPercent = i("bomb_avoid_hatred_target_percent", 80, 0, 100, "存在其他候选玩家时，炸弹避开当前主要仇恨目标的概率，单位：百分比。默认：80。");
        bombDamageMaxHealthMultiplier = d("bomb_damage_max_health_multiplier", 10, 0, 1000, "炸弹引爆伤害倍率。实际伤害=携带者最大生命值×该数值×三蛇伤害倍率。默认：10。");
        bombRadius = d("bomb_damage_radius", 20, 0, 500, "炸弹引爆时的伤害判定范围，单位：格。默认：20。");
        layerDuration = i("snake_layer_effect_duration_ticks", 400, 1, 120000, "三蛇普通攻击附加元素层数的持续时间，单位：tick。默认：400（20秒）。");
        layerMaxStacks = i("snake_layer_max_stacks", 9, 1, 100, "元素层数上限。默认：9层。");
        maxStackDamageMultiplier = d("snake_max_stack_damage_multiplier", 2, 1, 100, "玩家元素层数达到上限后，对应三蛇普通攻击的伤害倍率。默认：2倍。");
        ringDamage = d("ring_trigger_damage", 40, 0, 1e9, "玩家触发元素圈时对范围内玩家造成的基础伤害。默认：40。");
        ringTriggerRadius = d("ring_trigger_radius", 2, 0, 100, "玩家进入元素圈并触发它的判定半径，单位：格。默认：2。");
        ringDamageRadius = d("ring_damage_radius", 20, 0, 500, "元素圈被触发后造成范围伤害的半径，单位：格。默认：20。");
        ringSnakeExistRadius = d("ring_snake_exist_check_radius", 100, 0, 1000, "元素圈检查附近是否仍有三蛇存活的范围，单位：格。默认：100。");
        snakeDamageMultPerDeath = d("snake_sacrifice_multiplier", 1.2, 1, 100, "一条蛇死亡后给予附近其他蛇的伤害倍率。1.2=提高到120%。默认：1.2。");
        snakeSacrificeRange = d("snake_sacrifice_range", 100, 0, 1000, "蛇死亡时寻找可获得强化的其他蛇的范围，单位：格。默认：100。");
        snakeSacrificeTargets = i("snake_sacrifice_max_targets", 2, 0, 100, "一条蛇死亡时最多强化的其他蛇数量。默认：2。");
        goldPadSearchRadius = i("summoner_gold_block_search_radius", 32, 1, 256, "艳后死亡后寻找三蛇出生金块的水平半径，单位：格。默认：32。");
        goldPadVerticalRange = i("summoner_gold_block_vertical_range", 12, 0, 128, "寻找三蛇出生金块时允许的上下搜索范围，单位：格。默认：12。");
        summonPoisonTick = i("summoner_poison_tick", 60, 0, 120000, "艳后死亡后第多少 tick 生成毒蛇。默认：60（3秒）。");
        summonFireTick = i("summoner_fire_tick", 120, 0, 120000, "艳后死亡后第多少 tick 生成火焰蛇。默认：120（6秒）。");
        summonIceTick = i("summoner_ice_tick", 180, 0, 120000, "艳后死亡后第多少 tick 生成寒冰蛇。默认：180（9秒）。");
        summonerDiscardTick = i("summoner_discard_tick", 200, 1, 120000, "三蛇召唤器存在时间，单位：tick。默认：200（10秒）。");
    }

    public static Map<String, Number> prepare(Map<String, String> values, List<String> errors) {
        Map<String, Number> prepared = new LinkedHashMap<>();
        Map<String, String> section = values == null ? Map.of() : values;

        for (Definition def : DEFINITIONS.values()) {
            String raw = section.get(def.name());
            if (raw == null || raw.isBlank()) {
                prepared.put(def.name(), def.integer() ? (int) Math.round(def.defaultValue()) : def.defaultValue());
                continue;
            }

            try {
                double parsed = Double.parseDouble(unquote(raw));
                if (!Double.isFinite(parsed)) throw new NumberFormatException("not finite");
                if (def.integer() && Math.rint(parsed) != parsed) {
                    errors.add("[" + SECTION + "] " + def.name() + " 必须是整数，当前：" + raw);
                    continue;
                }
                if (parsed < def.min() || parsed > def.max()) {
                    errors.add("[" + SECTION + "] " + def.name() + " 超出范围 "
                            + format(def.min()) + " ~ " + format(def.max()) + "，当前：" + raw);
                    continue;
                }
                prepared.put(def.name(), def.integer() ? (int) parsed : parsed);
            } catch (NumberFormatException ex) {
                errors.add("[" + SECTION + "] " + def.name() + " 不是有效数字：" + raw);
            }
        }
        return prepared;
    }

    public static void commit(Map<String, Number> prepared) {
        if (prepared == null) return;
        for (Definition def : DEFINITIONS.values()) {
            Number value = prepared.get(def.name());
            if (value == null) continue;
            if (def.integer()) {
                ((IntValue) def.holder()).set(value.intValue());
            } else {
                ((DoubleValue) def.holder()).set(value.doubleValue());
            }
        }
    }

    public static void appendDefaultText(StringBuilder out) {
        out.append("\n[cleopatra_battle]\n");
        out.append("# BOSS：艳后。战斗参数继续和其它生物属性放在同一个 yellowduck-entities.toml 中。\n");
        out.append("# 修改后执行 /yd reload。配置读取失败时继续沿用上一份有效值，不会自动改回默认。\n");
        for (Definition def : DEFINITIONS.values()) {
            out.append("# ").append(def.comment()).append('\n');
            out.append(def.name()).append(" = ").append(format(def.defaultValue())).append("\n\n");
        }
    }

    public static int definitionCount() {
        return DEFINITIONS.size();
    }

    private static DoubleValue d(String name, double value, double min, double max, String comment) {
        DoubleValue holder = new DoubleValue(value);
        DEFINITIONS.put(name, new Definition(name, false, value, min, max, comment, holder));
        return holder;
    }

    private static IntValue i(String name, int value, int min, int max, String comment) {
        IntValue holder = new IntValue(value);
        DEFINITIONS.put(name, new Definition(name, true, value, min, max, comment, holder));
        return holder;
    }

    private static String unquote(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static String format(double value) {
        if (Math.rint(value) == value && Math.abs(value) <= Long.MAX_VALUE) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public static final class DoubleValue {
        private volatile double value;
        private DoubleValue(double value) { this.value = value; }
        public Double get() { return value; }
        private void set(double value) { this.value = value; }
    }

    public static final class IntValue {
        private volatile int value;
        private IntValue(int value) { this.value = value; }
        public Integer get() { return value; }
        private void set(int value) { this.value = value; }
    }

    private record Definition(String name, boolean integer, double defaultValue,
                              double min, double max, String comment, Object holder) {}

    private CleopatraConfig() {}
}
