package com.yourname.yellowduck.item;

import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** 头颅方块物品形态，直接使用对应的嵌入贴图 GLB。 */
public class BossHeadItem extends BlockItem {
    private final ResourceLocation model;

    public BossHeadItem(Block block, Properties properties, ResourceLocation model) {
        super(block, properties);
        this.model = model;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new GltfItemRenderer(model, GltfRenderOptions.builder()
                            .scale(0.1F)
                            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                            .build());
                }
                return renderer;
            }
        });
    }
}
