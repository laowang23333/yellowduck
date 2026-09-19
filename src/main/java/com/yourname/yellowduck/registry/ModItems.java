package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.item.MountSummonItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 独立物品注册表；不改动原有 ModBlocks.ITEMS。 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Item> GHOST_WOLF_MOUNT =
            ITEMS.register("mount_egg_ghost_wolf_stars",
                    () -> new MountSummonItem(new Item.Properties(), "ghost_wolf_stars"));

    // 兼容新版 MountEntity 使用的魔化天狗字段名；复用同一注册对象，避免重复注册。
    public static final RegistryObject<Item> DEMON_TENGU_MOUNT = GHOST_WOLF_MOUNT;

    /**
     * Jade / 坐骑收藏界面专用展示物品。
     * 不加入创造栏、不参与坐骑绑定；仅用于给实体提供独立的高清图标。
     */
    public static final RegistryObject<Item> GHOST_WOLF_MOUNT_DISPLAY =
            ITEMS.register("mount_display_ghost_wolf_stars",
                    () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RABBIT_MOUNT =
            ITEMS.register("mount_egg_rabbit",
                    () -> new MountSummonItem(new Item.Properties(), "rabbit"));

    public static final RegistryObject<Item> ALPACA_MOUNT =
            ITEMS.register("mount_egg_alpaca",
                    () -> new MountSummonItem(new Item.Properties(), "alpaca"));

    public static final RegistryObject<Item> BAMBOO_HORSE_MOUNT =
            ITEMS.register("mount_egg_bamboo_horse",
                    () -> new MountSummonItem(new Item.Properties(), "bamboo_horse"));

    private ModItems() {}
}
