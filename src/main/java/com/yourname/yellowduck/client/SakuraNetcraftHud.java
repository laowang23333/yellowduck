package com.yourname.yellowduck.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.silk.SilkBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 斯尔克专属的 NetCraft 风格状态图标行。
 *
 * NetCraft 原版会在 Boss 血条下方绘制技能/Buff 图标。当前资源包没有携带
 * YYBuffImages.png 完整图集，所以这里使用已经提取到 YellowDuck 的斯尔克原技能素材，
 * 但状态、层数和倒计时全部由服务端同步，不再只是装饰图。
 */
public final class SilkBossStatusHud {
    private static final ResourceLocation FIRE =
            new ResourceLocation("yellowduck", "textures/particle/silk/fire_0.png");
    private static final ResourceLocation DARK_FIRE =
            new ResourceLocation("yellowduck", "textures/particle/silk/dark_fire_0.png");
    private static final ResourceLocation SOUL =
            new ResourceLocation("yellowduck", "textures/particle/silk/soul_0.png");
    private static final ResourceLocation BLACK_BALL =
            new ResourceLocation("yellowduck", "textures/entity/silk/stone_ball_black.png");
    private static final ResourceLocation FIRE_CIRCLE =
            new ResourceLocation("yellowduck", "textures/entity/silk/skill_circle_10_red.png");
    private static final ResourceLocation PILLAR =
            new ResourceLocation("yellowduck", "textures/entity/silk/fire_pillar_billboard.png");
    private static final ResourceLocation FIRE_ORB =
            new ResourceLocation("yellowduck", "textures/entity/silk/buff_ball_billboard.png");
    private static final ResourceLocation BLACK_WATER =
            new ResourceLocation("yellowduck", "textures/entity/silk/pool_black.png");

    private record StatusIcon(ResourceLocation texture, String text, int frameColor) {}

    private SilkBossStatusHud() {}

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        AABB searchBox = mc.player.getBoundingBox().inflate(64.0D);
        List<NetcraftBossBase> bosses = new ArrayList<>(mc.level.getEntitiesOfClass(
                NetcraftBossBase.class,
                searchBox,
                e -> e.isAlive() && !e.isRemoved()
        ));
        if (bosses.isEmpty()) return;

        bosses.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (bosses.size() > 3) bosses = new ArrayList<>(bosses.subList(0, 3));

