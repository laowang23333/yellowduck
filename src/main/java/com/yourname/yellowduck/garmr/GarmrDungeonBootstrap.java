package com.yourname.yellowduck.garmr;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * 只负责给已有 yellowduck-dungeons.toml 安全追加 [dungeon.garmr]。
 * 已存在时完全不改，绝不会覆盖玩家手改值。
 */
public final class GarmrDungeonBootstrap {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("yellowduck-dungeons.toml");

    private GarmrDungeonBootstrap() {}

    /** @return 是否真的追加了新段；调用方可据此 reload。 */
    public static boolean ensureDungeonSection() {
        try {
            if (Files.notExists(PATH)) return false; // 先让 DungeonConfig 生成自己的默认文件
            String text = Files.readString(PATH, StandardCharsets.UTF_8);
            if (text.toLowerCase(Locale.ROOT).contains("[dungeon.garmr]")) return false;
            Files.writeString(PATH, "\n" + defaultSection(), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
            return true;
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
                min_players = 1
                max_players = 5
                time_limit_seconds = 1500
                boss_spawn_delay_seconds = 5
                reward_preview_seconds = 60
                # 原版冷却/奖励尚未确认，先不擅自套用小樱/艳后的值。
                cooldown_seconds = 0
                wipe_close_seconds = 30
                revive_mode = "players"
                fixed_revives = 3
                # 奖励数值尚未确认：先不给经验/物品，避免把占位值带进正式服。
                experience = 0
                item = ""
                """;
    }
}
