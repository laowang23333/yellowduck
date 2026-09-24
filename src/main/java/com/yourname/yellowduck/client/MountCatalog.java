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

    /** 新天狗没有蛋；这里的 texture 只是坐骑界面头像，使用用户原图完整显示。 */
    public static final MountDefinition TENGU = new MountDefinition(
            "tengu",
            "天狗",
            new ResourceLocation("yellowduck", "textures/item/mount_tengu.png"),
            new ResourceLocation("yellowduck", "tengu_white")
    );

    public static final MountDefinition ALPACA = new MountDefinition(
            "alpaca",
            "羊驼",
            new ResourceLocation("yellowduck", "textures/item/alpaca_egg.png"),
            new ResourceLocation("yellowduck", "alpaca_embedded")
    );

    public static List<MountDefinition> all() {
        return List.of(GHOST_WOLF, TENGU, ALPACA, new MountDefinition("rabbit", "玉兔",
                new ResourceLocation("yellowduck", "textures/item/items_mount_egg_rabbit.png"),
                new ResourceLocation("yellowduck", "rabbit_mount_embedded")),
                new MountDefinition("bamboo_horse", "竹马",
                new ResourceLocation("yellowduck", "textures/item/items_mount_egg_bamboo_horse.png"),
                new ResourceLocation("yellowduck", "bamboo_horse_embedded")));
    }

    private MountCatalog() {}
}
