package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.item.GltfModelItem;
import com.yourname.yellowduck.item.MooncakeItem;
import com.yourname.yellowduck.item.MountSummonItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
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

    /** 天狗驯服材料：每个随机增加 5 / 10 点信任值。 */
    public static final RegistryObject<Item> RED_MOON_CRYSTAL =
            ITEMS.register("red_moon_crystal",
                    () -> new GltfModelItem(
                            new Item.Properties(),
                            new ResourceLocation("yellowduck", "models/gltf/item/red_moon_crystal.glb"),
                            0.95F
                    ));

    /** 天狗信任值达到 100 后，用高鞍完成捕捉。 */
    public static final RegistryObject<Item> HIGH_SADDLE =
            ITEMS.register("high_saddle",
                    () -> new GltfModelItem(
                            new Item.Properties().stacksTo(1),
                            new ResourceLocation("yellowduck", "models/gltf/item/high_saddle.glb"),
                            0.90F
                    ));

    private static FoodProperties smallMooncakeFood() {
        // 前四种只增加 3 点饱食度；alwaysEat 允许满饱食时为了 Buff 继续食用。
        return new FoodProperties.Builder()
                .nutrition(3)
                .saturationMod(0.30F)
                .alwaysEat()
                .build();
    }

    private static FoodProperties fullMooncakeFood() {
        // nutrition 20 会直接补到 20/20；高饱和度同时会被原版上限裁到当前饱食度。
        return new FoodProperties.Builder()
                .nutrition(20)
                .saturationMod(1.0F)
                .alwaysEat()
                .build();
    }

    public static final RegistryObject<Item> SAKURA_ICE_MOONCAKE_RED =
            ITEMS.register("sakura_ice_mooncake_red",
                    () -> mooncake(smallMooncakeFood(), MooncakeItem.Kind.SAKURA_DAMAGE,
                            "sakura_ice_mooncake_red"));

    public static final RegistryObject<Item> SAKURA_ICE_MOONCAKE_YELLOW =
            ITEMS.register("sakura_ice_mooncake_yellow",
                    () -> mooncake(smallMooncakeFood(), MooncakeItem.Kind.SAKURA_REGENERATION,
                            "sakura_ice_mooncake_yellow"));

    public static final RegistryObject<Item> ROSE_MOONCAKE =
            ITEMS.register("rose_mooncake",
                    () -> mooncake(smallMooncakeFood(), MooncakeItem.Kind.ROSE_SPEED,
                            "rose_mooncake"));

    public static final RegistryObject<Item> RABBIT_CAKE =
            ITEMS.register("rabbit_cake",
                    () -> mooncake(smallMooncakeFood(), MooncakeItem.Kind.RABBIT_HASTE,
                            "rabbit_cake"));

    public static final RegistryObject<Item> RABBIT_RABBIT_CAKE =
            ITEMS.register("rabbit_rabbit_cake",
                    () -> mooncake(fullMooncakeFood(), MooncakeItem.Kind.RABBIT_ALL_NETCRAFT_BUFFS,
                            "rabbit_rabbit_cake"));

    private static MooncakeItem mooncake(FoodProperties food, MooncakeItem.Kind kind, String modelName) {
        return new MooncakeItem(
                new Item.Properties().food(food),
                kind,
                new ResourceLocation(YellowDuckMod.MOD_ID, "models/gltf/item/" + modelName + ".glb"),
                0.92F
        );
    }

    private ModItems() {}
}
