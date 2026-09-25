package com.yourname.yellowduck.effect;

import com.yourname.yellowduck.event.ToyBearEntangleEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 根须缠绕：锁移动、锁攻击，并每秒造成 5 点伤害。 */
public class RootEntangleEffect extends MobEffect {
    private static final String ATTACK_SPEED_UUID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String ATTACK_DAMAGE_UUID = "c3d4e5f6-a7b8-9012-cdef-123456789012";

    public RootEntangleEffect() {
        super(MobEffectCategory.HARMFUL, 0x228B22);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID,
                -1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_UUID,
                -1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (living.level().isClientSide) return;

        // 兼容旧版本残留的“无限时长”Buff：玩家已经没有根须绑定时立即自清。
        if (!(living instanceof Player player) || !ToyBearEntangleEvents.isEntangled(player)) {
            living.removeEffect(this);
            return;
        }

        Vec3 motion = living.getDeltaMovement();
        living.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        living.hurtMarked = true;

        // 每 20 tick 造成 5 点伤害。
        if (living.tickCount % 20 == 0) {
            living.hurt(living.damageSources().generic(), 5.0F);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
