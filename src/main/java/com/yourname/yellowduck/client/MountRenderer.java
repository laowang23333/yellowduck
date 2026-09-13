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
            new ResourceLocation("yellowduck", "mount");

    private static final float MODEL_SCALE = 0.12F;

    public MountRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .build());
    }

    @Override
    public void render(MountEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        AnimationController ctrl = GltfEntityRenderer.getAnimationController(entity);
        if (ctrl != null) {
            String wantAnim;
            if (entity.isVehicle()) {
                // 被骑乘：检查是否在移动
                double speedSqr = entity.getDeltaMovement().horizontalDistanceSqr();
                if (speedSqr > 0.001D) {
                    wantAnim = "Anim-1_walk";   // 走路
                } else {
                    wantAnim = "Anim-1_ride";   // 骑乘待机
                }
            } else {
                wantAnim = "Anim-1_stand";      // 平时待机
            }
            String current = ctrl.getAnimationName();
            if (current == null || !current.equals(wantAnim)) {
                ctrl.play(wantAnim);
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        event.registerEntityRenderer(entityType, MountRenderer::new);
    }
}
