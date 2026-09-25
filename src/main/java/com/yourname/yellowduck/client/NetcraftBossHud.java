package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.garmr.GarmrBoss;
import com.yourname.yellowduck.tengu.TenguBoss;
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

/**
 * 所有继承 NetcraftBossBase 的 Boss 共用的 NetCraft 风格 HUD。
 * 天狗按需求只显示世界空间血条，因此这里排除。
 */
public final class NetcraftBossHud {
    private static final ResourceLocation BLOOD_BG =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_bg.png");
    private static final ResourceLocation BLOOD_GREEN =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_green.png");
    private static final ResourceLocation BLOOD_YELLOW =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_yellow.png");
    private static final ResourceLocation BLOOD_RED =
            new ResourceLocation("yellowduck", "textures/gui/boss_blood_red.png");
    private static final ResourceLocation BOSS_ICON_ATLAS =
            new ResourceLocation("yellowduck", "textures/gui/boss_map_icon.png");

    private static final int ICON_ATLAS_W = 1368;
    private static final int ICON_ATLAS_H = 1012;

    // boss_map_icon.png 第一排第 4 个头像。
    private static final int GARMR_ICON_U = 396;
    private static final int GARMR_ICON_V = 2;
    private static final int GARMR_ICON_W = 106;
    private static final int GARMR_ICON_H = 95;

    private NetcraftBossHud() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        AABB searchBox = mc.player.getBoundingBox().inflate(64.0D);
        List<NetcraftBossBase> bosses = mc.level.getEntitiesOfClass(
                NetcraftBossBase.class,
                searchBox,
                e -> e.isAlive() && !e.isRemoved() && !(e instanceof TenguBoss)
        );
        if (bosses.isEmpty()) return;

        bosses.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (bosses.size() > 3) bosses = bosses.subList(0, 3);

        RenderSystem.disableScissor();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int count = bosses.size();

        for (int i = 0; i < count; i++) {
            renderBoss(graphics, mc, bosses.get(i), screenWidth, i, count);
        }
    }

    private static void renderBoss(
            GuiGraphics graphics,
            Minecraft mc,
            NetcraftBossBase boss,
            int screenWidth,
            int index,
            int bossCount
    ) {
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
            drawFilledThreeSliceBar(
                    graphics,
                    chooseBloodTexture(healthRatio),
                    barX,
                    barY,
                    barWidth,
                    barHeight,
                    leftCap,
                    rightCap,
                    healthRatio
            );
        }

        float iconWidth = 25.44F * screenScale * groupScale;
        float iconHeight = 22.8F * screenScale * groupScale;
        float iconX = barX - 26.235F * screenScale * groupScale;
        float iconY = barY - 7.6F * screenScale;
        drawBossIcon(graphics, boss, iconX, iconY, iconWidth, iconHeight);

        String percent = String.format(Locale.ROOT, "%.1f%%", healthRatio * 100.0F);
        float textScale = 0.7F * screenScale;
        drawCenteredScaledText(
                graphics,
                mc,
                percent,
                barX + barWidth / 2.0F,
                barY + barHeight / 2.0F,
                textScale
        );
    }

    private static ResourceLocation chooseBloodTexture(float healthRatio) {
        if (healthRatio >= 0.80F) return BLOOD_GREEN;
        if (healthRatio < 0.50F) return BLOOD_RED;
        return BLOOD_YELLOW;
    }

    private static void drawThreeSliceBar(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            float leftCap,
            float rightCap
    ) {
        float middleWidth = width - leftCap - rightCap;
        drawTexturedQuad(graphics, texture, x, y, leftCap, height, 0.0F, 0.0F, 0.16F, 1.0F);
        drawTexturedQuad(graphics, texture, x + leftCap, y, middleWidth, height, 0.20F, 0.0F, 0.76F, 1.0F);
        drawTexturedQuad(graphics, texture, x + leftCap + middleWidth, y, rightCap, height, 0.80F, 0.0F, 1.0F, 1.0F);
    }

    private static void drawFilledThreeSliceBar(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            float leftCap,
            float rightCap,
            float ratio
    ) {
        float fillWidth = width * ratio;
        if (fillWidth <= 0.001F) return;

        float safeFillWidth = Math.max(1.0F, fillWidth);
        float drawnRightCap = Math.min(rightCap, safeFillWidth);
        float rightX = x + safeFillWidth - drawnRightCap;

        float rightU0 = 0.80F;
        if (drawnRightCap < rightCap && rightCap > 0.0F) {
            float missing = 1.0F - drawnRightCap / rightCap;
            rightU0 = 0.80F + 0.20F * missing;
        }

        float availableBeforeRight = Math.max(0.0F, rightX - x);
        float drawnLeftCap = Math.min(leftCap, availableBeforeRight);
        if (drawnLeftCap > 0.001F) {
            float leftU1 = 0.16F * (drawnLeftCap / leftCap);
            drawTexturedQuad(graphics, texture, x, y, drawnLeftCap, height, 0.0F, 0.0F, leftU1, 1.0F);
        }

        float middleX = x + drawnLeftCap;
        float middleWidth = rightX - middleX;
        if (middleWidth > 0.001F) {
            drawTexturedQuad(graphics, texture, middleX, y, middleWidth, height, 0.20F, 0.0F, 0.76F, 1.0F);
        }

        drawTexturedQuad(graphics, texture, rightX, y, drawnRightCap, height, rightU0, 0.0F, 1.0F, 1.0F);
    }

    private static void drawBossIcon(
            GuiGraphics graphics,
            NetcraftBossBase boss,
            float x,
            float y,
            float width,
            float height
    ) {
        int iconU = boss instanceof GarmrBoss ? GARMR_ICON_U : boss.getIconAtlasU();
        int iconV = boss instanceof GarmrBoss ? GARMR_ICON_V : boss.getIconAtlasV();
        int iconW = boss instanceof GarmrBoss ? GARMR_ICON_W : boss.getIconWidth();
        int iconH = boss instanceof GarmrBoss ? GARMR_ICON_H : boss.getIconHeight();

        float u0 = iconU / (float) ICON_ATLAS_W;
        float v0 = iconV / (float) ICON_ATLAS_H;
        float u1 = (iconU + iconW) / (float) ICON_ATLAS_W;
        float v1 = (iconV + iconH) / (float) ICON_ATLAS_H;
        drawTexturedQuad(graphics, BOSS_ICON_ATLAS, x, y, width, height, u0, v0, u1, v1);
    }

    private static void drawTexturedQuad(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1
    ) {
        if (width <= 0.0F || height <= 0.0F) return;

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y, 0.0F).uv(u0, v0).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0F).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0F).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0F).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawCenteredScaledText(
            GuiGraphics graphics,
            Minecraft mc,
            String text,
            float centerX,
            float centerY,
            float scale
    ) {
        if (scale <= 0.0F) return;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY - 4.0F * scale, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFF, true);
        pose.popPose();
    }
}
