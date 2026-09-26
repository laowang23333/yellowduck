package com.yourname.yellowduck.change;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ChangeStackEffect extends MobEffect {
    public ChangeStackEffect(boolean harmful, int color) {
        super(harmful ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, color);
    }
}
