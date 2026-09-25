package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.garmr.GarmrHelperEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 熔岩守卫固定 3 格近战攻击距离；其它使用 MeleeAttackGoal 的实体保持原版距离。 */
@Mixin(MeleeAttackGoal.class)
public abstract class GarmrLavaGuardAttackRangeMixin {
    private static final double YELLOWDUCK_LAVA_GUARD_ATTACK_RANGE_SQR = 3.0D * 3.0D;

    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = "getAttackReachSqr", at = @At("HEAD"), cancellable = true)
    private void yellowduck$lavaGuardThreeBlockReach(
            LivingEntity target,
            CallbackInfoReturnable<Double> cir) {
        if (mob instanceof GarmrHelperEntity helper
                && helper.getVariant() == GarmrHelperEntity.LAVA_GUARD) {
            cir.setReturnValue(YELLOWDUCK_LAVA_GUARD_ATTACK_RANGE_SQR);
        }
    }
}
