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
                    .title(Component.translatable("itemGroup.yellowduck.main"))
                    .icon(() -> new ItemStack(ModBlocks.BIG_CHEST_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.BIG_CHEST_ITEM.get());
                        output.accept(ModBlocks.JINYUANQUAN.get());
                    })
                    .build());
}
