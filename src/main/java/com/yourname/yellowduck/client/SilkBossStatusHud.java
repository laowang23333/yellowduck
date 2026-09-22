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
 * 斯尔克血条下方的 NetCraft 风格 Buff 行。
 *
 * 本版严格收口：只显示已经从奶块 buff.txt 明确解析到、并且配置中带可见图标字段的 Buff。
 * 不再显示 YellowDuck 自己增加的：
 * - 5次普攻进度
 * - 下一技能
 * - “理/疯/暴”阶段文字
 * - 史莱姆25秒爆炸倒计时
 * - 火圈/光柱等场景对象标记
 *
 * 已确认的原配置图标路径：
 * 2279 腐蚀黑水     -> YYBuffImages/blast_black
 * 2280 黑暗疫病     -> YYBuffImages/black_energy
 * 2281 心火庇护     -> YYBuffImages/fly_fast
 * 2283 强化火焰     -> YYBuffImages/de_fire
 * 2293 能量爆发     -> YYBuffImages/posion
 *
 * 当前解包资源没有 YYBuffImages.png 原图集，所以只在“Buff种类/层数/倒计时”上严格按解析结果，
 * 图面暂用已经提取到 YellowDuck 的斯尔克资源作占位；以后拿到 YYBuffImages 原图集只需替换纹理。
 */
public final class SilkBossStatusHud {
    private static final ResourceLocation ICON_BLACK_ENERGY =
            new ResourceLocation("yellowduck", "textures/entity/silk/stone_ball_black.png");
    private static final ResourceLocation ICON_BLACK_WATER =
            new ResourceLocation("yellowduck", "textures/entity/silk/pool_black.png");
    private static final ResourceLocation ICON_HEART_FIRE =
            new ResourceLocation("yellowduck", "textures/entity/silk/buff_ball_billboard.png");
    private static final ResourceLocation ICON_STRENGTHENED_FIRE =
            new ResourceLocation("yellowduck", "textures/particle/silk/fire_0.png");
    private static final ResourceLocation ICON_ENERGY_BURST =
            new ResourceLocation("yellowduck", "textures/particle/silk/soul_0.png");

    private record StatusIcon(ResourceLocation texture, String overlay, int frameColor) {}

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

        float barWidth = 112.0F * screenScale * groupScale;
        float barHeight = 10.24F * screenScale;
        float slotWidth = (float) screenWidth / bossCount;
        float barX = slotWidth * index + (slotWidth - barWidth) / 2.0F;
        float barY = barHeight * 2.0F;

        List<StatusIcon> icons = new ArrayList<>();

        // 2280 黑暗疫病（传染） -> YYBuffImages/black_energy
        int plagueSeconds = Math.max(0, boss.getEntityData().get(SilkBoss.PLAGUE_SECONDS));
        if (plagueSeconds > 0) {
            icons.add(new StatusIcon(
                    ICON_BLACK_ENERGY,
                    Integer.toString(plagueSeconds),
                    0xFF3E86B8
            ));
        }

        // 2279 腐蚀黑水 -> YYBuffImages/blast_black
        int blackWaterSeconds = Math.max(0, boss.getEntityData().get(SilkBoss.BLACK_WATER_SECONDS));
        if (blackWaterSeconds > 0) {
            icons.add(new StatusIcon(
                    ICON_BLACK_WATER,
                    Integer.toString(blackWaterSeconds),
                    0xFF7A4A9E
            ));
        }

        // 2281 心火庇护 -> YYBuffImages/fly_fast
        int heartFireStacks = Math.max(0, boss.getEntityData().get(SilkBoss.HEART_FIRE_STACKS));
        if (heartFireStacks > 0) {
            icons.add(new StatusIcon(
                    ICON_HEART_FIRE,
                    Integer.toString(heartFireStacks),
                    0xFF2D9BC0
            ));
        }

        // 2283 强化火焰 -> YYBuffImages/de_fire
        int fireStacks = Math.max(0, boss.getEntityData().get(SilkBoss.STRENGTHENED_FIRE_STACKS));
        if (fireStacks > 0) {
            icons.add(new StatusIcon(
                    ICON_STRENGTHENED_FIRE,
                    Integer.toString(fireStacks),
                    0xFFD89125
            ));
        }

        // 2293 能量爆发 -> YYBuffImages/posion
        int burstSeconds = Math.max(0, boss.getEntityData().get(SilkBoss.BURST_SECONDS));
        if (burstSeconds > 0) {
            icons.add(new StatusIcon(
                    ICON_ENERGY_BURST,
                    Integer.toString(burstSeconds),
                    0xFF7E4FB0
            ));
        }

        if (icons.isEmpty()) return;

        // 参考用户给的奶块截图：小方形图标紧贴血条下方横向排列，数字覆盖在右下角。
        float icon = 13.0F * screenScale * groupScale;
        float gap = 1.4F * screenScale * groupScale;
        float y = barY + barHeight + 1.6F * screenScale;
        float totalWidth = icons.size() * icon + Math.max(0, icons.size() - 1) * gap;
        float x = barX + Math.max(0.0F, (barWidth - totalWidth) / 2.0F);

        float slotLeft = slotWidth * index + 2.0F * screenScale;
        float slotRight = slotWidth * (index + 1) - 2.0F * screenScale;
        if (x < slotLeft) x = slotLeft;

        for (StatusIcon status : icons) {
            if (x + icon > slotRight) break;
            drawStatus(g, mc, status, x, y, icon, screenScale * groupScale);
            x += icon + gap;
        }
    }

    private static void drawStatus(GuiGraphics g, Minecraft mc, StatusIcon status,
                                   float x, float y, float size, float scale) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int is = Math.max(9, Math.round(size));

        // NetCraft 截图风格：深底 + 有色细框。
        g.fill(ix - 1, iy - 1, ix + is + 1, iy + is + 1, 0xE6101014);
        g.fill(ix, iy, ix + is, iy + is, status.frameColor());
        g.fill(ix + 1, iy + 1, ix + is - 1, iy + is - 1, 0xE6202430);

        float pad = Math.max(1.0F, size * 0.10F);
        drawTexturedQuad(g, status.texture(),
                x + pad, y + pad,
                size - pad * 2.0F, size - pad * 2.0F);

        String text = status.overlay();
        if (text == null || text.isEmpty()) return;

        // 数字压在右下角，布局更接近截图中的 Buff 层数/倒计时。
        float textScale = Math.max(0.48F, 0.54F * scale);
        g.pose().pushPose();
        g.pose().translate(x + size - 1.0F, y + size - 5.0F * textScale, 300.0F);
        g.pose().scale(textScale, textScale, 1.0F);
        g.drawString(mc.font, text, -mc.font.width(text), 0, 0xFFFFFFFF, true);
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
