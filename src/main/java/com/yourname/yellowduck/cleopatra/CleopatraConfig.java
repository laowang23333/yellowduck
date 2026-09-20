package com.yourname.yellowduck.cleopatra;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** 艳后战斗系统可调数值。 */
public final class CleopatraConfig {
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> RELOADABLE = new LinkedHashMap<>();
    private static final String SECTION = "cleopatra_battle";

    // 艳后本体
    public static ForgeConfigSpec.DoubleValue bossHealth, bossAttack, bossMeleeDefense, bossRangedDefense, bossMagicDefense, bossReduction;
    public static ForgeConfigSpec.DoubleValue hatredRange, hatredPerScan, autoDeathRatio, meleeRange, bossTurnSpeed;
    public static ForgeConfigSpec.IntValue hatredScanInterval, hatredLostTicks, normalCd, normalDamageDelay, normalAnimTicks;
    public static ForgeConfigSpec.IntValue volleyFirstCd, volleyCd, volleyTargets, volleyAnimTicks;
    public static ForgeConfigSpec.DoubleValue volleyDamage;
    public static ForgeConfigSpec.IntValue scorpionFirstCd, scorpionCd, scorpionCount, scorpionAnimTicks;
    public static ForgeConfigSpec.DoubleValue scorpionSpawnDistance, sandworm75, sandworm50, sandwormSpawnOffset;
    public static ForgeConfigSpec.IntValue sandwormSummonAnimTicks;

    // 沙虫
    public static ForgeConfigSpec.DoubleValue sandwormHealth, sandwormBulletDamage, sandwormRange;
    public static ForgeConfigSpec.IntValue sandwormAttackCd, sandwormRetryCd, sandwormTargets, sandwormAttackAnimTicks, sandwormBulletDelay, sicknessDuration;

    // 蝎子与毒池
    public static ForgeConfigSpec.DoubleValue scorpionHealth, scorpionSpeed, scorpionKnockbackResistance, scorpionArrivalDistance, scorpionExplosionRange;
    public static ForgeConfigSpec.IntValue scorpionChargeTicks, scorpionOwnerRefreshTicks;
    public static ForgeConfigSpec.DoubleValue poolNormalRatio, poolSicknessRatio, poolRadius;
    public static ForgeConfigSpec.IntValue poolLife, poolDamageInterval;

    // 三蛇
    public static ForgeConfigSpec.DoubleValue snakeHealth, snakeAttributeAttack, snakeAttack, snakeMeleeDefense, snakeRangedDefense, snakeMagicDefense, snakeReduction;
    public static ForgeConfigSpec.DoubleValue snakeTargetRange, snakePackRange, snakeDamageMultPerDeath, snakeSacrificeRange;
    public static ForgeConfigSpec.IntValue snakeSacrificeTargets, snakeNormalCd, snakeAttackAnimTicks, snakeAppearTicks;
    public static ForgeConfigSpec.IntValue snakeRingCd, snakeRingAttempts;
    public static ForgeConfigSpec.DoubleValue snakeRingRandomDiameter, snakeRingGroundYRange;
    public static ForgeConfigSpec.IntValue snakeBombFirstCd, snakeBombCd, bombEffectDuration, bombFuseTicks, layerDuration, layerMaxStacks;
    public static ForgeConfigSpec.DoubleValue bombCandidateRadius, bombDamageMaxHealthMultiplier, bombRadius, maxStackDamageMultiplier;
    public static ForgeConfigSpec.IntValue bombFarthestPoolSize, tankAvoidPercent;
    public static ForgeConfigSpec.DoubleValue ringDamage, ringTriggerRadius, ringDamageRadius, ringSnakeExistRadius;

    // 三蛇召唤器
    public static ForgeConfigSpec.IntValue goldPadSearchRadius, goldPadVerticalRange;
    public static ForgeConfigSpec.IntValue summonPoisonTick, summonFireTick, summonIceTick, summonerDiscardTick;

