package com.yourname.yellowduck.client;

import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.Map;
import java.util.WeakHashMap;

/** 旁观者根据实体同步状态和实际位置变化播放动画，不读取远程玩家按键。 */
@OnlyIn(Dist.CLIENT)
public final class MountAnimationState {
    private static final Map<MountEntity, Motion> MOTION = new WeakHashMap<>();
    private static final class Motion {
        double x, z;
        long movingUntil = Long.MIN_VALUE;
        Motion(MountEntity entity) { x = entity.getX(); z = entity.getZ(); }
    }
    private MountAnimationState() {}
    public static boolean isWalking(MountEntity entity) {
        if (entity.isGuiPreview()) return false;
        Motion motion = MOTION.computeIfAbsent(entity, Motion::new);
        double dx = entity.getX() - motion.x;
        double dz = entity.getZ() - motion.z;
        double distance = dx * dx + dz * dz;
        motion.x = entity.getX(); motion.z = entity.getZ();
        long now = entity.level().getGameTime();
        // 跨越同步包间隔保持3tick，避免停走闪烁；忽略明显传送。
        if (distance > 1.0E-8D && distance < 16.0D) motion.movingUntil = now + 3;
        else if (distance >= 16.0D) motion.movingUntil = Long.MIN_VALUE;
        boolean localInput = false;
        var player = Minecraft.getInstance().player;
        if (player != null && entity.getControllingPassenger() == player) {
            localInput = Math.abs(player.xxa) > 0.01F || Math.abs(player.zza) > 0.01F;
        }
        return localInput || entity.getEntityData().get(MountEntity.IS_WALKING)
                || now <= motion.movingUntil;
    }
}
