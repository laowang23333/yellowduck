package com.yourname.yellowduck.piratechest;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 龙宫海盗箱方块物品，物品栏/手持时使用与方块相同的 3D GLB。 */
public final class DragonPalacePirateChestItem extends BlockItem {

    public DragonPalacePirateChestItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return DragonPalacePirateChestItemRenderer.getInstance();
            }
        });
    }
}
