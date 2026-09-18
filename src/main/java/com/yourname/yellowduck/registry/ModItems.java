package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.item.MountSummonItem;
import com.yourname.yellowduck.util.MountData;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 独立物品注册表；不改动原有 ModBlocks.ITEMS。 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Item> DEMON_TENGU_MOUNT =
            /*
             * 注册 ID 保留旧值，避免正式服已有坐骑蛋变成 missing item。
             * Java 名称、逻辑 ID、界面名称已经全部改为“魔化天狗”。
             */
            ITEMS.register("mount_egg_ghost_wolf_stars",
                    () -> new MountSummonItem(new Item.Properties(), MountData.DEMON_TENGU_ID));

    /**
     * Jade / 坐骑收藏界面专用展示物品。
     * 不加入创造栏、不参与坐骑绑定；仅用于给实体提供独立的高清图标。
     * 注册 ID 同样保留旧值，只为兼容旧世界注册表。
     */
    public static final RegistryObject<Item> DEMON_TENGU_MOUNT_DISPLAY =
            ITEMS.register("mount_display_ghost_wolf_stars",
                    () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ALPACA_MOUNT =
            ITEMS.register("mount_egg_alpaca",
                    () -> new MountSummonItem(new Item.Properties(), MountData.ALPACA_ID));

    private ModItems() {}
}
