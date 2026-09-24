package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.BossHeadBlock;
import com.yourname.yellowduck.block.BossHeadBlockEntity;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import com.yourname.yellowduck.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** 头颅方块的 YellowDuck Native GLTF 渲染器。 */
public final class BossHeadRenderer implements BlockEntityRenderer<BossHeadBlockEntity> {
    private static final float MODEL_SCALE = 0.1F;

    private final Map<Block, ResourceLocation> models = Map.of(
            ModBlocks.EARL_HEAD.get(), model("earl_head"),
            ModBlocks.SAKURA_HEAD.get(), model("sakura_head"),
            ModBlocks.TOY_BEAR_HEAD.get(), model("toy_bear_head"),
            ModBlocks.ALPACA_HEAD.get(), model("alpaca_head"),
            ModBlocks.CLEOPATRA_HEAD.get(), model("cleopatra_head"),
            ModBlocks.SNAKE_HEAD.get(), model("snake_head"),
            ModBlocks.SNOW_MONSTER_HEAD.get(), model("snow_monster_head"));

    public BossHeadRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation model(String name) {
        return new ResourceLocation(YellowDuckMod.MOD_ID,
                "models/gltf/boss_head/" + name + ".glb");
    }

    @Override
    public void render(BossHeadBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation location = models.get(blockEntity.getBlockState().getBlock());
        if (location == null) return;

        YellowGltfModel model = YellowGltfModelCache.getOrLoad(location);
        if (model == null) return;

        poseStack.pushPose();
        try {
            poseStack.translate(0.5D, 0.0D, 0.5D);
            Direction facing = blockEntity.getBlockState().getValue(BossHeadBlock.FACING);

            // 这些头颅 GLB 的原始正面朝 SOUTH(+Z)。
            // Minecraft Direction#toYRot 的正角方向与这里 GLB 绕 Y 轴的朝向相反：
            // 原来直接使用 +toYRot 会导致 EAST/WEST 两个方向互换。
            // 使用负角后，FACING 就严格表示“模型正面朝向”，配合
            // BossHeadBlock#getStateForPlacement 的 playerDirection.opposite()，
            // 无论玩家从东南西北哪边放置，头颅都会正面朝向放置玩家。
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            YellowGltfRenderUtil.renderModel(model, poseStack, buffer, packedLight,
                    0.0F, null, false);
        } finally {
            poseStack.popPose();
        }
    }
}
