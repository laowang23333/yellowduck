package com.yourname.yellowduck.cleopatra;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** 艳后战斗系统全部可调数值。默认值按 Stargazer 1.1.3-beta。 */
public final class CleopatraConfig {
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> RELOADABLE = new LinkedHashMap<>();
    private static final String SECTION = "cleopatra_battle";

    // Cleopatra body
    public static ForgeConfigSpec.DoubleValue bossHealth, bossAttack, bossMeleeDefense, bossRangedDefense, bossMagicDefense, bossReduction;
    public static ForgeConfigSpec.DoubleValue hatredRange, hatredPerScan, autoDeathRatio, meleeRange, bossTurnSpeed;
    public static ForgeConfigSpec.IntValue hatredScanInterval, hatredLostTicks, normalCd, normalDamageDelay, normalAnimTicks;
    public static ForgeConfigSpec.IntValue volleyFirstCd, volleyCd, volleyTargets, volleyAnimTicks;
    public static ForgeConfigSpec.DoubleValue volleyDamage;
    public static ForgeConfigSpec.IntValue scorpionFirstCd, scorpionCd, scorpionCount, scorpionAnimTicks;
    public static ForgeConfigSpec.DoubleValue scorpionSpawnDistance, sandworm75, sandworm50, sandwormSpawnOffset;
    public static ForgeConfigSpec.IntValue sandwormSummonAnimTicks;

    // Sandworm
    public static ForgeConfigSpec.DoubleValue sandwormHealth, sandwormBulletDamage, sandwormRange;
    public static ForgeConfigSpec.IntValue sandwormAttackCd, sandwormRetryCd, sandwormTargets, sandwormAttackAnimTicks, sandwormBulletDelay, sicknessDuration;

    // Scorpion/pool
    public static ForgeConfigSpec.DoubleValue scorpionHealth, scorpionSpeed, scorpionKnockbackResistance, scorpionArrivalDistance, scorpionExplosionRange;
    public static ForgeConfigSpec.IntValue scorpionChargeTicks, scorpionOwnerRefreshTicks;
    public static ForgeConfigSpec.DoubleValue poolNormalRatio, poolSicknessRatio, poolRadius;
    public static ForgeConfigSpec.IntValue poolLife, poolDamageInterval;

    // Snakes
    public static ForgeConfigSpec.DoubleValue snakeHealth, snakeAttributeAttack, snakeAttack, snakeMeleeDefense, snakeRangedDefense, snakeMagicDefense, snakeReduction;
    public static ForgeConfigSpec.DoubleValue snakeTargetRange, snakePackRange, snakeDamageMultPerDeath, snakeSacrificeRange;
    public static ForgeConfigSpec.IntValue snakeSacrificeTargets, snakeNormalCd, snakeAttackAnimTicks, snakeAppearTicks;
    public static ForgeConfigSpec.IntValue snakeRingCd, snakeRingAttempts;
    public static ForgeConfigSpec.DoubleValue snakeRingRandomDiameter, snakeRingGroundYRange;
    public static ForgeConfigSpec.IntValue snakeBombFirstCd, snakeBombCd, bombEffectDuration, bombFuseTicks, layerDuration, layerMaxStacks;
    public static ForgeConfigSpec.DoubleValue bombCandidateRadius, bombDamageMaxHealthMultiplier, bombRadius, maxStackDamageMultiplier;
    public static ForgeConfigSpec.IntValue bombFarthestPoolSize, tankAvoidPercent;
    public static ForgeConfigSpec.DoubleValue ringDamage, ringTriggerRadius, ringDamageRadius, ringSnakeExistRadius;

    // Summoner / arena
    public static ForgeConfigSpec.BooleanValue useAmethystSpawnMarkers;
    public static ForgeConfigSpec.IntValue arenaMinX, arenaMaxX, arenaMinZ, arenaMaxZ, arenaMinY, arenaMaxY;
    public static ForgeConfigSpec.IntValue summonPoisonTick, summonFireTick, summonIceTick, summonerDiscardTick;
    public static ForgeConfigSpec.DoubleValue fallbackForwardDistance, fallbackSideOffset;

