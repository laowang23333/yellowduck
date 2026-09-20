package com.yourname.yellowduck.cleopatra;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class CleopatraUtil {
    private CleopatraUtil() {}

    static boolean validPlayer(Player player) {
        return player != null && player.isAlive() && !player.isRemoved()
                && !player.isCreative() && !player.isSpectator();
    }

    static boolean magicHurt(LivingEntity target, LivingEntity attacker, float damage) {
        if (target == null || !target.isAlive() || damage <= 0.0F) return false;
        if (attacker != null) {
            return target.hurt(target.damageSources().indirectMagic(attacker, attacker), damage);
        }
        return target.hurt(target.damageSources().magic(), damage);
    }
}
