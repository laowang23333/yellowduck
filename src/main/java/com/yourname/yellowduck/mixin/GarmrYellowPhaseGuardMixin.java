package com.yourname.yellowduck.mixin;

import com.yourname.yellowduck.garmr.GarmrBoss;
import com.yourname.yellowduck.garmr.GarmrConfig;
import com.yourname.yellowduck.garmr.GarmrHelperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 加姆黄血守护圈、阿努比斯状态与阶段复位保护。
 */
@Mixin(GarmrBoss.class)
public abstract class GarmrYellowPhaseGuardMixin {
    @Unique
    private static final String YELLOWDUCK_DISENGAGE_RESET_ALLOWED = "YellowDuckGarmrDisengageResetAllowed";

    @Shadow(remap = false) private int nextAnubisAction;
    @Shadow(remap = false) private UUID carrierId;
    @Shadow(remap = false) private int protectionUntilTick;
    @Shadow(remap = false) private UUID anubisId;
    @Shadow(remap = false) private boolean anubisLost;

    @Shadow(remap = false)
    private ServerPlayer nearestParticipant(Entity reference) {
        return null;
    }

    @Shadow(remap = false)
    private void tickProtectionCircle(ServerLevel server) {
    }

    @Shadow(remap = false)
    private void spawnAnubis() {
    }

    @Shadow(remap = false)
    private void enterP2() {
    }

    /** 黄血阶段一落地就把第一次守护圈排到下一次服务器 tick。 */
    @Inject(method = "enterP2", at = @At("TAIL"), remap = false)
    private void yellowduck$guardImmediatelyOnYellowPhase(CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        this.nextAnubisAction = boss.tickCount;
    }

    /**
     * 守护圈到点时只有真正选到玩家才进入 30 秒冷却。
     * 玩家刚死亡、复活或短暂不在有效参与者列表时，1 秒后重试，不吞掉这一轮技能。
     */
    @Inject(method = "tickAnubisGuard", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$reliableGuard(ServerLevel server, LivingEntity anubis, CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;

        if (this.carrierId != null && boss.tickCount >= this.protectionUntilTick) {
            this.carrierId = null;
            this.protectionUntilTick = 0;
        }

        if (boss.tickCount >= this.nextAnubisAction) {
            ServerPlayer nearest = this.nearestParticipant(anubis);
            if (nearest != null) {
                this.carrierId = nearest.getUUID();
                this.protectionUntilTick = boss.tickCount + GarmrConfig.ANUBIS_GUARD_DURATION_TICKS;
                this.nextAnubisAction = boss.tickCount + GarmrConfig.ANUBIS_GUARD_INTERVAL_TICKS;
            } else {
                this.nextAnubisAction = boss.tickCount + 20;
            }
        }

        this.tickProtectionCircle(server);
        ci.cancel();
    }

    /**
     * 阿努比斯 UUID 暂时无法解析时先等待并尝试重新绑定，不把整场机制永久标记为丢失。
     */
    @Inject(method = "tickAnubis", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$recoverAnubisBeforeTick(ServerLevel server, CallbackInfo ci) {
        if (this.anubisLost) return;
        if (!yellowduck$ensureAnubis(server)) ci.cancel();
    }

    /** P3 小恶魔流程也使用相同的阿努比斯恢复判断。 */
    @Inject(method = "tickDevils", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$recoverAnubisBeforeDevils(ServerLevel server, CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        if (boss.getEntityData().get(GarmrBoss.PHASE) != GarmrBoss.P3 || this.anubisLost) return;
        if (!yellowduck$ensureAnubis(server)) ci.cancel();
    }

    /**
     * 黄血或更低生命值绝不允许重新进入起飞召怪阶段。
     * 如果旧状态意外回到 P1_GROUND，直接恢复为 P2。
     */
    @Inject(method = "beginTakeoff", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$preventYellowHealthTakeoff(CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        float threshold = boss.getMaxHealth() * GarmrConfig.PHASE_TWO_HEALTH;
        if (boss.getHealth() <= threshold + 0.5F) {
            this.enterP2();
            ci.cancel();
        }
    }

    /**
     * Boss 自身检测到普通回血时不能把黄血阶段重置回 P1。
     * 只有脱战检测明确授权的完整复位可以执行。
     */
    @Inject(method = "resetForNewFight", at = @At("HEAD"), cancellable = true, remap = false)
    private void yellowduck$gateFightReset(ServerLevel server, CallbackInfo ci) {
        GarmrBoss boss = (GarmrBoss) (Object) this;
        if (!boss.getPersistentData().getBoolean(YELLOWDUCK_DISENGAGE_RESET_ALLOWED)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean yellowduck$ensureAnubis(ServerLevel server) {
        GarmrBoss boss = (GarmrBoss) (Object) this;

        if (this.anubisId == null) {
            this.spawnAnubis();
        }

        if (this.anubisId != null) {
            Entity current = server.getEntity(this.anubisId);
            if (current instanceof LivingEntity living && !living.isRemoved()) {
                return true;
            }
        }

        for (Entity entity : server.getAllEntities()) {
            if (!(entity instanceof GarmrHelperEntity helper)) continue;
            if (helper.getVariant() != GarmrHelperEntity.ANUBIS) continue;
            if (helper.isRemoved()) continue;
            if (!helper.getPersistentData().hasUUID("GarmrOwner")) continue;
            if (!boss.getUUID().equals(helper.getPersistentData().getUUID("GarmrOwner"))) continue;

            this.anubisId = helper.getUUID();
            return true;
        }

        return false;
    }
}
