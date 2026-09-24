package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlock;
import com.yourname.yellowduck.block.BossHeadBlock;
import com.yourname.yellowduck.block.IndStatueBlock;
import com.yourname.yellowduck.block.MeetStoneBlock;
import com.yourname.yellowduck.item.MeetStoneItem;
import com.yourname.yellowduck.block.ProfessorSilkBlock;
import com.yourname.yellowduck.block.SakuraEruptionMarkerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.resources.ResourceLocation;
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

    // =========================
    // 大箱子
    // =========================

    public static final RegistryObject<Block> BIG_CHEST = BLOCKS.register("big_chest",
            () -> new BigChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .noOcclusion()));

    public static final RegistryObject<Item> BIG_CHEST_ITEM = ITEMS.register("big_chest",
            () -> new BlockItem(BIG_CHEST.get(), new Item.Properties()));

    // =========================
    // 鲸元卷物品
    // =========================

    public static final RegistryObject<Item> JINYUANQUAN = ITEMS.register("jinyuanquan",
            () -> new Item(new Item.Properties()));

    // =========================
    // 奶块石柱（两格高）
    // =========================

    public static final RegistryObject<Block> MEET_STONE = BLOCKS.register("meet_stone",
            () -> new MeetStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)
                    .noOcclusion()));

    public static final RegistryObject<Item> MEET_STONE_ITEM = ITEMS.register("meet_stone",
            () -> new MeetStoneItem(MEET_STONE.get(), new Item.Properties()));

    // =========================
    // Professor Silk 两格高模型方块
    // =========================

    public static final RegistryObject<Block> PROFESSOR_SILK = BLOCKS.register("professor_silk",
            () -> new ProfessorSilkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(1.5F)
                    .noOcclusion()
                    .noCollission()
                    .isViewBlocking((state, level, pos) -> false)));

    public static final RegistryObject<Item> PROFESSOR_SILK_ITEM = ITEMS.register("professor_silk",
            () -> new BlockItem(PROFESSOR_SILK.get(), new Item.Properties()));

    // =========================
    // 印神像
    // =========================

    public static final RegistryObject<Block> YIN_SHEN_XIANG = BLOCKS.register("yin_shen_xiang",
            () -> new IndStatueBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)));

    public static final RegistryObject<Item> YIN_SHEN_XIANG_ITEM = ITEMS.register("yin_shen_xiang",
            () -> new BlockItem(YIN_SHEN_XIANG.get(), new Item.Properties()));

    // =========================
    // 小黄鸭方块
    // =========================

    public static final RegistryObject<Block> XIAOHUANGYA = BLOCKS.register("xiaohuangya",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(1.5F)));

    public static final RegistryObject<Item> XIAOHUANGYA_ITEM = ITEMS.register("xiaohuangya",
            () -> new BlockItem(XIAOHUANGYA.get(), new Item.Properties()));

    // =========================
    // 唱片：小琪
    // 30 秒 = 600 ticks
    // =========================

    public static final RegistryObject<Item> XIAOQI_DISC = ITEMS.register("xiaoqi_disc",
            () -> new RecordItem(
                    15,
                    ModSounds.XIAOQI_DISC,
                    new Item.Properties().stacksTo(1),
                    600
            ));

    // =========================
    // 唱片：一滴一滴
    // 30 秒 = 600 ticks
    // =========================

    public static final RegistryObject<Item> YIDIYIDI_DISC = ITEMS.register("yidiyidi_disc",
            () -> new RecordItem(
                    13,
                    ModSounds.YIDIYIDI_DISC,
                    new Item.Properties().stacksTo(1),
                    600
            ));

    // =========================
    // 新增五张唱片
    // =========================

    public static final RegistryObject<Item> MUSIC_DISC_1 = ITEMS.register("music_disc_1",
            () -> new RecordItem(1, ModSounds.MUSIC_DISC_1, new Item.Properties().stacksTo(1), 2381));

    public static final RegistryObject<Item> MUSIC_DISC_2 = ITEMS.register("music_disc_2",
            () -> new RecordItem(2, ModSounds.MUSIC_DISC_2, new Item.Properties().stacksTo(1), 2613));

    public static final RegistryObject<Item> MUSIC_DISC_3 = ITEMS.register("music_disc_3",
            () -> new RecordItem(3, ModSounds.MUSIC_DISC_3, new Item.Properties().stacksTo(1), 1204));

    public static final RegistryObject<Item> MUSIC_DISC_4 = ITEMS.register("music_disc_4",
            () -> new RecordItem(4, ModSounds.MUSIC_DISC_4, new Item.Properties().stacksTo(1), 1398));

    public static final RegistryObject<Item> MUSIC_DISC_5 = ITEMS.register("music_disc_5",
            () -> new RecordItem(5, ModSounds.MUSIC_DISC_5, new Item.Properties().stacksTo(1), 5234));

    // =========================
    // Stargazer 小樱火焰喷发 PNG 地面标记（无物品，仅 Boss 临时生成）
    // =========================

    public static final RegistryObject<Block> SAKURA_ERUPTION_MARKER = BLOCKS.register("sakura_eruption_marker",
            () -> new SakuraEruptionMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .noCollission()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)));

    // =========================
    // 副本定位方块（创造模式管理用）
    // =========================

    public static final RegistryObject<Block> DUNGEON_ENTRANCE_MARKER = BLOCKS.register("dungeon_entrance_marker",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 8)));

    public static final RegistryObject<Item> DUNGEON_ENTRANCE_MARKER_ITEM = ITEMS.register("dungeon_entrance_marker",
            () -> new BlockItem(DUNGEON_ENTRANCE_MARKER.get(), new Item.Properties()));

    public static final RegistryObject<Block> DUNGEON_BOSS_SPAWN_MARKER = BLOCKS.register("dungeon_boss_spawn_marker",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 8)));

    public static final RegistryObject<Item> DUNGEON_BOSS_SPAWN_MARKER_ITEM = ITEMS.register("dungeon_boss_spawn_marker",
            () -> new BlockItem(DUNGEON_BOSS_SPAWN_MARKER.get(), new Item.Properties()));

    // =========================
    // Boss / 生物头颅展示方块
    // =========================

    private static BlockBehaviour.Properties headProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .noOcclusion();
    }

    public static final RegistryObject<Block> EARL_HEAD = BLOCKS.register("earl_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> EARL_HEAD_ITEM = ITEMS.register("earl_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(EARL_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/earl_head")));

    public static final RegistryObject<Block> SAKURA_HEAD = BLOCKS.register("sakura_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> SAKURA_HEAD_ITEM = ITEMS.register("sakura_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(SAKURA_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/sakura_head")));

    public static final RegistryObject<Block> TOY_BEAR_HEAD = BLOCKS.register("toy_bear_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> TOY_BEAR_HEAD_ITEM = ITEMS.register("toy_bear_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(TOY_BEAR_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/toy_bear_head")));

    public static final RegistryObject<Block> ALPACA_HEAD = BLOCKS.register("alpaca_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> ALPACA_HEAD_ITEM = ITEMS.register("alpaca_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(ALPACA_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/alpaca_head")));

    public static final RegistryObject<Block> CLEOPATRA_HEAD = BLOCKS.register("cleopatra_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> CLEOPATRA_HEAD_ITEM = ITEMS.register("cleopatra_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(CLEOPATRA_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/cleopatra_head")));

    public static final RegistryObject<Block> SNAKE_HEAD = BLOCKS.register("snake_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> SNAKE_HEAD_ITEM = ITEMS.register("snake_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(SNAKE_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/snake_head")));

    public static final RegistryObject<Block> SNOW_MONSTER_HEAD = BLOCKS.register("snow_monster_head",
            () -> new BossHeadBlock(headProperties()));
    public static final RegistryObject<Item> SNOW_MONSTER_HEAD_ITEM = ITEMS.register("snow_monster_head",
            () -> new com.yourname.yellowduck.item.BossHeadItem(SNOW_MONSTER_HEAD.get(), new Item.Properties(),
                    new ResourceLocation(YellowDuckMod.MOD_ID, "boss_head/snow_monster_head")));
}
