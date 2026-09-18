package com.yourname.yellowduck.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** GUI 使用的通用坐骑目录；新增坐骑时扩展这里，不需要重做界面布局。 */
public final class MountCatalog {
    public record MountDefinition(
            String id,
            String name,
            ResourceLocation eggTexture,
            ResourceLocation modelId
    ) {}

    public static final MountDefinition GHOST_WOLF = new MountDefinition(
            "ghost_wolf_stars",
            "魔化天狗",
            new ResourceLocation("yellowduck", "textures/item/mount_egg_ghost_wolf_stars.png"),
            new ResourceLocation("yellowduck", "ghost_wolf_mount_final_v2")
    );

    public static final MountDefinition ALPACA = new MountDefinition(
            "alpaca",
            "羊驼",
            new ResourceLocation("yellowduck", "textures/item/alpaca_egg.png"),
            new ResourceLocation("yellowduck", "alpaca_embedded")
    );

    public static List<MountDefinition> all() {
        return List.of(GHOST_WOLF, ALPACA);
    }

    private MountCatalog() {}
}
