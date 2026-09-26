package com.yourname.yellowduck.change;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ChangeStackEffect extends MobEffect {
    private final Component displayName;

    public ChangeStackEffect(boolean harmful, int color, String displayName) {
        super(
                harmful
                        ? MobEffectCategory.HARMFUL
                        : MobEffectCategory.BENEFICIAL,
                color
        );
        this.displayName = Component.literal(displayName);
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }
}
