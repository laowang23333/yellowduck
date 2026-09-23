package com.yourname.yellowduck.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 两阶段小黄鸭的物理锁定版本。
 * 不改技能状态机，只取消玩家身体推动和 vanilla 击退。
 */
public final class LockedTwoPhaseBossEntity extends TwoPhaseBossEntity {
    public LockedTwoPhaseBossEntity(EntityType<? extends PathfinderMob> type, Level level) {
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
