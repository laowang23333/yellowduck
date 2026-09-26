package com.yourname.yellowduck.statue;

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

/** 嫦娥雕像 Native GLTF 方块渲染器。 */
public final class ChangeStatueRenderer
        implements BlockEntityRenderer<ChangeStatueBlockEntity> {

    public static final ResourceLocation MODEL =
            new ResourceLocation(
                    YellowDuckMod.MOD_ID,
                    "models/gltf/change_statue.glb"
            );

    private YellowGltfModel model;
    private Bounds bounds;

    public ChangeStatueRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ChangeStatueBlockEntity blockEntity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (!ensureModel()) return;

        Direction facing = blockEntity.getBlockState().getValue(ChangeStatueBlock.FACING);
        float rotation = switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        try {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(rotation));

            // 原模型最高约 13.1 单位。自动缩放到一格内，底座贴地并水平居中。
            float scale = 0.95F / bounds.maxExtent;
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
            float[] p = mesh.positions;
            for (int i = 0; i + 2 < p.length; i += 3) {
                minX = Math.min(minX, p[i]);
                minY = Math.min(minY, p[i + 1]);
                minZ = Math.min(minZ, p[i + 2]);
                maxX = Math.max(maxX, p[i]);
                maxY = Math.max(maxY, p[i + 1]);
                maxZ = Math.max(maxZ, p[i + 2]);
            }
        }

        if (!Float.isFinite(minX)) return null;

        return new Bounds(
                minX, minY, minZ,
                maxX, maxY, maxZ,
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
