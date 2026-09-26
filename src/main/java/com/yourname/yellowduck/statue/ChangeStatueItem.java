package com.yourname.yellowduck.statue;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

public final class ChangeStatueItem extends BlockItem {
    public ChangeStatueItem(Block block, Item.Properties properties) { super(block, properties); }

    @Override
    public Component getName(ItemStack stack) {
        String id = ChangeStatueContent.modelId(getBlock());
        return Component.literal(switch (id) {
            case "idun_statue" -> "依登雕像";
            case "idun_deer_statue" -> "依登鹿雕像";
            case "jade_rabbit_statue" -> "玉兔雕像";
            default -> "嫦娥雕像";
        });
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ChangeStatueItemRenderer.getInstance();
            }
        });
    }
}
