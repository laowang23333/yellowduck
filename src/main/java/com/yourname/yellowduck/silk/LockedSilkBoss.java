package com.yourname.yellowduck.silk;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 教授斯尔克的物理锁定版本。
 * NetCraft 战斗逻辑全部继承原 SilkBoss，只取消玩家推动/击退。
 */
public final class LockedSilkBoss extends SilkBoss {
    public LockedSilkBoss(EntityType<? extends SilkBoss> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Boss 不接受 vanilla knockback。
    }
}
