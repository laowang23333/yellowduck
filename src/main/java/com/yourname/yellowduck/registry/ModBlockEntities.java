package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlockEntity;
import com.yourname.yellowduck.block.MeetStoneBlockEntity; // 新增导入
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);

    // ==========================================
    // 大箱子
    // ==========================================
    public static final RegistryObject<BlockEntityType<BigChestBlockEntity>> BIG_CHEST =
            BLOCK_ENTITIES.register("big_chest",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BigChestBlockEntity(ModBlockEntities.BIG_CHEST.get(), pos, state),
                            ModBlocks.BIG_CHEST.get()).build(null));

    // ==========================================
    // 新增：奶块石柱
    // ==========================================
    public static final RegistryObject<BlockEntityType<MeetStoneBlockEntity>> MEET_STONE =
            BLOCK_ENTITIES.register("meet_stone",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new MeetStoneBlockEntity(ModBlockEntities.MEET_STONE.get(), pos, state),
                            ModBlocks.MEET_STONE.get()).build(null));
}
