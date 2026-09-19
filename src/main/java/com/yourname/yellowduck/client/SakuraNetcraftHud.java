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

/**
 * 魔女小樱的 NetCraft 风格 Boss HUD。
 *
 * 贴图直接来自 NetCraft 1.4.18：
 * - boss_blood_bg.png
 * - boss_blood_green.png
 * - boss_blood_yellow.png
 * - boss_blood_red.png
 * - boss_map_icon.png
 *
 * 小樱头像在 NetCraft 图集中的原始区域：
 * U=1083, V=631, 103x79，图集尺寸 1368x1012。
 */
public final class SakuraNetcraftHud {

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
    private static final int SAKURA_ICON_U = 1083;
    private static final int SAKURA_ICON_V = 631;
    private static final int SAKURA_ICON_W = 103;
    private static final int SAKURA_ICON_H = 79;

    private SakuraNetcraftHud() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        AABB searchBox = mc.player.getBoundingBox().inflate(64.0D);
        List<SakurawitchEntity> bosses = mc.level.getEntitiesOfClass(
                SakurawitchEntity.class,
                searchBox,
                e -> e.isAlive() && !e.isRemoved()
        );

        if (bosses.isEmpty()) {
            return;
        }

        // NetCraft 最多同时画 3 条 Boss 血条。
        bosses.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (bosses.size() > 3) {
            bosses = bosses.subList(0, 3);
        }

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
            SakurawitchEntity boss,
            int screenWidth,
            int index,
            int bossCount
    ) {
        float healthRatio = boss.getMaxHealth() <= 0.0F
                ? 0.0F
                : boss.getHealth() / boss.getMaxHealth();
        healthRatio = Math.max(0.0F, Math.min(1.0F, healthRatio));

        // 这些尺寸/位置系数按 NetCraft BossHealthBarRenderer 1.4.18 还原。
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

        ResourceLocation bloodTexture = chooseBloodTexture(healthRatio);
        if (healthRatio > 0.0F) {
            drawFilledThreeSliceBar(
                    graphics,
                    bloodTexture,
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
        drawSakuraIcon(graphics, iconX, iconY, iconWidth, iconHeight);

        String percent = String.format(Locale.ROOT, "%.1f%%", healthRatio * 100.0F);
        float textCenterX = barX + barWidth / 2.0F;
        float textCenterY = barY + barHeight / 2.0F;
        float textScale = 0.7F * screenScale;
        drawCenteredScaledText(graphics, mc, percent, textCenterX, textCenterY, textScale);
    }

    private static ResourceLocation chooseBloodTexture(float healthRatio) {
        if (healthRatio >= 0.80F) {
            return BLOOD_GREEN;
        }
        if (healthRatio < 0.50F) {
            return BLOOD_RED;
        }
        return BLOOD_YELLOW;
    }

    /** NetCraft 的完整三段式血条：左端 + 中间拉伸 + 右端。 */
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

        drawTexturedQuad(graphics, texture, x, y, leftCap, height, 0.0F, 0.16F);
        drawTexturedQuad(graphics, texture, x + leftCap, y, middleWidth, height, 0.20F, 0.76F);
        drawTexturedQuad(graphics, texture, x + leftCap + middleWidth, y, rightCap, height, 0.80F, 1.0F);
    }

    /**
     * NetCraft 的血量填充算法。右端帽会跟着当前血量位置移动，
     * 而不是简单把整张纹理横向压缩。
     */
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
        if (fillWidth <= 0.001F) {
            return;
        }

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
            drawTexturedQuad(graphics, texture, x, y, drawnLeftCap, height, 0.0F, leftU1);
        }

        float middleX = x + drawnLeftCap;
        float middleWidth = rightX - middleX;
        if (middleWidth > 0.001F) {
            drawTexturedQuad(graphics, texture, middleX, y, middleWidth, height, 0.20F, 0.76F);
        }

        drawTexturedQuad(graphics, texture, rightX, y, drawnRightCap, height, rightU0, 1.0F);
    }

    /**
     * 从 NetCraft 的 boss_map_icon.png 原图集裁出魔女小樱头像。
     */
    private static void drawSakuraIcon(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height
    ) {
        float u0 = SAKURA_ICON_U / (float) ICON_ATLAS_W;
        float v0 = SAKURA_ICON_V / (float) ICON_ATLAS_H;
        float u1 = (SAKURA_ICON_U + SAKURA_ICON_W) / (float) ICON_ATLAS_W;
        float v1 = (SAKURA_ICON_V + SAKURA_ICON_H) / (float) ICON_ATLAS_H;
        drawTexturedQuad(graphics, BOSS_ICON_ATLAS, x, y, width, height, u0, v0, u1, v1);
    }

    /**
     * 使用纹理的完整 V 轴，只裁 U 范围；用于 NetCraft 的 25x64 血条纹理。
     */
    private static void drawTexturedQuad(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            float u0,
            float u1
    ) {
        drawTexturedQuad(graphics, texture, x, y, width, height, u0, 0.0F, u1, 1.0F);
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
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

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
        if (scale <= 0.0F) {
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY - 4.0F * scale, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFF, true);
        pose.popPose();
    }
}