    public static void build(ForgeConfigSpec.Builder b) {
        b.comment(
                "",
                "BOSS：艳后",
                "艳后本体、沙虫、蝎子、毒池、三蛇、元素圈和炸弹相关数值。",
                "修改 config/yellowduck-entities.toml 后执行 /yellowduck reload 重新读取。"
        ).push(SECTION);

        bossHealth = d(b, "boss_health", 20000, 1, 1e9,
                "艳后最大生命值。默认：20000。");
        bossAttack = d(b, "boss_normal_attack_damage", 70, 0, 1e9,
                "艳后普通攻击伤害。默认：70。");
        bossMeleeDefense = d(b, "boss_melee_defense", 60, 0, 1e9,
                "艳后近战防御值。数值越高，受到的近战伤害越低。默认：60。");
        bossRangedDefense = d(b, "boss_ranged_defense", 10, 0, 1e9,
                "艳后远程防御值。数值越高，受到的远程伤害越低。默认：10。");
        bossMagicDefense = d(b, "boss_magic_defense", 10, 0, 1e9,
                "艳后魔法防御值。数值越高，受到的魔法伤害越低。默认：10。");
        bossReduction = d(b, "boss_fixed_reduction", 0.5, 0, 0.99,
                "艳后固定减伤比例。0.5=额外减少50%伤害。默认：0.5。");
        hatredRange = d(b, "boss_hatred_range", 3, 0, 500,
                "艳后建立和维持仇恨的范围，单位：格。默认：3。");
        hatredPerScan = d(b, "boss_hatred_per_scan", 10, 0, 1e9,
                "艳后每次扫描时给范围内玩家增加的原始仇恨值。默认：10。");
        hatredScanInterval = i(b, "boss_hatred_scan_interval_ticks", 4, 1, 120000,
                "艳后仇恨扫描间隔，单位：tick。20 tick=1秒。默认：4。");
        hatredLostTicks = i(b, "boss_hatred_lost_ticks", 40, 1, 120000,
                "范围内持续没有有效玩家多少 tick 后清空仇恨并恢复状态。默认：40。");
        autoDeathRatio = d(b, "boss_auto_death_health_ratio", 0.10, 0, 1,
                "艳后生命值低于该比例时进入死亡流程。0.10=10%。默认：0.10。");
        meleeRange = d(b, "boss_melee_range", 3, 0, 100,
                "艳后普通攻击判定距离，单位：格。默认：3。");
        bossTurnSpeed = d(b, "boss_turn_speed_degrees_per_tick", 12.0, 0.1, 180.0,
                "艳后每 tick 最大转身角度。数值越小转向越平滑。默认：12。");
        normalCd = i(b, "normal_attack_cooldown_ticks", 40, 1, 120000,
                "艳后普通攻击冷却，单位：tick。默认：40（2秒）。");
        normalDamageDelay = i(b, "normal_attack_damage_delay_ticks", 10, 0, 120000,
                "普通攻击播放动画后延迟多少 tick 结算伤害。默认：10。");
        normalAnimTicks = i(b, "normal_attack_animation_ticks", 22, 1, 120000,
                "普通攻击状态持续时间，单位：tick。默认：22。");
        volleyFirstCd = i(b, "volley_first_cooldown_ticks", 200, 0, 120000,
                "艳后第一次毒弹齐射前的冷却，单位：tick。默认：200（10秒）。");
        volleyCd = i(b, "volley_cooldown_ticks", 400, 1, 120000,
                "艳后毒弹齐射后续冷却，单位：tick。默认：400（20秒）。");
        volleyDamage = d(b, "volley_damage", 40, 0, 1e9,
                "艳后毒弹齐射每颗毒弹的伤害。默认：40。");
        volleyTargets = i(b, "volley_max_targets", 3, 1, 100,
                "艳后一次毒弹齐射最多选择的玩家数量。默认：3。");
        volleyAnimTicks = i(b, "volley_animation_ticks", 22, 1, 120000,
                "艳后毒弹齐射动画状态持续时间，单位：tick。默认：22。");
        scorpionFirstCd = i(b, "scorpion_first_cooldown_ticks", 300, 0, 120000,
                "艳后第一次召唤蝎子前的冷却，单位：tick。默认：300（15秒）。");
        scorpionCd = i(b, "scorpion_cooldown_ticks", 600, 1, 120000,
                "艳后召唤蝎子的后续冷却，单位：tick。默认：600（30秒）。");
        scorpionCount = i(b, "scorpion_count", 3, 0, 100,
                "艳后每次召唤的蝎子数量。默认：3。");
        scorpionSpawnDistance = d(b, "scorpion_spawn_distance", 10, 0, 1000,
                "蝎子生成点距离艳后的基础距离，单位：格。默认：10。");
        scorpionAnimTicks = i(b, "scorpion_summon_animation_ticks", 20, 1, 120000,
                "艳后召唤蝎子时的动画状态持续时间，单位：tick。默认：20。");
        sandworm75 = d(b, "sandworm_first_health_ratio", 0.75, 0, 1,
                "第一次召唤沙虫的生命比例。0.75=剩余75%生命时触发。默认：0.75。");
        sandworm50 = d(b, "sandworm_second_health_ratio", 0.50, 0, 1,
                "第二次召唤沙虫的生命比例。0.50=剩余50%生命时触发。默认：0.50。");
        sandwormSpawnOffset = d(b, "sandworm_spawn_offset", 3, 0, 1000,
                "沙虫生成位置距离艳后的偏移距离，单位：格。默认：3。");
        sandwormSummonAnimTicks = i(b, "sandworm_summon_animation_ticks", 22, 1, 120000,
                "艳后召唤沙虫时的动画状态持续时间，单位：tick。默认：22。");

        sandwormHealth = d(b, "sandworm_health", 1000, 1, 1e9,
                "沙虫最大生命值。默认：1000。");
        sandwormBulletDamage = d(b, "sandworm_bullet_damage", 30, 0, 1e9,
                "沙虫每颗毒弹造成的伤害。默认：30。");
        sandwormRange = d(b, "sandworm_target_range", 50, 1, 500,
                "沙虫寻找攻击目标的范围，单位：格。默认：50。");
        sandwormAttackCd = i(b, "sandworm_attack_cooldown_ticks", 100, 1, 120000,
                "沙虫两轮攻击之间的冷却，单位：tick。默认：100（5秒）。");
        sandwormRetryCd = i(b, "sandworm_empty_target_retry_ticks", 20, 1, 120000,
                "沙虫没有找到有效目标时，多久后再次搜索目标，单位：tick。默认：20。");
        sandwormTargets = i(b, "sandworm_max_targets", 3, 1, 100,
                "沙虫每轮毒弹最多同时攻击的玩家数量。默认：3。");
        sandwormAttackAnimTicks = i(b, "sandworm_attack_animation_ticks", 30, 1, 120000,
                "沙虫一次攻击状态持续时间，单位：tick。默认：30。");
        sandwormBulletDelay = i(b, "sandworm_bullet_delay_ticks", 16, 0, 120000,
                "沙虫进入攻击动画后，延迟多少 tick 发射毒弹。默认：16。");
        sicknessDuration = i(b, "venom_sickness_duration_ticks", 200, 1, 120000,
                "沙虫毒弹附加“毒素侵袭”效果的持续时间，单位：tick。默认：200（10秒）。");

        scorpionHealth = d(b, "scorpion_health", 300, 1, 1e9,
                "蝎子最大生命值。默认：300。");
        scorpionSpeed = d(b, "scorpion_speed", 0.34, 0, 10,
                "蝎子移动速度。默认：0.34。");
        scorpionKnockbackResistance = d(b, "scorpion_knockback_resistance", 0.4, 0, 1,
                "蝎子击退抗性。0=无抗性，1=完全抗击退。默认：0.4。");
        scorpionArrivalDistance = d(b, "scorpion_arrival_distance", 1.2, 0, 100,
                "蝎子距离目标位置多少格时开始自爆蓄力。默认：1.2。");
        scorpionExplosionRange = d(b, "scorpion_explosion_radius", 1.5, 0, 100,
                "蝎子自爆伤害判定范围，单位：格。默认：1.5。");
        scorpionChargeTicks = i(b, "scorpion_charge_ticks", 12, 1, 120000,
                "蝎子到达目标位置后自爆前的蓄力时间，单位：tick。默认：12。");
        scorpionOwnerRefreshTicks = i(b, "scorpion_owner_refresh_ticks", 5, 1, 120000,
                "蝎子刷新追踪目标位置的间隔，单位：tick。默认：5。");
        poolLife = i(b, "pool_life_ticks", 600, 1, 120000,
                "毒池存在时间，单位：tick。默认：600（30秒）。");
        poolDamageInterval = i(b, "pool_damage_interval_ticks", 20, 1, 120000,
                "毒池伤害结算间隔，单位：tick。默认：20（1秒）。");
        poolRadius = d(b, "pool_radius", 1.0, 0, 100,
                "毒池伤害判定半径，单位：格。默认：1。");
        poolNormalRatio = d(b, "pool_normal_max_health_ratio", 0.30, 0, 100,
                "玩家没有“毒素侵袭”时，毒池伤害占玩家最大生命值的比例。0.30=30%。默认：0.30。");
        poolSicknessRatio = d(b, "pool_sickness_max_health_ratio", 1.0, 0, 100,
                "玩家带有“毒素侵袭”时，毒池伤害占玩家最大生命值的比例。1.0=100%。默认：1.0。");

        snakeHealth = d(b, "snake_health", 20000, 1, 1e9,
                "毒蛇、火焰蛇、寒冰蛇的最大生命值。默认：20000。");
        snakeAttributeAttack = d(b, "snake_attribute_attack_damage", 30, 0, 1e9,
                "三蛇基础攻击力属性。默认：30。");
        snakeAttack = d(b, "snake_skill_attack_damage", 45, 0, 1e9,
                "三蛇普通技能攻击的基础伤害。默认：45。");
        snakeMeleeDefense = d(b, "snake_melee_defense", 60, 0, 1e9,
                "三蛇近战防御值。默认：60。");
        snakeRangedDefense = d(b, "snake_ranged_defense", 10, 0, 1e9,
                "三蛇远程防御值。默认：10。");
        snakeMagicDefense = d(b, "snake_magic_defense", 10, 0, 1e9,
                "三蛇魔法防御值。默认：10。");
        snakeReduction = d(b, "snake_fixed_reduction", 0.5, 0, 0.99,
                "三蛇固定减伤比例。0.5=额外减少50%伤害。默认：0.5。");
        snakeTargetRange = d(b, "snake_target_range", 50, 0, 1000,
                "三蛇选择攻击目标的范围，单位：格。默认：50。");
        snakePackRange = d(b, "snake_pack_link_range", 150, 0, 1000,
                "三蛇之间同步战斗目标和仇恨的范围，单位：格。默认：150。");
        snakeNormalCd = i(b, "snake_normal_attack_cooldown_ticks", 60, 1, 120000,
                "三蛇普通攻击冷却，单位：tick。默认：60（3秒）。");
        snakeAttackAnimTicks = i(b, "snake_attack_animation_ticks", 20, 1, 120000,
                "三蛇普通攻击动画状态持续时间，单位：tick。默认：20。");
        snakeAppearTicks = i(b, "snake_appear_ticks", 30, 1, 120000,
                "三蛇出生动画状态持续时间，单位：tick。默认：30。");
        snakeRingCd = i(b, "snake_ring_cooldown_ticks", 300, 1, 120000,
                "三蛇生成元素圈的冷却，单位：tick。默认：300（15秒）。");
        snakeRingAttempts = i(b, "snake_ring_ground_attempts", 8, 1, 100,
                "生成元素圈时随机寻找可用地面的最大尝试次数。默认：8。");
        snakeRingRandomDiameter = d(b, "snake_ring_random_diameter", 80, 0, 1000,
                "元素圈随机生成区域的直径，单位：格。默认：80。");
        snakeRingGroundYRange = d(b, "snake_ring_ground_vertical_range", 5, 0, 100,
                "元素圈寻找地面时允许的上下搜索范围，单位：格。默认：5。");
        snakeBombFirstCd = i(b, "snake_first_bomb_cooldown_ticks", 300, 0, 120000,
                "三蛇第一次给玩家放置炸弹前的冷却，单位：tick。默认：300（15秒）。");
        snakeBombCd = i(b, "snake_bomb_cooldown_ticks", 600, 1, 120000,
                "三蛇放置炸弹的后续冷却，单位：tick。默认：600（30秒）。");
        bombEffectDuration = i(b, "bomb_effect_duration_ticks", 400, 1, 120000,
                "玩家身上炸弹状态效果的总持续时间，单位：tick。默认：400（20秒）。");
        bombFuseTicks = i(b, "bomb_fuse_ticks", 300, 1, 120000,
                "炸弹标记生成后多少 tick 引爆。默认：300（15秒）。");
        bombCandidateRadius = d(b, "bomb_candidate_radius", 40, 0, 1000,
                "三蛇选择炸弹目标时搜索玩家的范围，单位：格。默认：40。");
        bombFarthestPoolSize = i(b, "bomb_random_farthest_player_count", 3, 1, 100,
                "炸弹从距离较远的前多少名玩家中随机选择目标。默认：3。");
        tankAvoidPercent = i(b, "bomb_avoid_hatred_target_percent", 80, 0, 100,
                "存在其他候选玩家时，炸弹避开当前主要仇恨目标的概率，单位：百分比。默认：80。");
        bombDamageMaxHealthMultiplier = d(b, "bomb_damage_max_health_multiplier", 10, 0, 1000,
                "炸弹引爆伤害倍率。实际伤害=携带者最大生命值×该数值×三蛇伤害倍率。默认：10。");
        bombRadius = d(b, "bomb_damage_radius", 20, 0, 500,
                "炸弹引爆时的伤害判定范围，单位：格。默认：20。");
        layerDuration = i(b, "snake_layer_effect_duration_ticks", 400, 1, 120000,
                "三蛇普通攻击附加元素层数的持续时间，单位：tick。默认：400（20秒）。");
        layerMaxStacks = i(b, "snake_layer_max_stacks", 9, 1, 100,
                "元素层数上限。默认：9层。");
        maxStackDamageMultiplier = d(b, "snake_max_stack_damage_multiplier", 2, 1, 100,
                "玩家元素层数达到上限后，对应三蛇普通攻击的伤害倍率。默认：2倍。");
        ringDamage = d(b, "ring_trigger_damage", 40, 0, 1e9,
                "玩家触发元素圈时对范围内玩家造成的基础伤害。默认：40。");
        ringTriggerRadius = d(b, "ring_trigger_radius", 2, 0, 100,
                "玩家进入元素圈并触发它的判定半径，单位：格。默认：2。");
        ringDamageRadius = d(b, "ring_damage_radius", 20, 0, 500,
                "元素圈被触发后造成范围伤害的半径，单位：格。默认：20。");
        ringSnakeExistRadius = d(b, "ring_snake_exist_check_radius", 100, 0, 1000,
                "元素圈检查附近是否仍有三蛇存活的范围，单位：格。默认：100。");
        snakeDamageMultPerDeath = d(b, "snake_sacrifice_multiplier", 1.2, 1, 100,
                "一条蛇死亡后给予附近其他蛇的伤害倍率。1.2=提高到120%。默认：1.2。");
        snakeSacrificeRange = d(b, "snake_sacrifice_range", 100, 0, 1000,
                "蛇死亡时寻找可获得强化的其他蛇的范围，单位：格。默认：100。");
        snakeSacrificeTargets = i(b, "snake_sacrifice_max_targets", 2, 0, 100,
                "一条蛇死亡时最多强化的其他蛇数量。默认：2。");

        goldPadSearchRadius = i(b, "summoner_gold_block_search_radius", 32, 1, 256,
                "艳后死亡后寻找三蛇出生金块的水平半径，单位：格。默认：32。");
        goldPadVerticalRange = i(b, "summoner_gold_block_vertical_range", 12, 0, 128,
                "寻找三蛇出生金块时允许的上下搜索范围，单位：格。默认：12。");
        summonPoisonTick = i(b, "summoner_poison_tick", 60, 0, 120000,
                "艳后死亡后第多少 tick 生成毒蛇。默认：60（3秒）。");
        summonFireTick = i(b, "summoner_fire_tick", 120, 0, 120000,
                "艳后死亡后第多少 tick 生成火焰蛇。默认：120（6秒）。");
        summonIceTick = i(b, "summoner_ice_tick", 180, 0, 120000,
                "艳后死亡后第多少 tick 生成寒冰蛇。默认：180（9秒）。");
        summonerDiscardTick = i(b, "summoner_discard_tick", 200, 1, 120000,
                "三蛇召唤器存在时间，单位：tick。默认：200（10秒）。");

        b.pop();
    }

