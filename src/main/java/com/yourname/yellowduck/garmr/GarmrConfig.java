package com.yourname.yellowduck.garmr;

/**
 * 地狱双头犬·加姆战斗参数。
 *
 * 已从交接/资源确认的数值直接标为 CONFIRMED；其余是“可测试占位值”，
 * 绝不冒充奶块原版值。等解析到原配置后只需要替换这里，不改状态机结构。
 */
public final class GarmrConfig {
    private GarmrConfig() {}

    // ===== 已确认 =====
    public static final int CURSE_INTERVAL_TICKS = 100;          // CONFIRMED: 5 秒 +1 索命
    public static final int CURSE_KILL_STACKS = 20;              // CONFIRMED: 20 层死亡
    public static final int P1_PROJECTILE_INTERVAL_TICKS = 100;  // 攻略交叉：约 5 秒
    public static final int BASIC_ATTACKS_PER_BREATH = 4;        // CONFIRMED: 4 次普通攻击 -> 吐息
    public static final int LADY_INTERVAL_TICKS = 600;           // 攻略交叉：约 30 秒
    public static final int DEVIL_INTERVAL_TICKS = 300;          // CONFIRMED: P3 约 15 秒
    public static final float BREATH_HALF_ANGLE_DEGREES = 30.0F; // .pj CONFIRMED: ±30°
    public static final float PHASE_TWO_HEALTH = 0.80F;
    public static final float PHASE_THREE_HEALTH = 0.50F;

    // ===== 可测试占位值：TODO 用原始 NetCraft/奶块配置替换 =====
    public static final double BOSS_HEALTH = 2000.0D; // TODO parsed original value
    public static final float BASIC_DAMAGE = 20.0F;   // TODO
    public static final float RANGED_DAMAGE = 18.0F;  // TODO
    public static final float P1_AOE_DAMAGE = 20.0F;  // TODO
    public static final float BREATH_DAMAGE = 28.0F;  // TODO
    public static final float LADY_AOE_DAMAGE = 18.0F;// TODO
    public static final int MELEE_DEFENSE = 0;        // TODO
    public static final int RANGED_DEFENSE = 0;       // TODO
    public static final int MAGIC_DEFENSE = 0;        // TODO
    public static final float DAMAGE_REDUCTION = 0F;  // TODO

    public static final double MELEE_RANGE = 4.8D;
    public static final double BREATH_RANGE = 18.0D;  // TODO original distance
    public static final double PROJECTILE_AOE_RADIUS = 3.5D; // TODO
    public static final int BASIC_COOLDOWN_TICKS = 32; // TODO
    // V3：动作 clip 已拆分。普攻/远程动作至少保持到客户端能完整看到一次。
    public static final int BASIC_ACTION_TICKS = 15;   // 44~66，约 0.73 秒
    public static final int RANGED_ACTION_TICKS = 20;  // 地面约 0.97 秒；空中约 0.77 秒
    public static final int BREATH_DURATION_TICKS = 40; // 战斗判定仍保持 2 秒，客户端将 clip 等比拉伸到该时长
    public static final int LANDING_TICKS = 60;       // TODO original landing duration；landing clip 等比拉伸

    public static final double ANUBIS_OFFSET_Z = 18.0D; // default arena temporary coordinate
    public static final double LADY_OFFSET = 16.0D;     // default arena temporary coordinate
    public static final double CLEANSE_RADIUS = 3.25D;  // TODO original white-circle radius
    public static final int ANUBIS_SELECT_INTERVAL_TICKS = 100; // TODO
    public static final float ANUBIS_SELECT_DAMAGE = 4.0F;      // TODO
}
