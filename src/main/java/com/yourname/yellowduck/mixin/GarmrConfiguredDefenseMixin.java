package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.config.EntityTuningConfig;
import com.yourname.yellowduck.garmr.GarmrBoss;
import com.yourname.yellowduck.garmr.GarmrConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Garmr 自定义 NetCraft 防御原本固定返回源码常量，导致 [garmr].armor 无效。
 * 三种伤害类型统一读取 armor；保持原规则“近战/远程/魔法防御相同”。
 */
@Mixin(GarmrBoss.class)
public abstract class GarmrConfiguredDefenseMixin {
    private int yellowduck$configDefense() {
        return Math.max(0, (int)Math.round(EntityTuningConfig.configured(
                "garmr", "armor", GarmrConfig.BOSS_DEFENSE)));
    }

    @Inject(method = "getMeleeDefense", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$melee(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(yellowduck$configDefense());
    }

    @Inject(method = "getRangedDefense", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$ranged(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(yellowduck$configDefense());
    }

    @Inject(method = "getMagicDefense", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$magic(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(yellowduck$configDefense());
    }
}
