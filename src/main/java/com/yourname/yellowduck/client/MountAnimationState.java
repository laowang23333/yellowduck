package com.yourname.yellowduck.client;

import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 坐骑客户端动画状态。
 *
 * 自己骑乘：直接读取本地输入，动画立即响应。
 * 其他玩家的坐骑：客户端根据实际位置变化连续判断，服务端 IS_WALKING 只作为提示。
 */
@OnlyIn(Dist.CLIENT)
public final class MountAnimationState {
    private static final int REMOTE_MOVING_GRACE_TICKS = 4;

    private MountAnimationState() {
    }

    public static boolean isWalking(MountEntity entity) {
        if (entity.isGuiPreview()) {
            return false;
        }

        boolean syncedWalking = entity.getEntityData().get(MountEntity.IS_WALKING);
        boolean observedMoving = ClientEntityMotionState.isMoving(
                entity,
                syncedWalking,
                REMOTE_MOVING_GRACE_TICKS
        );

        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;

        if (localPlayer != null && entity.getControllingPassenger() == localPlayer) {
            // 本地骑手不等服务器回包；按键一动就立即进入 ride/run。
            boolean localInput = Math.abs(localPlayer.xxa) > 0.01F
                    || Math.abs(localPlayer.zza) > 0.01F;
            return localInput || observedMoving;
        }

        return observedMoving;
    }
}
