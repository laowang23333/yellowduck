package com.yourname.yellowduck.silk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yourname.yellowduck.client.ClientEntityMotionState;
import dev.phe.polymesh.animation.AnimationController;
import dev.phe.polymesh.api.GltfRenderOptions;
import dev.phe.polymesh.client.GltfEntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Slime;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "yellowduck", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilkClient {
    private static final ResourceLocation BAT_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/bat.png");
    private static final ResourceLocation SLIME_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/red_slime.png");
    private static final ResourceLocation BALL_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/stone_ball_black.png");
    private static final ResourceLocation WATER_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/pool_black.png");
    private static final ResourceLocation CIRCLE_PURPLE =
            new ResourceLocation("yellowduck", "textures/entity/silk/skill_circle_10_purple.png");
    private static final ResourceLocation CIRCLE_RED =
            new ResourceLocation("yellowduck", "textures/entity/silk/skill_circle_10_red.png");
    private static final ResourceLocation PILLAR_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/fire_pillar_billboard.png");
    private static final ResourceLocation FIRE_ORB_TEXTURE =
            new ResourceLocation("yellowduck", "textures/entity/silk/buff_ball_billboard.png");

    private SilkClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SilkContent.BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(SilkContent.BAT.get(), SilkBatRenderer::new);
        event.registerEntityRenderer(SilkContent.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(SilkContent.PLAGUE_BEAR.get(), PlagueBearRenderer::new);
        event.registerEntityRenderer(SilkContent.DARK_TEDDY.get(), DarkTeddyRenderer::new);
        event.registerEntityRenderer(SilkContent.DARK_SLIME.get(), DarkSlimeRenderer::new);
        event.registerEntityRenderer(SilkContent.BLACK_WATER.get(), BlackWaterRenderer::new);
        event.registerEntityRenderer(SilkContent.BLACK_BALL.get(), BlackBallRenderer::new);
        event.registerEntityRenderer(SilkContent.VISUAL_CIRCLE.get(), VisualCircleRenderer::new);
    }

    public static final class BossRenderer extends GltfEntityRenderer<SilkBoss> {
        private static final int MOVE_GRACE_TICKS = 4;
        private final Map<SilkBoss, Integer> serials = new WeakHashMap<>();

        public BossRenderer(EntityRendererProvider.Context context) {
            super(context, new ResourceLocation("yellowduck", "silk_boss_embedded"),
                    GltfRenderOptions.builder()
                            .scale(0.15F)
                            .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                            .preferGpuAnimatedMeshes(false)
                            .preferGpuStaticMeshes(false)
                            .loopAnimation(true)
                            .build());
        }

        @Override
        public void render(SilkBoss boss, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            AnimationController controller = getAnimationController(boss);
            if (controller != null) {
                int attack = boss.getEntityData().get(SilkBoss.ANIMATION);
                int serial = boss.getEntityData().get(SilkBoss.CAST_SERIAL);
                boolean moving = ClientEntityMotionState.isMoving(
                        boss, boss.getEntityData().get(SilkBoss.WALKING), MOVE_GRACE_TICKS);

                String animation = !boss.isAlive() || attack < 0 ? "Anim-1_death"
                        : attack > 0 ? "Anim-1_attack_0" + attack
                        : moving ? "Anim-1_walk"
                        : boss.getEntityData().get(SilkBoss.MAD) ? "Anim-1_stand2"
                        : "Anim-1_stand";

                boolean newCast = attack != 0
                        && serials.getOrDefault(boss, Integer.MIN_VALUE) != serial;
                if (!animation.equals(controller.getAnimationName()) || newCast) {
                    controller.play(animation, attack == 0 && boss.isAlive());
                    serials.put(boss, serial);
                }
            }
            super.render(boss, yaw, partialTick, pose, buffers, light);
        }
    }

    /** 保留旧实体渲染，兼容已经存在于世界中的 silk_plague_bear。 */
    public static final class PlagueBearRenderer extends GltfEntityRenderer<SilkPlagueBear> {
        public PlagueBearRenderer(EntityRendererProvider.Context context) {
            super(context, new ResourceLocation("yellowduck", "entity_toy_bear"), bearOptions());
        }

        @Override
        public void render(SilkPlagueBear bear, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            playSingleBearAnimation(this, bear);
            super.render(bear, yaw, partialTick, pose, buffers, light);
        }
    }

    /** 黑暗泰迪暂复用现有布偶熊 GLB，但使用 GLB 里真实存在的四套动画。 */
    public static final class DarkTeddyRenderer extends GltfEntityRenderer<SilkDarkTeddy> {
        private final Map<SilkDarkTeddy, Integer> actionSerials = new WeakHashMap<>();

        public DarkTeddyRenderer(EntityRendererProvider.Context context) {
            super(context, new ResourceLocation("yellowduck", "entity_toy_bear"), darkBearOptions());
        }

        @Override
        public void render(SilkDarkTeddy teddy, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            try {
                AnimationController controller = getAnimationController(teddy);
                if (controller != null) {
                    int action = teddy.getEntityData().get(SilkDarkTeddy.ACTION);
                    int serial = teddy.getEntityData().get(SilkDarkTeddy.ACTION_SERIAL);
                    boolean moving = teddy.getDeltaMovement().horizontalDistanceSqr() > 0.0004D;

                    String animation = !teddy.isAlive() ? "ToyBearDeath"
                            : action != 0 ? "ToyBearAttack"
                            : moving ? "ToyBearWalk"
                            : "ToyBearIdle";
                    boolean restart = action != 0
                            && actionSerials.getOrDefault(teddy, Integer.MIN_VALUE) != serial;
                    boolean loop = teddy.isAlive() && action == 0;
                    if (restart || !animation.equals(controller.getAnimationName())) {
                        controller.play(animation, loop);
                        actionSerials.put(teddy, serial);
                    }
                }
            } catch (Throwable ignored) {
            }
            super.render(teddy, yaw, partialTick, pose, buffers, light);
        }
    }

    private static GltfRenderOptions bearOptions() {
        return GltfRenderOptions.builder()
                .scale(0.15F)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build();
    }

    private static GltfRenderOptions darkBearOptions() {
        return GltfRenderOptions.builder()
                .scale(0.15F)
                .tint(0xFF555565)
                .shaderCompatMode(GltfRenderOptions.ShaderCompatMode.FORCE_CPU)
                .preferGpuAnimatedMeshes(false)
                .preferGpuStaticMeshes(false)
                .loopAnimation(true)
                .build();
    }

    private static <T extends net.minecraft.world.entity.Entity> void playSingleBearAnimation(
            GltfEntityRenderer<T> renderer, T entity) {
        try {
            AnimationController controller = renderer.getAnimationController(entity);
            if (controller != null && !"ToyBearIdle".equals(controller.getAnimationName())) {
                controller.play("ToyBearIdle", true);
            }
        } catch (Throwable ignored) {
        }
    }

    public static final class SilkBatRenderer extends BatRenderer {
        public SilkBatRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Bat bat) {
            return BAT_TEXTURE;
        }
    }

    public static final class DarkSlimeRenderer extends SlimeRenderer {
        public DarkSlimeRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Slime slime) {
            return SLIME_TEXTURE;
        }
    }

    public static final class MeteorRenderer extends EntityRenderer<SilkMeteor> {
        public MeteorRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.7F;
        }

        @Override
        public ResourceLocation getTextureLocation(SilkMeteor entity) {
            return BALL_TEXTURE;
        }

        @Override
        public void render(SilkMeteor entity, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 6.0F));
            renderCrossedSprite(pose, buffers, LightTexture.FULL_BRIGHT, BALL_TEXTURE, 1.25F, 255);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }

    public static final class BlackBallRenderer extends EntityRenderer<SilkBlackBall> {
        public BlackBallRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.45F;
        }

        @Override
        public ResourceLocation getTextureLocation(SilkBlackBall entity) {
            return BALL_TEXTURE;
        }

        @Override
        public void render(SilkBlackBall entity, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0.0D, 0.45D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 4.0F));
            renderCrossedSprite(pose, buffers, LightTexture.FULL_BRIGHT, BALL_TEXTURE, 0.85F, 255);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }

    public static final class BlackWaterRenderer extends EntityRenderer<SilkBlackWater> {
        public BlackWaterRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public ResourceLocation getTextureLocation(SilkBlackWater entity) {
            return WATER_TEXTURE;
        }

        @Override
        public void render(SilkBlackWater entity, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0.0D, 0.025D, 0.0D);
            renderGroundQuad(pose, buffers, LightTexture.FULL_BRIGHT, WATER_TEXTURE, (float) SilkBalance.BLACK_WATER_RADIUS, 225);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }

    public static final class VisualCircleRenderer extends EntityRenderer<SilkVisualCircle> {
        public VisualCircleRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public ResourceLocation getTextureLocation(SilkVisualCircle entity) {
            return switch (entity.style()) {
                case SilkVisualCircle.RED_FIRE_RAIN -> CIRCLE_RED;
                case SilkVisualCircle.HEART_PILLAR -> PILLAR_TEXTURE;
                case SilkVisualCircle.FIRE_ORB -> FIRE_ORB_TEXTURE;
                default -> CIRCLE_PURPLE;
            };
        }

        @Override
        public void render(SilkVisualCircle entity, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            ResourceLocation texture = getTextureLocation(entity);
            pose.pushPose();
            if (entity.style() == SilkVisualCircle.HEART_PILLAR) {
                pose.translate(0.0D, 0.03D, 0.0D);
                renderCrossedRect(pose, buffers, LightTexture.FULL_BRIGHT, texture, 1.15F, 5.2F, 235);
            } else if (entity.style() == SilkVisualCircle.FIRE_ORB) {
                pose.translate(0.0D, 0.85D, 0.0D);
                pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 3.0F));
                renderCrossedSprite(pose, buffers, LightTexture.FULL_BRIGHT, texture, 0.65F, 245);
            } else {
                pose.translate(0.0D, 0.03D, 0.0D);
                renderGroundQuad(pose, buffers, LightTexture.FULL_BRIGHT, texture, 5.0F, 210);
            }
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }

    private static void renderGroundQuad(PoseStack pose, MultiBufferSource buffers, int light,
                                         ResourceLocation texture, float halfSize, int alpha) {
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        vertex.vertex(matrix, -halfSize, 0.0F, -halfSize).color(255, 255, 255, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertex.vertex(matrix, -halfSize, 0.0F, halfSize).color(255, 255, 255, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertex.vertex(matrix, halfSize, 0.0F, halfSize).color(255, 255, 255, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertex.vertex(matrix, halfSize, 0.0F, -halfSize).color(255, 255, 255, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void renderCrossedSprite(PoseStack pose, MultiBufferSource buffers, int light,
                                            ResourceLocation texture, float halfSize, int alpha) {
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            renderVerticalQuad(pose, buffers, light, texture, halfSize, alpha);
            pose.popPose();
        }
    }


    private static void renderCrossedRect(PoseStack pose, MultiBufferSource buffers, int light,
                                          ResourceLocation texture, float halfWidth, float height, int alpha) {
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            renderVerticalRect(pose, buffers, light, texture, halfWidth, height, alpha);
            pose.popPose();
        }
    }

    private static void renderVerticalRect(PoseStack pose, MultiBufferSource buffers, int light,
                                           ResourceLocation texture, float halfWidth, float height, int alpha) {
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        vertex.vertex(matrix, -halfWidth, 0.0F, 0.0F).color(255, 255, 255, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, halfWidth, 0.0F, 0.0F).color(255, 255, 255, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, halfWidth, height, 0.0F).color(255, 255, 255, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, -halfWidth, height, 0.0F).color(255, 255, 255, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }
    private static void renderVerticalQuad(PoseStack pose, MultiBufferSource buffers, int light,
                                           ResourceLocation texture, float halfSize, int alpha) {
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        float bottom = -halfSize;
        float top = halfSize;
        vertex.vertex(matrix, -halfSize, bottom, 0.0F).color(255, 255, 255, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, halfSize, bottom, 0.0F).color(255, 255, 255, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, halfSize, top, 0.0F).color(255, 255, 255, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, -halfSize, top, 0.0F).color(255, 255, 255, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
