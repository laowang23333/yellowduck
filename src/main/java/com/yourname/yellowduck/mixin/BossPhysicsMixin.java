package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.boss.NetcraftBossBase;
import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * YellowDuck Boss 物理锁定：
 * - 参考艳后 isPushable=false，玩家身体碰撞不能把 Boss 推走；
 * - 同时取消 Boss 自身收到的 vanilla knockback，击退武器也推不动 Boss；
 * - 不影响 Boss 自己通过 setDeltaMovement / 寻路执行的技能移动。
 */
@Mixin(LivingEntity.class)
public abstract class BossPhysicsMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void yellowduck$bossCannotBeBodyPushed(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (yellowduck$isLockedBoss(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void yellowduck$bossCannotBeKnockedBack(double strength, double x, double z, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (yellowduck$isLockedBoss(self)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean yellowduck$isLockedBoss(LivingEntity entity) {
        // NetcraftBossBase 会自动覆盖艳后、教授斯尔克以及以后继续沿用该基类的 Boss。
        return entity instanceof NetcraftBossBase
                || entity instanceof SakurawitchEntity
                || entity instanceof ToyBearEntity
                || entity instanceof TwoPhaseBossEntity;
    }
}
