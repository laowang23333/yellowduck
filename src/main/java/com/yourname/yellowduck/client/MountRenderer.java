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
        try {
            AnimationController ctrl = this.getAnimationController(entity);
            if (ctrl != null) {
                // 统一按实体自身速度判断：走路 / 站立
                double speedSqr = entity.getDeltaMovement().horizontalDistanceSqr();
                String wantAnim;
                if (speedSqr > 0.001D) {
                    wantAnim = "Anim-1_walk";   // 移动中（无论是否被骑）
                } else {
                    wantAnim = "Anim-1_stand";  // 静止（骑乘待机也用它）
                }
                String current = ctrl.getAnimationName();
                if (current == null || !current.equals(wantAnim)) {
                    ctrl.play(wantAnim);
                }
            }
        } catch (Throwable t) {
            // 动画出错不影响模型渲染
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends MountEntity> entityType) {
        event.registerEntityRenderer(entityType, MountRenderer::new);
    }
}
