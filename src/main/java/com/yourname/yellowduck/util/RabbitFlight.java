package com.yourname.yellowduck.util;

import com.yourname.yellowduck.entity.BambooHorseEntity;
import com.yourname.yellowduck.entity.RabbitMountEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 6000单位：飞行每tick耗3（100秒），每次飞行结束后地面休息1200tick（60秒）。
 * 数据保存在玩家 yellowduck_mounts 根中，召回不重置。
 *
 * 网络优化：服务端耐力仍然每 tick 精确计算，但只每 5 tick 向坐骑实体同步一次 HUD 数据，
 * 避免飞行/恢复期间每 tick 产生 EntityData 更新包。
 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class RabbitFlight {
    public static final int MAX = 6000;
    private static final String ROOT = "yellowduck_mounts";
    private static final int HUD_SYNC_INTERVAL = 5;

    private RabbitFlight() {}

    private static boolean bamboo(Player player) {
        return player.getVehicle() instanceof BambooHorseEntity;
    }

    private static String suffix(Player player) {
        return bamboo(player) ? "Bamboo" : "Rabbit";
    }

    /** 获取持久数据根；只在根不存在时 put 一次，之后直接修改同一个 CompoundTag。 */
    private static CompoundTag mountData(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static int energy(CompoundTag data, String suffix) {
        String key = suffix + "Energy";
        return data.contains(key) ? Math.max(0, Math.min(MAX, data.getInt(key))) : MAX;
    }

    public static int energy(Player player) {
        CompoundTag data = mountData(player);
        return energy(data, suffix(player));
    }

    public static boolean canFly(Player player) {
        CompoundTag data = mountData(player);
        String suffix = suffix(player);
        return energy(data, suffix) == MAX && !data.getBoolean(suffix + "Rest");
    }

    public static void requireRest(Player player) {
        CompoundTag data = mountData(player);
        String suffix = suffix(player);
        int currentEnergy = energy(data, suffix);
        data.putBoolean(suffix + "Rest", true);
        data.putInt(suffix + "RestTicks", 1200);
        data.putInt(suffix + "RestStart", currentEnergy);
    }

    private static void updateMountState(CompoundTag data, String suffix, boolean flying, boolean grounded) {
        int energy = energy(data, suffix);
        boolean rest = data.getBoolean(suffix + "Rest");
        int restTicks = Math.max(0, Math.min(1200, data.getInt(suffix + "RestTicks")));

        if (flying) {
            energy = Math.max(0, energy - 3);
            rest = true;
            restTicks = 1200;
            data.putInt(suffix + "RestStart", energy);
        } else if (rest && grounded) {
            restTicks = Math.max(0, restTicks - 1);
            int start = Math.max(0, Math.min(MAX, data.getInt(suffix + "RestStart")));
            energy = start + (MAX - start) * (1200 - restTicks) / 1200;
            if (restTicks == 0) {
                energy = MAX;
                rest = false;
            }
        }

        data.putInt(suffix + "RestTicks", restTicks);
        data.putInt(suffix + "Energy", energy);
        data.putBoolean(suffix + "Rest", rest);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!MountData.hasMount(player, "rabbit") && !MountData.hasMount(player, "bamboo_horse")) {
            return;
        }

        Entity vehicle = player.getVehicle();
        RabbitMountEntity rabbit = vehicle instanceof RabbitMountEntity r ? r : null;
        boolean grounded = (vehicle == null ? player.onGround() : vehicle.onGround())
                && !player.isFallFlying()
                && !player.getAbilities().flying;

        CompoundTag data = mountData(player);

        // 两种飞行坐骑分别维护恢复计时。这样下竹马后，竹马耐力也能正常恢复，
        // 不会因为当前没有 vehicle 而错误地只更新玉兔数据。
        if (MountData.hasMount(player, "rabbit")) {
            boolean rabbitFlying = rabbit != null
                    && !(rabbit instanceof BambooHorseEntity)
                    && rabbit.isFlying();
            updateMountState(data, "Rabbit", rabbitFlying, grounded);
        }
        if (MountData.hasMount(player, "bamboo_horse")) {
            boolean bambooFlying = rabbit instanceof BambooHorseEntity && rabbit.isFlying();
            updateMountState(data, "Bamboo", bambooFlying, grounded);
        }

        if (rabbit != null) {
            String activeSuffix = rabbit instanceof BambooHorseEntity ? "Bamboo" : "Rabbit";
            int energy = energy(data, activeSuffix);
            boolean rest = data.getBoolean(activeSuffix + "Rest");
            int restTicks = Math.max(0, Math.min(1200, data.getInt(activeSuffix + "RestTicks")));

            // 飞行开始/恢复结束等状态切换立即同步；数值变化平时每 5 tick 同步一次。
            boolean importantStateChange = rabbit.isResting() != rest
                    || energy == 0
                    || (!rest && energy == MAX);
            if (importantStateChange || player.tickCount % HUD_SYNC_INTERVAL == 0) {
                rabbit.syncEnergy(energy, rest, restTicks);
            }

            if (energy == 0 && rabbit.isFlying()) {
                rabbit.stopFlying();
            }
        }
    }

    private static void dismount(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.getVehicle() instanceof RabbitMountEntity rabbit && rabbit.isFlying()) {
            rabbit.stopFlying();
            player.stopRiding();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getAmount() > 0) {
            dismount(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        // getEntity 同时包含玩家近战和玩家发射的箭等间接伤害。
        if (event.getAmount() > 0) {
            dismount(event.getSource().getEntity());
        }
    }
}
