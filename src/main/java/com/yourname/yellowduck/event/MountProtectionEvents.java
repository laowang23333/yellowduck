package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.MountEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 坐骑保护：所有继承 MountEntity 的坐骑都不会成为敌对怪物的攻击目标。
 *
 * LivingChangeTargetEvent 同时覆盖传统 Mob#setTarget 和 Brain 的 StartAttacking，
 * 因而不需要给每一种原版/Mod 怪物单独修改 AI Goal。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MountProtectionEvents {

    private MountProtectionEvents() {
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof MountEntity)) {
            return;
        }

        LivingEntity attacker = event.getEntity();

        // Enemy 覆盖绝大多数原版敌对生物；MONSTER 分类再兜底第三方 Mod 怪物。
        if (attacker instanceof Enemy || attacker.getType().getCategory() == MobCategory.MONSTER) {
            event.setNewTarget(null);
        }
    }
}
