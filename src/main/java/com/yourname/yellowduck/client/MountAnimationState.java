package com.yourname.yellowduck.client;

import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 坐骑客户端动画状态。
 *
 * 本地骑手优先直接读取输入，保证自己骑乘时即时切换动画；
 * 其他玩家看到的坐骑则结合服务器同步状态和客户端位置变化，并保留短暂的移动宽限期，
 * 避免网络包偶尔晚一两个 tick 时 run/idle 来回闪烁。
 */
@OnlyIn(Dist.CLIENT)
public final class MountAnimationState {
    private static final int REMOTE_MOVING_GRACE_TICKS = 4;
    private static final double MOVE_EPSILON_SQR = 1.0E-8D;
    private static final double TELEPORT_DISTANCE_SQR = 16.0D;

    private static final Map<MountEntity, Motion> MOTION = new WeakHashMap<>();

    private static final class Motion {
        double x;
        double z;
        long movingUntil = Long.MIN_VALUE;

        Motion(MountEntity entity) {
            x = entity.getX();
            z = entity.getZ();
        }
    }

    private MountAnimationState() {
    }

    public static boolean isWalking(MountEntity entity) {
        if (entity.isGuiPreview()) {
            return false;
        }

        Motion motion = MOTION.computeIfAbsent(entity, Motion::new);

        double dx = entity.getX() - motion.x;
        double dz = entity.getZ() - motion.z;
        double distanceSqr = dx * dx + dz * dz;

        motion.x = entity.getX();
        motion.z = entity.getZ();

        long now = entity.level().getGameTime();
        boolean syncedWalking = entity.getEntityData().get(MountEntity.IS_WALKING);

        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        boolean localRider = localPlayer != null && entity.getControllingPassenger() == localPlayer;

        if (localRider) {
            // 自己骑乘时直接使用本地输入，避免等待服务器同步包以后才开始跑步动画。
            boolean localInput = Math.abs(localPlayer.xxa) > 0.01F
                    || Math.abs(localPlayer.zza) > 0.01F;
            return localInput || syncedWalking;
        }

        // 明显传送不能当作“正在走路”，否则跨维度/回城后会多播放几帧跑步。
        if (distanceSqr >= TELEPORT_DISTANCE_SQR) {
            motion.movingUntil = Long.MIN_VALUE;
            return syncedWalking;
        }

        // 位置正在变化，或服务端明确同步为移动，都刷新宽限期。
        // 这样即使下一两个客户端 tick 没收到新的位置包，也不会瞬间切回待机。
        if (distanceSqr > MOVE_EPSILON_SQR || syncedWalking) {
            motion.movingUntil = now + REMOTE_MOVING_GRACE_TICKS;
        }

        return now <= motion.movingUntil;
    }
}
