package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 斯尔克心智腐蚀死亡与 NetCraft 灵魂药水的联动。NetCraft 负责死亡画面/复活位置，本类只做20秒锁定。 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SilkCombatEvents {
    private static final String KEY = "YellowduckSilkReviveLock";
    private SilkCombatEvents() {}
    public static long clock(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static void lock(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        long until = Math.max(tag.getLong("Until"), clock(player) + SilkBalance.REVIVE_LOCK_TICKS);
        tag.putLong("Until", until);
        player.getPersistentData().put(KEY, tag);
        player.displayClientMessage(Component.literal("§4心智腐蚀：NetCraft原地复活禁用20秒"), false);
        MountNetwork.sendSilkReviveLock(player, SilkBalance.REVIVE_LOCK_TICKS);
    }
    public static boolean locked(ServerPlayer player) {
        return player.getPersistentData().getCompound(KEY).getLong("Until") > clock(player);
    }
    @SubscribeEvent public static void totem(LivingUseTotemEvent event) {
        // 心智腐蚀死亡不允许图腾绕过 NetCraft 的死亡界面。
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(KEY))
            event.getEntity().getPersistentData().put(KEY, event.getOriginal().getPersistentData().getCompound(KEY).copy());
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        if (!tag.contains("Until")) return;
        long remaining = tag.getLong("Until") - clock(player);
        if (remaining <= 0) {
            player.getPersistentData().remove(KEY);
            MountNetwork.sendSilkReviveLock(player, 0);
        } else if (player.tickCount % 20 == 0) {
            player.displayClientMessage(Component.literal("§4心智腐蚀：原地复活还需 " + ((remaining + 19) / 20) + " 秒"), true);
        }
    }
    public static void resizeSlime(net.minecraft.world.entity.monster.Slime slime, int size) {
        CompoundTag saved = new CompoundTag(); slime.addAdditionalSaveData(saved); saved.putInt("Size", size - 1); slime.readAdditionalSaveData(saved);
        if (slime.isAlive()) slime.setHealth(slime.getMaxHealth());
    }
    @SubscribeEvent public static void summonTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        var mob = event.getEntity();
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel sl) || mob.tickCount % 20 != 0) return;
        if (mob.getPersistentData().hasUUID("SilkOwner")) {
            var owner = sl.getEntity(mob.getPersistentData().getUUID("SilkOwner"));
            if (!(owner instanceof SilkBoss boss) || !boss.isAlive()) mob.discard();
        }
    }
    @SubscribeEvent public static void slimeDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.monster.Slime slime && slime.getPersistentData().hasUUID("SilkOwner")) resizeSlime(slime, 1);
    }
    @SubscribeEvent public static void summonDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().hasUUID("SilkOwner")) event.setCanceled(true);
    }
}
