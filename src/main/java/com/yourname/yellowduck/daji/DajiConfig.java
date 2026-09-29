package com.yourname.yellowduck.daji;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 妲己战斗参数。
 *
 * 所有参数和 YellowDuck 其它生物共用 config/yellowduck-entities.toml，
 * 不再创建单独的 yellowduck-daji.toml。
 * 客户端资料能确认的数值按原表作为默认；原服务端 AI 未下发的项目显式保留为可调值。
 */
public final class DajiConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-entities.toml");
    private static final String SECTION = "daji_battle";

    private static volatile long lastModified = Long.MIN_VALUE;
    private static volatile long lastCheckNanos;

    // ===== 客户端表可确认的 Boss 基础值 =====
    public static volatile double bossHealth = 15000.0D;
    public static volatile double bossAttack = 200.0D;
    public static volatile int bossDefense = 120;
    public static volatile double movementSpeed = 0.30D;
    public static volatile double followRange = 30.0D;

    // ===== 行为复原：形态持续 =====
    public static volatile int humanTicks = 480;
    public static volatile int fireTicks = 480;
    public static volatile int spiralTicks = 300;
    public static volatile int shieldFailsafeTicks = 1200;

    // ===== 客户端表可确认的技能 CD =====
    public static volatile int basicCooldown = 40;
    public static volatile int windCooldown = 280;
    public static volatile int summonCooldown = 360;
    public static volatile int whiteFoxCooldown = 360;
    public static volatile int chargeCooldown = 300;
    public static volatile int redPowerCooldown = 400;
    public static volatile int spiritFireCooldown = 480;
    public static volatile int rageInterval = 480;

    // ===== MC 实际结算伤害：原表三元组不能直接等价成 Minecraft 最终伤害，因此开放配置 =====
    public static volatile float basicDamage = 200.0F;
    public static volatile float windDamage = 200.0F;
    public static volatile float chargeDamage = 200.0F;
    public static volatile float fireDamage = 200.0F;
    public static volatile float rageDamage = 100.0F;

    // ===== 客户端表可确认的持续时间 =====
    public static volatile int redPowerDuration = 160;
    public static volatile int spiritFireDuration = 200;
    public static volatile int spiritFireSpawnInterval = 20;
    public static volatile int chargeSlowTicks = 120;
    public static volatile int fireSlowTicks = 100;
    // 原表 change_move_speed:-300，对应约 -30%；原版缓慢 II 正好约 -30%。
    public static volatile int slowAmplifier = 1;

    // ===== 行为复原：原服务端未下发 =====
    public static volatile float shieldHealth = 6000.0F;
    public static volatile float rageDamagePerStack = 0.10F;
    public static volatile float rageReductionPerStack = 0.10F;
    public static volatile float rageReductionCap = 0.90F;
    public static volatile int rageMaxStacks = 99;

    public static volatile int smallFoxCount = 4;
    public static volatile int blueFoxCount = 1;
    public static volatile int whiteFoxCount = 1;
    public static volatile double summonRadius = 12.0D;

    // ===== 客户端表可确认的召唤物基础值 =====
    public static volatile double smallFoxHealth = 500.0D;
    public static volatile double smallFoxAttack = 60.0D;
    public static volatile double eliteFoxHealth = 2000.0D;
    public static volatile double eliteFoxAttack = 120.0D;

    // ===== 行为复原：范围/移动节奏 =====
    public static volatile int chargeWarmupTicks = 13;
    public static volatile int chargeMoveTicks = 24;
    public static volatile double chargeSpeed = 1.10D;
    public static volatile double windRadius = 4.0D;
    public static volatile double rageRadius = 6.0D;
    public static volatile int firePatchLifetimeTicks = 100;
    public static volatile double firePatchRadius = 4.0D;

    private DajiConfig() {}

    /**
     * YellowDuckMod 会先让 EntityTuningConfig 创建/读取总配置，再调用这里。
     * 老配置没有 [daji_battle] 时只在文件末尾追加妲己段，不覆盖任何已有参数。
     */
    public static synchronized void ensureLoaded() {
        try {
            if (Files.notExists(PATH)) {
                LOGGER.warn("{} 尚未创建，妲己暂时使用源码默认值。", PATH.getFileName());
                return;
            }
            ensureSectionExists();
            reloadInternal();
        } catch (Exception e) {
            LOGGER.error("读取妲己配置失败，继续使用上一份有效值/内置默认值", e);
        }
    }

    /**
     * Boss/召唤物会调用；总配置保存后约 1 秒自动热读取。
     * 因此仍和现有 YellowDuck 配置一样，不需要额外的妲己配置文件。
     */
    public static void reloadIfChanged() {
        long now = System.nanoTime();
        if (now - lastCheckNanos < 1_000_000_000L) return;
        lastCheckNanos = now;
        try {
            if (Files.notExists(PATH)) return;
            long modified = Files.getLastModifiedTime(PATH).toMillis();
            if (modified != lastModified) {
                ensureSectionExists();
                reloadInternal();
            }
        } catch (Exception e) {
            LOGGER.warn("检查 {} 中妲己配置更新失败：{}", PATH.getFileName(), e.toString());
        }
    }

    /** 手动重载入口，后续需要接到其它命令时可直接复用。 */
    public static synchronized boolean reload() {
        try {
            if (Files.notExists(PATH)) return false;
            ensureSectionExists();
            reloadInternal();
            return true;
        } catch (Exception e) {
            LOGGER.error("重载 {} 中的妲己配置失败；继续使用上一份有效值。", PATH.getFileName(), e);
            return false;
        }
    }

    private static void ensureSectionExists() throws Exception {
        List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);
        String wanted = "[" + SECTION + "]";
        for (String raw : lines) {
            if (raw.trim().equalsIgnoreCase(wanted)) return;
        }
        Files.writeString(PATH, "\n" + defaultSection(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        LOGGER.info("已向 {} 追加妲己战斗配置 [{}]；已有配置值未改动。", PATH.getFileName(), SECTION);
    }

    private static synchronized void reloadInternal() throws Exception {
        List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        String section = "";

        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                continue;
            }
            if (!SECTION.equals(section)) continue;

            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = unquote(line.substring(eq + 1).trim());
            values.put(key, value);
        }

        // 基础属性
        bossHealth = d(values, "boss_health", bossHealth, 1, 1e9);
        bossAttack = d(values, "boss_attack", bossAttack, 0, 1e9);
        bossDefense = i(values, "boss_defense", bossDefense, 0, 1_000_000);
        movementSpeed = d(values, "movement_speed", movementSpeed, 0, 5);
        followRange = d(values, "follow_range", followRange, 1, 256);

        // 形态轮转
        humanTicks = i(values, "human_form_ticks", humanTicks, 20, 12000);
        fireTicks = i(values, "fire_form_ticks", fireTicks, 20, 12000);
        spiralTicks = i(values, "spiral_form_ticks", spiralTicks, 20, 12000);
        shieldFailsafeTicks = i(values, "shield_failsafe_ticks", shieldFailsafeTicks, 20, 120000);

        // 技能 CD
        basicCooldown = i(values, "basic_cooldown_ticks", basicCooldown, 1, 12000);
        windCooldown = i(values, "wind_cooldown_ticks", windCooldown, 1, 12000);
        summonCooldown = i(values, "summon_cooldown_ticks", summonCooldown, 1, 12000);
        whiteFoxCooldown = i(values, "white_fox_cooldown_ticks", whiteFoxCooldown, 1, 12000);
        chargeCooldown = i(values, "charge_cooldown_ticks", chargeCooldown, 1, 12000);
        redPowerCooldown = i(values, "red_power_cooldown_ticks", redPowerCooldown, 1, 12000);
        spiritFireCooldown = i(values, "spirit_fire_cooldown_ticks", spiritFireCooldown, 1, 12000);
        rageInterval = i(values, "rage_interval_ticks", rageInterval, 20, 12000);

        // MC 伤害
        basicDamage = f(values, "basic_damage", basicDamage, 0.0F, 1_000_000_000.0F);
        windDamage = f(values, "wind_damage", windDamage, 0.0F, 1_000_000_000.0F);
        chargeDamage = f(values, "charge_damage", chargeDamage, 0.0F, 1_000_000_000.0F);
        fireDamage = f(values, "fire_damage", fireDamage, 0.0F, 1_000_000_000.0F);
        rageDamage = f(values, "rage_damage", rageDamage, 0.0F, 1_000_000_000.0F);

        // 持续/控制
        redPowerDuration = i(values, "red_power_duration_ticks", redPowerDuration, 1, 12000);
        spiritFireDuration = i(values, "spirit_fire_duration_ticks", spiritFireDuration, 1, 12000);
        spiritFireSpawnInterval = i(values, "spirit_fire_spawn_interval_ticks", spiritFireSpawnInterval, 1, 1200);
        chargeSlowTicks = i(values, "charge_slow_ticks", chargeSlowTicks, 1, 12000);
        fireSlowTicks = i(values, "fire_slow_ticks", fireSlowTicks, 1, 12000);
        slowAmplifier = i(values, "slow_amplifier", slowAmplifier, 0, 10);

        // 护盾/狂暴
        shieldHealth = f(values, "shield_health", shieldHealth, 1.0F, 1_000_000_000.0F);
        rageDamagePerStack = f(values, "rage_damage_per_stack", rageDamagePerStack, 0, 10);
        rageReductionPerStack = f(values, "rage_reduction_per_stack", rageReductionPerStack, 0, 1);
        rageReductionCap = f(values, "rage_reduction_cap", rageReductionCap, 0, 0.99F);
        rageMaxStacks = i(values, "rage_max_stacks", rageMaxStacks, 1, 99);

        // 召唤物
        smallFoxCount = i(values, "small_fox_count", smallFoxCount, 0, 64);
        blueFoxCount = i(values, "blue_fox_count", blueFoxCount, 0, 64);
        whiteFoxCount = i(values, "white_fox_count", whiteFoxCount, 0, 64);
        summonRadius = d(values, "summon_radius", summonRadius, 1, 64);
        smallFoxHealth = d(values, "small_fox_health", smallFoxHealth, 1, 1e9);
        smallFoxAttack = d(values, "small_fox_attack", smallFoxAttack, 0, 1e9);
        eliteFoxHealth = d(values, "elite_fox_health", eliteFoxHealth, 1, 1e9);
        eliteFoxAttack = d(values, "elite_fox_attack", eliteFoxAttack, 0, 1e9);

        // 行为复原
        chargeWarmupTicks = i(values, "charge_warmup_ticks", chargeWarmupTicks, 1, 1200);
        chargeMoveTicks = i(values, "charge_move_ticks", chargeMoveTicks, 1, 1200);
        chargeSpeed = d(values, "charge_speed", chargeSpeed, 0.05, 10);
        windRadius = d(values, "wind_radius", windRadius, 0.5, 64);
        rageRadius = d(values, "rage_radius", rageRadius, 0.5, 64);
        firePatchLifetimeTicks = i(values, "fire_patch_lifetime_ticks", firePatchLifetimeTicks, 20, 12000);
        firePatchRadius = d(values, "fire_patch_radius", firePatchRadius, 0.5, 64);

        lastModified = Files.getLastModifiedTime(PATH).toMillis();
        LOGGER.info("妲己配置已从 {} 的 [{}] 读取。", PATH.getFileName(), SECTION);
    }

    private static int i(Map<String,String> v,String k,int fallback,int min,int max){
        try{return Math.max(min,Math.min(max,Integer.parseInt(v.getOrDefault(k,Integer.toString(fallback)).trim())));}catch(Exception e){return fallback;}
    }

    private static double d(Map<String,String> v,String k,double fallback,double min,double max){
        try{double n=Double.parseDouble(v.getOrDefault(k,Double.toString(fallback)).trim());return Double.isFinite(n)?Math.max(min,Math.min(max,n)):fallback;}catch(Exception e){return fallback;}
    }

    private static float f(Map<String,String> v,String k,float fallback,float min,float max){
        return (float)d(v,k,fallback,min,max);
    }

    private static String stripComment(String line){
        boolean q=false;
        for(int n=0;n<line.length();n++){
            char c=line.charAt(n);
            if(c=='"'&&(n==0||line.charAt(n-1)!='\\'))q=!q;
            if(c=='#'&&!q)return line.substring(0,n);
        }
        return line;
    }

    private static String unquote(String s){
        s=s.trim();
        return s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;
    }

    private static String defaultSection() {
        return """
[daji_battle]
# BOSS：妲己。20 tick = 1 秒。
# 这一段属于 YellowDuck 总生物配置 yellowduck-entities.toml，不会再生成 yellowduck-daji.toml。
# SC 已确认：Boss 15000生命、200攻击、120防御；普攻2秒、风雷引14秒、召狐18秒、冲撞15秒、赤狐之力20秒、召唤白狐18秒、灵狐魅火24秒。
# SC 已确认：赤狐之力8秒；灵狐魅火10秒且每1秒生成一次魔火；冲撞减速6秒；魔火减速5秒；狂暴每层伤害+10%、承伤-10%，最多99层。
# 原活动服务端 AI 未下发，所以形态轮转、实际盾值、召唤数量、冲撞位移、魔火残留范围等是可调复原参数。

# ===== Boss基础属性 =====
boss_health = 150000
boss_attack = 120
boss_defense = 120
movement_speed = 0.30
follow_range = 30

# ===== 形态轮转 =====
human_form_ticks = 480
fire_form_ticks = 480
spiral_form_ticks = 300
# 护灵镜原 Buff 持续时间为 -1；正常靠破盾离开。这里只作为防卡死兜底。
shield_failsafe_ticks = 1200

# ===== 技能CD =====
basic_cooldown_ticks = 40
wind_cooldown_ticks = 280
summon_cooldown_ticks = 360
white_fox_cooldown_ticks = 360
charge_cooldown_ticks = 300
red_power_cooldown_ticks = 400
spirit_fire_cooldown_ticks = 480
rage_interval_ticks = 480

# ===== MC实际结算伤害 =====
# SC 的 skill_damage 三元组不能直接等价为 Minecraft 最终伤害，因此单独开放。
basic_damage = 200
wind_damage = 200
charge_damage = 200
fire_damage = 200
rage_damage = 100

# ===== 持续与控制 =====
red_power_duration_ticks = 160
spirit_fire_duration_ticks = 200
spirit_fire_spawn_interval_ticks = 20
charge_slow_ticks = 120
fire_slow_ticks = 100
# 缓慢II约等于30%移速降低，与原表 -300 更接近。
slow_amplifier = 1

# ===== 护灵镜 / 狂暴 =====
# SC 只明确“护灵镜=无敌”，没有下发 Boss 实际护盾耐久。
shield_health = 6000
rage_damage_per_stack = 0.10
rage_reduction_per_stack = 0.10
# 防止高层数把 Minecraft 最终伤害压成负数，默认封顶90%减伤。
rage_reduction_cap = 0.90
rage_max_stacks = 99

# ===== 召唤物 =====
# 数量属于 AI 复原值；生命/攻击按当前已解析数据作为默认。
small_fox_count = 4
blue_fox_count = 1
white_fox_count = 1
summon_radius = 12
small_fox_health = 500
small_fox_attack = 60
elite_fox_health = 2000
elite_fox_attack = 120

# ===== AI复原参数 =====
# 狐形 attack_04 原动画约0.64秒，作为冲撞蓄力默认值。
charge_warmup_ticks = 13
charge_move_ticks = 24
charge_speed = 1.10
wind_radius = 4.0
rage_radius = 6.0
# 魔火 NPC 原表没有强制存在时间；这里给战斗场地残留一个可调测试值。
fire_patch_lifetime_ticks = 100
fire_patch_radius = 4.0
""";
    }
}
