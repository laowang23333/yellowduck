package com.yourname.yellowduck.praytree;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PrayTreeContent {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);

    public static final RegistryObject<Block> PRAY_SAPLING = BLOCKS.register("pray_sapling",
            () -> new PraySaplingBlock(BlockBehaviour.Properties.copy(Blocks.CHERRY_SAPLING)
                    .randomTicks()
                    .noOcclusion()));

    public static final RegistryObject<Block> PRAY_TREE = BLOCKS.register("pray_tree",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.CHERRY_LOG)));

    public static final RegistryObject<Block> PRAY_LEAVES = BLOCKS.register("pray_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.CHERRY_LEAVES)
                    .noOcclusion()));

    public static final RegistryObject<Block> PRAY_RIBBON = BLOCKS.register("pray_ribbon",
            () -> new PrayRibbonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .noCollission()
                    .noOcclusion()));

    public static final RegistryObject<Item> PRAY_SAPLING_ITEM = ITEMS.register("pray_sapling",
            () -> new BlockItem(PRAY_SAPLING.get(), new Item.Properties()));
    public static final RegistryObject<Item> PRAY_TREE_ITEM = ITEMS.register("pray_tree",
            () -> new BlockItem(PRAY_TREE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PRAY_LEAVES_ITEM = ITEMS.register("pray_leaves",
            () -> new BlockItem(PRAY_LEAVES.get(), new Item.Properties()));
    public static final RegistryObject<Item> PRAY_RIBBON_ITEM = ITEMS.register("pray_ribbon",
            () -> new BlockItem(PRAY_RIBBON.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private PrayTreeContent() {}
}