        for (int index = 0; index < bosses.size(); index++) {
            if (bosses.get(index) instanceof SilkBoss silk) {
                renderSilk(event.getGuiGraphics(), mc, silk,
                        mc.getWindow().getGuiScaledWidth(), index, bosses.size());
            }
        }
    }

    private static void renderSilk(GuiGraphics g, Minecraft mc, SilkBoss boss,
                                   int screenWidth, int index, int bossCount) {
        float screenScale = screenWidth / 427.0F;
        float groupScale = switch (bossCount) {
            case 1 -> 1.2F;
            case 2 -> 1.0F;
            case 3 -> 0.8F;
            default -> 1.0F;
        };

        // 与 NetcraftBossHud 完全相同的血条定位公式，确保状态图标紧贴对应斯尔克血条。
        float barWidth = 112.0F * screenScale * groupScale;
        float barHeight = 10.24F * screenScale;
        float slotWidth = (float) screenWidth / bossCount;
        float barX = slotWidth * index + (slotWidth - barWidth) / 2.0F;
        float barY = barHeight * 2.0F;

        List<StatusIcon> icons = new ArrayList<>();

        int chain = Math.max(0, boss.getEntityData().get(SilkBoss.BASIC_CHAIN));
        int need = Math.max(1, boss.getEntityData().get(SilkBoss.BASIC_REQUIRED));
        icons.add(new StatusIcon(FIRE, chain + "/" + need, 0xFF1597CB));

        int next = boss.getEntityData().get(SilkBoss.NEXT_SPECIAL);
        icons.add(new StatusIcon(iconForSkill(next), shortSkillName(next), 0xFF1597CB));

        int phase = Math.max(1, Math.min(3, boss.getEntityData().get(SilkBoss.PHASE_SYNC)));
        icons.add(new StatusIcon(phase == 3 ? DARK_FIRE : BLACK_BALL,
                phase == 1 ? "理" : phase == 2 ? "疯" : "暴",
                phase == 3 ? 0xFFB04D24 : 0xFFB22828));

        int plague = boss.getEntityData().get(SilkBoss.PLAGUE_SECONDS);
        if (plague > 0) {
            icons.add(new StatusIcon(SOUL, "疫" + plague, 0xFF8E1A9B));
        }

        int slime = boss.getEntityData().get(SilkBoss.SLIME_SECONDS);
        if (slime > 0) {
            icons.add(new StatusIcon(DARK_FIRE, "爆" + slime, 0xFFC13C28));
        }

        int support = boss.getEntityData().get(SilkBoss.SUPPORT_FLAGS);
        if ((support & 1) != 0) icons.add(new StatusIcon(FIRE_ORB, "火", 0xFFD9911A));
        if ((support & 2) != 0) icons.add(new StatusIcon(FIRE_CIRCLE, "圈", 0xFFD95E1A));
        if ((support & 4) != 0) icons.add(new StatusIcon(PILLAR, "柱", 0xFF48A7CC));
        if ((support & 8) != 0) icons.add(new StatusIcon(BLACK_WATER, "心", 0xFFE2A43B));

        float icon = 12.5F * screenScale * groupScale;
        float gap = 1.5F * screenScale * groupScale;
        float y = barY + barHeight + 1.8F * screenScale;
        float x = barX;

        // 一个血条槽位过窄时最多画到可见区域，不让图标压到其它 Boss 血条。
        float maxRight = slotWidth * (index + 1) - 2.0F * screenScale;
        for (StatusIcon status : icons) {
            if (x + icon > maxRight) break;
            drawStatus(g, mc, status, x, y, icon, screenScale * groupScale);
            x += icon + gap;
        }
    }

    private static ResourceLocation iconForSkill(int action) {
        return switch (action) {
            case SilkBoss.ACT_BATS -> SOUL;
            case SilkBoss.ACT_METEOR, SilkBoss.ACT_BLACK_BALL -> BLACK_BALL;
            case SilkBoss.ACT_FLAME, SilkBoss.ACT_SWEEP -> DARK_FIRE;
            case SilkBoss.ACT_PLAGUE -> SOUL;
            case SilkBoss.ACT_BLACK_WATER -> BLACK_WATER;
            case SilkBoss.ACT_SUMMON -> FIRE_CIRCLE;
            case SilkBoss.ACT_BURST -> FIRE;
            default -> FIRE;
        };
    }

    private static String shortSkillName(int action) {
        return switch (action) {
            case SilkBoss.ACT_BATS -> "蝠";
            case SilkBoss.ACT_METEOR -> "星";
            case SilkBoss.ACT_FLAME -> "火";
            case SilkBoss.ACT_SWEEP -> "扫";
            case SilkBoss.ACT_SUMMON -> "召";
            case SilkBoss.ACT_PLAGUE -> "疫";
            case SilkBoss.ACT_BURST -> "爆";
            case SilkBoss.ACT_BLACK_WATER -> "水";
            case SilkBoss.ACT_BLACK_BALL -> "球";
            default -> "普";
        };
    }

    private static void drawStatus(GuiGraphics g, Minecraft mc, StatusIcon status,
                                   float x, float y, float size, float scale) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int is = Math.max(8, Math.round(size));

        // 原图截图中状态图标都是有色边框，这里保留同样的视觉层级。
        g.fill(ix - 1, iy - 1, ix + is + 1, iy + is + 1, 0xDD121212);
        g.fill(ix, iy, ix + is, iy + is, status.frameColor());
        g.fill(ix + 1, iy + 1, ix + is - 1, iy + is - 1, 0xD9222430);

        float pad = Math.max(1.0F, size * 0.12F);
        drawTexturedQuad(g, status.texture(), x + pad, y + pad,
                size - pad * 2.0F, size - pad * 2.0F);

        float textScale = Math.max(0.45F, 0.48F * scale);
        g.pose().pushPose();
        float centerX = x + size / 2.0F;
        float textY = y + size - 5.0F * textScale;
        g.pose().translate(centerX, textY, 300.0F);
        g.pose().scale(textScale, textScale, 1.0F);
        g.drawString(mc.font, status.text(), -mc.font.width(status.text()) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void drawTexturedQuad(GuiGraphics graphics, ResourceLocation texture,
                                         float x, float y, float width, float height) {
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
        buffer.vertex(matrix, x, y, 250.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(matrix, x, y + height, 250.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x + width, y + height, 250.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x + width, y, 250.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
