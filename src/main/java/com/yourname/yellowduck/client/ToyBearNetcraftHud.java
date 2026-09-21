package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
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

/** Stargazer 原 bear.png 头像 + YellowDuck 已有 NetCraft 血条素材。 */
public final class ToyBearNetcraftHud {
    private static final ResourceLocation BLOOD_BG =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_bg.png");
    private static final ResourceLocation BLOOD_GREEN =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_green.png");
    private static final ResourceLocation BLOOD_YELLOW =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_yellow.png");
    private static final ResourceLocation BLOOD_RED =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_red.png");
    private static final ResourceLocation BEAR_ICON =
            new ResourceLocation("yellowduck", "textures/gui/boss_head/bear.png");

    private ToyBearNetcraftHud() {}

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        AABB box = mc.player.getBoundingBox().inflate(64.0D);
        List<ToyBearEntity> bears = mc.level.getEntitiesOfClass(
                ToyBearEntity.class,
                box,
                e -> !e.isRemoved() && (e.isAlive() || e.getEntityData().get(ToyBearEntity.DYING))
        );
        if (bears.isEmpty()) return;

        bears.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (bears.size() > 3) bears = bears.subList(0, 3);

        boolean sakuraVisible = !mc.level.getEntitiesOfClass(
                SakurawitchEntity.class,
                box,
                e -> !e.isRemoved() && (e.isAlive() || e.getEntityData().get(SakurawitchEntity.IS_DYING))
        ).isEmpty();

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenScale = screenWidth / 427.0F;
        float baseY = (10.24F * screenScale * 2.0F)
                + (sakuraVisible ? 18.0F * screenScale : 0.0F);

        int count = bears.size();
        for (int i = 0; i < count; i++) {
            renderBear(graphics, mc, bears.get(i), screenWidth, i, count, baseY);
        }
    }

    private static void renderBear(
            GuiGraphics graphics,
            Minecraft mc,
            ToyBearEntity bear,
            int screenWidth,
            int index,
            int count,
            float barY
    ) {
        float ratio = bear.getMaxHealth() <= 0.0F ? 0.0F : bear.getHealth() / bear.getMaxHealth();
        ratio = Math.max(0.0F, Math.min(1.0F, ratio));

        float screenScale = screenWidth / 427.0F;
        float groupScale = count == 1 ? 1.2F : count == 2 ? 1.0F : 0.8F;
        float barWidth = 112.0F * screenScale * groupScale;
        float barHeight = 10.24F * screenScale;
        float slotWidth = (float) screenWidth / count;
        float barX = slotWidth * index + (slotWidth - barWidth) / 2.0F;

        drawQuad(graphics, BLOOD_BG, barX, barY, barWidth, barHeight, 0, 0, 1, 1);
        if (ratio > 0.0F) {
            ResourceLocation fill = ratio >= 0.80F ? BLOOD_GREEN : ratio > 0.50F ? BLOOD_YELLOW : BLOOD_RED;
            drawQuad(graphics, fill, barX, barY, barWidth * ratio, barHeight, 0, 0, ratio, 1);
        }

        float iconW = 24.0F * screenScale * groupScale;
        float iconH = 21.5F * screenScale * groupScale;
        drawQuad(graphics, BEAR_ICON,
                barX - 24.7F * screenScale * groupScale,
                barY - 6.5F * screenScale,
                iconW, iconH, 0, 0, 1, 1);

        String text = String.format(Locale.ROOT, "%.1f%%", ratio * 100.0F);
        graphics.pose().pushPose();
        float textScale = 0.70F * screenScale;
        graphics.pose().translate(barX + barWidth / 2.0F, barY + barHeight / 2.0F - 4.0F * textScale, 0);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private static void drawQuad(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x, float y, float width, float height,
            float u0, float v0, float u1, float v1
    ) {
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
}
