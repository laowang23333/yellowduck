package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.entity.ToyBearEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 布偶熊普通攻击触发距离：2.5 格 -> 3 格。
 *
 * ToyBearEntity 是 YellowDuck 自己的类，不参与 Minecraft 混淆映射，
 * 因此这里明确关闭 remap，避免 Mixin AP 尝试寻找不存在的映射。
 */
@Mixin(value = ToyBearEntity.class, remap = false)
public abstract class ToyBearAttackRangeMixin {
    @ModifyConstant(
            method = "tickSkills",
            constant = @Constant(doubleValue = 2.5D),
            require = 1,
            remap = false
    )
    private double yellowduck$threeBlockNormalAttackRange(double original) {
        return 3.0D;
    }
}
