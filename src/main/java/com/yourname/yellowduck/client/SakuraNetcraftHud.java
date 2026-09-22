package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SakuraNetcraftHud {
    private static final ResourceLocation BLOOD_BG =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_bg.png");
    private static final ResourceLocation BLOOD_GREEN =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_green.png");
    private static final ResourceLocation BLOOD_YELLOW =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_yellow.png");
    private static final ResourceLocation BLOOD_RED =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_red.png");
    private static final ResourceLocation SAKURA_HEAD =
            new ResourceLocation("yellowduck", "textures/gui/boss_head/sakura.png");
    private static final ResourceLocation SAKURA_FLAME_CHARGE =
            new ResourceLocation("yellowduck", "textures/mob_effect/sakura_flame_charge.png");

    private SakuraNetcraftHud() {}

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        AABB searchBox = mc.player.getBoundingBox().inflate(64.0D);
        List<SakurawitchEntity> bosses = mc.level.getEntitiesOfClass(
                SakurawitchEntity.class, searchBox, e -> e.isAlive() && !e.isRemoved());
        if (bosses.isEmpty()) return;

        bosses.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (bosses.size() > 3) bosses = bosses.subList(0, 3);

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int count = bosses.size();

        for (int i = 0; i < count; i++) {
            renderBoss(graphics, mc, bosses.get(i), screenWidth, i, count);
        }
    }

    private static void renderBoss(GuiGraphics graphics, Minecraft mc, SakurawitchEntity boss,
                                   int screenWidth, int index, int bossCount) {
        float healthRatio = boss.getMaxHealth() <= 0.0F ? 0.0F : boss.getHealth() / boss.getMaxHealth();
        healthRatio = Math.max(0.0F, Math.min(1.0F, healthRatio));

        float screenScale = screenWidth / 427.0F;
        float groupScale = switch (bossCount) {
            case 1 -> 1.2F;
            case 2 -> 1.0F;
            case 3 -> 0.8F;
            default -> 1.0F;
        };

        float barWidth = 112.0F * screenScale * groupScale;
        float barHeight = 10.24F * screenScale;
        float leftCap = 0.64F * screenScale * groupScale;
        float rightCap = 0.64F * screenScale * groupScale;
        float slotWidth = (float) screenWidth / bossCount;
        float barX = slotWidth * index + (slotWidth - barWidth) / 2.0F;
        float barY = barHeight * 2.0F;

        drawThreeSliceBar(graphics, BLOOD_BG, barX, barY, barWidth, barHeight, leftCap, rightCap);
        if (healthRatio > 0.0F) {
            drawFilledThreeSliceBar(graphics, chooseBloodTexture(healthRatio),
                    barX, barY, barWidth, barHeight, leftCap, rightCap, healthRatio);
        }

        float iconWidth = 25.44F * screenScale * groupScale;
        float iconHeight = 22.8F * screenScale * groupScale;
        drawTexturedQuad(graphics, SAKURA_HEAD,
                barX - 26.235F * screenScale * groupScale,
                barY - 7.6F * screenScale,
                iconWidth, iconHeight, 0, 0, 1, 1);

        String percent = String.format(Locale.ROOT, "%.1f%%", healthRatio * 100.0F);
        drawCenteredScaledText(graphics, mc, percent,
                barX + barWidth / 2.0F, barY + barHeight / 2.0F, 0.7F * screenScale);

        int fireStacks = boss.getEntityData().get(SakurawitchEntity.FIRE_MARK_STACKS);
        if (fireStacks > 0) {
            float size = 12.0F * screenScale * groupScale;
            float x = barX + barWidth + 3.0F * screenScale;
            float y = barY - 1.0F * screenScale;
            drawTexturedQuad(graphics, SAKURA_FLAME_CHARGE, x, y, size, size, 0, 0, 1, 1);
            drawCenteredScaledText(graphics, mc, Integer.toString(fireStacks),
                    x + size + 3.5F * screenScale, y + size / 2.0F,
                    0.65F * screenScale * groupScale);
        }
    }

    private static ResourceLocation chooseBloodTexture(float ratio) {
        if (ratio >= 0.80F) return BLOOD_GREEN;
        if (ratio < 0.50F) return BLOOD_RED;
        return BLOOD_YELLOW;
    }

    private static void drawThreeSliceBar(GuiGraphics g, ResourceLocation tex,
                                          float x, float y, float w, float h,
                                          float left, float right) {
        float mid = w - left - right;
        drawTexturedQuad(g, tex, x, y, left, h, 0.0F, 0.0F, 0.16F, 1.0F);
        drawTexturedQuad(g, tex, x + left, y, mid, h, 0.20F, 0.0F, 0.76F, 1.0F);
        drawTexturedQuad(g, tex, x + left + mid, y, right, h, 0.80F, 0.0F, 1.0F, 1.0F);
    }

    private static void drawFilledThreeSliceBar(GuiGraphics g, ResourceLocation tex,
                                                float x, float y, float w, float h,
                                                float left, float right, float ratio) {
        float fill = w * ratio;
        if (fill <= 0.001F) return;

        float safe = Math.max(1.0F, fill);
        float drawnRight = Math.min(right, safe);
        float rightX = x + safe - drawnRight;
        float rightU0 = 0.80F;
        if (drawnRight < right && right > 0.0F) {
            rightU0 = 0.80F + 0.20F * (1.0F - drawnRight / right);
        }

        float beforeRight = Math.max(0.0F, rightX - x);
        float drawnLeft = Math.min(left, beforeRight);
        if (drawnLeft > 0.001F) {
            float leftU1 = 0.16F * (drawnLeft / left);
            drawTexturedQuad(g, tex, x, y, drawnLeft, h, 0.0F, 0.0F, leftU1, 1.0F);
        }

        float midX = x + drawnLeft;
        float midW = rightX - midX;
        if (midW > 0.001F) {
            drawTexturedQuad(g, tex, midX, y, midW, h, 0.20F, 0.0F, 0.76F, 1.0F);
        }

        drawTexturedQuad(g, tex, rightX, y, drawnRight, h, rightU0, 0.0F, 1.0F, 1.0F);
    }

    private static void drawTexturedQuad(GuiGraphics graphics, ResourceLocation texture,
                                         float x, float y, float width, float height,
                                         float u0, float v0, float u1, float v1) {
        if (width <= 0 || height <= 0) return;

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(matrix, x, y, 0).uv(u0, v0).endVertex();
        b.vertex(matrix, x, y + height, 0).uv(u0, v1).endVertex();
        b.vertex(matrix, x + width, y + height, 0).uv(u1, v1).endVertex();
        b.vertex(matrix, x + width, y, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(b.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawCenteredScaledText(GuiGraphics g, Minecraft mc, String text,
                                               float centerX, float centerY, float scale) {
        if (scale <= 0) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY - 4.0F * scale, 0);
        pose.scale(scale, scale, 1);
        g.drawString(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFF, true);
        pose.popPose();
    }
}
