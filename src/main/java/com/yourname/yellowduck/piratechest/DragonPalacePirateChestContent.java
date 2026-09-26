package com.yourname.yellowduck.piratechest;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DragonPalacePirateChestContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> DRAGON_PALACE_PIRATE_CHEST = BLOCKS.register(
            "dragon_palace_pirate_chest",
            () -> new DragonPalacePirateChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .noOcclusion()));

    public static final RegistryObject<Item> DRAGON_PALACE_PIRATE_CHEST_ITEM = ITEMS.register(
            "dragon_palace_pirate_chest",
            () -> new BlockItem(DRAGON_PALACE_PIRATE_CHEST.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<DragonPalacePirateChestBlockEntity>> DRAGON_PALACE_PIRATE_CHEST_BE =
            BLOCK_ENTITIES.register("dragon_palace_pirate_chest",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new DragonPalacePirateChestBlockEntity(
                                    DRAGON_PALACE_PIRATE_CHEST_BE.get(), pos, state),
                            DRAGON_PALACE_PIRATE_CHEST.get()).build(null));

    public static final RegistryObject<MenuType<DragonPalacePirateChestMenu>> DRAGON_PALACE_PIRATE_CHEST_MENU =
            MENUS.register("dragon_palace_pirate_chest",
                    () -> IForgeMenuType.create(DragonPalacePirateChestMenu::new));

    private DragonPalacePirateChestContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
