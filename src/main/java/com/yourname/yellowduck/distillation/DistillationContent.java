package com.yourname.yellowduck.distillation;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class DistillationContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> HEAT_CONDUCTING_COPPER_COMPONENT = registerBlock(
            "heat_conducting_copper_component",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK)));

    public static final RegistryObject<Block> HARDENED_GLASS = registerBlock(
            "hardened_glass",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion()));

    public static final RegistryObject<Block> LARGE_DISTILLATION_PLATFORM = registerBlock(
            "large_distillation_platform",
            () -> new LargeDistillationPlatformBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_RED)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(LargeDistillationPlatformBlock.MAIN) ? 13 : 0)));

    public static final RegistryObject<BlockEntityType<LargeDistillationPlatformBlockEntity>> LARGE_DISTILLATION_PLATFORM_BE =
            BLOCK_ENTITIES.register("large_distillation_platform",
                    () -> BlockEntityType.Builder.of(
                            LargeDistillationPlatformBlockEntity::new,
                            LARGE_DISTILLATION_PLATFORM.get()).build(null));

    public static final RegistryObject<MenuType<LargeDistillationPlatformMenu>> LARGE_DISTILLATION_PLATFORM_MENU =
            MENUS.register("large_distillation_platform",
                    () -> IForgeMenuType.create(LargeDistillationPlatformMenu::new));

    private DistillationContent() {}

    private static RegistryObject<Block> registerBlock(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
