package com.yourname.yellowduck.piratechest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.gltf.YellowGltfMesh;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 龙宫海盗箱原生 GLTF 渲染器。
 *
 * 不再使用 Polymesh，避免 .x -> GLB 模型因背面剔除/材质兼容出现完全透明。
 * 同一个嵌入贴图 GLB 同时用于世界方块与 3D 物品渲染。
 */
public final class DragonPalacePirateChestRenderer
        implements BlockEntityRenderer<DragonPalacePirateChestBlockEntity> {

    public static final ResourceLocation MODEL =
            new ResourceLocation(
                    YellowDuckMod.MOD_ID,
                    "models/gltf/dragon_palace_pirate_chest.glb"
            );

    private YellowGltfModel model;
    private Bounds bounds;

    public DragonPalacePirateChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            DragonPalacePirateChestBlockEntity be,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (!ensureModel()) return;

        Direction facing =
                be.getBlockState().getValue(DragonPalacePirateChestBlock.FACING);

        float rotation = switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        try {
            // 方块中心。
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(rotation));

            // 模型原始尺寸约 11 x 10 x 10，自动缩放到约 1 格宽，
            // 并将模型底部贴到方块 Y=0、XZ 几何中心贴到方块中心。
            float scale = 0.90F / bounds.maxExtent;
            pose.scale(scale, scale, scale);
            pose.translate(-bounds.centerX, -bounds.minY, -bounds.centerZ);

            YellowGltfRenderUtil.renderModel(
                    model,
                    pose,
                    buffers,
                    packedLight,
                    0.0F,
                    null,
                    false
            );
        } finally {
            pose.popPose();
        }
    }

    private boolean ensureModel() {
        if (model != null && bounds != null) return true;

        model = YellowGltfModelCache.getOrLoad(MODEL);
        if (model == null) return false;

        bounds = calculateBounds(model);
        return bounds != null && bounds.maxExtent > 1.0E-6F;
    }

    static Bounds calculateBounds(YellowGltfModel model) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (YellowGltfMesh mesh : model.meshes) {
            float[] positions = mesh.positions;
            for (int i = 0; i + 2 < positions.length; i += 3) {
                minX = Math.min(minX, positions[i]);
                minY = Math.min(minY, positions[i + 1]);
                minZ = Math.min(minZ, positions[i + 2]);
                maxX = Math.max(maxX, positions[i]);
                maxY = Math.max(maxY, positions[i + 1]);
                maxZ = Math.max(maxZ, positions[i + 2]);
            }
        }

        if (!Float.isFinite(minX)) return null;

        return new Bounds(
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))
        );
    }

    record Bounds(
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float centerX,
            float centerY,
            float centerZ,
            float maxExtent
    ) {
    }
}
