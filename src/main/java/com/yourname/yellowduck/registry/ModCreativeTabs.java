package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YellowDuckMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("小菜蛋"))
                    .icon(() -> new ItemStack(ModBlocks.JINYUANQUAN.get()))
                    .displayItems((params, output) -> {
                        // 原有物品
                        output.accept(ModBlocks.BIG_CHEST_ITEM.get());
                        output.accept(ModBlocks.JINYUANQUAN.get());
                        output.accept(ModBlocks.MEET_STONE_ITEM.get());

                        // 副本管理定位方块
                        output.accept(ModBlocks.DUNGEON_ENTRANCE_MARKER_ITEM.get());
                        output.accept(ModBlocks.DUNGEON_BOSS_SPAWN_MARKER_ITEM.get());

                        // 新增：两个方块
                        output.accept(ModBlocks.YIN_SHEN_XIANG_ITEM.get());
                        output.accept(ModBlocks.XIAOHUANGYA_ITEM.get());

                        // 新增：两个唱片
                        output.accept(ModBlocks.XIAOQI_DISC.get());
                        output.accept(ModBlocks.YIDIYIDI_DISC.get());

                        // 新增五张唱片
                        output.accept(ModBlocks.MUSIC_DISC_1.get());
                        output.accept(ModBlocks.MUSIC_DISC_2.get());
                        output.accept(ModBlocks.MUSIC_DISC_3.get());
                        output.accept(ModBlocks.MUSIC_DISC_4.get());
                        output.accept(ModBlocks.MUSIC_DISC_5.get());

                        // 坐骑蛋：用于第一次绑定坐骑图鉴
                        output.accept(ModItems.GHOST_WOLF_MOUNT.get());
                        output.accept(ModItems.ALPACA_MOUNT.get());
                        output.accept(ModItems.RABBIT_MOUNT.get());
                        output.accept(ModItems.BAMBOO_HORSE_MOUNT.get());

                        // Boss / 生物头颅展示方块
                        output.accept(ModBlocks.EARL_HEAD_ITEM.get());
                        output.accept(ModBlocks.SAKURA_HEAD_ITEM.get());
                        output.accept(ModBlocks.TOY_BEAR_HEAD_ITEM.get());
                        output.accept(ModBlocks.ALPACA_HEAD_ITEM.get());
                        output.accept(ModBlocks.CLEOPATRA_HEAD_ITEM.get());
                        output.accept(ModBlocks.SNAKE_HEAD_ITEM.get());
                        output.accept(ModBlocks.SNOW_MONSTER_HEAD_ITEM.get());
                    })
                    .build());
}
