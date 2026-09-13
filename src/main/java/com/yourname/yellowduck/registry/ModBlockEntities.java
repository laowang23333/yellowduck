package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YellowDuckMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<BigChestBlockEntity>> BIG_CHEST =
            BLOCK_ENTITIES.register("big_chest",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BigChestBlockEntity(ModBlockEntities.BIG_CHEST.get(), pos, state),
                            ModBlocks.BIG_CHEST.get()).build(null));
}
