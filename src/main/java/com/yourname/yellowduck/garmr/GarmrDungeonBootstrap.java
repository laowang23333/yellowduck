package com.yourname.yellowduck.garmr;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * 给 yellowduck-dungeons.toml 安全补充/迁移 [dungeon.garmr]。
 *
 * V4 图片明确：恐惧之地 5 人开启（5~5），总经验 5w。
 * 已有玩家自定义段原则上不覆盖；只识别 V3 自动生成的原样默认段并迁移 1人/0经验 -> 5人/50000经验。
 */
public final class GarmrDungeonBootstrap {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-dungeons.toml");

    private GarmrDungeonBootstrap() {}

    /** @return 是否修改过文件；调用方据此 reload。 */
    public static boolean ensureDungeonSection() {
        try {
            if (Files.notExists(PATH)) return false; // 先让 DungeonConfig 生成自己的默认文件
            String text = Files.readString(PATH, StandardCharsets.UTF_8);
            String lower = text.toLowerCase(Locale.ROOT);
            if (!lower.contains("[dungeon.garmr]")) {
                Files.writeString(PATH, "\n" + defaultSection(), StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
                return true;
            }

            // 只迁移 V3 自动生成且仍保留旧默认注释/旧默认值的段，不碰用户自行改过的 Garmr 配置。
            if (text.contains("# 奖励数值尚未确认：先不给经验/物品，避免把占位值带进正式服。")
                    && text.contains("min_players = 1")
                    && text.contains("experience = 0")) {
                String migrated = text
                        .replace("min_players = 1", "min_players = 5")
                        .replace("experience = 0", "experience = 50000")
                        .replace("# 奖励数值尚未确认：先不给经验/物品，避免把占位值带进正式服。",
                                "# V4规则表：总经验 5w；物品奖励仍待补充。")
                        .replace("# 原版冷却/奖励尚未确认，先不擅自套用小樱/艳后的值。",
                                "# 冷却仍未在规则表中给出，继续保留 0；经验按规则表 5w。")
                        .replace("min_players = 5\n                max_players = 5", "min_players = 5\n                max_players = 5");
                if (!migrated.equals(text)) {
                    Files.writeString(PATH, migrated, StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String defaultSection() {
        return """
                # 地狱双头犬·加姆 / 恐惧之地
                [dungeon.garmr]
                enabled = true
                display_name = "恐惧之地"
                boss = "yellowduck:garmr"
                completion = "boss_death"
                # V4规则表：固定 5 人副本。
                min_players = 5
                max_players = 5
                time_limit_seconds = 1500
                boss_spawn_delay_seconds = 5
                reward_preview_seconds = 60
                # 规则表没有给冷却时间，先保持 0。
                cooldown_seconds = 0
                wipe_close_seconds = 30
                revive_mode = "players"
                fixed_revives = 3
                # V4规则表：总经验 5w；物品奖励仍待补充。
                experience = 50000
                item = ""
                """;
    }
}
