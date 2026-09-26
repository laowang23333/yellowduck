package com.yourname.yellowduck.item;

import com.yourname.yellowduck.client.GltfItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 使用 YellowDuck Native GLTF 直接渲染的 3D 物品。 */
public class GltfModelItem extends Item {
    private final ResourceLocation modelLocation;
    private final float visualScale;

    public GltfModelItem(Properties properties, ResourceLocation modelLocation, float visualScale) {
        super(properties);
        this.modelLocation = modelLocation;
        this.visualScale = visualScale;
    }

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public float getVisualScale() {
        return visualScale;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GltfItemRenderer.getInstance();
            }
        });
    }
}
