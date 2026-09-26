package com.yourname.yellowduck.change;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class ChangeStatus {
    private ChangeStatus() {}

    public static int drink(LivingEntity e) { return level(e, ChangeContent.DRINK.get()); }
    public static int thirst(LivingEntity e) { return level(e, ChangeContent.THIRST.get()); }
    public static int boost(LivingEntity e) { return level(e, ChangeContent.DRINK_BOOST.get()); }

    private static int level(LivingEntity e, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance i = e.getEffect(effect);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    public static void addDrink(LivingEntity e, int add) {
        int next = drink(e) + add;
        if (next >= 5) {
            e.removeEffect(ChangeContent.DRINK.get());
            e.addEffect(new MobEffectInstance(ChangeContent.DRUNKEN.get(), 100, 0, false, true, true));
            return;
        }
        set(e, ChangeContent.DRINK.get(), Math.min(4, next), -1);
    }

    public static void addThirst(LivingEntity e, int add) {
        set(e, ChangeContent.THIRST.get(), Math.min(5, thirst(e) + add), -1);
    }

    public static void removeOneThirst(LivingEntity e) {
        int now = thirst(e);
        if (now <= 1) e.removeEffect(ChangeContent.THIRST.get());
        else set(e, ChangeContent.THIRST.get(), now - 1, -1);
    }

    public static void addBoost(LivingEntity e) {
        set(e, ChangeContent.DRINK_BOOST.get(), Math.min(10, boost(e) + 1), -1);
    }

    private static void set(LivingEntity e, net.minecraft.world.effect.MobEffect effect, int stacks, int duration) {
        e.removeEffect(effect);
        if (stacks > 0) e.addEffect(new MobEffectInstance(effect, duration, stacks - 1, false, true, true));
    }
}
