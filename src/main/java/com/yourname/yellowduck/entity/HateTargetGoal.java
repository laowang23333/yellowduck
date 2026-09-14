package com.yourname.yellowduck.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 用 HateHelper 选目标的 Goal。
 * 每 20 tick 扫描一次，用挑衅/隐蔽附魔加权选目标。
 */
public class HateTargetGoal extends Goal {

    private final Mob mob;
    private final double searchRange;
    private int scanCooldown = 0;

    public HateTargetGoal(Mob mob, double searchRange) {
        this.mob = mob;
        this.searchRange = searchRange;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = 20;

        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive()
                && mob.distanceTo(current) <= searchRange) {
            return false; // 已有有效目标，不换
        }

        Player best = HateHelper.findHighestHateTarget(mob, searchRange);
        if (best == null) return false;

        mob.setTarget(best);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
