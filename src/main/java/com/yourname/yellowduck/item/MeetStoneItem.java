package com.yourname.yellowduck.item;

import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * 副本柱子的物品形态。
 *
 * 世界里的方块由 MeetStoneRenderer 渲染 GLB；物品栏/手持也直接用同一个 GLB，
 * 避免因为缺少普通 JSON 方块模型而显示成紫黑色缺失模型。
 */
public class MeetStoneItem extends BlockItem {
    private static final ResourceLocation MODEL = new ResourceLocation("yellowduck", "meet_stone");

    public MeetStoneItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new GltfItemRenderer(MODEL, GltfRenderOptions.builder()
                            .scale(0.1F)
                            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                            .build());
                }
                return renderer;
            }
        });
    }
}
