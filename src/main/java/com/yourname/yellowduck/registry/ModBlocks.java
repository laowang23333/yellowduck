package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlock;
import com.yourname.yellowduck.block.MeetStoneBlock; // 新增导入
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, YellowDuckMod.MOD_ID);

    // ==========================================
    // 大箱子
    // ==========================================
    public static final RegistryObject<Block> BIG_CHEST = BLOCKS.register("big_chest",
            () -> new BigChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5F).noOcclusion()));

    public static final RegistryObject<Item> BIG_CHEST_ITEM = ITEMS.register("big_chest",
            () -> new BlockItem(BIG_CHEST.get(), new Item.Properties()));

    // ==========================================
    // 鲸元卷物品
    // ==========================================
    public static final RegistryObject<Item> JINYUANQUAN = ITEMS.register("jinyuanquan",
            () -> new Item(new Item.Properties()));

    // ==========================================
    // 新增：奶块石柱（两格高）
    // ==========================================
    public static final RegistryObject<Block> MEET_STONE = BLOCKS.register("meet_stone",
            () -> new MeetStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(2.5F).noOcclusion()));

    public static final RegistryObject<Item> MEET_STONE_ITEM = ITEMS.register("meet_stone",
            () -> new BlockItem(MEET_STONE.get(), new Item.Properties()));
}
