package com.yourname.yellowduck.silk;

/**
 * 斯尔克战斗数值。
 *
 * 机制/计时优先按奶块客户端配置还原；YellowDuck 原本已经使用的 Boss 总血量和伤害量级保留，
 * 避免直接把服务器现有数值平衡改回奶块客户端表里的 32500/180。
 */
public final class SilkBalance {
    private SilkBalance() {}

    public static final double HEALTH = 430000.0D;
    public static final double ARENA_RADIUS = 32.0D;
    public static final double LEASH_RADIUS = 48.0D;

    public static final float BASIC_DAMAGE = 8.0F;
    public static final float BAT_DAMAGE = 12.0F;
    public static final float METEOR_DAMAGE = 18.0F;
    public static final float SWEEP_DAMAGE = 10.0F;
    public static final float BURST_DAMAGE = 12.0F;
    public static final float FLAME_DAMAGE = 18.0F;
    public static final float TEDDY_DAMAGE = 10.0F;
    public static final float SLIME_DAMAGE = 6.0F;
    public static final float BLACK_BALL_DAMAGE = 15.0F;

    /** 奶块 buff 2270/2271 上限均为 99。 */
    public static final int MAX_METER = 99;
    public static final int BLACK_ENERGY_PER_HIT = 5;

    /** 2273：陷入疯狂，120 秒。 */
    public static final int MADNESS_TICKS = 120 * 20;
    /** 2280：黑暗疫病，20 秒。 */
    public static final int PLAGUE_TICKS = 20 * 20;
    /** 2298：疫病宿主标记，5 秒。 */
    public static final int PLAGUE_HOST_MARK_TICKS = 5 * 20;
    /** 2278：禁止复活，30 秒。 */
    public static final int REVIVE_LOCK_TICKS = 30 * 20;
    /** 2289：流星禁锢，10 秒。 */
    public static final int METEOR_ROOT_TICKS = 10 * 20;
    /** 2279 / 2299：黑水 10 秒后生成 / 分裂。 */
    public static final int BLACK_WATER_DELAY_TICKS = 10 * 20;
    public static final int BLACK_WATER_SPLIT_TICKS = 10 * 20;
    /** 2281：心火庇护，20 秒。 */
    public static final int HEART_FIRE_TICKS = 20 * 20;
    /** 2283：强化火焰，15 秒。 */
    public static final int STRENGTHENED_FIRE_TICKS = 15 * 20;

    // 技能冷却（20 TPS）。明确来自 skill.txt 的项目按原值换算；流星用 7504 的 20 秒预警循环防止 7063 的 1 秒内部技能被误当主循环。
    public static final int BASIC_COOLDOWN = 20;
    public static final int BAT_COOLDOWN = 10 * 20;
    public static final int METEOR_COOLDOWN = 20 * 20;
    public static final int FLAME_COOLDOWN = 30 * 20;
    public static final int SWEEP_COOLDOWN = 2 * 20;
    public static final int SUMMON_COOLDOWN = 60 * 20;
    public static final int PLAGUE_COOLDOWN = 60 * 20;
    public static final int BURST_COOLDOWN = 45 * 20;
    public static final int BLACK_WATER_COOLDOWN = 30 * 20;
    public static final int BLACK_BALL_COOLDOWN = 15 * 20;

    // 助战小樱四个技能的客户端冷却。
    public static final int SUPPORT_FIRE_ORB_COOLDOWN = 20 * 20;
    public static final int SUPPORT_FIRE_RAIN_COOLDOWN = 30 * 20;
    public static final int SUPPORT_PILLAR_COOLDOWN = 40 * 20;
    public static final int SUPPORT_HEART_FIRE_COOLDOWN = 40 * 20;

    public static final int BASIC_CORRUPTION = 0;
    public static final int SWEEP_CORRUPTION = 1;
    public static final int TEDDY_HIT_CORRUPTION = 5;
    public static final int TEDDY_ROAR_CORRUPTION = 2;
    public static final int SLIME_HIT_CORRUPTION = 1;
    public static final int SLIME_EXPLODE_CORRUPTION = 5;
    public static final int BURST_INITIAL_CORRUPTION = 3;
    public static final int BURST_ECHO_CORRUPTION = 5;
    public static final int BLACK_WATER_CORRUPTION = 3;
    public static final int BLACK_BALL_CORRUPTION = 10;

    public static final double BASIC_RADIUS = 4.0D;
    public static final double METEOR_SPLIT_RADIUS = 5.0D;
    public static final double PLAGUE_TRANSFER_RADIUS = 5.0D;
    public static final double BURST_ECHO_RADIUS = 3.0D;
    public static final double FLAME_RANGE = 10.0D;
    public static final double BLACK_WATER_RADIUS = 1.55D;
    public static final double BLACK_BALL_RADIUS = 3.0D;
    public static final double SUPPORT_ZONE_RADIUS = 4.0D;

    /** 旧 YellowDuck 阶段阈值保留；奶块客户端包没有暴露服务端 AI 的 HP 阈值。 */
    public static final float PHASE_TWO_HEALTH = 0.80F;
    public static final float PHASE_THREE_HEALTH = 0.20F;
    public static final float PHASE_THREE_MULTIPLIER = 1.15F;
}
