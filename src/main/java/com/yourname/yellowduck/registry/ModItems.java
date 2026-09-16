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

    private ModItems() {}
}
