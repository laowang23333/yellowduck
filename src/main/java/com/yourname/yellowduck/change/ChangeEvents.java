package com.yourname.yellowduck.change;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ChangeEvents {
    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            int stacks = ChangeStatus.drink(player);
            if (stacks > 0) event.setAmount(event.getAmount() * Math.max(0.0F, 1.0F - stacks * 0.10F));
        }
    }

    @SubscribeEvent
    public static void heal(LivingHealEvent event) {
        int stacks = ChangeStatus.drink(event.getEntity());
        if (stacks > 0) event.setAmount(event.getAmount() * Math.max(0.0F, 1.0F - stacks * 0.10F));
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(ChangeContent.DRUNKEN.get())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;

        int drink = ChangeStatus.drink(player);
        if (drink > 0) {
            double mul = Math.max(0.0D, 1.0D - drink * 0.10D);
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * mul, v.y, v.z * mul);
        }

        if (player.hasEffect(ChangeContent.DRUNKEN.get())) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, v.y, 0.0D);
            player.hurtMarked = true;
        }

        int thirst = ChangeStatus.thirst(player);
        if (thirst > 0 && player.tickCount % 60 == 0) {
            float damage = player.getMaxHealth() * 0.20F * thirst;
            player.hurt(player.damageSources().magic(), damage);
        }
    }

    private ChangeEvents() {}
}
