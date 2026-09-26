package com.yourname.yellowduck.rabbitbox;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 兔兔宝箱方块物品；使用 YellowDuck Native GLTF 物品渲染。 */
public final class RabbitBoxItem extends BlockItem {
    public RabbitBoxItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RabbitBoxItemRenderer.getInstance();
            }
        });
    }
}
