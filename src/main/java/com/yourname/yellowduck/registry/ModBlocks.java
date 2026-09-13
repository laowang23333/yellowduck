package com.yourname.yellowduck.registry;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BigChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YellowDuckMod.MODID);

    public static final RegistryObject<Block> BIG_CHEST = BLOCKS.register("big_chest",
            () -> new BigChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5F).noOcclusion()));
}
