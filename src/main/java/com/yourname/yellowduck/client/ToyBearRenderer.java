package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.ToyBearEntity;
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

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public class ToyBearRenderer extends GltfEntityRenderer<ToyBearEntity> {
    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "entity_toy_bear");

    private static final float MODEL_SCALE = 0.15F;
    private static final String IDLE_ANIMATION = "ToyBearIdle";
    private static final String WALK_ANIMATION = "ToyBearWalk";
    private static final String ATTACK_ANIMATION = "ToyBearAttack";
    private static final String DEATH_ANIMATION = "ToyBearDeath";

    private final Map<ToyBearEntity, Integer> attackSerials = new WeakHashMap<>();

    public ToyBearRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build());
    }

    @Override
    public void render(ToyBearEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            AnimationController ctrl = getAnimationController(entity);
            if (ctrl != null) {
                String animation;
                boolean loop;
                boolean forceRestart = false;

                if (entity.getEntityData().get(ToyBearEntity.DYING) || !entity.isAlive()) {
                    animation = DEATH_ANIMATION;
                    loop = false;
                } else if (entity.getEntityData().get(ToyBearEntity.ATTACKING)) {
                    animation = ATTACK_ANIMATION;
                    loop = false;
                    int serial = entity.getEntityData().get(ToyBearEntity.ATTACK_SERIAL);
                    forceRestart = attackSerials.getOrDefault(entity, -1) != serial;
                    attackSerials.put(entity, serial);
                } else if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.0004D) {
                    animation = WALK_ANIMATION;
                    loop = true;
                } else {
                    animation = IDLE_ANIMATION;
                    loop = true;
                }

                if (forceRestart || !animation.equals(ctrl.getAnimationName())) {
                    ctrl.play(animation, loop);
                }
            }
        } catch (Throwable ignored) {
            // 动画失败不影响模型渲染。
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends ToyBearEntity> type) {
        event.registerEntityRenderer(type, ToyBearRenderer::new);
    }
}
