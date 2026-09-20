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
        super(ctx, new ResourceLocation("yellowduck", "bamboo_horse_embedded"), GltfRenderOptions.builder()
                .scale(0.12F).shaderCompatMode(GltfRenderOptions.ShaderCompatMode.AUTO)
                .preferGpuAnimatedMeshes(true).preferGpuStaticMeshes(true).loopAnimation(true).animationTransitionSeconds(0.10F).build());
    }
    @Override public void render(BambooHorseEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        AnimationController controller = getAnimationController(entity);
        if (controller != null) {
            boolean walking = MountAnimationState.isWalking(entity);
            String wanted = entity.isFlying() ? (walking ? "Anim-1_fly_ride" : "Anim-1_fly_stand")
                    : (entity.isVehicle() && walking ? "Anim-1_ride" : "Anim-1_stand");
            if (!wanted.equals(controller.getAnimationName())) controller.play(wanted, true, 0.10F);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
