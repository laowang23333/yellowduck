package com.yourname.yellowduck.event;

import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.entity.RootVineEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import com.yourname.yellowduck.registry.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 布偶熊“根须缠绕”。
 *
 * 根须存活时只给玩家一个很短的根须缠绕效果并持续刷新；根须死亡后立即清除。
 * 即使 Mohist/其它事件环境漏掉一次死亡清理，效果也会在很短时间内自然到期，
 * 不再使用 Integer.MAX_VALUE 导致 Buff 显示数万小时甚至永久残留。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class ToyBearEntangleEvents {
    public static final String GUARD_TARGET_KEY = "yellowduck_entangle_target";
    public static final String GUARD_OWNER_KEY = "yellowduck_entangle_owner";
    public static final String PLAYER_GUARD_KEY = "yellowduck_entangle_guard";

    /** 根须存活时 Buff 始终刷新为 2 秒；根须死亡后即使主动清理漏掉，也最多残留约 2 秒。 */
    public static final int ROOT_EFFECT_REFRESH_TICKS = 40;

    private ToyBearEntangleEvents() {}

    /** ToyBearEntity 兼容入口：先在临时守卫身上记录目标。 */
    public static void markGuard(Mob guard, Player target) {
        if (guard == null || target == null) return;
        guard.getPersistentData().putUUID(GUARD_TARGET_KEY, target.getUUID());
    }

    /** ToyBearEntity 兼容入口：记录是哪只熊释放的技能，便于熊死亡时清理。 */
    public static void markGuardOwner(Mob guard, ToyBearEntity bear) {
        if (guard == null || bear == null) return;
        guard.getPersistentData().putUUID(GUARD_OWNER_KEY, bear.getUUID());
    }

    /**
     * ToyBearEntity 在临时骷髅入场后会调用这里。
     * 直接把临时守卫换成 RootVineEntity，不修改布偶熊本体的技能调度。
     */
    public static void entangle(Player player, LivingEntity temporaryGuard) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel serverLevel)
                || temporaryGuard == null) return;

        CompoundTag guardData = temporaryGuard.getPersistentData();
        UUID ownerBearId = guardData.hasUUID(GUARD_OWNER_KEY)
                ? guardData.getUUID(GUARD_OWNER_KEY)
                : null;

        RootVineEntity root = RootVineEntity.create(serverLevel, player, ownerBearId);
        LivingEntity activeGuard = temporaryGuard;

        if (root != null) {
            root.getPersistentData().putUUID(GUARD_TARGET_KEY, player.getUUID());
            if (ownerBearId != null) root.getPersistentData().putUUID(GUARD_OWNER_KEY, ownerBearId);

            if (serverLevel.addFreshEntity(root)) {
                activeGuard = root;
                temporaryGuard.discard();
            }
        }

        serverPlayer.getPersistentData().putUUID(PLAYER_GUARD_KEY, activeGuard.getUUID());
        refreshRootEffect(serverPlayer);
    }

    public static boolean isEntangled(Player player) {
        return player != null && player.getPersistentData().hasUUID(PLAYER_GUARD_KEY);
    }

    /**
     * 把根须效果维持在短时长。
     * 同时兼容旧版本已经留下的 Integer.MAX_VALUE Buff：发现异常长持续时间时先删后重加。
     */
    public static void refreshRootEffect(Player player) {
        if (player == null || player.level().isClientSide) return;

        MobEffectInstance current = player.getEffect(ModEffects.ROOT_ENTANGLE.get());
        if (current != null && current.getDuration() > ROOT_EFFECT_REFRESH_TICKS * 3) {
            player.removeEffect(ModEffects.ROOT_ENTANGLE.get());
        }

        player.addEffect(new MobEffectInstance(
                ModEffects.ROOT_ENTANGLE.get(),
                ROOT_EFFECT_REFRESH_TICKS,
                0,
                false,
                false,
                true
        ));
    }

    private static boolean isGuard(Entity entity) {
        return entity instanceof RootVineEntity
                || (entity != null && entity.getPersistentData().hasUUID(GUARD_TARGET_KEY));
    }

    /** 被根须缠绕的玩家不能对外造成伤害。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof Player player && isEntangled(player)) {
            event.setCanceled(true);
        }
    }

    /** 提前取消左键实体，避免客户端/服务端出现一下挥击判定。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isEntangled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof ToyBearEntity bear) {
            clearGuardsOwnedBy(bear);
            return;
        }

        if (!isGuard(entity)) return;

        UUID targetId = getGuardTarget(entity);
        MinecraftServer server = entity.getServer();
        if (server == null || targetId == null) return;

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

        UUID rootId = player.getPersistentData().getUUID(PLAYER_GUARD_KEY);
        Entity guard = findEntity(player.getServer(), rootId);
        if (!(guard instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            clearPlayer(player, false);
            return;
        }

        // 根须存活期间锁死水平位移并禁止向上跳。
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        player.hurtMarked = true;

        // 每 tick 刷新为 2 秒，而不是永久时长。
        refreshRootEffect(player);
    }

    public static void clearGuardsOwnedBy(ToyBearEntity bear) {
        if (bear == null || bear.getServer() == null) return;
        UUID ownerId = bear.getUUID();
        MinecraftServer server = bear.getServer();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                CompoundTag tag = entity.getPersistentData();
                UUID storedOwner = null;

                if (entity instanceof RootVineEntity root) {
                    storedOwner = root.getOwnerBearUUID();
                }
                if (storedOwner == null && tag.hasUUID(GUARD_OWNER_KEY)) {
                    storedOwner = tag.getUUID(GUARD_OWNER_KEY);
                }

                if (!ownerId.equals(storedOwner)) continue;

                UUID targetId = getGuardTarget(entity);
                if (targetId != null) {
                    ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                    if (target != null) clearPlayer(target, false);
                }
                entity.discard();
            }
        }
    }

    /** RootVineEntity 自己消失/死亡时调用。 */
    public static void releaseByRoot(RootVineEntity root) {
        if (root == null || root.level().isClientSide) return;
        MinecraftServer server = root.getServer();
        UUID targetId = root.getTargetUUID();
        if (server == null || targetId == null) return;

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) return;

        CompoundTag data = target.getPersistentData();
        if (!data.hasUUID(PLAYER_GUARD_KEY)
                || !root.getUUID().equals(data.getUUID(PLAYER_GUARD_KEY))) return;

        clearPlayer(target, false);
    }

    private static UUID getGuardTarget(Entity entity) {
        if (entity instanceof RootVineEntity root && root.getTargetUUID() != null) {
            return root.getTargetUUID();
        }
        CompoundTag tag = entity.getPersistentData();
        return tag.hasUUID(GUARD_TARGET_KEY) ? tag.getUUID(GUARD_TARGET_KEY) : null;
    }

    private static void clearPlayer(Player player, boolean removeGuard) {
        if (player == null) return;
        CompoundTag data = player.getPersistentData();
        UUID guardId = data.hasUUID(PLAYER_GUARD_KEY) ? data.getUUID(PLAYER_GUARD_KEY) : null;

        data.remove(PLAYER_GUARD_KEY);
        player.removeEffect(ModEffects.ROOT_ENTANGLE.get());

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
