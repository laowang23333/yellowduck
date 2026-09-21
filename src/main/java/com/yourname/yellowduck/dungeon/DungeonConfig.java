package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
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

/**
 * 副本配置。使用独立的 yellowduck-dungeons.toml，支持动态增加 [dungeon.xxx] 段。
 * 这里只解析本系统需要的简单 TOML 键值，避免新增副本时必须重新编译配置类。
 */
public final class DungeonConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-dungeons.toml");

    private static volatile Snapshot snapshot = new Snapshot(512, 100, 16, 48, Map.of());
    private static volatile boolean loaded;

    private DungeonConfig() {}

    public static synchronized void ensureLoaded() {
        if (!loaded) reload();
    }

    public static synchronized boolean reload() {
        try {
            if (Files.notExists(PATH)) {
                Files.createDirectories(PATH.getParent());
                try {
                    // 仅在第一次生成时创建。已有配置永远不走覆盖写入，避免手改数值被默认模板顶回去。
                    Files.writeString(PATH, defaultText(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // 并发启动/面板刚好已创建：直接读取现有文件。
                }
            }
            parse(Files.readAllLines(PATH, StandardCharsets.UTF_8));
            loaded = true;
            LOGGER.info("YellowDuck 副本配置已读取：{} 个副本。", snapshot.dungeons().size());
            return true;
        } catch (Exception ex) {
            LOGGER.error("读取 yellowduck-dungeons.toml 失败", ex);
            return false;
        }
    }

    public static int instanceSpacing() { ensureLoaded(); return snapshot.instanceSpacing(); }
    public static int instanceY() { ensureLoaded(); return snapshot.instanceY(); }
    public static int maxInstances() { ensureLoaded(); return snapshot.maxInstances(); }
    public static int arenaRadius() { ensureLoaded(); return snapshot.arenaRadius(); }

    public static DungeonDefinition get(String id) {
        ensureLoaded();
        return id == null ? null : snapshot.dungeons().get(id.toLowerCase(Locale.ROOT));
    }

    public static List<DungeonDefinition> enabledDungeons() {
        ensureLoaded();
        List<DungeonDefinition> list = new ArrayList<>();
        for (DungeonDefinition def : snapshot.dungeons().values()) if (def.enabled()) list.add(def);
        return Collections.unmodifiableList(list);
    }

    private static void parse(List<String> lines) {
        int spacing = 512;
        int y = 100;
        int maxInstances = 16;
        int arenaRadius = 48;
        String section = "";
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        sections.put("general", new LinkedHashMap<>());

        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                sections.computeIfAbsent(section, k -> new LinkedHashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = unquote(line.substring(eq + 1).trim());
            sections.computeIfAbsent(section.isEmpty() ? "general" : section, k -> new LinkedHashMap<>()).put(key, value);
        }

        Map<String, String> general = sections.getOrDefault("general", Map.of());
        spacing = intValue(general, "instance_spacing", spacing, 128, 100000);
        y = intValue(general, "instance_y", y, -32, 300);
        maxInstances = intValue(general, "max_instances", maxInstances, 1, 128);
        arenaRadius = intValue(general, "arena_radius", arenaRadius, 16, 96);
        int minimumSafeSpacing = arenaRadius * 2 + 64;
        if (spacing < minimumSafeSpacing) {
            LOGGER.warn("副本 instance_spacing={} 过小，按 arena_radius={} 自动提升为安全值 {}，避免不同队伍实例范围重叠。",
                    spacing, arenaRadius, minimumSafeSpacing);
            spacing = minimumSafeSpacing;
        }

        Map<String, DungeonDefinition> dungeons = new LinkedHashMap<>();
        for (var entry : sections.entrySet()) {
            if (!entry.getKey().startsWith("dungeon.")) continue;
            String id = entry.getKey().substring("dungeon.".length()).trim();
            if (id.isEmpty()) continue;
            Map<String, String> v = entry.getValue();
            int minPlayers = intValue(v, "min_players", 1, 1, 100);
            int maxPlayers = intValue(v, "max_players", 5, 1, 100);
            if (minPlayers > maxPlayers) {
                LOGGER.warn("副本 {} 的 min_players={} 大于 max_players={}，已自动交换为 {}～{}。",
                        id, minPlayers, maxPlayers, maxPlayers, minPlayers);
                int swap = minPlayers;
                minPlayers = maxPlayers;
                maxPlayers = swap;
            }
            DungeonDefinition def = new DungeonDefinition(
                    id,
                    v.getOrDefault("display_name", id),
                    v.getOrDefault("boss", "yellowduck:cleopatra"),
                    v.getOrDefault("completion", "cleopatra".equalsIgnoreCase(id) ? "cleopatra_snakes" : "boss_death"),
                    boolValue(v, "enabled", true),
                    minPlayers,
                    maxPlayers,
                    intValue(v, "time_limit_seconds", 1500, 30, 86400),
                    intValue(v, "boss_spawn_delay_seconds", 5, 0, 300),
                    intValue(v, "reward_preview_seconds", 60, 0, 3600),
                    intValue(v, "cooldown_seconds", 0, 0, 31536000),
                    intValue(v, "wipe_close_seconds", 30, 1, 600),
                    v.getOrDefault("revive_mode", "players"),
                    intValue(v, "fixed_revives", 3, 0, 1000),
                    intValue(v, "experience", 5000, 0, Integer.MAX_VALUE),
                    v.getOrDefault("item", "")
            );
            dungeons.put(id.toLowerCase(Locale.ROOT), def);
        }
        snapshot = new Snapshot(spacing, y, maxInstances, arenaRadius, Collections.unmodifiableMap(dungeons));
    }

    private static int intValue(Map<String, String> values, String key, int fallback, int min, int max) {
        try {
            int n = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)).trim());
            return Math.max(min, Math.min(max, n));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static String stripComment(String line) {
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quote = !quote;
            if (c == '#' && !quote) return line.substring(0, i);
        }
        return line;
    }

    private static String unquote(String text) {
        String s = text.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static String defaultText() {
        return """
                # YellowDuck 副本配置
                # 修改后使用 /yellowduck reload 或 /yd reload 重新读取。
                # 正在进行中的副本使用开场时的配置快照，修改只影响之后新开的副本。

                [general]
                # 不同副本实例中心之间的距离，单位：方块。默认：512。
                # 如果设置得过小，系统会根据 arena_radius 自动提升到安全距离，避免不同队伍串副本。
                instance_spacing = 512
                # 副本竞技场基础高度。默认：100。
                instance_y = 100
                # 同时允许存在的最大副本实例数量。默认：16。
                max_instances = 16
                # 默认竞技场半径，同时用于寻找入口/Boss出生点方块。默认：48。
                arena_radius = 48

                [dungeon.cleopatra]
                # 是否启用这个副本。
                enabled = true
                # 副本在组队GUI和提示中显示的名称。
                display_name = "艳后神殿"
                # 这个副本生成的主Boss实体ID。
                boss = "yellowduck:cleopatra"
                # 通关规则。cleopatra_snakes=艳后死亡后必须等三蛇全部结束；boss_death=主Boss死亡即通关。
                completion = "cleopatra_snakes"
                # 允许开始副本的最少/最多人数。
                min_players = 1
                max_players = 5
                # 副本最大战斗时间，单位：秒。超时自动失败并关闭。
                time_limit_seconds = 1500
                # 玩家进入副本后多久生成Boss，单位：秒。
                boss_spawn_delay_seconds = 5
                # 通关后奖励预览GUI保留多久，随后全员离开副本，单位：秒。
                reward_preview_seconds = 60
                # 成功通关后的再次挑战冷却，单位：秒。0=关闭冷却。按玩家+副本分别记录，重启服务器仍保留。
                cooldown_seconds = 0
                # 全队死亡后自动关闭副本的倒计时，单位：秒。
                wipe_close_seconds = 30
                # 复活次数模式：players=进入几个人就有几次；fixed=使用 fixed_revives。
                revive_mode = "players"
                # revive_mode=fixed 时使用的团队复活次数。
                fixed_revives = 3
                # 通关总经验。离开副本时在所有仍有奖励资格的参与者之间平均分配。
                experience = 5000
                # 通关物品奖励。每条独立判定，因此一次可以获得多个物品。
                # 格式：[物品ID|最少数量|最多数量|概率]，概率1=100%。
                # 多个物品直接用英文逗号隔开。
                item = [minecraft:diamond|1|10|0.7],[minecraft:emerald|2|5|0.25]

                [dungeon.sakura]
                # 是否启用小樱之境副本。
                enabled = true
                # 副本在组队GUI和提示中显示的名称。
                display_name = "小樱之境"
                # 小樱Boss实体ID。
                boss = "yellowduck:sakurawitch"
                # 通关规则。普通Boss使用 boss_death。
                completion = "boss_death"
                # 允许开始副本的最少/最多人数。
                min_players = 1
                max_players = 5
                # 副本最大战斗时间，单位：秒。默认：1500（25分钟）。
                time_limit_seconds = 1500
                # 玩家进入副本后多久生成Boss，单位：秒。默认：5。
                boss_spawn_delay_seconds = 5
                # 通关后奖励预览GUI保留多久，单位：秒。默认：60。
                reward_preview_seconds = 60
                # 成功通关后的再次挑战冷却，单位：秒。0=关闭冷却。按玩家+副本分别记录，重启服务器仍保留。
                cooldown_seconds = 0
                # 全队死亡后自动关闭副本的倒计时，单位：秒。默认：30。
                wipe_close_seconds = 30
                # 复活次数模式：players=进入几个人就有几次；fixed=使用 fixed_revives。
                revive_mode = "players"
                # revive_mode=fixed 时使用的团队复活次数。
                fixed_revives = 3
                # 通关总经验。默认：5000。
                experience = 5000
                # 通关物品奖励，格式：[物品ID|最少数量|最多数量|概率]。
                item = ""
                """;
    }

    private record Snapshot(int instanceSpacing, int instanceY, int maxInstances, int arenaRadius,
                            Map<String, DungeonDefinition> dungeons) {}
}
