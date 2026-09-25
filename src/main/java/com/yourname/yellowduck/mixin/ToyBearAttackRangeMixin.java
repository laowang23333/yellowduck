package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.entity.ToyBearEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 布偶熊普通攻击触发距离：2.5 格 -> 3 格。 */
@Mixin(ToyBearEntity.class)
public abstract class ToyBearAttackRangeMixin {
    @ModifyConstant(
            method = "tickSkills",
            constant = @Constant(doubleValue = 2.5D),
            require = 1
    )
    private double yellowduck$threeBlockNormalAttackRange(double original) {
        return 3.0D;
    }
}
