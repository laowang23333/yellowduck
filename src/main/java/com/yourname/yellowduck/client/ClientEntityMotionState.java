package com.yourname.yellowduck.client;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 客户端实体移动动画判定。
 *
 * 思路和 NetCraft 的 GLTF 实体动画驱动类似：
 * 服务端只提供“是否移动”的同步状态作为提示，客户端同时观察实体实际位置变化，
 * 并在网络包之间保留一个很短的移动宽限期。
 *
 * 这样服务器偶尔晚一两个 tick 时，不会把 walk/run 立刻切回 idle。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEntityMotionState {
    private static final double MOVE_EPSILON_SQR = 1.0E-8D;
    private static final double TELEPORT_DISTANCE_SQR = 16.0D;

    private static final Map<Entity, Motion> MOTION = new WeakHashMap<>();

    private static final class Motion {
        double x;
        double z;
        long movingUntil = Long.MIN_VALUE;

        Motion(Entity entity) {
            this.x = entity.getX();
            this.z = entity.getZ();
        }
    }

    private ClientEntityMotionState() {
    }

    /**
     * @param entity 实体
     * @param syncedMoving 服务端同步的移动状态，只作为提示，不作为唯一依据
     * @param graceTicks 停止收到位置变化后继续保持移动动画的 tick 数
     */
    public static boolean isMoving(Entity entity, boolean syncedMoving, int graceTicks) {
        Motion motion = MOTION.computeIfAbsent(entity, Motion::new);

        double x = entity.getX();
        double z = entity.getZ();
        double dx = x - motion.x;
        double dz = z - motion.z;
        double distanceSqr = dx * dx + dz * dz;

        motion.x = x;
        motion.z = z;

        long now = entity.tickCount;

        // 传送/跨维度造成的大幅坐标跳变不应被当作普通走路。
        if (distanceSqr >= TELEPORT_DISTANCE_SQR) {
            motion.movingUntil = Long.MIN_VALUE;
            return syncedMoving;
        }

        // 实际位置变化是客户端的主要判断来源。
        // 服务端同步值只用来填补刚开始移动但本地位置尚未插值出来的短暂空档。
        if (distanceSqr > MOVE_EPSILON_SQR || syncedMoving) {
            motion.movingUntil = now + Math.max(1, graceTicks);
        }

        return now <= motion.movingUntil;
    }
}
