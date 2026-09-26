package com.yourname.yellowduck.effect;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * 月饼专用的正面 Buff。
 * 图标直接绘制对应月饼的小图，不使用 Minecraft 默认药水图标。
 */
public final class MooncakeIconEffect extends MobEffect {
    private static final int ICON_TEXTURE_SIZE = 48;

    private final ResourceLocation iconTexture;
    private final boolean regeneration;

    public MooncakeIconEffect(int color, ResourceLocation iconTexture, boolean regeneration) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.iconTexture = iconTexture;
        this.regeneration = regeneration;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (regeneration && entity.getHealth() < entity.getMaxHealth()) {
            // 对齐原版 Regeneration I：每次恢复 1 点生命值。
            entity.heal(1.0F);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        if (!regeneration) return false;
        int interval = 50 >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance,
                                               EffectRenderingInventoryScreen<?> screen,
                                               GuiGraphics graphics,
                                               int x,
                                               int y,
                                               int blitOffset) {
                drawIcon(graphics, x, y + 7);
                return true;
            }

            @Override
            public boolean renderGuiIcon(MobEffectInstance instance,
                                         Gui gui,
                                         GuiGraphics graphics,
                                         int x,
                                         int y,
                                         float z,
                                         float alpha) {
                drawIcon(graphics, x + 3, y + 3);
                return true;
            }

            private void drawIcon(GuiGraphics graphics, int x, int y) {
                graphics.blit(
                        iconTexture,
                        x, y,
                        18, 18,
                        0.0F, 0.0F,
                        ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE,
                        ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE
                );
            }
        });
    }
}
