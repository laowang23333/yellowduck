package com.yourname.yellowduck.statue;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 嫦娥雕像独立注册。 */
public final class ChangeStatueContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> CHANGE_STATUE =
            BLOCKS.register("change_statue",
                    () -> new ChangeStatueBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.QUARTZ)
                            .strength(2.5F)
                            .noOcclusion()));

    public static final RegistryObject<Item> CHANGE_STATUE_ITEM =
            ITEMS.register("change_statue",
                    () -> new ChangeStatueItem(
                            CHANGE_STATUE.get(),
                            new Item.Properties().stacksTo(64)));

    public static final RegistryObject<BlockEntityType<ChangeStatueBlockEntity>> CHANGE_STATUE_BE =
            BLOCK_ENTITIES.register("change_statue",
                    () -> BlockEntityType.Builder.of(
                            ChangeStatueBlockEntity::new,
                            CHANGE_STATUE.get()
                    ).build(null));

    private ChangeStatueContent() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
