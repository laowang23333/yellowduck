package com.yourname.yellowduck.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 小樱的物理锁定版本。
 * 不改任何技能/仇恨/属性逻辑，只让玩家身体碰撞和击退无法推动 Boss。
 */
public final class LockedSakurawitchEntity extends SakurawitchEntity {
    public LockedSakurawitchEntity(EntityType<? extends PathfinderMob> type, Level level) {
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