    public static int reload(Map<String, String> values) {
        int changed = 0;
        for (Map.Entry<String, ForgeConfigSpec.ConfigValue<?>> e : RELOADABLE.entrySet()) {
            String raw = values.get(e.getKey());
            if (raw == null) continue;
            try {
                if (setFromRaw(e.getValue(), raw)) changed++;
            } catch (Exception ignored) {
            }
        }
        return changed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setFromRaw(ForgeConfigSpec.ConfigValue value, String raw) {
        Object old = value.get();
        Object parsed;
        if (old instanceof Integer) parsed = Integer.parseInt(raw.trim());
        else if (old instanceof Double) parsed = Double.parseDouble(raw.trim());
        else if (old instanceof Boolean) parsed = Boolean.parseBoolean(raw.trim());
        else parsed = raw;
        value.set(parsed);
        return true;
    }

    private static ForgeConfigSpec.DoubleValue d(ForgeConfigSpec.Builder b, String name, double value,
                                                  double min, double max, String... comments) {
        var x = b.comment(comments).defineInRange(name, value, min, max);
        RELOADABLE.put(SECTION + "." + name, x);
        return x;
    }

    private static ForgeConfigSpec.IntValue i(ForgeConfigSpec.Builder b, String name, int value,
                                               int min, int max, String... comments) {
        var x = b.comment(comments).defineInRange(name, value, min, max);
        RELOADABLE.put(SECTION + "." + name, x);
        return x;
    }

    private CleopatraConfig() {
    }
}
