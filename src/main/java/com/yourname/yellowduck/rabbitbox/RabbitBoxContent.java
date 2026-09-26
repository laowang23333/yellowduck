package com.yourname.yellowduck.rabbitbox;

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

/** 兔兔宝箱：放下 10 秒后自动开启并随机掉落奖励。 */
public final class RabbitBoxContent {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> JADE_RABBIT_BOX =
            BLOCKS.register("jade_rabbit_box",
                    () -> new RabbitBoxBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PINK)
                            .strength(1.5F, 3.0F)
                            .noOcclusion()));

    public static final RegistryObject<Item> JADE_RABBIT_BOX_ITEM =
            ITEMS.register("jade_rabbit_box",
                    () -> new RabbitBoxItem(
                            JADE_RABBIT_BOX.get(),
                            new Item.Properties().stacksTo(64)));

    public static final RegistryObject<BlockEntityType<RabbitBoxBlockEntity>> JADE_RABBIT_BOX_BE =
            BLOCK_ENTITIES.register("jade_rabbit_box",
                    () -> BlockEntityType.Builder.of(
                            RabbitBoxBlockEntity::new,
                            JADE_RABBIT_BOX.get()
                    ).build(null));

    private RabbitBoxContent() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