    public static void build(ForgeConfigSpec.Builder b) {
        b.comment("", "艳后完整战斗系统（Stargazer 1.1.3-beta 行为移植）",
                "修改 config/yellowduck-entities.toml 后执行 /yellowduck reload 即可重新读取。")
                .push(SECTION);

        bossHealth=d(b,"boss_health",20000,1,1e9); bossAttack=d(b,"boss_normal_attack_damage",70,0,1e9);
        bossMeleeDefense=d(b,"boss_melee_defense",60,0,1e9); bossRangedDefense=d(b,"boss_ranged_defense",10,0,1e9);
        bossMagicDefense=d(b,"boss_magic_defense",10,0,1e9); bossReduction=d(b,"boss_fixed_reduction",0.5,0,0.99);
        hatredRange=d(b,"boss_hatred_range",3,0,500); hatredPerScan=d(b,"boss_hatred_per_scan",10,0,1e9);
        hatredScanInterval=i(b,"boss_hatred_scan_interval_ticks",4,1,120000); hatredLostTicks=i(b,"boss_hatred_lost_ticks",40,1,120000);
        autoDeathRatio=d(b,"boss_auto_death_health_ratio",0.10,0,1); meleeRange=d(b,"boss_melee_range",3,0,100); bossTurnSpeed=d(b,"boss_turn_speed_degrees_per_tick",12.0,0.1,180.0);
        normalCd=i(b,"normal_attack_cooldown_ticks",40,1,120000); normalDamageDelay=i(b,"normal_attack_damage_delay_ticks",10,0,120000);
        normalAnimTicks=i(b,"normal_attack_animation_ticks",22,1,120000);
        volleyFirstCd=i(b,"volley_first_cooldown_ticks",200,0,120000); volleyCd=i(b,"volley_cooldown_ticks",400,1,120000);
        volleyDamage=d(b,"volley_damage",40,0,1e9); volleyTargets=i(b,"volley_max_targets",3,1,100); volleyAnimTicks=i(b,"volley_animation_ticks",22,1,120000);
        scorpionFirstCd=i(b,"scorpion_first_cooldown_ticks",300,0,120000); scorpionCd=i(b,"scorpion_cooldown_ticks",600,1,120000);
        scorpionCount=i(b,"scorpion_count",3,0,100); scorpionSpawnDistance=d(b,"scorpion_spawn_distance",10,0,1000); scorpionAnimTicks=i(b,"scorpion_summon_animation_ticks",20,1,120000);
        sandworm75=d(b,"sandworm_first_health_ratio",0.75,0,1); sandworm50=d(b,"sandworm_second_health_ratio",0.50,0,1);
        sandwormSpawnOffset=d(b,"sandworm_spawn_offset",3,0,1000); sandwormSummonAnimTicks=i(b,"sandworm_summon_animation_ticks",22,1,120000);

        sandwormHealth=d(b,"sandworm_health",1000,1,1e9); sandwormBulletDamage=d(b,"sandworm_bullet_damage",30,0,1e9);
        sandwormRange=d(b,"sandworm_target_range",50,1,500); sandwormAttackCd=i(b,"sandworm_attack_cooldown_ticks",100,1,120000);
        sandwormRetryCd=i(b,"sandworm_empty_target_retry_ticks",20,1,120000); sandwormTargets=i(b,"sandworm_max_targets",3,1,100);
        sandwormAttackAnimTicks=i(b,"sandworm_attack_animation_ticks",30,1,120000); sandwormBulletDelay=i(b,"sandworm_bullet_delay_ticks",16,0,120000);
        sicknessDuration=i(b,"venom_sickness_duration_ticks",200,1,120000);

        scorpionHealth=d(b,"scorpion_health",300,1,1e9); scorpionSpeed=d(b,"scorpion_speed",0.34,0,10);
        scorpionKnockbackResistance=d(b,"scorpion_knockback_resistance",0.4,0,1); scorpionArrivalDistance=d(b,"scorpion_arrival_distance",1.2,0,100);
        scorpionExplosionRange=d(b,"scorpion_explosion_radius",1.5,0,100); scorpionChargeTicks=i(b,"scorpion_charge_ticks",12,1,120000);
        scorpionOwnerRefreshTicks=i(b,"scorpion_owner_refresh_ticks",5,1,120000);
        poolLife=i(b,"pool_life_ticks",600,1,120000); poolDamageInterval=i(b,"pool_damage_interval_ticks",20,1,120000);
        poolRadius=d(b,"pool_radius",1.0,0,100); poolNormalRatio=d(b,"pool_normal_max_health_ratio",0.30,0,100); poolSicknessRatio=d(b,"pool_sickness_max_health_ratio",1.0,0,100);

        snakeHealth=d(b,"snake_health",20000,1,1e9); snakeAttributeAttack=d(b,"snake_attribute_attack_damage",30,0,1e9);
        snakeAttack=d(b,"snake_skill_attack_damage",45,0,1e9); snakeMeleeDefense=d(b,"snake_melee_defense",60,0,1e9);
        snakeRangedDefense=d(b,"snake_ranged_defense",10,0,1e9); snakeMagicDefense=d(b,"snake_magic_defense",10,0,1e9); snakeReduction=d(b,"snake_fixed_reduction",0.5,0,0.99);
        snakeTargetRange=d(b,"snake_target_range",50,0,1000); snakePackRange=d(b,"snake_pack_link_range",150,0,1000);
        snakeNormalCd=i(b,"snake_normal_attack_cooldown_ticks",60,1,120000); snakeAttackAnimTicks=i(b,"snake_attack_animation_ticks",20,1,120000); snakeAppearTicks=i(b,"snake_appear_ticks",30,1,120000);
        snakeRingCd=i(b,"snake_ring_cooldown_ticks",300,1,120000); snakeRingAttempts=i(b,"snake_ring_ground_attempts",8,1,100);
        snakeRingRandomDiameter=d(b,"snake_ring_random_diameter",80,0,1000); snakeRingGroundYRange=d(b,"snake_ring_ground_vertical_range",5,0,100);
        snakeBombFirstCd=i(b,"snake_first_bomb_cooldown_ticks",300,0,120000); snakeBombCd=i(b,"snake_bomb_cooldown_ticks",600,1,120000);
        bombEffectDuration=i(b,"bomb_effect_duration_ticks",400,1,120000); bombFuseTicks=i(b,"bomb_fuse_ticks",300,1,120000);
        bombCandidateRadius=d(b,"bomb_candidate_radius",40,0,1000); bombFarthestPoolSize=i(b,"bomb_random_farthest_player_count",3,1,100);
        tankAvoidPercent=i(b,"bomb_avoid_hatred_target_percent",80,0,100); bombDamageMaxHealthMultiplier=d(b,"bomb_damage_max_health_multiplier",10,0,1000); bombRadius=d(b,"bomb_damage_radius",20,0,500);
        layerDuration=i(b,"snake_layer_effect_duration_ticks",400,1,120000); layerMaxStacks=i(b,"snake_layer_max_stacks",9,1,100);
        maxStackDamageMultiplier=d(b,"snake_max_stack_damage_multiplier",2,1,100);
        ringDamage=d(b,"ring_trigger_damage",40,0,1e9); ringTriggerRadius=d(b,"ring_trigger_radius",2,0,100); ringDamageRadius=d(b,"ring_damage_radius",20,0,500); ringSnakeExistRadius=d(b,"ring_snake_exist_check_radius",100,0,1000);
        snakeDamageMultPerDeath=d(b,"snake_sacrifice_multiplier",1.2,1,100); snakeSacrificeRange=d(b,"snake_sacrifice_range",100,0,1000); snakeSacrificeTargets=i(b,"snake_sacrifice_max_targets",2,0,100);

        useAmethystSpawnMarkers=bool(b,"summoner_use_amethyst_markers",true);
        arenaMinX=i(b,"summoner_marker_min_x",-29,-30000000,30000000); arenaMaxX=i(b,"summoner_marker_max_x",30,-30000000,30000000);
        arenaMinZ=i(b,"summoner_marker_min_z",-29,-30000000,30000000); arenaMaxZ=i(b,"summoner_marker_max_z",30,-30000000,30000000);
        arenaMinY=i(b,"summoner_marker_min_y",100,-64,320); arenaMaxY=i(b,"summoner_marker_max_y",140,-64,320);
        summonPoisonTick=i(b,"summoner_poison_tick",60,0,120000); summonFireTick=i(b,"summoner_fire_tick",120,0,120000); summonIceTick=i(b,"summoner_ice_tick",180,0,120000); summonerDiscardTick=i(b,"summoner_discard_tick",200,1,120000);
        fallbackForwardDistance=d(b,"summoner_fallback_forward_distance",9,0,1000); fallbackSideOffset=d(b,"summoner_fallback_side_offset",8,0,1000);
        b.pop();
    }

