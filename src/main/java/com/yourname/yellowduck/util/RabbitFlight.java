package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.RabbitMountEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 6000单位：飞行每tick耗3（100秒），每次飞行结束后地面休息1200tick（60秒）。
 * 保存在已有玩家坐骑数据根中，召回不重置，死亡由MountData继承。
 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class RabbitFlight {
    public static final int MAX = 6000;
    private static final String ROOT = "yellowduck_mounts";
    private RabbitFlight() {}
    public static int energy(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        return data.contains("RabbitEnergy") ? Math.max(0, Math.min(MAX, data.getInt("RabbitEnergy"))) : MAX;
    }
    public static boolean canFly(Player player) {
        return energy(player) == MAX && !player.getPersistentData().getCompound(ROOT).getBoolean("RabbitRest");
    }
    public static void requireRest(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        data.putBoolean("RabbitRest", true);
        data.putInt("RabbitRestTicks", 1200); data.putInt("RabbitRestStart", energy(player));
        player.getPersistentData().put(ROOT, data);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!MountData.hasMount(player, "rabbit")) return;
        Entity vehicle = player.getVehicle();
        RabbitMountEntity rabbit = vehicle instanceof RabbitMountEntity r ? r : null;
        int energy = energy(player);
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        boolean rest = data.getBoolean("RabbitRest");
        int restTicks = Math.max(0, Math.min(1200, data.getInt("RabbitRestTicks")));
        if (rabbit != null && rabbit.isFlying()) {
            energy = Math.max(0, energy - 3); rest = true; restTicks = 1200;
            data.putInt("RabbitRestStart", energy);
        } else if (rest && (vehicle == null ? player.onGround() : vehicle.onGround())
                && !player.isFallFlying() && !player.getAbilities().flying) {
            restTicks = Math.max(0, restTicks - 1);
            int start = Math.max(0, Math.min(MAX, data.getInt("RabbitRestStart")));
            energy = start + (MAX - start) * (1200 - restTicks) / 1200;
            if (restTicks == 0) { energy = MAX; rest = false; }
        }
        data.putInt("RabbitRestTicks", restTicks);
        data.putInt("RabbitEnergy", energy); data.putBoolean("RabbitRest", rest); player.getPersistentData().put(ROOT, data);
        if (rabbit != null) { rabbit.syncEnergy(energy, rest, restTicks); if (energy == 0 && rabbit.isFlying()) rabbit.stopFlying(); }
    }
    private static void dismount(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.getVehicle() instanceof RabbitMountEntity rabbit && rabbit.isFlying()) { rabbit.stopFlying(); player.stopRiding(); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void onAttacked(LivingAttackEvent event) {
        if (event.getAmount() > 0) dismount(event.getEntity());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void onDamage(LivingDamageEvent event) {
        // getEntity 同时包含玩家近战和玩家发射的箭等间接伤害。
        if (event.getAmount() > 0) dismount(event.getSource().getEntity());
    }
}
