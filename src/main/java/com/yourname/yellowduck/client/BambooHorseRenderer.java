package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.BambooHorseEntity;
import dev.phe.polymesh.animation.AnimationController;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class BambooHorseRenderer extends GltfEntityRenderer<BambooHorseEntity> {
    public BambooHorseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ResourceLocation("yellowduck", "bamboo_horse_embedded"),
                GltfRenderOptions.builder()
                        .scale(0.12F)
                        // PolyMesh 1.0.0 的 GPU 动画路径在部分 1.20.1 客户端会出现整模发黑。
                        // 坐骑恢复稳定的 CPU 兼容路径，优先保证材质/光照正确。
                        .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                        .preferGpuAnimatedMeshes(false)
                        .preferGpuStaticMeshes(false)
                        .loopAnimation(true)
                        .build());
    }

    @Override
    public void render(BambooHorseEntity entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        AnimationController controller = getAnimationController(entity);
        if (controller != null) {
            boolean walking = MountAnimationState.isWalking(entity);
            String wanted = entity.isFlying()
                    ? (walking ? "Anim-1_fly_ride" : "Anim-1_fly_stand")
                    : (entity.isVehicle() && walking ? "Anim-1_ride" : "Anim-1_stand");

            // 只在动画真的发生变化时切换，避免每个渲染帧重启动画。
            if (!wanted.equals(controller.getAnimationName())) {
                controller.play(wanted, true);
            }
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