    public static int reload(Map<String,String> values) {
        int changed=0;
        for (Map.Entry<String, ForgeConfigSpec.ConfigValue<?>> e : RELOADABLE.entrySet()) {
            String raw=values.get(e.getKey()); if(raw==null) continue;
            try { if(setFromRaw(e.getValue(),raw)) changed++; } catch (Exception ignored) {}
        }
        return changed;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static boolean setFromRaw(ForgeConfigSpec.ConfigValue value,String raw){
        Object old=value.get(); Object parsed;
        if(old instanceof Integer) parsed=Integer.parseInt(raw.trim());
        else if(old instanceof Double) parsed=Double.parseDouble(raw.trim());
        else if(old instanceof Boolean) parsed=Boolean.parseBoolean(raw.trim());
        else parsed=raw;
        value.set(parsed); return true;
    }

    private static ForgeConfigSpec.DoubleValue d(ForgeConfigSpec.Builder b,String n,double v,double min,double max){var x=b.defineInRange(n,v,min,max);RELOADABLE.put(SECTION+"."+n,x);return x;}
    private static ForgeConfigSpec.IntValue i(ForgeConfigSpec.Builder b,String n,int v,int min,int max){var x=b.defineInRange(n,v,min,max);RELOADABLE.put(SECTION+"."+n,x);return x;}
    private static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder b,String n,boolean v){var x=b.define(n,v);RELOADABLE.put(SECTION+"."+n,x);return x;}
    private CleopatraConfig(){}
}
