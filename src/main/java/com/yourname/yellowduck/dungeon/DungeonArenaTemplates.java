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
        // litematic 中心标记位于 (29,0,29)，因此让它正好落在 instance.origin。
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
