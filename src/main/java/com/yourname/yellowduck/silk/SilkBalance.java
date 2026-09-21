package com.yourname.yellowduck.silk;

/**
 * 斯尔克战斗运行时数值。
 *
 * 默认值与教授移植 v1/v1.1 保持一致；SilkConfig 会在服务端启动或 /yd reload 时
 * 从 config/yellowduck-entities.toml 原子更新这些值。
 */
public final class SilkBalance {
    private SilkBalance() {}

    // Boss 基础属性
    public static volatile double HEALTH = 430000.0D;
    public static volatile double BOSS_MOVEMENT_SPEED = 0.23D;
    public static volatile double BOSS_FOLLOW_RANGE = 48.0D;
    public static volatile double BOSS_KNOCKBACK_RESISTANCE = 1.0D;
    public static volatile int BOSS_TIER = 5;
    public static volatile int BOSS_MELEE_DEFENSE = 0;
    public static volatile int BOSS_RANGED_DEFENSE = 0;
    public static volatile int BOSS_MAGIC_DEFENSE = 0;
    public static volatile float BOSS_DAMAGE_REDUCTION = 0.0F;

    public static volatile double ARENA_RADIUS = 32.0D;
    public static volatile double LEASH_RADIUS = 48.0D;

    // 技能伤害
    public static volatile float BASIC_DAMAGE = 8.0F;
    public static volatile float BAT_DAMAGE = 12.0F;
    public static volatile float METEOR_DAMAGE = 18.0F;
    public static volatile float SWEEP_DAMAGE = 10.0F;
    public static volatile float BURST_DAMAGE = 12.0F;
    public static volatile float FLAME_DAMAGE = 18.0F;
    public static volatile float TEDDY_DAMAGE = 10.0F;
    public static volatile float SLIME_DAMAGE = 6.0F;
    public static volatile float BLACK_BALL_DAMAGE = 15.0F;

    // 召唤物属性
    public static volatile double TEDDY_HEALTH = 10000.0D;
    public static volatile double TEDDY_MOVEMENT_SPEED = 0.30D;
    public static volatile double TEDDY_FOLLOW_RANGE = 48.0D;
    public static volatile double TEDDY_ARMOR = 20.0D;
    public static volatile double SLIME_HEALTH = 300.0D;
    public static volatile double SLIME_MOVEMENT_SPEED = 0.24D;
    public static volatile double SLIME_FOLLOW_RANGE = 32.0D;
    public static volatile double BLACK_BALL_HEALTH = 200.0D;
    public static volatile double BLACK_BALL_ARMOR = 24.0D;

    // 状态层数
    public static volatile int MAX_METER = 99;
    public static volatile int BLACK_ENERGY_PER_HIT = 5;

    // 持续时间（tick）
    public static volatile int MADNESS_TICKS = 120 * 20;
    public static volatile int PLAGUE_TICKS = 20 * 20;
    public static volatile int PLAGUE_HOST_MARK_TICKS = 5 * 20;
    public static volatile int REVIVE_LOCK_TICKS = 30 * 20;
    public static volatile int METEOR_ROOT_TICKS = 10 * 20;
    public static volatile int BLACK_WATER_DELAY_TICKS = 10 * 20;
    public static volatile int BLACK_WATER_SPLIT_TICKS = 10 * 20;
    public static volatile int HEART_FIRE_TICKS = 20 * 20;
    public static volatile int STRENGTHENED_FIRE_TICKS = 15 * 20;
    public static volatile int BOILING_BLOOD_TICKS = 10 * 20;
    public static volatile int BURST_ECHO_DELAY_TICKS = 3 * 20;

