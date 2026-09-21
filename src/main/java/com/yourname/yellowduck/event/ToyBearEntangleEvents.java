package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.ToyBearEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Stargazer 布偶熊“根须缠绕”规则复刻。
 * 被缠绕玩家在守卫存活期间几乎不能移动，并且只能攻击自己的守卫；
 * 守卫死亡、玩家死亡/退出或守卫异常消失时自动解除。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ToyBearEntangleEvents {
    public static final String GUARD_TARGET_KEY = "yellowduck_entangle_target";
    public static final String GUARD_OWNER_KEY = "yellowduck_entangle_owner";
    public static final String PLAYER_GUARD_KEY = "yellowduck_entangle_guard";

    private ToyBearEntangleEvents() {}

    public static void markGuard(Mob guard, Player target) {
        if (guard == null || target == null) return;
        guard.getPersistentData().putUUID(GUARD_TARGET_KEY, target.getUUID());
    }

    public static void markGuardOwner(Mob guard, ToyBearEntity bear) {
        if (guard == null || bear == null) return;
        guard.getPersistentData().putUUID(GUARD_OWNER_KEY, bear.getUUID());
    }

    public static void entangle(Player player, LivingEntity guard) {
        if (player == null || guard == null) return;
        player.getPersistentData().putUUID(PLAYER_GUARD_KEY, guard.getUUID());
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                Integer.MAX_VALUE,
                255,
                false,
                false
        ));
    }

    public static boolean isEntangled(Player player) {
        return player != null && player.getPersistentData().hasUUID(PLAYER_GUARD_KEY);
    }

    private static boolean isGuard(Entity entity) {
        return entity != null && entity.getPersistentData().hasUUID(GUARD_TARGET_KEY);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player player) || !isEntangled(player)) return;

        LivingEntity victim = event.getEntity();
        if (victim == player) return;

        UUID guardId = player.getPersistentData().getUUID(PLAYER_GUARD_KEY);
        if (guardId.equals(victim.getUUID()) && isGuard(victim)) return;

        // 对齐 Stargazer：缠绕期间只能攻击负责缠绕自己的守卫。
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isGuard(entity)) return;

        CompoundTag data = entity.getPersistentData();
        UUID targetId = data.getUUID(GUARD_TARGET_KEY);
        MinecraftServer server = entity.getServer();
        if (server == null) return;

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target != null) clearPlayer(target, false);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayer(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayer(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        if (!isEntangled(player)) return;

        UUID guardId = player.getPersistentData().getUUID(PLAYER_GUARD_KEY);
        Entity guard = findEntity(player.getServer(), guardId);
        if (!(guard instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            clearPlayer(player, false);
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, motion.y, 0.0D);
        player.hurtMarked = true;
    }

    public static void clearGuardsOwnedBy(ToyBearEntity bear) {
        if (bear == null || bear.getServer() == null) return;
        UUID ownerId = bear.getUUID();
        MinecraftServer server = bear.getServer();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                CompoundTag tag = entity.getPersistentData();
                if (!tag.hasUUID(GUARD_OWNER_KEY) || !ownerId.equals(tag.getUUID(GUARD_OWNER_KEY))) continue;
                if (tag.hasUUID(GUARD_TARGET_KEY)) {
                    ServerPlayer target = server.getPlayerList().getPlayer(tag.getUUID(GUARD_TARGET_KEY));
                    if (target != null) clearPlayer(target, false);
                }
                entity.discard();
            }
        }
    }

    private static void clearPlayer(Player player, boolean removeGuard) {
        if (player == null) return;
        CompoundTag data = player.getPersistentData();
        UUID guardId = data.hasUUID(PLAYER_GUARD_KEY) ? data.getUUID(PLAYER_GUARD_KEY) : null;

        data.remove(PLAYER_GUARD_KEY);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        if (removeGuard && guardId != null) {
            Entity guard = findEntity(player.getServer(), guardId);
            if (guard != null && isGuard(guard)) guard.discard();
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
}
