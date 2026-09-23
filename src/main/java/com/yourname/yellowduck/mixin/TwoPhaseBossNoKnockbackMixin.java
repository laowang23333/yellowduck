package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.entity.TwoPhaseBossEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 两阶段小黄鸭的 dealDamage() 本身已经是：
 * 保存旧速度 -> hurt -> 恢复旧速度。
 *
 * 但旧代码仍有 5 个技能在伤害后额外把 Y 速度写成 1.0/1.2/1.5，
 * 以及一阶段变身时 vanilla explode 会产生爆炸击退。
 *
 * 这里仅在这些“真正结算伤害/爆炸”的 tick 保存并恢复附近玩家速度。
 * 黑洞持续吸人、抓取传送/定身属于技能机制，不会被取消。
 */
@Mixin(TwoPhaseBossEntity.class)
public abstract class TwoPhaseBossNoKnockbackMixin {

    @Shadow private int transformTimer;
    @Shadow private int slamTimer;
    @Shadow private int grabTimer;
    @Shadow private int meteorTimer;
    @Shadow private int blackHoleTimer;
    @Shadow private int groundSlamTimer;

    @Shadow private boolean isGrabbing;
    @Shadow private boolean isMeteorShower;
    @Shadow private boolean isBlackHole;
    @Shadow private boolean isGroundSlam;

    @Unique
    private final Map<UUID, Vec3> yellowduck$motionsBeforeDamageBurst = new HashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void yellowduck$saveBurstVictimMotions(CallbackInfo ci) {
        yellowduck$motionsBeforeDamageBurst.clear();

        TwoPhaseBossEntity self = (TwoPhaseBossEntity) (Object) this;

        /*
         * tick() 内部会先 ++ 对应计时器，所以这里用“结算值 - 1”判断：
         * transform 99 -> 100：变身爆炸
         * slam 370 -> 371：撼地落地爆炸
         * grab 60 -> 61：抓取结束爆炸
         * meteor 230 -> 231：陨石雨落地爆炸
         * blackHole 60 -> 61：黑洞爆炸
         * groundSlam 30 -> 31：裂地斩结算
         */
        boolean damageBurstThisTick =
                (self.getEntityData().get(TwoPhaseBossEntity.IS_TRANSFORMING) && transformTimer == 99)
                || (self.getEntityData().get(TwoPhaseBossEntity.IS_SLAMMING) && slamTimer == 370)
                || (isGrabbing && grabTimer == 60)
                || (isMeteorShower && meteorTimer == 230)
                || (isBlackHole && blackHoleTimer == 60)
                || (isGroundSlam && groundSlamTimer == 30);

        if (!damageBurstThisTick) {
            return;
        }

        // 所有上述伤害范围最大只有 8 格；16 格留出安全余量。
        for (Player player : self.level().getEntitiesOfClass(
                Player.class,
                self.getBoundingBox().inflate(16.0D),
                p -> p.isAlive() && !p.isRemoved())) {
            yellowduck$motionsBeforeDamageBurst.put(player.getUUID(), player.getDeltaMovement());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void yellowduck$restoreBurstVictimMotions(CallbackInfo ci) {
        if (yellowduck$motionsBeforeDamageBurst.isEmpty()) {
            return;
        }

        TwoPhaseBossEntity self = (TwoPhaseBossEntity) (Object) this;
        for (Map.Entry<UUID, Vec3> entry : yellowduck$motionsBeforeDamageBurst.entrySet()) {
            Player player = self.level().getPlayerByUUID(entry.getKey());
            if (player != null && !player.isRemoved()) {
                player.setDeltaMovement(entry.getValue());
                player.hurtMarked = true;
            }
        }
        yellowduck$motionsBeforeDamageBurst.clear();
    }
}
