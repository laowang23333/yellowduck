package com.yourname.yellowduck.change;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ChangeConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-entities.toml");
    private static final String APPLIED_VERSION_KEY = "YellowDuckChangeConfigVersion";

    private static volatile Snapshot snapshot = Snapshot.defaults();
    private static volatile boolean loaded;
    private static volatile long version = 1L;
    private static volatile long lastModified = -1L;
    private static volatile long lastCheckedGameTime = Long.MIN_VALUE;

    private ChangeConfig() {}

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        ensureLoaded();
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        ensureSections();
        reloadInternal();
        loaded = true;
    }

    public static void applyBoss(ChangeBoss boss) {
        applyIfNeeded(boss, snapshot().boss());
    }

    public static void applyClone(ChangeClone clone) {
        applyIfNeeded(clone, snapshot().clone());
    }

    public static void applyRabbit(ChangeRabbit rabbit) {
        applyIfNeeded(rabbit, snapshot().rabbit());
    }

    public static int bossMeleeDefense() {
        return (int) Math.round(snapshot().boss().meleeDefense());
    }

    public static int bossRangedDefense() {
        return (int) Math.round(snapshot().boss().rangedDefense());
    }

    public static int bossMagicDefense() {
        return (int) Math.round(snapshot().boss().magicDefense());
    }

    public static float bossDamageReduction() {
        return (float) snapshot().boss().damageReduction();
    }

    public static int cloneMeleeDefense() {
        return (int) Math.round(snapshot().clone().meleeDefense());
    }

    public static int cloneRangedDefense() {
        return (int) Math.round(snapshot().clone().rangedDefense());
    }

    public static int cloneMagicDefense() {
        return (int) Math.round(snapshot().clone().magicDefense());
    }

    public static float cloneDamageReduction() {
        return (float) snapshot().clone().damageReduction();
    }

    public static int rabbitMeleeDefense() {
        return (int) Math.round(snapshot().rabbit().meleeDefense());
    }

    public static int rabbitRangedDefense() {
        return (int) Math.round(snapshot().rabbit().rangedDefense());
    }

    public static int rabbitMagicDefense() {
        return (int) Math.round(snapshot().rabbit().magicDefense());
    }

    public static float rabbitDamageReduction() {
        return (float) snapshot().rabbit().damageReduction();
    }

    private static Snapshot snapshot() {
        ensureLoaded();
        return snapshot;
    }

    private static void applyIfNeeded(LivingEntity entity, Values values) {
        ensureLoaded();
        checkFileChanged(entity.level().getGameTime());

        long currentVersion = version;
        if (entity.getPersistentData().getLong(APPLIED_VERSION_KEY) == currentVersion) return;

        double oldMax = entity.getMaxHealth();
        float oldHealth = entity.getHealth();
        double ratio = oldMax > 0.0D ? oldHealth / oldMax : 1.0D;

        boolean healthChanged = setAttribute(entity, Attributes.MAX_HEALTH, values.maxHealth());
        setAttribute(entity, Attributes.ATTACK_DAMAGE, values.attackDamage());
        setAttribute(entity, Attributes.MOVEMENT_SPEED, values.movementSpeed());
        setAttribute(entity, Attributes.ARMOR, values.armor());
        setAttribute(entity, Attributes.ARMOR_TOUGHNESS, values.armorToughness());
        setAttribute(entity, Attributes.KNOCKBACK_RESISTANCE, values.knockbackResistance());
        setAttribute(entity, Attributes.FOLLOW_RANGE, values.followRange());

        if (healthChanged) {
            entity.setHealth((float) Math.max(
                    0.1D,
                    Math.min(entity.getMaxHealth(), entity.getMaxHealth() * ratio)
            ));
        }

        if (entity instanceof NetcraftBossBase boss) {
            boss.setBaseTier((int) Math.round(values.netcraftTier()));
            boss.setBaseDamage((int) Math.round(values.attackDamage()));
            boss.setBaseDefense((int) Math.round(values.meleeDefense()));
        }

        entity.getPersistentData().putLong(APPLIED_VERSION_KEY, currentVersion);
    }

    private static boolean setAttribute(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) return false;
        if (Math.abs(instance.getBaseValue() - value) <= 1.0E-9D) return false;
        instance.setBaseValue(value);
        return true;
    }

    private static synchronized void checkFileChanged(long gameTime) {
        if (gameTime == lastCheckedGameTime || Math.floorMod(gameTime, 20L) != 0L) return;
        lastCheckedGameTime = gameTime;

        try {
            long modified = Files.exists(PATH)
                    ? Files.getLastModifiedTime(PATH).toMillis()
                    : -1L;
            if (modified != lastModified) {
                ensureSections();
                reloadInternal();
            }
        } catch (Exception ex) {
            LOGGER.warn("读取嫦娥属性配置更新时间失败：{}", ex.toString());
        }
    }

    private static synchronized void reloadInternal() {
        try {
            if (Files.notExists(PATH)) return;

            Map<String, Map<String, String>> sections =
                    parse(Files.readAllLines(PATH, StandardCharsets.UTF_8));

            Values boss = readValues(
                    sections.get("change_boss"),
                    Values.bossDefaults()
            );
            Values clone = readValues(
                    sections.get("change_clone"),
                    Values.cloneDefaults()
            );
            Values rabbit = readValues(
                    sections.get("change_brewing_rabbit"),
                    Values.rabbitDefaults()
            );

            snapshot = new Snapshot(boss, clone, rabbit);
            lastModified = Files.getLastModifiedTime(PATH).toMillis();
            version++;
            LOGGER.info("嫦娥属性配置已读取。");
        } catch (Exception ex) {
            LOGGER.error("读取嫦娥属性配置失败，继续使用上一份有效配置。", ex);
        }
    }

    private static Values readValues(Map<String, String> map, Values defaults) {
        if (map == null) return defaults;
        return new Values(
                number(map, "max_health", defaults.maxHealth(), 1.0D, 1000000000.0D),
                number(map, "attack_damage", defaults.attackDamage(), 0.0D, 100000000.0D),
                number(map, "movement_speed", defaults.movementSpeed(), 0.0D, 10.0D),
                number(map, "armor", defaults.armor(), 0.0D, 1000000.0D),
                number(map, "armor_toughness", defaults.armorToughness(), 0.0D, 1000000.0D),
                number(map, "knockback_resistance", defaults.knockbackResistance(), 0.0D, 1.0D),
                number(map, "follow_range", defaults.followRange(), 1.0D, 2048.0D),
                number(map, "netcraft_tier", defaults.netcraftTier(), 1.0D, 100.0D),
                number(map, "melee_defense", defaults.meleeDefense(), 0.0D, 1000000.0D),
                number(map, "ranged_defense", defaults.rangedDefense(), 0.0D, 1000000.0D),
                number(map, "magic_defense", defaults.magicDefense(), 0.0D, 1000000.0D),
                number(map, "damage_reduction", defaults.damageReduction(), 0.0D, 0.95D)
        );
    }

    private static double number(
            Map<String, String> map,
            String key,
            double fallback,
            double min,
            double max
    ) {
        String raw = map.get(key);
        if (raw == null) return fallback;
        raw = unquote(raw);
        if (raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) return fallback;
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Map<String, Map<String, String>> parse(List<String> lines) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        String section = "";

        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1)
                        .trim()
                        .toLowerCase(Locale.ROOT);
                result.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0 || section.isEmpty()) continue;

            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).trim();
            result.computeIfAbsent(section, ignored -> new LinkedHashMap<>())
                    .put(key, value);
        }
        return result;
    }

    private static synchronized void ensureSections() {
        try {
            Files.createDirectories(PATH.getParent());

            String text = Files.exists(PATH)
                    ? Files.readString(PATH, StandardCharsets.UTF_8)
                    : "# YellowDuck 生物属性配置\n";

            String lower = text.toLowerCase(Locale.ROOT);
            StringBuilder add = new StringBuilder();

            if (!lower.contains("[change_boss]")) {
                add.append("\n").append(section(
                        "change_boss",
                        "BOSS：嫦娥",
                        Values.bossDefaults()
                ));
            }

            if (!lower.contains("[change_clone]")) {
                add.append("\n").append(section(
                        "change_clone",
                        "嫦娥分身",
                        Values.cloneDefaults()
                ));
            }

            if (!lower.contains("[change_brewing_rabbit]")) {
                add.append("\n").append(section(
                        "change_brewing_rabbit",
                        "酿酒玉兔",
                        Values.rabbitDefaults()
                ));
            }

            if (add.length() > 0) {
                if (Files.exists(PATH)) {
                    Files.writeString(
                            PATH,
                            add.toString(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND
                    );
                } else {
                    Files.writeString(
                            PATH,
                            text + add,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE
                    );
                }
            }
        } catch (Exception ex) {
            LOGGER.error("写入嫦娥属性配置段失败。", ex);
        }
    }

    private static String section(String id, String title, Values v) {
        return """
                [%s]
                # %s
                # 0.70 = 最终伤害再减 70%%。
                max_health = %s
                attack_damage = %s
                movement_speed = %s
                armor = %s
                armor_toughness = %s
                knockback_resistance = %s
                follow_range = %s
                netcraft_tier = %s
                melee_defense = %s
                ranged_defense = %s
                magic_defense = %s
                damage_reduction = %s
                """.formatted(
                id,
                title,
                clean(v.maxHealth()),
                clean(v.attackDamage()),
                clean(v.movementSpeed()),
                clean(v.armor()),
                clean(v.armorToughness()),
                clean(v.knockbackResistance()),
                clean(v.followRange()),
                clean(v.netcraftTier()),
                clean(v.meleeDefense()),
                clean(v.rangedDefense()),
                clean(v.magicDefense()),
                clean(v.damageReduction())
        );
    }

    private static String clean(double value) {
        long whole = (long) value;
        return value == whole ? Long.toString(whole) : Double.toString(value);
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
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private record Snapshot(Values boss, Values clone, Values rabbit) {
        static Snapshot defaults() {
            return new Snapshot(
                    Values.bossDefaults(),
                    Values.cloneDefaults(),
                    Values.rabbitDefaults()
            );
        }
    }

    private record Values(
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double armor,
            double armorToughness,
            double knockbackResistance,
            double followRange,
            double netcraftTier,
            double meleeDefense,
            double rangedDefense,
            double magicDefense,
            double damageReduction
    ) {
        static Values bossDefaults() {
            return new Values(
                    100000.0D,
                    120.0D,
                    0.25D,
                    0.0D,
                    0.0D,
                    1.0D,
                    40.0D,
                    4.0D,
                    60.0D,
                    10.0D,
                    10.0D,
                    0.70D
            );
        }

        static Values cloneDefaults() {
            return new Values(
                    50000.0D,
                    120.0D,
                    0.25D,
                    0.0D,
                    0.0D,
                    1.0D,
                    40.0D,
                    4.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }

        static Values rabbitDefaults() {
            return new Values(
                    500.0D,
                    50.0D,
                    0.30D,
                    0.0D,
                    0.0D,
                    1.0D,
                    20.0D,
                    4.0D,
                    10.0D,
                    10.0D,
                    10.0D,
                    0.0D
            );
        }
    }
}