    // 技能冷却（tick）
    public static volatile int BASIC_COOLDOWN = 20;
    public static volatile int BAT_COOLDOWN = 10 * 20;
    public static volatile int METEOR_COOLDOWN = 20 * 20;
    public static volatile int FLAME_COOLDOWN = 30 * 20;
    public static volatile int SWEEP_COOLDOWN = 2 * 20;
    public static volatile int SUMMON_COOLDOWN = 60 * 20;
    public static volatile int PLAGUE_COOLDOWN = 60 * 20;
    public static volatile int BURST_COOLDOWN = 45 * 20;
    public static volatile int BLACK_WATER_COOLDOWN = 30 * 20;
    public static volatile int BLACK_BALL_COOLDOWN = 15 * 20;
    public static volatile int SUPPORT_FIRE_ORB_COOLDOWN = 20 * 20;
    public static volatile int SUPPORT_FIRE_RAIN_COOLDOWN = 30 * 20;
    public static volatile int SUPPORT_PILLAR_COOLDOWN = 40 * 20;
    public static volatile int SUPPORT_HEART_FIRE_COOLDOWN = 40 * 20;
    public static volatile int TEDDY_HIT_COOLDOWN = 2 * 20;
    public static volatile int TEDDY_ROAR_COOLDOWN = 30 * 20;
    public static volatile int SLIME_ATTACK_COOLDOWN = 2 * 20;
    public static volatile int BLACK_BALL_PULSE_COOLDOWN = 2 * 20;
    public static volatile int BOILING_BLOOD_INTERVAL = 2 * 20;

    // 心智/能量层数
    public static volatile int BASIC_CORRUPTION = 0;
    public static volatile int SWEEP_CORRUPTION = 1;
    public static volatile int TEDDY_HIT_CORRUPTION = 5;
    public static volatile int TEDDY_ROAR_CORRUPTION = 2;
    public static volatile int SLIME_HIT_CORRUPTION = 1;
    public static volatile int SLIME_EXPLODE_CORRUPTION = 5;
    public static volatile int BURST_INITIAL_CORRUPTION = 3;
    public static volatile int BURST_ECHO_CORRUPTION = 5;
    public static volatile int BLACK_WATER_CORRUPTION = 3;
    public static volatile int BLACK_BALL_CORRUPTION = 10;

    // 范围
    public static volatile double BASIC_RADIUS = 4.0D;
    public static volatile double SWEEP_RADIUS = 3.5D;
    public static volatile double METEOR_SPLIT_RADIUS = 5.0D;
    public static volatile double PLAGUE_TRANSFER_RADIUS = 5.0D;
    public static volatile double BURST_ECHO_RADIUS = 3.0D;
    public static volatile double FLAME_RANGE = 10.0D;
    public static volatile double FLAME_HALF_ANGLE_DEGREES = 60.0D;
    public static volatile double BLACK_WATER_RADIUS = 1.55D;
    public static volatile double BLACK_WATER_SPREAD_DISTANCE = 3.0D;
    public static volatile double BLACK_BALL_RADIUS = 3.0D;
    public static volatile double TEDDY_ROAR_RADIUS = 10.0D;
    public static volatile double SUPPORT_ZONE_RADIUS = 4.0D;

    // 状态倍率
    public static volatile float PLAGUE_OUTGOING_MULTIPLIER = 1.20F;
    public static volatile float PLAGUE_INCOMING_MULTIPLIER = 1.50F;
    public static volatile float MADNESS_OUTGOING_MULTIPLIER = 3.00F;
    public static volatile double MADNESS_SPEED_MODIFIER = -0.50D;
    public static volatile float STRENGTHENED_FIRE_PER_STACK = 0.10F;
    public static volatile float BOILING_BLOOD_DAMAGE_PER_STACK = 0.75F;
    public static volatile float BOILING_BLOOD_DAMAGE_CAP = 10.0F;

    // 阶段阈值。奶块客户端包未暴露服务端 HP 阈值，因此默认继续沿用 YellowDuck v1。
    public static volatile float PHASE_TWO_HEALTH = 0.80F;
    public static volatile float PHASE_THREE_HEALTH = 0.20F;
    public static volatile float PHASE_THREE_MULTIPLIER = 1.15F;
}
