package com.yourname.yellowduck.silk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 疫病无熊爆炸后的禁复活：图腾禁用，死亡后观察者等待剩余时间。 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SilkCombatEvents {
    private static final String KEY = "YellowduckSilkReviveLock";
    public static long clock(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static void lock(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        tag.putLong("Until", Math.max(tag.getLong("Until"), clock(player) + SilkBalance.REVIVE_LOCK_TICKS));
        if (!tag.getBoolean("Ghost")) tag.putInt("Mode", player.gameMode.getGameModeForPlayer().getId());
        player.getPersistentData().put(KEY, tag);
        player.displayClientMessage(Component.literal("§4暗黑疫病：30秒内无法复活！"), false);
    }
    public static boolean locked(ServerPlayer player) {
        return player.getPersistentData().getCompound(KEY).getLong("Until") > clock(player);
    }
    @SubscribeEvent public static void totem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY, event.getOriginal().getPersistentData().getCompound(KEY).copy());
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player && locked(player)) {
            CompoundTag tag = player.getPersistentData().getCompound(KEY);
            tag.putBoolean("Ghost", true);
            tag.putDouble("X", player.getX()); tag.putDouble("Y", player.getY()); tag.putDouble("Z", player.getZ());
            tag.putString("Dimension", player.level().dimension().location().toString());
            player.getPersistentData().put(KEY, tag);
            player.setGameMode(GameType.SPECTATOR);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().contains(KEY)) return;
        CompoundTag tag = player.getPersistentData().getCompound(KEY);
        long remaining = tag.getLong("Until") - clock(player);
        if (remaining <= 0) {
            if (tag.getBoolean("Ghost")) player.setGameMode(GameType.byId(tag.getInt("Mode")));
            player.getPersistentData().remove(KEY);
        } else if (!tag.getBoolean("Ghost")) {
            if (player.tickCount % 20 == 0) player.displayClientMessage(Component.literal("§4暗黑疫病：禁止复活 " + ((remaining + 19) / 20) + "秒"), true);
        } else {
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            if (player.tickCount % 20 == 0) {
                player.displayClientMessage(Component.literal("§c暗黑疫病：" + ((remaining + 19) / 20) + "秒后复活"), true);
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(tag.getString("Dimension")));
                var level = player.server.getLevel(key);
                if (level != null) player.teleportTo(level, tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), player.getYRot(), player.getXRot());
            }
        }
    }
    public static void resizeSlime(net.minecraft.world.entity.monster.Slime slime, int size) {
        // 使用公开NBT接口，兼容setSize在不同映射/发行版中的访问级别。
        CompoundTag saved = new CompoundTag();
        slime.addAdditionalSaveData(saved); saved.putInt("Size", size - 1);
        slime.readAdditionalSaveData(saved);
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
        // 防止3只召唤史莱姆死亡后分裂成无主小史莱姆。
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.monster.Slime slime
                && slime.getPersistentData().hasUUID("SilkOwner")) resizeSlime(slime, 1);
    }
    @SubscribeEvent public static void summonDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().hasUUID("SilkOwner")) event.setCanceled(true);
    }

}
