package com.yourname.yellowduck.config;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.cleopatra.CleopatraConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YellowDuck 生物属性、掉落和艳后战斗参数统一配置。
 *
 * 重要：
 * yellowduck-entities.toml 不再注册为 ForgeConfigSpec。
 * Forge 不再拥有“Correcting / corrected to its default”后自动改写该文件的机会。
 *
 * reload 使用“先完整解析 -> 全部验证通过 -> 一次性切换”的方式：
 * - 文件保存到一半、空文件、非法数字：reload 失败，当前有效配置继续使用；
 * - 不会部分参数更新、部分参数回默认；
 * - 配置文件本身也不会因为解析失败被自动改写。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class EntityTuningConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern DROP_PATTERN = Pattern.compile("\\[([^\\]]+)]");
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-entities.toml");

    private static final List<String> NUMERIC_KEYS = List.of(
            "max_health", "attack_damage", "movement_speed", "attack_speed",
            "armor", "armor_toughness", "knockback_resistance", "follow_range",
            "attack_knockback", "flying_speed", "jump_strength", "netcraft_tier"
    );

    private static final Map<String, SectionInfo> SECTION_INFO = new LinkedHashMap<>();

    static {
        section("two_phase_boss", "BOSS：小黄鸭",
                "生命=100000，攻击=110，移动速度=0.28，护甲=12，击退抗性=1，跟随范围=35。");
        section("sakurawitch", "BOSS：魔女小樱",
                "生命=200000，攻击=110，移动速度=0.30，护甲=10，攻击击退=0，击退抗性=1，跟随范围=35。");
        section("toy_bear", "小樱布偶熊",
                "生命=20000，攻击=15，移动速度=0.30，击退抗性=1，跟随范围=48。");
        section("mount", "坐骑：魔化天狗",
                "生命=40，移动速度=0.34，击退抗性=0.8。");
        section("alpaca_mount", "坐骑：羊驼",
                "生命=40，移动速度=0.34，击退抗性=0.8。");
        section("rabbit_mount", "坐骑：玉兔",
                "生命=40，移动速度=0.34，击退抗性=0.8。");
        section("bamboo_horse_mount", "坐骑：竹马",
                "生命=40，移动速度=0.34，击退抗性=0.8。");
        section("silk_boss", "BOSS：疯狂教授斯尔克（T5）",
                "生命=430000，攻击=8，移动速度=0.23，击退抗性=1，跟随范围=48，NetCraft等级=T5。");
        section("silk_shadow_bat", "疯狂教授斯尔克召唤物：暗影蝙蝠",
                "沿用该召唤物/原版蝙蝠属性；需要覆盖时再填写对应项目。");
        section("silk_meteor", "疯狂教授斯尔克召唤物：追踪陨石",
                "源码设计生命=40；其余主要数值由斯尔克技能逻辑控制。");
        section("silk_plague_bear", "疯狂教授斯尔克召唤物：疫病转移之熊",
                "生命=200000，攻击=18，移动速度=0.35，攻击击退=0，击退抗性=1，跟随范围=64。");
        section("silk_summoned_slime", "疯狂教授斯尔克召唤物：不稳定史莱姆",
                "实体ID仍为 minecraft:slime；只有带 SilkProfessorSlime 标记的史莱姆读取这一段。");
    }

    private static volatile Snapshot snapshot = emptySnapshot();
    private static volatile boolean loaded;

    private EntityTuningConfig() {}

    private static void section(String key, String title, String defaults) {
        SECTION_INFO.put(key, new SectionInfo(title, defaults));
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;

        try {
            if (Files.notExists(PATH)) {
                Files.createDirectories(PATH.getParent());
                try {
                    Files.writeString(PATH, defaultText(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                }
            }

            if (reloadInternal()) {
                migrateCommentsOnly();
            }
        } finally {
            loaded = true;
        }
    }

    /**
     * @return true=新文件完整解析并已切换；false=继续使用上一份有效配置。
     */
    public static synchronized boolean reload() {
        ensureLoaded();
        boolean ok = reloadInternal();
        if (ok) migrateCommentsOnly();
        return ok;
    }

    private static boolean reloadInternal() {
        if (Files.notExists(PATH)) {
            LOGGER.error("找不到生物配置文件：{}", PATH);
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(PATH, StandardCharsets.UTF_8);

            // 面板保存时有时会短暂产生 0 字节/极小文件。绝不能因此切到源码默认。
            long meaningful = lines.stream()
                    .map(EntityTuningConfig::stripComment)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .count();
            if (meaningful < 3) {
                LOGGER.error("{} 内容疑似为空或保存未完成；拒绝切换配置，继续使用上一份有效值。", PATH.getFileName());
                return false;
            }

            ParsedToml parsed = parse(lines);
            List<String> errors = new ArrayList<>(parsed.errors());

            Map<String, Entry> entityEntries = new LinkedHashMap<>();
            for (String section : SECTION_INFO.keySet()) {
                entityEntries.put(section,
                        parseEntityEntry(section, parsed.sections().getOrDefault(section, Map.of()), errors));
            }

            Map<String, Number> cleopatraPrepared = CleopatraConfig.prepare(
                    parsed.sections().getOrDefault(CleopatraConfig.SECTION, Map.of()), errors);

            if (!errors.isEmpty()) {
                LOGGER.error("{} 读取失败，共 {} 个错误。配置未切换，仍使用上一份有效值。",
                        PATH.getFileName(), errors.size());
                for (String error : errors) LOGGER.error(" - {}", error);
                return false;
            }

            snapshot = new Snapshot(Collections.unmodifiableMap(entityEntries));
            CleopatraConfig.commit(cleopatraPrepared);

            LOGGER.info("YellowDuck 生物配置已读取：{} 个生物分组，艳后战斗参数 {} 项。"
                            + " 该文件由 YellowDuck 自己管理，不再经过 ForgeConfigSpec 自动纠正。",
                    entityEntries.size(), CleopatraConfig.definitionCount());
            return true;
        } catch (Exception ex) {
            LOGGER.error("{} 读取异常；配置未切换，继续使用上一份有效值。",
                    PATH.getFileName(), ex);
            return false;
        }
    }

    private static ParsedToml parse(List<String> lines) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        String section = "";

        for (int i = 0; i < lines.size(); i++) {
            String cleaned = stripComment(lines.get(i)).trim();
            if (cleaned.isEmpty()) continue;

            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                section = cleaned.substring(1, cleaned.length() - 1)
                        .trim().toLowerCase(Locale.ROOT);
                if (section.isEmpty()) {
                    errors.add("第 " + (i + 1) + " 行：空的配置分组。");
                } else {
                    sections.computeIfAbsent(section, k -> new LinkedHashMap<>());
                }
                continue;
            }

            int eq = cleaned.indexOf('=');
            if (eq <= 0) {
                errors.add("第 " + (i + 1) + " 行无法解析：" + cleaned);
                continue;
            }

            if (section.isEmpty()) {
                // 允许以后加入顶层版本号等字段；当前不参与实体配置。
                continue;
            }

            String key = cleaned.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = cleaned.substring(eq + 1).trim();
            if (key.isEmpty()) {
                errors.add("第 " + (i + 1) + " 行键名为空。");
                continue;
            }

            Map<String, String> values = sections.computeIfAbsent(section, k -> new LinkedHashMap<>());
            if (values.put(key, value) != null) {
                errors.add("第 " + (i + 1) + " 行重复配置：" + section + "." + key);
            }
        }

        return new ParsedToml(sections, errors);
    }

    private static Entry parseEntityEntry(String section, Map<String, String> values, List<String> errors) {
        Map<String, Double> numeric = new LinkedHashMap<>();

        for (String key : NUMERIC_KEYS) {
            String raw = values.get(key);
            if (raw == null || unquote(raw).isBlank()) continue;

            try {
                double value = Double.parseDouble(unquote(raw));
                if (!Double.isFinite(value)) throw new NumberFormatException("not finite");
                numeric.put(key, value);
            } catch (NumberFormatException ex) {
                errors.add("[" + section + "] " + key + " 不是有效数字：" + raw);
            }
        }

        String items = values.containsKey("items") ? unquote(values.get("items")) : "";
        return new Entry(Collections.unmodifiableMap(numeric), items);
    }

    /**
     * 只迁移注释，不碰任何 key=value。
     * 删除旧版“支持 xxx = 50000 或 ...”提示，并给生物分组补上真实源码默认值说明。
     */
    private static void migrateCommentsOnly() {
        try {
            List<String> old = Files.readAllLines(PATH, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            boolean changed = false;

            for (int i = 0; i < old.size(); i++) {
                String line = old.get(i);
                String trim = line.trim();

                if (trim.startsWith("#") && trim.contains("支持：")) {
                    changed = true;
                    continue;
                }

                out.add(line);

                if (trim.startsWith("[") && trim.endsWith("]")) {
                    String section = trim.substring(1, trim.length() - 1)
                            .trim().toLowerCase(Locale.ROOT);
                    SectionInfo info = SECTION_INFO.get(section);
                    if (info != null) {
                        boolean nearbyAlreadyHasDefault = false;
                        for (int j = i + 1; j < Math.min(old.size(), i + 6); j++) {
                            String next = old.get(j).trim();
                            if (next.startsWith("[")) break;
                            if (next.contains("源码默认：")) {
                                nearbyAlreadyHasDefault = true;
                                break;
                            }
                        }
                        if (!nearbyAlreadyHasDefault) {
                            out.add("# 源码默认：" + info.defaults());
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                Files.write(PATH, out, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                LOGGER.info("{} 注释已升级：删除旧版 50000 格式提示，并补充真实源码默认属性；配置值未改动。",
                        PATH.getFileName());
            }
        } catch (Exception ex) {
            // 注释迁移失败不能影响真实配置。
            LOGGER.warn("{} 注释迁移失败，但当前有效配置仍可正常使用：{}",
                    PATH.getFileName(), ex.toString());
        }
    }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity entity)) return;
        reapply(entity);
    }

    private static Entry entryFor(LivingEntity entity) {
        if (entity instanceof Slime slime
                && slime.getPersistentData().getBoolean("SilkProfessorSlime")) {
            return snapshot.entries().get("silk_summoned_slime");
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null || !YellowDuckMod.MOD_ID.equals(id.getNamespace())) return null;
        return snapshot.entries().get(id.getPath());
    }

    public static void reapply(LivingEntity entity) {
        Entry entry = entryFor(entity);
        if (entry != null) applyAttributes(entity, entry);
    }

    private static void applyAttributes(LivingEntity entity, Entry entry) {
        double oldMax = entity.getMaxHealth();
        float oldHealth = entity.getHealth();
        double healthRatio = oldMax > 0.0D ? oldHealth / oldMax : 1.0D;

        boolean maxHealthChanged = setAttribute(entity, Attributes.MAX_HEALTH, entry.value("max_health"));
        setAttribute(entity, Attributes.ATTACK_DAMAGE, entry.value("attack_damage"));
        setAttribute(entity, Attributes.MOVEMENT_SPEED, entry.value("movement_speed"));
        setAttribute(entity, Attributes.ATTACK_SPEED, entry.value("attack_speed"));
        setAttribute(entity, Attributes.ARMOR, entry.value("armor"));
        setAttribute(entity, Attributes.ARMOR_TOUGHNESS, entry.value("armor_toughness"));
        setAttribute(entity, Attributes.KNOCKBACK_RESISTANCE, entry.value("knockback_resistance"));
        setAttribute(entity, Attributes.FOLLOW_RANGE, entry.value("follow_range"));
        setAttribute(entity, Attributes.ATTACK_KNOCKBACK, entry.value("attack_knockback"));
        setAttribute(entity, Attributes.FLYING_SPEED, entry.value("flying_speed"));
        setAttribute(entity, Attributes.JUMP_STRENGTH, entry.value("jump_strength"));

        if (maxHealthChanged && entity.getMaxHealth() > 0.0F) {
            entity.setHealth((float) Math.max(
                    0.1D,
                    Math.min(entity.getMaxHealth(), entity.getMaxHealth() * healthRatio)
            ));
        }

        if (entity instanceof NetcraftBossBase boss) {
            Double tier = entry.value("netcraft_tier");
            if (tier != null && tier > 0) boss.setBaseTier((int) Math.round(tier));

            Double attack = entry.value("attack_damage");
            if (attack != null) boss.setBaseDamage((int) Math.round(attack));
        }
    }

    private static boolean setAttribute(LivingEntity entity, Attribute attribute, Double value) {
        if (value == null || !Double.isFinite(value)) return false;
        var instance = entity.getAttribute(attribute);
        if (instance == null) return false;
        if (Math.abs(instance.getBaseValue() - value) <= 1.0E-9D) return false;

        instance.setBaseValue(value);
        return true;
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Entry entry = entryFor(event.getEntity());
        if (entry == null || entry.items().isBlank()) return;

        List<DropRule> rules = parseDrops(entry.items());
        if (rules.isEmpty()) {
            LOGGER.warn("YellowDuck entity config: items 非空但没有任何有效条目，保留原掉落：{}",
                    entry.items());
            return;
        }

        event.getDrops().clear();

        for (DropRule rule : rules) {
            if (event.getEntity().getRandom().nextDouble() > rule.chance()) continue;

            int amount = rule.min()
                    + event.getEntity().getRandom().nextInt(rule.max() - rule.min() + 1);

            while (amount > 0) {
                int count = Math.min(amount, rule.item().getMaxStackSize());
                ItemStack stack = new ItemStack(rule.item(), count);
                ItemEntity drop = new ItemEntity(
                        event.getEntity().level(),
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        stack
                );
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
                amount -= count;
            }
        }
    }

    private static List<DropRule> parseDrops(String text) {
        List<DropRule> rules = new ArrayList<>();
        Matcher matcher = DROP_PATTERN.matcher(text);

        while (matcher.find()) {
            String[] parts = matcher.group(1).trim().split("\\|");
            if (parts.length != 4) {
                LOGGER.warn("YellowDuck掉落格式错误，应为 [物品ID|最小|最大|概率]：{}",
                        matcher.group());
                continue;
            }

            try {
                ResourceLocation id = new ResourceLocation(
                        parts[0].trim().toLowerCase(Locale.ROOT));
                Item item = ForgeRegistries.ITEMS.getValue(id);

                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    LOGGER.warn("YellowDuck掉落物ID不存在：{}", id);
                    continue;
                }

                int min = Math.max(1, Integer.parseInt(parts[1].trim()));
                int max = Math.max(min, Integer.parseInt(parts[2].trim()));
                double chance = Math.max(0.0D,
                        Math.min(1.0D, Double.parseDouble(parts[3].trim())));

                rules.add(new DropRule(item, min, max, chance));
            } catch (Exception ex) {
                LOGGER.warn("YellowDuck掉落配置无法解析：{}", matcher.group());
            }
        }

        return rules;
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
            return s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    private static Snapshot emptySnapshot() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String key : SECTION_INFO.keySet()) entries.put(key, new Entry(Map.of(), ""));
        return new Snapshot(Collections.unmodifiableMap(entries));
    }

    private static String defaultText() {
        StringBuilder out = new StringBuilder();
        out.append("# YellowDuck 生物属性、掉落与艳后战斗参数\n");
        out.append("# 本文件由 YellowDuck 自己读取，不再由 ForgeConfigSpec 自动纠正。\n");
        out.append("# 数值留空 \"\" = 使用源码默认；填写数字 = 覆盖。\n");
        out.append("# 修改后执行 /yd reload。解析失败时继续使用上一份有效配置，不会自动改回默认。\n");
        out.append("# 掉落格式：[物品ID|最小数量|最大数量|概率]\n");

        for (Map.Entry<String, SectionInfo> e : SECTION_INFO.entrySet()) {
            appendEntitySection(out, e.getKey(), e.getValue());
        }

        CleopatraConfig.appendDefaultText(out);
        return out.toString();
    }

    private static void appendEntitySection(StringBuilder out, String key, SectionInfo info) {
        out.append("\n[").append(key).append("]\n");
        out.append("# ").append(info.title()).append('\n');
        out.append("# 源码默认：").append(info.defaults()).append('\n');
        out.append("# 以下数值留空时不覆盖源码属性。\n");
        out.append("max_health = \"\"\n");
        out.append("attack_damage = \"\"\n");
        out.append("movement_speed = \"\"\n");
        out.append("attack_speed = \"\"\n");
        out.append("armor = \"\"\n");
        out.append("armor_toughness = \"\"\n");
        out.append("knockback_resistance = \"\"\n");
        out.append("follow_range = \"\"\n");
        out.append("attack_knockback = \"\"\n");
        out.append("flying_speed = \"\"\n");
        out.append("jump_strength = \"\"\n");
        out.append("netcraft_tier = \"\"\n");
        out.append("# 留空=保留原掉落；非空=完全使用这里的掉落。\n");
        out.append("items = \"\"\n");
    }

    private record SectionInfo(String title, String defaults) {}
    private record ParsedToml(Map<String, Map<String, String>> sections, List<String> errors) {}
    private record Snapshot(Map<String, Entry> entries) {}
    private record DropRule(Item item, int min, int max, double chance) {}

    private record Entry(Map<String, Double> numeric, String items) {
        Double value(String key) {
            return numeric.get(key);
        }
    }
}
