package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlockEntity;
import com.yourname.yellowduck.block.BossHeadBlockEntity;
import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.yourname.yellowduck.block.ProfessorSilkBlockEntity;
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
    // 奶块石柱
    // ==========================================
    public static final RegistryObject<BlockEntityType<MeetStoneBlockEntity>> MEET_STONE =
            BLOCK_ENTITIES.register("meet_stone",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new MeetStoneBlockEntity(ModBlockEntities.MEET_STONE.get(), pos, state),
                            ModBlocks.MEET_STONE.get()).build(null));

    // ==========================================
    // Professor Silk 模型方块
    // ==========================================
    public static final RegistryObject<BlockEntityType<ProfessorSilkBlockEntity>> PROFESSOR_SILK =
            BLOCK_ENTITIES.register("professor_silk",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new ProfessorSilkBlockEntity(ModBlockEntities.PROFESSOR_SILK.get(), pos, state),
                            ModBlocks.PROFESSOR_SILK.get()).build(null));

    // 7 个头颅方块共用一个轻量 BlockEntity，客户端渲染器按注册类型选择对应 GLB。
    public static final RegistryObject<BlockEntityType<BossHeadBlockEntity>> BOSS_HEAD =
            BLOCK_ENTITIES.register("boss_head",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BossHeadBlockEntity(ModBlockEntities.BOSS_HEAD.get(), pos, state),
                            ModBlocks.EARL_HEAD.get(), ModBlocks.SAKURA_HEAD.get(),
                            ModBlocks.TOY_BEAR_HEAD.get(), ModBlocks.ALPACA_HEAD.get(),
                            ModBlocks.CLEOPATRA_HEAD.get(), ModBlocks.SNAKE_HEAD.get(),
                            ModBlocks.SNOW_MONSTER_HEAD.get()).build(null));
}
