package com.yourname.yellowduck.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.yellowduck.entity.SakurawitchEntity;
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
public class SakurawitchRenderer extends GltfEntityRenderer<SakurawitchEntity> {

    private static final ResourceLocation MODEL_ID =
            new ResourceLocation("yellowduck", "entity_boss_t2_sakurawitch");

    private static final float MODEL_SCALE = 0.15F;
    private static final int MOVE_GRACE_TICKS = 4;

    public SakurawitchRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, MODEL_ID, GltfRenderOptions.builder()
                .scale(MODEL_SCALE)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build());
    }

    @Override
    public void render(SakurawitchEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            AnimationController ctrl = this.getAnimationController(entity);
            if (ctrl != null) {
                String wantAnim;

                if (entity.getEntityData().get(SakurawitchEntity.IS_DYING)) {
                    wantAnim = "Anim-1_death";
                } else {
                    int idx = entity.getEntityData().get(SakurawitchEntity.ATTACK_INDEX);

                    if (idx > 0) {
                        // 攻击状态仍由服务端决定；客户端收到一次后让 PolyMesh 自己连续播放。
                        wantAnim = "Anim-1_attack_" + String.format("%02d", idx);
                    } else {
                        boolean walking = ClientEntityMotionState.isMoving(
                                entity,
                                entity.getEntityData().get(SakurawitchEntity.IS_WALKING),
                                MOVE_GRACE_TICKS
                        );
                        wantAnim = walking ? "Anim-1_walk" : "Anim-1_stand";
                    }
                }

                String current = ctrl.getAnimationName();
                if (current == null || !current.equals(wantAnim)) {
                    boolean loop = wantAnim.equals("Anim-1_walk")
                            || wantAnim.equals("Anim-1_stand")
                            || wantAnim.equals("Anim-1_run");

                    // 只在状态真正变化时切换。之后由客户端渲染帧连续推进动画时间。
                    ctrl.play(wantAnim, loop);
                }
            }
        } catch (Throwable ignored) {
            // 动画异常不影响模型主体渲染。
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event,
                                EntityType<? extends SakurawitchEntity> type) {
        event.registerEntityRenderer(type, SakurawitchRenderer::new);
    }
}
