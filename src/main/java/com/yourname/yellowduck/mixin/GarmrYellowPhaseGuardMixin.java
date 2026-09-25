package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.garmr.GarmrBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 加姆进入黄血阶段（P2）时强制让阿努比斯立即发放一次守护圈。
 * 第一次发放完成后，GarmrBoss 原有逻辑会继续按 30 秒间隔轮转。
 */
@Mixin(GarmrBoss.class)
public abstract class GarmrYellowPhaseGuardMixin {
    @Shadow(remap = false)
    private int nextAnubisAction;

    @Inject(method = "enterP2", at = @At("TAIL"), remap = false)
    private void yellowduck$guardImmediatelyOnYellowPhase(CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        this.nextAnubisAction = boss.tickCount;
    }
}
