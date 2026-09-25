package com.yourname.yellowduck.garmr;

/**
 * 地狱双头犬·加姆战斗参数。
 *
 * V5 以用户提供的“4.1.6.3 恐惧之地”规则表为主：表里有明确数值的直接按表执行；
 * 表里只写了“增加/一定范围”但没有给出倍率或半径的项保留为显式可调值，不冒充原始数值。
 */
public final class GarmrConfig {
    private GarmrConfig() {}

    // ===== 基础规则 / Boss 数值（用户图片明确） =====
    public static final double BOSS_HEALTH = 40_000.0D;
    public static final float BASIC_DAMAGE = 180.0F;
    public static final int BOSS_DEFENSE = 360;
    public static final int BOSS_ATTACK_LEVEL = 5;
    public static final int BOSS_DEFENSE_LEVEL = 10;
    public static final int MELEE_DEFENSE = BOSS_DEFENSE;
    public static final int RANGED_DEFENSE = BOSS_DEFENSE;
    public static final int MAGIC_DEFENSE = BOSS_DEFENSE;
    public static final float DAMAGE_REDUCTION = 0.0F;

    public static final int CURSE_INTERVAL_TICKS = 100; // 5 秒 +1 索命
    public static final int CURSE_KILL_STACKS = 10;     // 用户图片：10 层必死

    // 玩家死亡 -> 亡灵战士
    public static final double DEATH_GUARD_HEALTH = 1_000.0D;
    public static final float DEATH_GUARD_ATTACK = 66.0F;
    public static final int DEATH_GUARD_DEFENSE = 68;
    public static final int DEATH_GUARD_ATTACK_LEVEL = 4;
    public static final int DEATH_GUARD_DEFENSE_LEVEL = 10;
    public static final int DEATH_GUARD_SLOW_TICKS = 200; // 10 秒
    public static final double DEATH_GUARD_SLOW_MULTIPLIER = -0.50D; // -50% 移速

    // ===== P1：100% ~ 80% =====
    public static final int TAKEOFF_TICKS = 20;
    public static final int P1_PROJECTILE_INTERVAL_TICKS = 100; // 5 秒
    public static final int P1_PROJECTILE_TARGET_COUNT = 3;     // 现有已解析攻略：随机多名，保留 3 人点名
    public static final float P1_AOE_DAMAGE = BASIC_DAMAGE * 0.30F; // 30% 近战物理 = 54
    public static final float RANGED_DAMAGE = P1_AOE_DAMAGE; // 投射物默认值；实际 P1 会显式 configure

    public static final double LAVA_GUARD_HEALTH = 8_000.0D;
    public static final float LAVA_GUARD_ATTACK = 90.0F;
    public static final int LAVA_GUARD_DEFENSE = 120;
    public static final int LAVA_GUARD_ATTACK_LEVEL = 5;
    public static final int LAVA_GUARD_DEFENSE_LEVEL = 10;

    public static final int P1_ARCHER_COUNT = 3; // 用户本轮明确“首击起飞后召唤四只怪”：1熔岩卫士+3射手
    public static final double P1_ARCHER_HEALTH = 150.0D;
    public static final float P1_ARCHER_ATTACK = 66.0F;
    public static final int P1_ARCHER_DEFENSE = 0;
    public static final int P1_ARCHER_ATTACK_LEVEL = 5;
    public static final int P1_ARCHER_DEFENSE_LEVEL = 10;

    // P1 阿努比斯祝福：伤害 +100%，持续 30 秒。
    // 图片未写祝福重新发放的间隔/命中目标规则；V4 以“持续 30 秒”作为轮转间隔并选最近玩家。
    public static final int ANUBIS_BLESS_INTERVAL_TICKS = 600;
    public static final int ANUBIS_BLESS_DURATION_TICKS = 600;
    public static final float ANUBIS_BLESS_DAMAGE_MULTIPLIER = 2.0F;

    // ===== P2：80% ~ 50% =====
    public static final float PHASE_TWO_HEALTH = 0.80F;
    public static final float PHASE_THREE_HEALTH = 0.50F;

