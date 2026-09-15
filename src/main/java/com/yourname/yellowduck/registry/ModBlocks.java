package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlock;
import com.yourname.yellowduck.block.IndStatueBlock;
import com.yourname.yellowduck.block.MeetStoneBlock;
import com.yourname.yellowduck.block.ProfessorSilkBlock;
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
    // 奶块石柱（两格高）
    // ==========================================
    public static final RegistryObject<Block> MEET_STONE = BLOCKS.register("meet_stone",
            () -> new MeetStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(2.5F).noOcclusion()));

    public static final RegistryObject<Item> MEET_STONE_ITEM = ITEMS.register("meet_stone",
            () -> new BlockItem(MEET_STONE.get(), new Item.Properties()));

    // ==========================================
    // Professor Silk 两格高模型方块
    // ==========================================
    public static final RegistryObject<Block> PROFESSOR_SILK = BLOCKS.register("professor_silk",
            () -> new ProfessorSilkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(1.5F)
                    .noOcclusion()
                    .noCollission()
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> PROFESSOR_SILK_ITEM = ITEMS.register("professor_silk",
            () -> new BlockItem(PROFESSOR_SILK.get(), new Item.Properties()));

    // ==========================================
    // 印神像
    // ==========================================
    public static final RegistryObject<Block> YIN_SHEN_XIANG = BLOCKS.register("yin_shen_xiang",
            () -> new IndStatueBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)));

    public static final RegistryObject<Item> YIN_SHEN_XIANG_ITEM = ITEMS.register("yin_shen_xiang",
            () -> new BlockItem(YIN_SHEN_XIANG.get(), new Item.Properties()));

    // ==========================================
    // 小黄鸭方块
    // ==========================================
    public static final RegistryObject<Block> XIAOHUANGYA = BLOCKS.register("xiaohuangya",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(1.5F)));

    public static final RegistryObject<Item> XIAOHUANGYA_ITEM = ITEMS.register("xiaohuangya",
            () -> new BlockItem(XIAOHUANGYA.get(), new Item.Properties()));
}
