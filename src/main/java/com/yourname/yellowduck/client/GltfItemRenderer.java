package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yourname.yellowduck.client.gltf.YellowGltfMesh;
import com.yourname.yellowduck.client.gltf.YellowGltfModel;
import com.yourname.yellowduck.client.gltf.YellowGltfModelCache;
import com.yourname.yellowduck.client.gltf.YellowGltfRenderUtil;
import com.yourname.yellowduck.item.GltfModelItem;
import com.yourname.yellowduck.item.MooncakeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * YellowDuck Native GLTF 通用物品渲染器。
 *
 * 月饼在 GUI / 快捷栏中使用轻量 2D 图标：
 * - 避免手机端创造物品栏一次渲染多个高面数 GLB 导致严重掉帧；
 * - 避免 FCL 触控因为帧时间过长出现“很难点中”的感觉；
 * - 同时解决月饼 GUI 角度偏暗的问题。
 *
 * 手持、第三人称、地面、展示框等仍然使用 YellowDuck Native GLTF，未使用 PolyMesh。
 */
@OnlyIn(Dist.CLIENT)
public final class GltfItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static GltfItemRenderer instance;

    private final Map<ResourceLocation, YellowGltfModel> models = new HashMap<>();
    private final Map<ResourceLocation, Bounds> bounds = new HashMap<>();

    private GltfItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    public static GltfItemRenderer getInstance() {
        if (instance == null) {
            instance = new GltfItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext displayContext,
                             PoseStack pose,
                             MultiBufferSource buffers,
                             int packedLight,
                             int packedOverlay) {
        if (!(stack.getItem() instanceof GltfModelItem item)) {
            return;
        }

        // 创造栏、背包、快捷栏都属于 GUI 上下文。
        // 月饼不在这里跑 GLB 顶点循环，手机端会轻很多。
        if (displayContext == ItemDisplayContext.GUI && item instanceof MooncakeItem mooncake) {
            renderGuiIcon(mooncake.getGuiIconTexture(), pose, buffers);
            return;
        }

        ResourceLocation modelLocation = item.getModelLocation();
        YellowGltfModel current = models.get(modelLocation);
        if (current == null) {
            current = YellowGltfModelCache.getOrLoad(modelLocation);
            if (current == null) return;
            models.put(modelLocation, current);
            Bounds calculated = calculateBounds(current);
            if (calculated != null) bounds.put(modelLocation, calculated);
        }

        Bounds b = bounds.get(modelLocation);
        if (b == null || b.maxExtent <= 1.0E-6F) return;

        int renderLight = switch (displayContext) {
            case GUI, GROUND, FIXED -> LightTexture.FULL_BRIGHT;
            default -> packedLight;
        };

        pose.pushPose();
        try {
            pose.translate(0.5D, 0.5D, 0.5D);

            float contextScale = switch (displayContext) {
                case GROUND -> 0.62F;
                case FIXED -> 0.86F;
                case GUI -> 0.94F;
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.78F;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.72F;
                case HEAD -> 0.80F;
                default -> 0.86F;
            };

            float fit = item.getVisualScale() * contextScale / b.maxExtent;
            pose.scale(fit, fit, fit);
            pose.translate(-b.centerX, -b.centerY, -b.centerZ);

            YellowGltfRenderUtil.renderModel(
                    current,
                    pose,
                    buffers,
                    renderLight,
                    0.0F,
                    null,
                    false
            );
        } finally {
            pose.popPose();
        }
    }

    /**
     * 自己画一个透明贴图四边形，不再递归调用 ItemRenderer。
     * 这样不会再次进入 BEWLR，也不会产生 GUI 渲染死循环。
     */
    private static void renderGuiIcon(ResourceLocation texture,
                                      PoseStack pose,
                                      MultiBufferSource buffers) {
        pose.pushPose();
        try {
            pose.translate(0.5D, 0.5D, 0.5D);
            pose.scale(0.92F, 0.92F, 0.92F);

            PoseStack.Pose last = pose.last();
            VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
            int light = LightTexture.FULL_BRIGHT;

            emitIconVertex(vertex, last, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light);
            emitIconVertex(vertex, last,  0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light);
            emitIconVertex(vertex, last,  0.5F,  0.5F, 0.0F, 1.0F, 0.0F, light);
            emitIconVertex(vertex, last, -0.5F,  0.5F, 0.0F, 0.0F, 0.0F, light);
        } finally {
            pose.popPose();
        }
    }

    private static void emitIconVertex(VertexConsumer vertex,
                                       PoseStack.Pose pose,
                                       float x,
                                       float y,
                                       float z,
                                       float u,
                                       float v,
                                       int light) {
        vertex.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private static Bounds calculateBounds(YellowGltfModel model) {
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
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))
        );
    }

    private record Bounds(float centerX, float centerY, float centerZ, float maxExtent) {
    }
}
