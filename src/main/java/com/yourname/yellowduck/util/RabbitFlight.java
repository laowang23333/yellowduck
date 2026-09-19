package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.RabbitMountEntity;
import com.yourname.yellowduck.entity.BambooHorseEntity;
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
    private static boolean bamboo(Player player) { return player.getVehicle() instanceof BambooHorseEntity; }
    private static String suffix(Player player) { return bamboo(player) ? "Bamboo" : "Rabbit"; }
    public static int energy(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        String key = suffix(player) + "Energy";
        return data.contains(key) ? Math.max(0, Math.min(MAX, data.getInt(key))) : MAX;
    }
    public static boolean canFly(Player player) {
        return energy(player) == MAX && !player.getPersistentData().getCompound(ROOT).getBoolean(suffix(player) + "Rest");
    }
    public static void requireRest(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        String suffix = suffix(player);
        data.putBoolean(suffix + "Rest", true);
        data.putInt(suffix + "RestTicks", 1200); data.putInt(suffix + "RestStart", energy(player));
        player.getPersistentData().put(ROOT, data);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!MountData.hasMount(player, "rabbit") && !MountData.hasMount(player, "bamboo_horse")) return;
        Entity vehicle = player.getVehicle();
        RabbitMountEntity rabbit = vehicle instanceof RabbitMountEntity r ? r : null;
        int energy = energy(player);
        CompoundTag data = player.getPersistentData().getCompound(ROOT);
        String suffix = rabbit instanceof BambooHorseEntity ? "Bamboo" : "Rabbit";
        boolean rest = data.getBoolean(suffix + "Rest");
        int restTicks = Math.max(0, Math.min(1200, data.getInt(suffix + "RestTicks")));
        if (rabbit != null && rabbit.isFlying()) {
            energy = Math.max(0, energy - 3); rest = true; restTicks = 1200;
            data.putInt(suffix + "RestStart", energy);
        } else if (rest && (vehicle == null ? player.onGround() : vehicle.onGround())
                && !player.isFallFlying() && !player.getAbilities().flying) {
            restTicks = Math.max(0, restTicks - 1);
            int start = Math.max(0, Math.min(MAX, data.getInt(suffix + "RestStart")));
            energy = start + (MAX - start) * (1200 - restTicks) / 1200;
            if (restTicks == 0) { energy = MAX; rest = false; }
        }
        data.putInt(suffix + "RestTicks", restTicks);
        data.putInt(suffix + "Energy", energy); data.putBoolean(suffix + "Rest", rest); player.getPersistentData().put(ROOT, data);
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
