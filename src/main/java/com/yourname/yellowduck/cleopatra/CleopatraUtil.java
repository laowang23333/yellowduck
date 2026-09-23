package com.yourname.yellowduck.cleopatra;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class CleopatraUtil {
    private CleopatraUtil() {}

    static boolean validPlayer(Player player) {
        return player != null && player.isAlive() && !player.isRemoved()
                && !player.isCreative() && !player.isSpectator();
    }

    /**
     * NetCraft 风格魔法伤害：
     * 正常调用 hurt() 结算伤害，但伤害本身不改变目标原有速度。
     */
    static boolean magicHurt(LivingEntity target, LivingEntity attacker, float damage) {
        if (target == null || !target.isAlive() || damage <= 0.0F) return false;

        DamageSource source = attacker != null
                ? target.damageSources().indirectMagic(attacker, attacker)
                : target.damageSources().magic();

        Vec3 oldMotion = target.getDeltaMovement();
        boolean hit = target.hurt(source, damage);
        if (hit) {
            target.setDeltaMovement(oldMotion);
            target.hurtMarked = true;
        }
        return hit;
    }
}
