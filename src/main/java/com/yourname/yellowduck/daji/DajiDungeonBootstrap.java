package com.yourname.yellowduck.daji;

import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** 在现有 YellowDuck 副本配置中补充瑶台琼室。没有场地蓝图时会自动使用公共测试场地。 */
public final class DajiDungeonBootstrap {
    private static final Path PATH=FMLPaths.CONFIGDIR.get().resolve("yellowduck-dungeons.toml");
    private DajiDungeonBootstrap(){}
    public static boolean ensureDungeonSection(){
        try{
            if(Files.notExists(PATH)) return false;
            String text=Files.readString(PATH,StandardCharsets.UTF_8);
            if(text.toLowerCase(Locale.ROOT).contains("[dungeon.daji]")) return false;
            Files.writeString(PATH,"\n"+section(),StandardCharsets.UTF_8, StandardOpenOption.APPEND); return true;
        }catch(Exception ignored){return false;}
    }
    private static String section(){return """
# 妲己 / 瑶台琼室
[dungeon.daji]
enabled = true
display_name = "瑶台琼室"
boss = "yellowduck:daji_boss"
completion = "boss_death"
min_players = 1
max_players = 5
time_limit_seconds = 1500
boss_spawn_delay_seconds = 5
reward_preview_seconds = 60
cooldown_seconds = 0
wipe_close_seconds = 30
revive_mode = "players"
fixed_revives = 3
experience = 0
item = ""
""";}
}
