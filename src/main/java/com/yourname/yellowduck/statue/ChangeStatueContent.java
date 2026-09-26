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

public final class ChangeStatueContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> CHANGE_STATUE = statue("change_statue");
    public static final RegistryObject<Block> IDUN_STATUE = statue("idun_statue");
    public static final RegistryObject<Block> IDUN_DEER_STATUE = statue("idun_deer_statue");
    public static final RegistryObject<Block> JADE_RABBIT_STATUE = statue("jade_rabbit_statue");

    public static final RegistryObject<Item> CHANGE_STATUE_ITEM = item("change_statue", CHANGE_STATUE);
    public static final RegistryObject<Item> IDUN_STATUE_ITEM = item("idun_statue", IDUN_STATUE);
    public static final RegistryObject<Item> IDUN_DEER_STATUE_ITEM = item("idun_deer_statue", IDUN_DEER_STATUE);
    public static final RegistryObject<Item> JADE_RABBIT_STATUE_ITEM = item("jade_rabbit_statue", JADE_RABBIT_STATUE);

    public static final RegistryObject<BlockEntityType<ChangeStatueBlockEntity>> CHANGE_STATUE_BE =
            BLOCK_ENTITIES.register("change_statue", () -> BlockEntityType.Builder.of(
                    ChangeStatueBlockEntity::new,
                    CHANGE_STATUE.get(), IDUN_STATUE.get(), IDUN_DEER_STATUE.get(), JADE_RABBIT_STATUE.get()
            ).build(null));

    private static RegistryObject<Block> statue(String id) {
        return BLOCKS.register(id, () -> new ChangeStatueBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ).strength(2.5F).noOcclusion()));
    }

    private static RegistryObject<Item> item(String id, RegistryObject<Block> block) {
        return ITEMS.register(id, () -> new ChangeStatueItem(block.get(), new Item.Properties().stacksTo(64)));
    }

    public static String modelId(Block block) {
        if (block == IDUN_STATUE.get()) return "idun_statue";
        if (block == IDUN_DEER_STATUE.get()) return "idun_deer_statue";
        if (block == JADE_RABBIT_STATUE.get()) return "jade_rabbit_statue";
        return "change_statue";
    }

    private ChangeStatueContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); }
}
