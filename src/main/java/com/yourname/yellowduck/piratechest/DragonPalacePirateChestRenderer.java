package com.yourname.yellowduck.piratechest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** 使用用户提供 big_chest_04.glb + big_chest_04.png 的龙宫海盗箱。 */
public final class DragonPalacePirateChestRenderer
        extends GltfBlockEntityRenderer<DragonPalacePirateChestBlockEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "dragon_palace_pirate_chest");

    public DragonPalacePirateChestRenderer(BlockEntityRendererProvider.Context context) {
        super(context, MODEL_ID, GltfRenderOptions.builder()
                .scale(1.0F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .build());
    }

    @Override
    public void render(DragonPalacePirateChestBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = be.getBlockState().getValue(DragonPalacePirateChestBlock.FACING);
        float rotation = switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        // big_chest_04.glb 的原始几何约 11x10x10 单位，缩放到约 1 个方块范围。
        pose.scale(0.09F, 0.09F, 0.09F);
        super.render(be, partialTick, pose, buffers, packedLight, packedOverlay);
        pose.popPose();
    }
}
