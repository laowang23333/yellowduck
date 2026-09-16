package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.MountEntity;
import dev.phe.polymesh.animation.AnimationController;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class MountRenderer extends GltfEntityRenderer<MountEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "ghost_wolf_mount_final_v2");
    private static final float MODEL_SCALE = 0.16F;

    public MountRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build());
    }

    @Override
    public void render(MountEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            AnimationController controller = this.getAnimationController(entity);
            if (controller != null) {
                boolean walking = entity.getEntityData().get(MountEntity.IS_WALKING);
                String wanted = walking ? "run" : "idle";
                if (!wanted.equals(controller.getAnimationName())) {
                    controller.play(wanted, true);
                }
            }
        } catch (Throwable ignored) {
            // 动画异常不影响模型主体渲染。
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                 EntityType<? extends MountEntity> type) {
        event.registerEntityRenderer(type, MountRenderer::new);
    }
}
