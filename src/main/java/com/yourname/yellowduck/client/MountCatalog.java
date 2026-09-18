package com.yourname.yellowduck.client;

import com.yourname.yellowduck.util.MountData;
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

    public static final MountDefinition DEMON_TENGU = new MountDefinition(
            MountData.DEMON_TENGU_ID,
            "魔化天狗",
            new ResourceLocation("yellowduck", "textures/item/mount_egg_demon_tengu.png"),
            new ResourceLocation("yellowduck", "demon_tengu_mount")
    );

    public static final MountDefinition ALPACA = new MountDefinition(
            MountData.ALPACA_ID,
            "羊驼",
            new ResourceLocation("yellowduck", "textures/item/alpaca_egg.png"),
            new ResourceLocation("yellowduck", "alpaca_embedded")
    );

    public static List<MountDefinition> all() {
        return List.of(DEMON_TENGU, ALPACA);
    }

    private MountCatalog() {}
}
