package com.yourname.yellowduck.client;

import com.yourname.yellowduck.block.BossHeadBlockEntity;
import com.yourname.yellowduck.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** 头颅方块的 GLB 渲染器。 */
public class BossHeadRenderer extends GltfBlockEntityRenderer<BossHeadBlockEntity> {
    private final Map<Block, GltfBlockEntityRenderer<BossHeadBlockEntity>> renderers;

    public BossHeadRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new ResourceLocation("yellowduck", "boss_head/earl_head"), GltfRenderOptions.builder()
                .scale(0.1F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
        renderers = Map.of(
                ModBlocks.EARL_HEAD.get(), delegate(context, "earl_head"),
                ModBlocks.SAKURA_HEAD.get(), delegate(context, "sakura_head"),
                ModBlocks.TOY_BEAR_HEAD.get(), delegate(context, "toy_bear_head"),
                ModBlocks.ALPACA_HEAD.get(), delegate(context, "alpaca_head"),
                ModBlocks.CLEOPATRA_HEAD.get(), delegate(context, "cleopatra_head"),
                ModBlocks.SNAKE_HEAD.get(), delegate(context, "snake_head"),
                ModBlocks.SNOW_MONSTER_HEAD.get(), delegate(context, "snow_monster_head"));
    }

    private static GltfBlockEntityRenderer<BossHeadBlockEntity> delegate(
            BlockEntityRendererProvider.Context context, String name) {
        return new GltfBlockEntityRenderer<>(context,
                new ResourceLocation("yellowduck", "boss_head/" + name),
                GltfRenderOptions.builder().scale(0.1F)
                        .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU).build());
    }

    @Override
    public void render(BossHeadBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        GltfBlockEntityRenderer<BossHeadBlockEntity> renderer =
                renderers.get(blockEntity.getBlockState().getBlock());
        if (renderer != null) {
            renderer.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
        }
    }
}
