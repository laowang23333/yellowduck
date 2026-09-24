package com.yourname.yellowduck.tengu;

/** 天狗 Boss / 坐骑基础参数。驯服规则后续单独接入。 */
public final class TenguConfig {
    private TenguConfig() {}

    public static final double BOSS_HEALTH = 100_000.0D;
    public static final double BOSS_ATTACK = 120.0D;
    public static final double BOSS_SPEED = 0.28D;
    public static final double BOSS_FOLLOW_RANGE = 48.0D;

    /** Boss 击杀后生成未驯服天狗的概率。 */
    public static final float MOUNT_SPAWN_CHANCE = 0.10F;

    /** 未驯服天狗存在 3 分钟。 */
    public static final int UNTAMED_LIFETIME_TICKS = 20 * 60 * 3;

    /** 主世界夜间随机刷新：每 60 秒检查一次，默认 5% 成功率。 */
    public static final int NATURAL_SPAWN_CHECK_INTERVAL = 20 * 60;
    public static final double NATURAL_SPAWN_CHANCE = 0.05D;
    public static final int NATURAL_SPAWN_MIN_DISTANCE = 24;
    public static final int NATURAL_SPAWN_MAX_DISTANCE = 48;
}
