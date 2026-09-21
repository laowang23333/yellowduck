package com.yourname.yellowduck.silk;

import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 斯尔克跨实体状态：30 秒复活锁、黑暗疫病增伤/易伤、陷入疯狂、强化火焰、黑暗沸血。
 * 这些状态只写服务器 PersistentData，不依赖 NetCraft 的 Buff 类。
 */
@Mod.EventBusSubscriber(modid = "yellowduck")
public final class SilkCombatEvents {
    private static final String REVIVE_KEY = "YellowduckSilkReviveLock";
    private static final String PLAGUE_UNTIL = "YellowduckSilkPlagueUntil";
    private static final String MAD_UNTIL = "YellowduckSilkMadUntil";
    private static final String FIRE_STACKS = "YellowduckSilkFireStacks";
    private static final String FIRE_UNTIL = "YellowduckSilkFireUntil";
    private static final String BOIL_STACKS = "YellowduckSilkBoilStacks";
    private static final String BOIL_UNTIL = "YellowduckSilkBoilUntil";

    private static final UUID MAD_SPEED_UUID = UUID.fromString("f2f08be4-88d1-4d18-9c1a-6e5b35575d19");
    
    private SilkCombatEvents() {
    }

    public static long clock(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static void lock(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(REVIVE_KEY);
        long until = Math.max(tag.getLong("Until"), clock(player) + SilkBalance.REVIVE_LOCK_TICKS);
        tag.putLong("Until", until);
        player.getPersistentData().put(REVIVE_KEY, tag);
        player.displayClientMessage(Component.literal("§4心智崩溃："
                + Math.max(0, SilkBalance.REVIVE_LOCK_TICKS / 20) + "秒内无法原地复活"), false);
        MountNetwork.sendSilkReviveLock(player, SilkBalance.REVIVE_LOCK_TICKS);
    }

    public static boolean locked(ServerPlayer player) {
        return player.getPersistentData().getCompound(REVIVE_KEY).getLong("Until") > clock(player);
    }

    public static void setPlague(ServerPlayer player, int ticks) {
        player.getPersistentData().putLong(PLAGUE_UNTIL, clock(player) + ticks);
    }

    public static void clearPlague(ServerPlayer player) {
        player.getPersistentData().remove(PLAGUE_UNTIL);
    }

    public static boolean plagued(ServerPlayer player) {
        return player.getPersistentData().getLong(PLAGUE_UNTIL) > clock(player);
    }

    public static void setMadness(ServerPlayer player, int ticks) {
        player.getPersistentData().putLong(MAD_UNTIL, clock(player) + ticks);
        applyMadSpeed(player, true);
    }

    public static boolean mad(ServerPlayer player) {
        return player.getPersistentData().getLong(MAD_UNTIL) > clock(player);
    }

    public static void setStrengthenedFire(ServerPlayer player, int stacks, int ticks) {
        int clamped = Math.max(0, Math.min(SilkBalance.MAX_METER, stacks));
        player.getPersistentData().putInt(FIRE_STACKS, clamped);
        player.getPersistentData().putLong(FIRE_UNTIL, clock(player) + ticks);
    }

    public static int fireStacks(ServerPlayer player) {
        if (player.getPersistentData().getLong(FIRE_UNTIL) <= clock(player)) return 0;
        return player.getPersistentData().getInt(FIRE_STACKS);
    }

    public static void addBoilingBlood(ServerPlayer player, int ticks) {
        int stacks = Math.min(SilkBalance.MAX_METER, Math.max(0, player.getPersistentData().getInt(BOIL_STACKS)) + 1);
        player.getPersistentData().putInt(BOIL_STACKS, stacks);
        player.getPersistentData().putLong(BOIL_UNTIL, clock(player) + ticks);
    }

    public static void clearCombatBuffs(ServerPlayer player) {
        player.getPersistentData().remove(PLAGUE_UNTIL);
        player.getPersistentData().remove(MAD_UNTIL);
        player.getPersistentData().remove(FIRE_STACKS);
        player.getPersistentData().remove(FIRE_UNTIL);
        player.getPersistentData().remove(BOIL_STACKS);
        player.getPersistentData().remove(BOIL_UNTIL);
        applyMadSpeed(player, false);
    }

    private static void applyMadSpeed(ServerPlayer player, boolean enabled) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (attribute.getModifier(MAD_SPEED_UUID) != null) attribute.removeModifier(MAD_SPEED_UUID);
        if (enabled) attribute.addTransientModifier(new AttributeModifier(
                MAD_SPEED_UUID, "yellowduck_silk_madness_slow", SilkBalance.MADNESS_SPEED_MODIFIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) return;

        float multiplier = 1.0F;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (plagued(attacker)) multiplier *= SilkBalance.PLAGUE_OUTGOING_MULTIPLIER;
            if (mad(attacker)) multiplier *= SilkBalance.MADNESS_OUTGOING_MULTIPLIER;
            int fire = fireStacks(attacker);
            if (fire > 0) multiplier *= 1.0F + SilkBalance.STRENGTHENED_FIRE_PER_STACK * fire;
        }
        if (event.getEntity() instanceof ServerPlayer victim && plagued(victim)) {
            multiplier *= SilkBalance.PLAGUE_INCOMING_MULTIPLIER;
        }
        if (multiplier != 1.0F) event.setAmount(event.getAmount() * multiplier);
    }

    @SubscribeEvent
    public static void totem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        // 只保留“禁止复活”的跨死亡计时；Boss 战临时增益/减益不复制到新玩家实体。
        if (event.getOriginal().getPersistentData().contains(REVIVE_KEY)) {
            event.getEntity().getPersistentData().put(
                    REVIVE_KEY,
                    event.getOriginal().getPersistentData().getCompound(REVIVE_KEY).copy());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        long now = clock(player);

        CompoundTag revive = player.getPersistentData().getCompound(REVIVE_KEY);
        if (revive.contains("Until")) {
            long remaining = revive.getLong("Until") - now;
            if (remaining <= 0L) {
                player.getPersistentData().remove(REVIVE_KEY);
                MountNetwork.sendSilkReviveLock(player, 0);
            }
        }

        if (player.getPersistentData().contains(MAD_UNTIL)) {
            if (player.getPersistentData().getLong(MAD_UNTIL) <= now) {
                player.getPersistentData().remove(MAD_UNTIL);
                applyMadSpeed(player, false);
            } else {
                applyMadSpeed(player, true);
            }
        }

        if (player.getPersistentData().getLong(FIRE_UNTIL) <= now) {
            player.getPersistentData().remove(FIRE_STACKS);
            player.getPersistentData().remove(FIRE_UNTIL);
        }
        if (player.getPersistentData().getLong(PLAGUE_UNTIL) <= now) {
            player.getPersistentData().remove(PLAGUE_UNTIL);
        }

        long boilUntil = player.getPersistentData().getLong(BOIL_UNTIL);
        if (boilUntil > now) {
            if (SilkBalance.BOILING_BLOOD_INTERVAL > 0 && player.tickCount % SilkBalance.BOILING_BLOOD_INTERVAL == 0) {
                int stacks = Math.max(1, player.getPersistentData().getInt(BOIL_STACKS));
                player.hurt(player.damageSources().magic(), Math.min(
                        SilkBalance.BOILING_BLOOD_DAMAGE_CAP, SilkBalance.BOILING_BLOOD_DAMAGE_PER_STACK * stacks));
            }
        } else {
            player.getPersistentData().remove(BOIL_STACKS);
            player.getPersistentData().remove(BOIL_UNTIL);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearCombatBuffs(player);
        }
    }
}
