package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.entity.ToyBearEntity;
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
 * 布偶熊伤害改成 NetCraft 风格：
 * 伤害照常结算，但伤害前后恢复玩家原本速度。
 *
 * 这样会同时覆盖：
 * - 普攻（旧代码伤害后会 setDeltaMovement(Vec3.ZERO)）；
 * - P2/P3 瞬移斩的分摊/单吃伤害。
 */
@Mixin(ToyBearEntity.class)
public abstract class ToyBearNoKnockbackMixin {

    @Shadow private UUID pendingDamageTarget;
    @Shadow private int pendingDamageDelay;
    @Shadow private UUID skill2TargetUUID;

    @Unique
    private Player yellowduck$pendingPlayer;

    @Unique
    private Vec3 yellowduck$pendingMotion;

    @Unique
    private final Map<UUID, Vec3> yellowduck$skill2Motions = new HashMap<>();

    @Inject(method = "tickPendingDamage", at = @At("HEAD"))
    private void yellowduck$saveNormalAttackMotion(CallbackInfo ci) {
        yellowduck$pendingPlayer = null;
        yellowduck$pendingMotion = null;

        // tickPendingDamage() 进入后先 --pendingDamageDelay；
        // 当前为 1 时，这一 tick 正好会真正结算普攻伤害。
        if (pendingDamageTarget == null || pendingDamageDelay != 1) {
            return;
        }

        ToyBearEntity self = (ToyBearEntity) (Object) this;
        Player player = self.level().getPlayerByUUID(pendingDamageTarget);
        if (player != null && player.isAlive() && !player.isRemoved()) {
            yellowduck$pendingPlayer = player;
            yellowduck$pendingMotion = player.getDeltaMovement();
        }
    }

    @Inject(method = "tickPendingDamage", at = @At("RETURN"))
    private void yellowduck$restoreNormalAttackMotion(CallbackInfo ci) {
        if (yellowduck$pendingPlayer != null
                && yellowduck$pendingMotion != null
                && !yellowduck$pendingPlayer.isRemoved()) {
            yellowduck$pendingPlayer.setDeltaMovement(yellowduck$pendingMotion);
            yellowduck$pendingPlayer.hurtMarked = true;
        }
        yellowduck$pendingPlayer = null;
        yellowduck$pendingMotion = null;
    }

    @Inject(method = "executeSkill2Damage", at = @At("HEAD"))
    private void yellowduck$saveSkill2Motion(CallbackInfo ci) {
        yellowduck$skill2Motions.clear();
        if (skill2TargetUUID == null) {
            return;
        }

        ToyBearEntity self = (ToyBearEntity) (Object) this;
        Player target = self.level().getPlayerByUUID(skill2TargetUUID);
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }

        // 原技能就是在目标周围 3 格取玩家，再用 2 格判断是否进入分摊。
        // 这里保存同一区域的玩家速度，伤害完成后恢复。
        for (Player player : self.level().getEntitiesOfClass(
                Player.class,
                target.getBoundingBox().inflate(3.0D),
                p -> p.isAlive() && !p.isRemoved())) {
            yellowduck$skill2Motions.put(player.getUUID(), player.getDeltaMovement());
        }
    }

    @Inject(method = "executeSkill2Damage", at = @At("RETURN"))
    private void yellowduck$restoreSkill2Motion(CallbackInfo ci) {
        ToyBearEntity self = (ToyBearEntity) (Object) this;
        for (Map.Entry<UUID, Vec3> entry : yellowduck$skill2Motions.entrySet()) {
            Player player = self.level().getPlayerByUUID(entry.getKey());
            if (player != null && !player.isRemoved()) {
                player.setDeltaMovement(entry.getValue());
                player.hurtMarked = true;
            }
        }
        yellowduck$skill2Motions.clear();
    }
}
