package com.yourname.yellowduck.change;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class ChangeStatus {
    private ChangeStatus() {}

    public static int drink(LivingEntity entity) {
        return level(entity, ChangeContent.DRINK.get());
    }

    public static int thirst(LivingEntity entity) {
        return level(entity, ChangeContent.THIRST.get());
    }

    public static int boost(LivingEntity entity) {
        return level(entity, ChangeContent.DRINK_BOOST.get());
    }

    private static int level(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    public static void addDrink(LivingEntity entity, int add) {
        if (entity.level().isClientSide || add <= 0 || entity.hasEffect(ChangeContent.DRUNKEN.get())) return;

        int next = drink(entity) + add;
        if (next >= 5) {
            entity.removeEffect(ChangeContent.DRINK.get());
            entity.addEffect(new MobEffectInstance(
                    ChangeContent.DRUNKEN.get(),
                    100,
                    0,
                    false,
                    true,
                    true
            ));
            return;
        }

        set(entity, ChangeContent.DRINK.get(), Math.min(4, next), -1);
        ensureMark(entity, ChangeContent.MARK_DRINK.get());
    }

    public static void addThirst(LivingEntity entity, int add) {
        if (entity.level().isClientSide || add <= 0) return;

        int next = Math.min(5, thirst(entity) + add);
        set(entity, ChangeContent.THIRST.get(), next, -1);
        ensureMark(entity, ChangeContent.MARK_THIRST.get());
    }

    public static void removeOneThirst(LivingEntity entity) {
        if (entity.level().isClientSide) return;

        int now = thirst(entity);
        if (now <= 1) {
            entity.removeEffect(ChangeContent.THIRST.get());
        } else {
            set(entity, ChangeContent.THIRST.get(), now - 1, -1);
            ensureMark(entity, ChangeContent.MARK_THIRST.get());
        }
    }

    public static void addBoost(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        set(entity, ChangeContent.DRINK_BOOST.get(), Math.min(10, boost(entity) + 1), -1);
    }

    private static void set(LivingEntity entity, MobEffect effect, int stacks, int duration) {
        entity.removeEffect(effect);
        if (stacks > 0) {
            entity.addEffect(new MobEffectInstance(
                    effect,
                    duration,
                    stacks - 1,
                    false,
                    true,
                    true
            ));
        }
    }

    private static void ensureMark(
            LivingEntity entity,
            EntityType<? extends ChangeEffectEntity> type
    ) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ChangeEffectEntity.ensureMark(serverLevel, entity, type);
        }
    }
}
