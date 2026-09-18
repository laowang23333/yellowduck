package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.AlpacaMountEntity;
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
public class AlpacaMountRenderer extends GltfEntityRenderer<AlpacaMountEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "alpaca_embedded");
    private static final float MODEL_SCALE = 0.12F;

    public AlpacaMountRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build());
    }

    @Override
    public void render(AlpacaMountEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            AnimationController controller = this.getAnimationController(entity);
            if (controller != null) {
                boolean walking = MountAnimationState.isWalking(entity);

                // 羊驼动画：
                // 未骑乘 / 骑乘静止 -> Anim-1_stand（待机）
                // 骑乘移动 -> Anim-1_ride（跑步）
                String wanted = walking ? "Anim-1_ride" : "Anim-1_stand";
                if (!wanted.equals(controller.getAnimationName())) {
                    controller.play(wanted, true);
                }
            }
        } catch (Throwable ignored) {
            // 动画异常不影响模型主体渲染。
        }

        poseStack.pushPose();
        try {
            // GLB 的默认朝向与 Minecraft 实体朝向一致；如模型看起来前后反了，
            // 只需把这里改成 180 度即可，不影响坐骑移动和乘客位置。
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                 EntityType<? extends AlpacaMountEntity> type) {
        event.registerEntityRenderer(type, AlpacaMountRenderer::new);
    }
}
