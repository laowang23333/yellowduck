package com.yourname.yellowduck.statue;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 嫦娥雕像方块物品；手持和物品栏使用同一 GLB 3D 渲染。 */
public final class ChangeStatueItem extends BlockItem {
    public ChangeStatueItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ChangeStatueItemRenderer.getInstance();
            }
        });
    }
}
