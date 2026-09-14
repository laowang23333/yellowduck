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
                .loopAnimation(true)
                .build());
    }

    @Override
    public void render(MountEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            AnimationController ctrl = this.getAnimationController(entity);
            if (ctrl != null) {
                // 【改】从同步数据读 walking 状态，而不是本地 getDeltaMovement()
                boolean walking = entity.getEntityData().get(MountEntity.IS_WALKING);

                String wantAnim;
                if (walking) {
                    wantAnim = "Anim-1_walk";
                } else {
                    wantAnim = "Anim-1_stand";
                }
                String current = ctrl.getAnimationName();
                if (current == null || !current.equals(wantAnim)) {
                    ctrl.play(wantAnim, true);
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
