package com.yourname.yellowduck.silk;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 疯狂教授斯尔克战斗参数。
 *
 * 注意：不再创建 yellowduck-silk.toml。
 * 所有值都写进已有的 config/yellowduck-entities.toml：
 * - [silk_boss] 继续负责教授基础属性；
 * - [silk_battle] 负责技能伤害、CD、持续时间、层数、范围、召唤物和倍率。
 *
 * 本类只读取/追加 YellowDuck 自己管理的 TOML，不注册 ForgeConfigSpec，
 * 因此不会出现 Forge “Correcting ... to its default” 自动改回默认的问题。
 */
public final class SilkConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-entities.toml");
    private static final String BOSS_SECTION = "silk_boss";
    private static final String BATTLE_SECTION = "silk_battle";
    /** 启动时先冻结源码默认值；配置删字段时回默认，而不是沿用上一轮运行值。 */
    private static final Values DEFAULTS = Values.current();
    private static volatile boolean loaded;

    private SilkConfig() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        try {
            if (Files.notExists(PATH)) {
                // 正常情况下 EntityTuningConfig 会先创建该文件；这里仅防御异常启动顺序。
                Files.createDirectories(PATH.getParent());
                Files.writeString(PATH,
                        "# YellowDuck 生物属性与战斗配置\n\n" + battleSectionText(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            } else {
                appendBattleSectionIfMissing();
            }
            reloadInternal();
        } catch (Exception ex) {
            LOGGER.error("读取教授配置 {} 失败；继续使用上一份有效/源码默认值。", PATH.getFileName(), ex);
        } finally {
            loaded = true;
        }
    }

    public static synchronized boolean reload() {
        ensureLoaded();
        try {
            appendBattleSectionIfMissing();
        } catch (Exception ex) {
            LOGGER.warn("补充 [{}] 配置段失败，但仍尝试读取现有配置：{}", BATTLE_SECTION, ex.toString());
        }
        return reloadInternal();
    }

    private static boolean reloadInternal() {
        if (Files.notExists(PATH)) {
            LOGGER.error("找不到教授共用配置文件：{}", PATH);
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);
            long meaningful = lines.stream()
                    .map(SilkConfig::stripComment)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .count();
            if (meaningful < 3) {
                LOGGER.error("{} 内容疑似为空或仍在保存；拒绝切换教授配置，继续使用上一份有效值。",
                        PATH.getFileName());
                return false;
            }

            Parsed parsed = parse(lines);
            List<String> errors = new ArrayList<>(parsed.errors());
            Values v = DEFAULTS.copy();

            Map<String, String> boss = parsed.sections().getOrDefault(BOSS_SECTION, Map.of());
            Map<String, String> battle = parsed.sections().getOrDefault(BATTLE_SECTION, Map.of());

            // 已有 [silk_boss]：继续使用原本的通用字段。
            v.health = d(boss, BOSS_SECTION, "max_health", v.health, 1.0D, 1.0E9D, errors);
            v.basicDamage = f(boss, BOSS_SECTION, "attack_damage", v.basicDamage, 0.0D, 1.0E6D, errors);
            v.bossMovementSpeed = d(boss, BOSS_SECTION, "movement_speed", v.bossMovementSpeed, 0.0D, 10.0D, errors);
            v.bossFollowRange = d(boss, BOSS_SECTION, "follow_range", v.bossFollowRange, 1.0D, 1024.0D, errors);
            v.bossKnockbackResistance = d(boss, BOSS_SECTION, "knockback_resistance", v.bossKnockbackResistance, 0.0D, 1.0D, errors);
            v.bossTier = i(boss, BOSS_SECTION, "netcraft_tier", v.bossTier, 1, 100, errors);

            // [silk_battle]：教授完整技能参数。
            v.bossMeleeDefense = i(battle, BATTLE_SECTION, "boss_melee_defense", v.bossMeleeDefense, 0, 1_000_000, errors);
            v.bossRangedDefense = i(battle, BATTLE_SECTION, "boss_ranged_defense", v.bossRangedDefense, 0, 1_000_000, errors);
            v.bossMagicDefense = i(battle, BATTLE_SECTION, "boss_magic_defense", v.bossMagicDefense, 0, 1_000_000, errors);
            v.bossDamageReduction = f(battle, BATTLE_SECTION, "boss_fixed_reduction", v.bossDamageReduction, 0.0D, 0.99D, errors);
            v.arenaRadius = d(battle, BATTLE_SECTION, "arena_radius", v.arenaRadius, 4.0D, 256.0D, errors);
            v.leashRadius = d(battle, BATTLE_SECTION, "leash_radius", v.leashRadius, 4.0D, 512.0D, errors);
            v.phaseTwoHealth = f(battle, BATTLE_SECTION, "phase2_health_ratio", v.phaseTwoHealth, 0.001D, 0.999D, errors);
            v.phaseThreeHealth = f(battle, BATTLE_SECTION, "phase3_health_ratio", v.phaseThreeHealth, 0.001D, 0.999D, errors);
            if (v.phaseThreeHealth >= v.phaseTwoHealth) {
                errors.add("[" + BATTLE_SECTION + "] phase3_health_ratio 必须小于 phase2_health_ratio");
            }
            v.phaseThreeMultiplier = f(battle, BATTLE_SECTION, "phase3_damage_multiplier", v.phaseThreeMultiplier, 0.0D, 100.0D, errors);

            v.batDamage = f(battle, BATTLE_SECTION, "bat_damage", v.batDamage, 0.0D, 1.0E6D, errors);
            v.meteorDamage = f(battle, BATTLE_SECTION, "meteor_damage", v.meteorDamage, 0.0D, 1.0E6D, errors);
            v.sweepDamage = f(battle, BATTLE_SECTION, "sweep_damage", v.sweepDamage, 0.0D, 1.0E6D, errors);
            v.burstDamage = f(battle, BATTLE_SECTION, "energy_burst_damage", v.burstDamage, 0.0D, 1.0E6D, errors);
            v.flameDamage = f(battle, BATTLE_SECTION, "dark_flame_damage", v.flameDamage, 0.0D, 1.0E6D, errors);
            v.teddyDamage = f(battle, BATTLE_SECTION, "dark_teddy_attack_damage", v.teddyDamage, 0.0D, 1.0E6D, errors);
            v.slimeDamage = f(battle, BATTLE_SECTION, "dark_slime_attack_damage", v.slimeDamage, 0.0D, 1.0E6D, errors);
            v.blackBallDamage = f(battle, BATTLE_SECTION, "black_ball_damage", v.blackBallDamage, 0.0D, 1.0E6D, errors);

            v.teddyHealth = d(battle, BATTLE_SECTION, "dark_teddy_health", v.teddyHealth, 1.0D, 1.0E9D, errors);
            v.teddyMovementSpeed = d(battle, BATTLE_SECTION, "dark_teddy_movement_speed", v.teddyMovementSpeed, 0.0D, 10.0D, errors);
            v.teddyFollowRange = d(battle, BATTLE_SECTION, "dark_teddy_follow_range", v.teddyFollowRange, 1.0D, 1024.0D, errors);
            v.teddyArmor = d(battle, BATTLE_SECTION, "dark_teddy_armor", v.teddyArmor, 0.0D, 1.0E6D, errors);
            v.slimeHealth = d(battle, BATTLE_SECTION, "dark_slime_health", v.slimeHealth, 1.0D, 1.0E9D, errors);
            v.slimeMovementSpeed = d(battle, BATTLE_SECTION, "dark_slime_movement_speed", v.slimeMovementSpeed, 0.0D, 10.0D, errors);
            v.slimeFollowRange = d(battle, BATTLE_SECTION, "dark_slime_follow_range", v.slimeFollowRange, 1.0D, 1024.0D, errors);
            v.blackBallHealth = d(battle, BATTLE_SECTION, "black_ball_health", v.blackBallHealth, 1.0D, 1.0E9D, errors);
            v.blackBallArmor = d(battle, BATTLE_SECTION, "black_ball_armor", v.blackBallArmor, 0.0D, 1.0E6D, errors);

            v.basicCooldown = ticks(battle, "basic_attack_cooldown_ticks", v.basicCooldown, 1, 1_000_000, errors);
            v.batCooldown = ticks(battle, "bats_cooldown_ticks", v.batCooldown, 1, 1_000_000, errors);
            v.meteorCooldown = ticks(battle, "meteor_cooldown_ticks", v.meteorCooldown, 1, 1_000_000, errors);
            v.flameCooldown = ticks(battle, "dark_flame_cooldown_ticks", v.flameCooldown, 1, 1_000_000, errors);
            v.sweepCooldown = ticks(battle, "sweep_cooldown_ticks", v.sweepCooldown, 1, 1_000_000, errors);
            v.summonCooldown = ticks(battle, "summon_cooldown_ticks", v.summonCooldown, 1, 1_000_000, errors);
            v.plagueCooldown = ticks(battle, "plague_cooldown_ticks", v.plagueCooldown, 1, 1_000_000, errors);
            v.burstCooldown = ticks(battle, "energy_burst_cooldown_ticks", v.burstCooldown, 1, 1_000_000, errors);
            v.blackWaterCooldown = ticks(battle, "black_water_cooldown_ticks", v.blackWaterCooldown, 1, 1_000_000, errors);
            v.blackBallCooldown = ticks(battle, "black_ball_cooldown_ticks", v.blackBallCooldown, 1, 1_000_000, errors);
            v.supportFireOrbCooldown = ticks(battle, "support_fire_orb_cooldown_ticks", v.supportFireOrbCooldown, 1, 1_000_000, errors);
            v.supportFireRainCooldown = ticks(battle, "support_fire_rain_cooldown_ticks", v.supportFireRainCooldown, 1, 1_000_000, errors);
            v.supportPillarCooldown = ticks(battle, "support_pillar_cooldown_ticks", v.supportPillarCooldown, 1, 1_000_000, errors);
            v.supportHeartFireCooldown = ticks(battle, "support_heart_fire_cooldown_ticks", v.supportHeartFireCooldown, 1, 1_000_000, errors);
            v.teddyHitCooldown = ticks(battle, "dark_teddy_attack_cooldown_ticks", v.teddyHitCooldown, 1, 1_000_000, errors);
            v.teddyRoarCooldown = ticks(battle, "dark_teddy_roar_cooldown_ticks", v.teddyRoarCooldown, 1, 1_000_000, errors);
            v.slimeAttackCooldown = ticks(battle, "dark_slime_attack_cooldown_ticks", v.slimeAttackCooldown, 1, 1_000_000, errors);
            v.blackBallPulseCooldown = ticks(battle, "black_ball_pulse_cooldown_ticks", v.blackBallPulseCooldown, 1, 1_000_000, errors);
            v.boilingBloodInterval = ticks(battle, "boiling_blood_interval_ticks", v.boilingBloodInterval, 1, 1_000_000, errors);

            v.madnessTicks = ticks(battle, "madness_duration_ticks", v.madnessTicks, 1, 10_000_000, errors);
            v.plagueTicks = ticks(battle, "plague_duration_ticks", v.plagueTicks, 1, 10_000_000, errors);
            v.plagueHostMarkTicks = ticks(battle, "plague_host_mark_ticks", v.plagueHostMarkTicks, 1, 10_000_000, errors);
            v.reviveLockTicks = ticks(battle, "revive_lock_ticks", v.reviveLockTicks, 0, 10_000_000, errors);
            v.meteorRootTicks = ticks(battle, "meteor_root_ticks", v.meteorRootTicks, 1, 10_000_000, errors);
            v.blackWaterDelayTicks = ticks(battle, "black_water_spawn_delay_ticks", v.blackWaterDelayTicks, 0, 10_000_000, errors);
            v.blackWaterSplitTicks = ticks(battle, "black_water_split_ticks", v.blackWaterSplitTicks, 1, 10_000_000, errors);
            v.heartFireTicks = ticks(battle, "heart_fire_duration_ticks", v.heartFireTicks, 1, 10_000_000, errors);
            v.strengthenedFireTicks = ticks(battle, "strengthened_fire_duration_ticks", v.strengthenedFireTicks, 1, 10_000_000, errors);
            v.boilingBloodTicks = ticks(battle, "boiling_blood_duration_ticks", v.boilingBloodTicks, 1, 10_000_000, errors);
            v.burstEchoDelayTicks = ticks(battle, "energy_burst_echo_delay_ticks", v.burstEchoDelayTicks, 0, 10_000_000, errors);

            v.maxMeter = i(battle, BATTLE_SECTION, "max_meter", v.maxMeter, 1, 100000, errors);
            v.blackEnergyPerHit = i(battle, BATTLE_SECTION, "black_energy_per_hit", v.blackEnergyPerHit, 0, 100000, errors);
            v.basicCorruption = i(battle, BATTLE_SECTION, "basic_corruption", v.basicCorruption, 0, 100000, errors);
            v.sweepCorruption = i(battle, BATTLE_SECTION, "sweep_corruption", v.sweepCorruption, 0, 100000, errors);
            v.teddyHitCorruption = i(battle, BATTLE_SECTION, "dark_teddy_hit_corruption", v.teddyHitCorruption, 0, 100000, errors);
            v.teddyRoarCorruption = i(battle, BATTLE_SECTION, "dark_teddy_roar_corruption", v.teddyRoarCorruption, 0, 100000, errors);
            v.slimeHitCorruption = i(battle, BATTLE_SECTION, "dark_slime_hit_corruption", v.slimeHitCorruption, 0, 100000, errors);
            v.slimeExplodeCorruption = i(battle, BATTLE_SECTION, "dark_slime_explode_corruption", v.slimeExplodeCorruption, 0, 100000, errors);
            v.burstInitialCorruption = i(battle, BATTLE_SECTION, "energy_burst_initial_corruption", v.burstInitialCorruption, 0, 100000, errors);
            v.burstEchoCorruption = i(battle, BATTLE_SECTION, "energy_burst_echo_corruption", v.burstEchoCorruption, 0, 100000, errors);
            v.blackWaterCorruption = i(battle, BATTLE_SECTION, "black_water_corruption", v.blackWaterCorruption, 0, 100000, errors);
            v.blackBallCorruption = i(battle, BATTLE_SECTION, "black_ball_corruption", v.blackBallCorruption, 0, 100000, errors);

            v.basicRadius = d(battle, BATTLE_SECTION, "basic_radius", v.basicRadius, 0.1D, 256.0D, errors);
            v.sweepRadius = d(battle, BATTLE_SECTION, "sweep_radius", v.sweepRadius, 0.1D, 256.0D, errors);
            v.meteorSplitRadius = d(battle, BATTLE_SECTION, "meteor_split_radius", v.meteorSplitRadius, 0.1D, 256.0D, errors);
            v.plagueTransferRadius = d(battle, BATTLE_SECTION, "plague_transfer_radius", v.plagueTransferRadius, 0.1D, 256.0D, errors);
            v.burstEchoRadius = d(battle, BATTLE_SECTION, "energy_burst_echo_radius", v.burstEchoRadius, 0.1D, 256.0D, errors);
            v.flameRange = d(battle, BATTLE_SECTION, "dark_flame_range", v.flameRange, 0.1D, 256.0D, errors);
            v.flameHalfAngleDegrees = d(battle, BATTLE_SECTION, "dark_flame_half_angle_degrees", v.flameHalfAngleDegrees, 1.0D, 179.0D, errors);
            v.blackWaterRadius = d(battle, BATTLE_SECTION, "black_water_radius", v.blackWaterRadius, 0.1D, 64.0D, errors);
            v.blackWaterSpreadDistance = d(battle, BATTLE_SECTION, "black_water_spread_distance", v.blackWaterSpreadDistance, 0.1D, 64.0D, errors);
            v.blackBallRadius = d(battle, BATTLE_SECTION, "black_ball_radius", v.blackBallRadius, 0.1D, 128.0D, errors);
            v.teddyRoarRadius = d(battle, BATTLE_SECTION, "dark_teddy_roar_radius", v.teddyRoarRadius, 0.1D, 128.0D, errors);
            v.supportZoneRadius = d(battle, BATTLE_SECTION, "support_zone_radius", v.supportZoneRadius, 0.1D, 128.0D, errors);

            v.plagueOutgoingMultiplier = f(battle, BATTLE_SECTION, "plague_outgoing_multiplier", v.plagueOutgoingMultiplier, 0.0D, 100.0D, errors);
            v.plagueIncomingMultiplier = f(battle, BATTLE_SECTION, "plague_incoming_multiplier", v.plagueIncomingMultiplier, 0.0D, 100.0D, errors);
            v.madnessOutgoingMultiplier = f(battle, BATTLE_SECTION, "madness_outgoing_multiplier", v.madnessOutgoingMultiplier, 0.0D, 100.0D, errors);
            v.madnessSpeedModifier = d(battle, BATTLE_SECTION, "madness_speed_modifier", v.madnessSpeedModifier, -0.99D, 10.0D, errors);
            v.strengthenedFirePerStack = f(battle, BATTLE_SECTION, "strengthened_fire_per_stack", v.strengthenedFirePerStack, 0.0D, 100.0D, errors);
            v.boilingBloodDamagePerStack = f(battle, BATTLE_SECTION, "boiling_blood_damage_per_stack", v.boilingBloodDamagePerStack, 0.0D, 1.0E6D, errors);
            v.boilingBloodDamageCap = f(battle, BATTLE_SECTION, "boiling_blood_damage_cap", v.boilingBloodDamageCap, 0.0D, 1.0E6D, errors);

            if (!errors.isEmpty()) {
                LOGGER.error("{} 的教授配置读取失败，共 {} 个错误。教授数值未切换，继续使用上一份有效值。",
                        PATH.getFileName(), errors.size());
                for (String error : errors) LOGGER.error(" - {}", error);
                return false;
            }

            v.commit();
            LOGGER.info("YellowDuck 教授配置已从 {} 的 [{}] / [{}] 读取。",
                    PATH.getFileName(), BOSS_SECTION, BATTLE_SECTION);
            return true;
        } catch (Exception ex) {
            LOGGER.error("{} 教授配置读取异常；继续使用上一份有效值。", PATH.getFileName(), ex);
            return false;
        }
    }

    /** /yd reload 后同步已经存在的教授和召唤物；生命值按当前百分比保留。 */
    public static void reapply(LivingEntity entity) {
        if (entity instanceof SilkBoss boss) {
            setHealth(boss, SilkBalance.HEALTH);
            set(boss, Attributes.ATTACK_DAMAGE, SilkBalance.BASIC_DAMAGE);
            set(boss, Attributes.MOVEMENT_SPEED, SilkBalance.BOSS_MOVEMENT_SPEED);
            set(boss, Attributes.FOLLOW_RANGE, SilkBalance.BOSS_FOLLOW_RANGE);
            set(boss, Attributes.KNOCKBACK_RESISTANCE, SilkBalance.BOSS_KNOCKBACK_RESISTANCE);
            boss.setBaseTier(SilkBalance.BOSS_TIER);
            boss.setBaseDamage(Math.round(SilkBalance.BASIC_DAMAGE));
            boss.clampRuntimeMeters();
        } else if (entity instanceof SilkDarkTeddy teddy) {
            setHealth(teddy, SilkBalance.TEDDY_HEALTH);
            set(teddy, Attributes.ATTACK_DAMAGE, SilkBalance.TEDDY_DAMAGE);
            set(teddy, Attributes.MOVEMENT_SPEED, SilkBalance.TEDDY_MOVEMENT_SPEED);
            set(teddy, Attributes.FOLLOW_RANGE, SilkBalance.TEDDY_FOLLOW_RANGE);
            set(teddy, Attributes.ARMOR, SilkBalance.TEDDY_ARMOR);
        } else if (entity instanceof SilkDarkSlime slime) {
            setHealth(slime, SilkBalance.SLIME_HEALTH);
            set(slime, Attributes.ATTACK_DAMAGE, SilkBalance.SLIME_DAMAGE);
            set(slime, Attributes.MOVEMENT_SPEED, SilkBalance.SLIME_MOVEMENT_SPEED);
            set(slime, Attributes.FOLLOW_RANGE, SilkBalance.SLIME_FOLLOW_RANGE);
        } else if (entity instanceof SilkBlackBall ball) {
            setHealth(ball, SilkBalance.BLACK_BALL_HEALTH);
            set(ball, Attributes.ARMOR, SilkBalance.BLACK_BALL_ARMOR);
        }
    }

    private static void setHealth(LivingEntity entity, double value) {
        var attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        double oldMax = Math.max(0.0001D, entity.getMaxHealth());
        double ratio = Math.max(0.0D, Math.min(1.0D, entity.getHealth() / oldMax));
        if (Math.abs(attr.getBaseValue() - value) > 1.0E-9D) attr.setBaseValue(value);
        entity.setHealth((float) Math.max(0.1D, Math.min(entity.getMaxHealth(), entity.getMaxHealth() * ratio)));
    }

    private static void set(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null && Math.abs(instance.getBaseValue() - value) > 1.0E-9D) {
            instance.setBaseValue(value);
        }
    }

    private static void appendBattleSectionIfMissing() throws Exception {
        if (Files.notExists(PATH)) return;
        List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);
        boolean found = false;
        for (String line : lines) {
            String clean = stripComment(line).trim().toLowerCase(Locale.ROOT);
            if (clean.equals("[" + BATTLE_SECTION + "]")) {
                found = true;
                break;
            }
        }
        if (found) return;
        Files.writeString(PATH, "\n\n" + battleSectionText(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        LOGGER.info("已把教授战斗参数 [{}] 追加到现有 {}，未改动其它配置值。",
                BATTLE_SECTION, PATH.getFileName());
    }

    private static Parsed parse(List<String> lines) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        String section = "";
        for (int n = 0; n < lines.size(); n++) {
            String line = stripComment(lines.get(n)).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                if (section.isEmpty()) errors.add("第 " + (n + 1) + " 行：空分组");
                else sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || section.isEmpty()) {
                // 兼容 EntityTuningConfig 允许的顶层未来字段；仅真正格式错误才记录。
                if (!section.isEmpty()) errors.add("第 " + (n + 1) + " 行无法解析：" + line);
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).trim();
            Map<String, String> map = sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
            if (map.put(key, value) != null) errors.add("第 " + (n + 1) + " 行重复配置：" + section + "." + key);
        }
        return new Parsed(sections, errors);
    }

    private static double d(Map<String, String> section, String sectionName, String key, double fallback,
                            double min, double max, List<String> errors) {
        String raw = section.get(key);
        if (raw == null || unquote(raw).isBlank()) return fallback;
        try {
            double value = Double.parseDouble(unquote(raw));
            if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            errors.add("[" + sectionName + "] " + key + " 必须是 " + min + " ～ " + max + " 的数字，当前=" + raw);
            return fallback;
        }
    }

    private static float f(Map<String, String> section, String sectionName, String key, float fallback,
                           double min, double max, List<String> errors) {
        return (float) d(section, sectionName, key, fallback, min, max, errors);
    }

    private static int i(Map<String, String> section, String sectionName, String key, int fallback,
                         int min, int max, List<String> errors) {
        String raw = section.get(key);
        if (raw == null || unquote(raw).isBlank()) return fallback;
        try {
            double parsed = Double.parseDouble(unquote(raw));
            if (!Double.isFinite(parsed) || Math.rint(parsed) != parsed || parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
            return (int) parsed;
        } catch (NumberFormatException ex) {
            errors.add("[" + sectionName + "] " + key + " 必须是 " + min + " ～ " + max + " 的整数，当前=" + raw);
            return fallback;
        }
    }

    private static int ticks(Map<String, String> section, String key, int fallback,
                             int min, int max, List<String> errors) {
        return i(section, BATTLE_SECTION, key, fallback, min, max, errors);
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == '#' && !quoted) return line.substring(0, i);
        }
        return line;
    }

    private static String unquote(String text) {
        String s = text == null ? "" : text.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    /** 只追加这一段到现有 yellowduck-entities.toml；其它分组完全不碰。 */
    public static String battleSectionText() {
        return """
                [silk_battle]
                # 疯狂教授斯尔克技能/机制参数。基础生命、普通攻击、移速等仍使用上面的 [silk_boss]。
                # 20 tick = 1 秒。修改后执行 /yd reload；非法值不会覆盖上一份有效配置。

                # Boss 防御/场地/阶段
                boss_melee_defense = 0
                boss_ranged_defense = 0
                boss_magic_defense = 0
                boss_fixed_reduction = 0.0
                arena_radius = 32
                leash_radius = 48
                # 当前 0.80 / 0.20 沿用 YellowDuck 既有阈值；奶块客户端资源未暴露服务端真实切阶段 HP。
                phase2_health_ratio = 0.80
                phase3_health_ratio = 0.20
                phase3_damage_multiplier = 1.15

                # 技能伤害
                bat_damage = 12
                meteor_damage = 18
                sweep_damage = 10
                energy_burst_damage = 12
                dark_flame_damage = 18
                dark_teddy_attack_damage = 10
                dark_slime_attack_damage = 6
                black_ball_damage = 15

                # 召唤物基础属性
                dark_teddy_health = 10000
                dark_teddy_movement_speed = 0.30
                dark_teddy_follow_range = 48
                dark_teddy_armor = 20
                dark_slime_health = 300
                dark_slime_movement_speed = 0.24
                dark_slime_follow_range = 32
                black_ball_health = 200
                black_ball_armor = 24

                # 技能冷却（tick）
                basic_attack_cooldown_ticks = 20
                bats_cooldown_ticks = 200
                meteor_cooldown_ticks = 400
                dark_flame_cooldown_ticks = 600
                sweep_cooldown_ticks = 40
                summon_cooldown_ticks = 1200
                plague_cooldown_ticks = 1200
                energy_burst_cooldown_ticks = 900
                black_water_cooldown_ticks = 600
                black_ball_cooldown_ticks = 300
                support_fire_orb_cooldown_ticks = 400
                support_fire_rain_cooldown_ticks = 600
                support_pillar_cooldown_ticks = 800
                support_heart_fire_cooldown_ticks = 800
                dark_teddy_attack_cooldown_ticks = 40
                dark_teddy_roar_cooldown_ticks = 600
                dark_slime_attack_cooldown_ticks = 40
                black_ball_pulse_cooldown_ticks = 40
                boiling_blood_interval_ticks = 40

                # 状态持续时间（tick）
                madness_duration_ticks = 2400
                plague_duration_ticks = 400
                plague_host_mark_ticks = 100
                revive_lock_ticks = 600
                meteor_root_ticks = 200
                black_water_spawn_delay_ticks = 200
                black_water_split_ticks = 200
                heart_fire_duration_ticks = 400
                strengthened_fire_duration_ticks = 300
                boiling_blood_duration_ticks = 200
                energy_burst_echo_delay_ticks = 60

                # 心智腐蚀/黑暗能量层数
                max_meter = 99
                black_energy_per_hit = 5
                basic_corruption = 0
                sweep_corruption = 1
                dark_teddy_hit_corruption = 5
                dark_teddy_roar_corruption = 2
                dark_slime_hit_corruption = 1
                dark_slime_explode_corruption = 5
                energy_burst_initial_corruption = 3
                energy_burst_echo_corruption = 5
                black_water_corruption = 3
                black_ball_corruption = 10

                # 范围/角度
                basic_radius = 4.0
                sweep_radius = 3.5
                meteor_split_radius = 5.0
                plague_transfer_radius = 5.0
                energy_burst_echo_radius = 3.0
                dark_flame_range = 10.0
                # 半角 60 = 完整 120 度扇形
                dark_flame_half_angle_degrees = 60.0
                black_water_radius = 1.55
                black_water_spread_distance = 3.0
                black_ball_radius = 3.0
                dark_teddy_roar_radius = 10.0
                support_zone_radius = 4.0

                # 状态倍率
                plague_outgoing_multiplier = 1.20
                plague_incoming_multiplier = 1.50
                madness_outgoing_multiplier = 3.00
                madness_speed_modifier = -0.50
                strengthened_fire_per_stack = 0.10
                boiling_blood_damage_per_stack = 0.75
                boiling_blood_damage_cap = 10.0
                """;
    }

    private record Parsed(Map<String, Map<String, String>> sections, List<String> errors) {}

    /** 先完整解析到临时值，所有字段都合法才一次性 commit。 */
    private static final class Values implements Cloneable {
        double health, bossMovementSpeed, bossFollowRange, bossKnockbackResistance, arenaRadius, leashRadius;
        int bossTier, bossMeleeDefense, bossRangedDefense, bossMagicDefense;
        float bossDamageReduction, basicDamage, batDamage, meteorDamage, sweepDamage, burstDamage, flameDamage,
                teddyDamage, slimeDamage, blackBallDamage;
        double teddyHealth, teddyMovementSpeed, teddyFollowRange, teddyArmor,
                slimeHealth, slimeMovementSpeed, slimeFollowRange, blackBallHealth, blackBallArmor;
        int maxMeter, blackEnergyPerHit;
        int madnessTicks, plagueTicks, plagueHostMarkTicks, reviveLockTicks, meteorRootTicks,
                blackWaterDelayTicks, blackWaterSplitTicks, heartFireTicks, strengthenedFireTicks,
                boilingBloodTicks, burstEchoDelayTicks;
        int basicCooldown, batCooldown, meteorCooldown, flameCooldown, sweepCooldown, summonCooldown,
                plagueCooldown, burstCooldown, blackWaterCooldown, blackBallCooldown,
                supportFireOrbCooldown, supportFireRainCooldown, supportPillarCooldown, supportHeartFireCooldown,
                teddyHitCooldown, teddyRoarCooldown, slimeAttackCooldown, blackBallPulseCooldown, boilingBloodInterval;
        int basicCorruption, sweepCorruption, teddyHitCorruption, teddyRoarCorruption, slimeHitCorruption,
                slimeExplodeCorruption, burstInitialCorruption, burstEchoCorruption, blackWaterCorruption, blackBallCorruption;
        double basicRadius, sweepRadius, meteorSplitRadius, plagueTransferRadius, burstEchoRadius,
                flameRange, flameHalfAngleDegrees, blackWaterRadius, blackWaterSpreadDistance,
                blackBallRadius, teddyRoarRadius, supportZoneRadius;
        float plagueOutgoingMultiplier, plagueIncomingMultiplier, madnessOutgoingMultiplier,
                strengthenedFirePerStack, boilingBloodDamagePerStack, boilingBloodDamageCap;
        double madnessSpeedModifier;
        float phaseTwoHealth, phaseThreeHealth, phaseThreeMultiplier;

        Values copy() {
            try {
                return (Values) clone();
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        static Values current() {
            Values v = new Values();
            v.health = SilkBalance.HEALTH;
            v.bossMovementSpeed = SilkBalance.BOSS_MOVEMENT_SPEED;
            v.bossFollowRange = SilkBalance.BOSS_FOLLOW_RANGE;
            v.bossKnockbackResistance = SilkBalance.BOSS_KNOCKBACK_RESISTANCE;
            v.bossTier = SilkBalance.BOSS_TIER;
            v.bossMeleeDefense = SilkBalance.BOSS_MELEE_DEFENSE;
            v.bossRangedDefense = SilkBalance.BOSS_RANGED_DEFENSE;
            v.bossMagicDefense = SilkBalance.BOSS_MAGIC_DEFENSE;
            v.bossDamageReduction = SilkBalance.BOSS_DAMAGE_REDUCTION;
            v.arenaRadius = SilkBalance.ARENA_RADIUS;
            v.leashRadius = SilkBalance.LEASH_RADIUS;
            v.basicDamage = SilkBalance.BASIC_DAMAGE;
            v.batDamage = SilkBalance.BAT_DAMAGE;
            v.meteorDamage = SilkBalance.METEOR_DAMAGE;
            v.sweepDamage = SilkBalance.SWEEP_DAMAGE;
            v.burstDamage = SilkBalance.BURST_DAMAGE;
            v.flameDamage = SilkBalance.FLAME_DAMAGE;
            v.teddyDamage = SilkBalance.TEDDY_DAMAGE;
            v.slimeDamage = SilkBalance.SLIME_DAMAGE;
            v.blackBallDamage = SilkBalance.BLACK_BALL_DAMAGE;
            v.teddyHealth = SilkBalance.TEDDY_HEALTH;
            v.teddyMovementSpeed = SilkBalance.TEDDY_MOVEMENT_SPEED;
            v.teddyFollowRange = SilkBalance.TEDDY_FOLLOW_RANGE;
            v.teddyArmor = SilkBalance.TEDDY_ARMOR;
            v.slimeHealth = SilkBalance.SLIME_HEALTH;
            v.slimeMovementSpeed = SilkBalance.SLIME_MOVEMENT_SPEED;
            v.slimeFollowRange = SilkBalance.SLIME_FOLLOW_RANGE;
            v.blackBallHealth = SilkBalance.BLACK_BALL_HEALTH;
            v.blackBallArmor = SilkBalance.BLACK_BALL_ARMOR;
            v.maxMeter = SilkBalance.MAX_METER;
            v.blackEnergyPerHit = SilkBalance.BLACK_ENERGY_PER_HIT;
            v.madnessTicks = SilkBalance.MADNESS_TICKS;
            v.plagueTicks = SilkBalance.PLAGUE_TICKS;
            v.plagueHostMarkTicks = SilkBalance.PLAGUE_HOST_MARK_TICKS;
            v.reviveLockTicks = SilkBalance.REVIVE_LOCK_TICKS;
            v.meteorRootTicks = SilkBalance.METEOR_ROOT_TICKS;
            v.blackWaterDelayTicks = SilkBalance.BLACK_WATER_DELAY_TICKS;
            v.blackWaterSplitTicks = SilkBalance.BLACK_WATER_SPLIT_TICKS;
            v.heartFireTicks = SilkBalance.HEART_FIRE_TICKS;
            v.strengthenedFireTicks = SilkBalance.STRENGTHENED_FIRE_TICKS;
            v.boilingBloodTicks = SilkBalance.BOILING_BLOOD_TICKS;
            v.burstEchoDelayTicks = SilkBalance.BURST_ECHO_DELAY_TICKS;
            v.basicCooldown = SilkBalance.BASIC_COOLDOWN;
            v.batCooldown = SilkBalance.BAT_COOLDOWN;
            v.meteorCooldown = SilkBalance.METEOR_COOLDOWN;
            v.flameCooldown = SilkBalance.FLAME_COOLDOWN;
            v.sweepCooldown = SilkBalance.SWEEP_COOLDOWN;
            v.summonCooldown = SilkBalance.SUMMON_COOLDOWN;
            v.plagueCooldown = SilkBalance.PLAGUE_COOLDOWN;
            v.burstCooldown = SilkBalance.BURST_COOLDOWN;
            v.blackWaterCooldown = SilkBalance.BLACK_WATER_COOLDOWN;
            v.blackBallCooldown = SilkBalance.BLACK_BALL_COOLDOWN;
            v.supportFireOrbCooldown = SilkBalance.SUPPORT_FIRE_ORB_COOLDOWN;
            v.supportFireRainCooldown = SilkBalance.SUPPORT_FIRE_RAIN_COOLDOWN;
            v.supportPillarCooldown = SilkBalance.SUPPORT_PILLAR_COOLDOWN;
            v.supportHeartFireCooldown = SilkBalance.SUPPORT_HEART_FIRE_COOLDOWN;
            v.teddyHitCooldown = SilkBalance.TEDDY_HIT_COOLDOWN;
            v.teddyRoarCooldown = SilkBalance.TEDDY_ROAR_COOLDOWN;
            v.slimeAttackCooldown = SilkBalance.SLIME_ATTACK_COOLDOWN;
            v.blackBallPulseCooldown = SilkBalance.BLACK_BALL_PULSE_COOLDOWN;
            v.boilingBloodInterval = SilkBalance.BOILING_BLOOD_INTERVAL;
            v.basicCorruption = SilkBalance.BASIC_CORRUPTION;
            v.sweepCorruption = SilkBalance.SWEEP_CORRUPTION;
            v.teddyHitCorruption = SilkBalance.TEDDY_HIT_CORRUPTION;
            v.teddyRoarCorruption = SilkBalance.TEDDY_ROAR_CORRUPTION;
            v.slimeHitCorruption = SilkBalance.SLIME_HIT_CORRUPTION;
            v.slimeExplodeCorruption = SilkBalance.SLIME_EXPLODE_CORRUPTION;
            v.burstInitialCorruption = SilkBalance.BURST_INITIAL_CORRUPTION;
            v.burstEchoCorruption = SilkBalance.BURST_ECHO_CORRUPTION;
            v.blackWaterCorruption = SilkBalance.BLACK_WATER_CORRUPTION;
            v.blackBallCorruption = SilkBalance.BLACK_BALL_CORRUPTION;
            v.basicRadius = SilkBalance.BASIC_RADIUS;
            v.sweepRadius = SilkBalance.SWEEP_RADIUS;
            v.meteorSplitRadius = SilkBalance.METEOR_SPLIT_RADIUS;
            v.plagueTransferRadius = SilkBalance.PLAGUE_TRANSFER_RADIUS;
            v.burstEchoRadius = SilkBalance.BURST_ECHO_RADIUS;
            v.flameRange = SilkBalance.FLAME_RANGE;
            v.flameHalfAngleDegrees = SilkBalance.FLAME_HALF_ANGLE_DEGREES;
            v.blackWaterRadius = SilkBalance.BLACK_WATER_RADIUS;
            v.blackWaterSpreadDistance = SilkBalance.BLACK_WATER_SPREAD_DISTANCE;
            v.blackBallRadius = SilkBalance.BLACK_BALL_RADIUS;
            v.teddyRoarRadius = SilkBalance.TEDDY_ROAR_RADIUS;
            v.supportZoneRadius = SilkBalance.SUPPORT_ZONE_RADIUS;
            v.plagueOutgoingMultiplier = SilkBalance.PLAGUE_OUTGOING_MULTIPLIER;
            v.plagueIncomingMultiplier = SilkBalance.PLAGUE_INCOMING_MULTIPLIER;
            v.madnessOutgoingMultiplier = SilkBalance.MADNESS_OUTGOING_MULTIPLIER;
            v.madnessSpeedModifier = SilkBalance.MADNESS_SPEED_MODIFIER;
            v.strengthenedFirePerStack = SilkBalance.STRENGTHENED_FIRE_PER_STACK;
            v.boilingBloodDamagePerStack = SilkBalance.BOILING_BLOOD_DAMAGE_PER_STACK;
            v.boilingBloodDamageCap = SilkBalance.BOILING_BLOOD_DAMAGE_CAP;
            v.phaseTwoHealth = SilkBalance.PHASE_TWO_HEALTH;
            v.phaseThreeHealth = SilkBalance.PHASE_THREE_HEALTH;
            v.phaseThreeMultiplier = SilkBalance.PHASE_THREE_MULTIPLIER;
            return v;
        }

        void commit() {
            SilkBalance.HEALTH = health;
            SilkBalance.BOSS_MOVEMENT_SPEED = bossMovementSpeed;
            SilkBalance.BOSS_FOLLOW_RANGE = bossFollowRange;
            SilkBalance.BOSS_KNOCKBACK_RESISTANCE = bossKnockbackResistance;
            SilkBalance.BOSS_TIER = bossTier;
            SilkBalance.BOSS_MELEE_DEFENSE = bossMeleeDefense;
            SilkBalance.BOSS_RANGED_DEFENSE = bossRangedDefense;
            SilkBalance.BOSS_MAGIC_DEFENSE = bossMagicDefense;
            SilkBalance.BOSS_DAMAGE_REDUCTION = bossDamageReduction;
            SilkBalance.ARENA_RADIUS = arenaRadius;
            SilkBalance.LEASH_RADIUS = leashRadius;
            SilkBalance.BASIC_DAMAGE = basicDamage;
            SilkBalance.BAT_DAMAGE = batDamage;
            SilkBalance.METEOR_DAMAGE = meteorDamage;
            SilkBalance.SWEEP_DAMAGE = sweepDamage;
            SilkBalance.BURST_DAMAGE = burstDamage;
            SilkBalance.FLAME_DAMAGE = flameDamage;
            SilkBalance.TEDDY_DAMAGE = teddyDamage;
            SilkBalance.SLIME_DAMAGE = slimeDamage;
            SilkBalance.BLACK_BALL_DAMAGE = blackBallDamage;
            SilkBalance.TEDDY_HEALTH = teddyHealth;
            SilkBalance.TEDDY_MOVEMENT_SPEED = teddyMovementSpeed;
            SilkBalance.TEDDY_FOLLOW_RANGE = teddyFollowRange;
            SilkBalance.TEDDY_ARMOR = teddyArmor;
            SilkBalance.SLIME_HEALTH = slimeHealth;
            SilkBalance.SLIME_MOVEMENT_SPEED = slimeMovementSpeed;
            SilkBalance.SLIME_FOLLOW_RANGE = slimeFollowRange;
            SilkBalance.BLACK_BALL_HEALTH = blackBallHealth;
            SilkBalance.BLACK_BALL_ARMOR = blackBallArmor;
            SilkBalance.MAX_METER = maxMeter;
            SilkBalance.BLACK_ENERGY_PER_HIT = blackEnergyPerHit;
            SilkBalance.MADNESS_TICKS = madnessTicks;
            SilkBalance.PLAGUE_TICKS = plagueTicks;
            SilkBalance.PLAGUE_HOST_MARK_TICKS = plagueHostMarkTicks;
            SilkBalance.REVIVE_LOCK_TICKS = reviveLockTicks;
            SilkBalance.METEOR_ROOT_TICKS = meteorRootTicks;
            SilkBalance.BLACK_WATER_DELAY_TICKS = blackWaterDelayTicks;
            SilkBalance.BLACK_WATER_SPLIT_TICKS = blackWaterSplitTicks;
            SilkBalance.HEART_FIRE_TICKS = heartFireTicks;
            SilkBalance.STRENGTHENED_FIRE_TICKS = strengthenedFireTicks;
            SilkBalance.BOILING_BLOOD_TICKS = boilingBloodTicks;
            SilkBalance.BURST_ECHO_DELAY_TICKS = burstEchoDelayTicks;
            SilkBalance.BASIC_COOLDOWN = basicCooldown;
            SilkBalance.BAT_COOLDOWN = batCooldown;
            SilkBalance.METEOR_COOLDOWN = meteorCooldown;
            SilkBalance.FLAME_COOLDOWN = flameCooldown;
            SilkBalance.SWEEP_COOLDOWN = sweepCooldown;
            SilkBalance.SUMMON_COOLDOWN = summonCooldown;
            SilkBalance.PLAGUE_COOLDOWN = plagueCooldown;
            SilkBalance.BURST_COOLDOWN = burstCooldown;
            SilkBalance.BLACK_WATER_COOLDOWN = blackWaterCooldown;
            SilkBalance.BLACK_BALL_COOLDOWN = blackBallCooldown;
            SilkBalance.SUPPORT_FIRE_ORB_COOLDOWN = supportFireOrbCooldown;
            SilkBalance.SUPPORT_FIRE_RAIN_COOLDOWN = supportFireRainCooldown;
            SilkBalance.SUPPORT_PILLAR_COOLDOWN = supportPillarCooldown;
            SilkBalance.SUPPORT_HEART_FIRE_COOLDOWN = supportHeartFireCooldown;
            SilkBalance.TEDDY_HIT_COOLDOWN = teddyHitCooldown;
            SilkBalance.TEDDY_ROAR_COOLDOWN = teddyRoarCooldown;
            SilkBalance.SLIME_ATTACK_COOLDOWN = slimeAttackCooldown;
            SilkBalance.BLACK_BALL_PULSE_COOLDOWN = blackBallPulseCooldown;
            SilkBalance.BOILING_BLOOD_INTERVAL = boilingBloodInterval;
            SilkBalance.BASIC_CORRUPTION = basicCorruption;
            SilkBalance.SWEEP_CORRUPTION = sweepCorruption;
            SilkBalance.TEDDY_HIT_CORRUPTION = teddyHitCorruption;
            SilkBalance.TEDDY_ROAR_CORRUPTION = teddyRoarCorruption;
            SilkBalance.SLIME_HIT_CORRUPTION = slimeHitCorruption;
            SilkBalance.SLIME_EXPLODE_CORRUPTION = slimeExplodeCorruption;
            SilkBalance.BURST_INITIAL_CORRUPTION = burstInitialCorruption;
            SilkBalance.BURST_ECHO_CORRUPTION = burstEchoCorruption;
            SilkBalance.BLACK_WATER_CORRUPTION = blackWaterCorruption;
            SilkBalance.BLACK_BALL_CORRUPTION = blackBallCorruption;
            SilkBalance.BASIC_RADIUS = basicRadius;
            SilkBalance.SWEEP_RADIUS = sweepRadius;
            SilkBalance.METEOR_SPLIT_RADIUS = meteorSplitRadius;
            SilkBalance.PLAGUE_TRANSFER_RADIUS = plagueTransferRadius;
            SilkBalance.BURST_ECHO_RADIUS = burstEchoRadius;
            SilkBalance.FLAME_RANGE = flameRange;
            SilkBalance.FLAME_HALF_ANGLE_DEGREES = flameHalfAngleDegrees;
            SilkBalance.BLACK_WATER_RADIUS = blackWaterRadius;
            SilkBalance.BLACK_WATER_SPREAD_DISTANCE = blackWaterSpreadDistance;
            SilkBalance.BLACK_BALL_RADIUS = blackBallRadius;
            SilkBalance.TEDDY_ROAR_RADIUS = teddyRoarRadius;
            SilkBalance.SUPPORT_ZONE_RADIUS = supportZoneRadius;
            SilkBalance.PLAGUE_OUTGOING_MULTIPLIER = plagueOutgoingMultiplier;
            SilkBalance.PLAGUE_INCOMING_MULTIPLIER = plagueIncomingMultiplier;
            SilkBalance.MADNESS_OUTGOING_MULTIPLIER = madnessOutgoingMultiplier;
            SilkBalance.MADNESS_SPEED_MODIFIER = madnessSpeedModifier;
            SilkBalance.STRENGTHENED_FIRE_PER_STACK = strengthenedFirePerStack;
            SilkBalance.BOILING_BLOOD_DAMAGE_PER_STACK = boilingBloodDamagePerStack;
            SilkBalance.BOILING_BLOOD_DAMAGE_CAP = boilingBloodDamageCap;
            SilkBalance.PHASE_TWO_HEALTH = phaseTwoHealth;
            SilkBalance.PHASE_THREE_HEALTH = phaseThreeHealth;
            SilkBalance.PHASE_THREE_MULTIPLIER = phaseThreeMultiplier;
        }
    }
}
