package com.yourname.yellowduck.tengu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.client.ClientEntityMotionState;
import com.yourname.yellowduck.client.gltf.YellowNativeEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Locale;

/** 天狗三种实体的 YellowDuck Native GLTF 渲染。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TenguClient {
    private static final ResourceLocation MODEL =
            new ResourceLocation("yellowduck", "models/gltf/tengu_white.glb");
    private static final ResourceLocation BLOOD_BG =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_bg.png");
    private static final ResourceLocation BLOOD_GREEN =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_green.png");
    private static final ResourceLocation BLOOD_YELLOW =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_yellow.png");
    private static final ResourceLocation BLOOD_RED =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_red.png");

    private TenguClient() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TenguContent.BOSS.get(), TenguBossRenderer::new);
        event.registerEntityRenderer(TenguContent.WILD_MOUNT.get(), TenguWildRenderer::new);
        event.registerEntityRenderer(TenguContent.MOUNT.get(), TenguMountRenderer::new);
    }

    private static final class TenguBossRenderer extends YellowNativeEntityRenderer<TenguBoss> {
        TenguBossRenderer(EntityRendererProvider.Context context) {
            super(context, MODEL, 0.16F, 1.0F);
        }

        @Override
        protected AnimationSpec animationFor(TenguBoss entity) {
            if (entity.isAttackAnimating()) {
                return once("tengu_attack", entity.getAttackAnimationSerial());
            }

            boolean moving = ClientEntityMotionState.isMoving(entity, false, 6);
            return loop(moving ? "tengu_walk" : "tengu_idle");
        }

        @Override
        public void render(TenguBoss entity, float entityYaw, float partialTick,
                           PoseStack pose, MultiBufferSource buffers, int packedLight) {
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
            renderOverHeadHealth(entity, pose, buffers);
        }
    }

    private static final class TenguWildRenderer extends YellowNativeEntityRenderer<TenguWildMountEntity> {
        TenguWildRenderer(EntityRendererProvider.Context context) {
            super(context, MODEL, 0.12F, 0.8F);
        }

        @Override
        protected AnimationSpec animationFor(TenguWildMountEntity entity) {
            boolean moving = ClientEntityMotionState.isMoving(entity, false, 8);
            return loop(moving ? "tengu_walk" : "tengu_idle");
        }
    }

    private static final class TenguMountRenderer extends YellowNativeEntityRenderer<TenguMountEntity> {
        TenguMountRenderer(EntityRendererProvider.Context context) {
            super(context, MODEL, 0.12F, 0.8F);
        }

        @Override
        protected AnimationSpec animationFor(TenguMountEntity entity) {
            // 不再直接依赖 MountAnimationState，避免移动判定在网络同步边缘瞬间掉回 idle。
            boolean moving = ClientEntityMotionState.isMoving(entity, false, 10);
            return loop(moving ? "tengu_walk" : "tengu_idle");
        }

        @Override
        protected void beforeModelTransform(TenguMountEntity entity, float entityYaw,
                                            float partialTick, PoseStack pose) {
            if (entity.isGuiPreview()) {
                pose.scale(0.62F, 0.62F, 0.62F);
                pose.translate(0.0D, 0.20D, 0.0D);
            }
        }
    }

    private static void renderOverHeadHealth(TenguBoss boss, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        float ratio = boss.getMaxHealth() <= 0.0F ? 0.0F : boss.getHealth() / boss.getMaxHealth();
        ratio = Math.max(0.0F, Math.min(1.0F, ratio));

        pose.pushPose();
        pose.translate(0.0D, boss.getBbHeight() + 0.55D, 0.0D);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);

        final float width = 132.0F;
        final float height = 11.0F;
        final float leftCap = 2.0F;
        final float rightCap = 2.0F;
        final float x = -width / 2.0F;

        drawThreeSliceBar(pose, buffers, BLOOD_BG, x, 0.0F, width, height, leftCap, rightCap);
        if (ratio > 0.0F) {
            ResourceLocation fill = ratio >= 0.80F ? BLOOD_GREEN : ratio >= 0.50F ? BLOOD_YELLOW : BLOOD_RED;
            drawFilledThreeSliceBar(pose, buffers, fill, x, 0.0F, width, height, leftCap, rightCap, ratio);
        }

        Font font = mc.font;
        String name = "天狗";
        String hp = String.format(Locale.ROOT, "%.0f / %.0f", boss.getHealth(), boss.getMaxHealth());
        Matrix4f matrix = pose.last().pose();
        font.drawInBatch(name, -font.width(name) / 2.0F, -11.0F,
                0xFFFFFFFF, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(hp, -font.width(hp) / 2.0F, 1.0F,
                0xFFFFFFFF, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        pose.popPose();
    }

    private static void drawThreeSliceBar(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                                          float x, float y, float width, float height,
                                          float leftCap, float rightCap) {
        float middle = width - leftCap - rightCap;
        drawQuad(pose, buffers, texture, x, y, leftCap, height, 0.0F, 0.0F, 0.16F, 1.0F);
        drawQuad(pose, buffers, texture, x + leftCap, y, middle, height, 0.20F, 0.0F, 0.76F, 1.0F);
        drawQuad(pose, buffers, texture, x + leftCap + middle, y, rightCap, height, 0.80F, 0.0F, 1.0F, 1.0F);
    }

    private static void drawFilledThreeSliceBar(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                                                float x, float y, float width, float height,
                                                float leftCap, float rightCap, float ratio) {
        float fillWidth = width * ratio;
        if (fillWidth <= 0.001F) return;

        float safeFill = Math.max(1.0F, fillWidth);
        float drawnRight = Math.min(rightCap, safeFill);
        float rightX = x + safeFill - drawnRight;
        float rightU0 = 0.80F;
        if (drawnRight < rightCap && rightCap > 0.0F) {
            float missing = 1.0F - drawnRight / rightCap;
            rightU0 = 0.80F + 0.20F * missing;
        }

        float beforeRight = Math.max(0.0F, rightX - x);
        float drawnLeft = Math.min(leftCap, beforeRight);
        if (drawnLeft > 0.001F) {
            float leftU1 = 0.16F * (drawnLeft / leftCap);
            drawQuad(pose, buffers, texture, x, y, drawnLeft, height, 0.0F, 0.0F, leftU1, 1.0F);
        }

        float middleX = x + drawnLeft;
        float middleWidth = rightX - middleX;
        if (middleWidth > 0.001F) {
            drawQuad(pose, buffers, texture, middleX, y, middleWidth, height, 0.20F, 0.0F, 0.76F, 1.0F);
        }

        drawQuad(pose, buffers, texture, rightX, y, drawnRight, height, rightU0, 0.0F, 1.0F, 1.0F);
    }

    private static void drawQuad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                                 float x, float y, float width, float height,
                                 float u0, float v0, float u1, float v1) {
        if (width <= 0.0F || height <= 0.0F) return;

        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        int light = LightTexture.FULL_BRIGHT;

        vertex.vertex(matrix, x, y, 0.0F).color(255, 255, 255, 255)
                .uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, x, y + height, 0.0F).color(255, 255, 255, 255)
                .uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, x + width, y + height, 0.0F).color(255, 255, 255, 255)
                .uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertex.vertex(matrix, x + width, y, 0.0F).color(255, 255, 255, 255)
                .uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