    public static final int LADY_INTERVAL_TICKS = 600; // 30 秒，冰/火轮流
    public static final double LADY_HEALTH = 99_999.0D;
    public static final float LADY_BASE_DAMAGE = 44.0F;
    public static final int LADY_DEFENSE = 1;
    public static final int LADY_ATTACK_LEVEL = 3;
    public static final int LADY_DEFENSE_LEVEL = 10;
    public static final float LADY_DAMAGE_GROWTH_PER_SECOND = 0.05F; // 每秒 +5%
    public static final double LADY_AOE_RADIUS = 3.5D;

    public static final int BREATH_INTERVAL_TICKS = 200; // 10 秒，冰/火轮流
    public static final float BREATH_DAMAGE = BASIC_DAMAGE * 0.50F; // 50% 近战物理 = 90
    public static final float BREATH_HALF_ANGLE_DEGREES = 30.0F; // 总扇形 60°
    public static final double BREATH_RANGE = 32.0D; // 冰/火吐息统一 32 格有效距离
    public static final int BREATH_DURATION_TICKS = 40;

    // P2 阿努比斯守护：进入黄血阶段先必出 1 次，之后每 30 秒最近玩家获得 15 秒守护，减伤 50%，圈内每秒 -1 索命。
    public static final int ANUBIS_GUARD_INTERVAL_TICKS = 600;
    public static final int ANUBIS_GUARD_DURATION_TICKS = 300;
    public static final float ANUBIS_PROTECTION_MULTIPLIER = 0.50F;
    public static final int CLEANSE_INTERVAL_TICKS = 20;
    public static final int CLEANSE_STACKS_PER_TICK = 1;
    public static final double CLEANSE_RADIUS = 3.25D; // 图片只写“一定区域”，半径仍保留可调

    // ===== P3：50% ~ 0% =====
    // 图片只写“普攻伤害增加”而未给增加倍率；V5 继续使用可调 25% 测试值，不标记为原始值。
    public static final float PHASE_THREE_BASIC_MULTIPLIER = 1.25F;

    public static final int DEVIL_INTERVAL_TICKS = 300; // 15 秒
    public static final double DEVIL_HEALTH = 400.0D;
    public static final float DEVIL_ATTACK = 1.0F;
    public static final int DEVIL_DEFENSE = 0;
    public static final int DEVIL_ATTACK_LEVEL = 5;
    public static final int DEVIL_DEFENSE_LEVEL = 10;
    public static final double DEVIL_EXPLOSION_RADIUS = 12.0D;
    public static final float DEVIL_EXPLOSION_MAX_HEALTH_RATIO = 0.20F;
    public static final double DEVIL_MOVE_SPEED = 0.06D; // V5：从阿努比斯头顶约 7 格缓慢下落

    // P3 阿努比斯献祭：50% 最大生命 -> 5 秒后分身 -> 30 秒持续。
    // 图片未写献祭本身的重施间隔；V5 沿用 P2 的 30 秒协战节奏，且不允许分身重叠。
    public static final int ANUBIS_SACRIFICE_INTERVAL_TICKS = 600;
    public static final float ANUBIS_SACRIFICE_DAMAGE_RATIO = 0.50F;
    public static final int ANUBIS_CLONE_DELAY_TICKS = 100;
    public static final int ANUBIS_CLONE_DURATION_TICKS = 600;
    public static final float ANUBIS_FAIL_DAMAGE_RATIO = 1.50F;

    // ===== 共用动作 / 场地测试值 =====
    public static final double MELEE_RANGE = 4.8D;
    public static final double PROJECTILE_AOE_RADIUS = 3.5D; // 图片未写子弹爆炸半径
    public static final int BASIC_COOLDOWN_TICKS = 32;       // 图片未写普攻间隔
    // Anim-1 的普攻段为 44..66（22 帧），保持完整播放，避免动作被过快压缩到看不见。
    public static final int BASIC_ACTION_TICKS = 22;
    public static final int RANGED_ACTION_TICKS = 20;
    public static final int LANDING_TICKS = 60;
    public static final double AIR_HEIGHT = 6.0D;

    // 恐惧之地蓝图：金块相对海晶灯 Boss 点位于 +Z 32 格，作为阿努比斯固定点。
    public static final double ANUBIS_OFFSET_Z = 32.0D;
    public static final double LADY_OFFSET = 16.0D;     // 默认测试场地临时坐标
}
