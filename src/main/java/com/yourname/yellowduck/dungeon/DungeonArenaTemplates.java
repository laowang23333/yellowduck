package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 副本地图模板注册表。
 * 一张地图只在对应永久实例槽第一次使用时放置一次，之后开/关本只复用建筑，不反复重建。
 */
public final class DungeonArenaTemplates {
    private static final Map<String, ArenaTemplate> TEMPLATES = new LinkedHashMap<>();

    static {
        // 埃及艳后场地：59 x 39 x 59。
        register("cleopatra", new ArenaTemplate(
                new ResourceLocation(YellowDuckMod.MOD_ID, "dungeons/cleopatra"),
                new BlockPos(-29, 0, -29),
                new BlockPos(0, 0, 0),
                List.of(
                        new BlockPos(-25, 0, -7),
                        new BlockPos(-25, 0, -3),
                        new BlockPos(-25, 0, 1),
                        new BlockPos(-25, 0, 5),
                        new BlockPos(-25, 0, 9)
                ),
                30,
                39
        ));

        // 小樱副本“暮色钟楼”：29 x 25 x 31。
        // litematic 中央金块 local=(14,0,15)，绑定为 Boss 出生点/实例原点。
        // 四个绿宝石块相对中央金块分别位于四个方向，绑定为玩家出生点。
        register("sakura", new ArenaTemplate(
                new ResourceLocation(YellowDuckMod.MOD_ID, "dungeons/sakura"),
                new BlockPos(-14, 0, -15),
                new BlockPos(0, 0, 0),
                List.of(
                        new BlockPos(0, 0, -12),
                        new BlockPos(-11, 0, 0),
                        new BlockPos(11, 0, 0),
                        new BlockPos(0, 0, 12)
                ),
                16,
                25
        ));

        // 恐惧之地：85 x 51 x 75。
        // 蓝图唯一海晶灯 local=(45,16,35) 作为 Boss 出生点/实例原点。
        // 场地独立金块 local=(45,16,67) 作为阿努比斯固定点，相对 Boss 为 +Z 32 格。
        // 玩家以“金块朝 Boss 方向前 2 格”为中心出生；5 人横向错开，避免完全重叠。
        register("garmr", new ArenaTemplate(
                new ResourceLocation(YellowDuckMod.MOD_ID, "dungeons/garmr"),
                new BlockPos(-45, -16, -35),
                new BlockPos(0, 0, 0),
                List.of(
                        new BlockPos(0, 0, 30),
                        new BlockPos(-1, 0, 30),
                        new BlockPos(1, 0, 30),
                        new BlockPos(-2, 0, 30),
                        new BlockPos(2, 0, 30)
                ),
                48,
                51
        ));
    }

    private DungeonArenaTemplates() {}

    public static void register(String dungeonId, ArenaTemplate template) {
        if (dungeonId == null || dungeonId.isBlank() || template == null) return;
        TEMPLATES.put(dungeonId.trim().toLowerCase(Locale.ROOT), template);
    }

    public static ArenaTemplate get(String dungeonId) {
        if (dungeonId == null) return null;
        return TEMPLATES.get(dungeonId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean has(String dungeonId) {
        return get(dungeonId) != null;
    }

    public record ArenaTemplate(
            ResourceLocation structureId,
            BlockPos placementOffset,
            BlockPos bossOffset,
            List<BlockPos> entranceOffsets,
            int horizontalRadius,
            int height
    ) {
        public ArenaTemplate {
            entranceOffsets = List.copyOf(entranceOffsets);
        }
    }
}
