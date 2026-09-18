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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class MountRenderer extends GltfEntityRenderer<MountEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "demon_tengu_mount");
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
                if (entity.getControllingPassenger() instanceof Player player) {
                    walking = Math.abs(player.xxa) > 0.01F
                            || Math.abs(player.zza) > 0.01F
                            || entity.getDeltaMovement().horizontalDistanceSqr() > 0.00001D;
                }
                String wanted = walking ? "run" : "idle";
                if (!wanted.equals(controller.getAnimationName())) {
                    controller.play(wanted, true);
                }
            }
        } catch (Throwable ignored) {
            // 动画异常不影响模型主体渲染。
        }
        // Polymesh 会按整个 GLB 的包围盒居中。这个模型的原始骨架原点偏向尾部，
        // 所以需要把模型沿自身前进方向后移约 2.3 格，让身体/碰撞箱重合。
        double yaw = Math.toRadians(entityYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        poseStack.pushPose();
        poseStack.translate(-2.30D * forwardX, 0.0D, -2.30D * forwardZ);

        // GUI 预览共用真实 MountRenderer，但 GLB 的原始包围盒远大于实体碰撞箱，
        // 不缩小的话 InventoryScreen 的镜头会被模型高度/尾巴撑爆，默认只剩脚。
        if (entity.isGuiPreview()) {
            poseStack.scale(0.50F, 0.50F, 0.50F);
            poseStack.translate(0.0D, 0.18D, 0.0D);
        }

        try {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                 EntityType<? extends MountEntity> type) {
        event.registerEntityRenderer(type, MountRenderer::new);
    }
}
