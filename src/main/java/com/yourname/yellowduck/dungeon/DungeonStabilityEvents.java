package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.garmr.GarmrBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 副本稳定性兜底：
 * 1. 服务端启动后主动加载异常中断实例的场地区块，清理残留 Boss/召唤物；
 * 2. 新主 Boss 加入场地时清理同场遗留的同类型 Boss，防止一场出现两只主 Boss；
 * 3. 加姆受到明确致死伤害后，在本 tick 末尾再次确认死亡并补做通关结算，避免死亡事件链异常后卡在战斗阶段。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DungeonStabilityEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DUNGEON_TAG = "YellowDuckDungeon";

    private static MinecraftServer observedServer;
    private static boolean startupSweepDone;
    private static final Map<UUID, PendingGarmrFatal> PENDING_GARMR_FATAL = new HashMap<>();

    private DungeonStabilityEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        if (observedServer != server) {
            observedServer = server;
            startupSweepDone = false;
            PENDING_GARMR_FATAL.clear();
        }

        if (!startupSweepDone) {
            ServerLevel dungeon = server.getLevel(DungeonManager.DUNGEON_LEVEL);
            if (dungeon != null) {
                runStartupSweep(server, dungeon);
                startupSweepDone = true;
            }
        }

        if (!PENDING_GARMR_FATAL.isEmpty()) {
            finishPendingGarmrDeaths(server);
        }
    }

    /**
     * 当前 DungeonManager 的恢复流程只有在区块已经加载时才能枚举到旧实体。
     * 这里先主动加载异常中断实例覆盖到的区块，再删除带旧实例 ID 的运行时实体。
     */
    private static void runStartupSweep(MinecraftServer server, ServerLevel level) {
        DungeonSavedData data = DungeonSavedData.get(server);
        List<DungeonSavedData.StaleInstance> staleInstances = data.staleInstances();
        if (staleInstances.isEmpty()) return;

        int removedTotal = 0;
        Set<String> loadedRegions = new HashSet<>();
        for (DungeonSavedData.StaleInstance stale : staleInstances) {
            try {
                int radius = Math.max(16, stale.radius()) + 16;
                String regionKey = stale.origin().getX() + ":" + stale.origin().getY() + ":"
                        + stale.origin().getZ() + ":" + radius;
                if (loadedRegions.add(regionKey)) {
                    loadArenaChunks(level, stale.origin(), radius);
                }
                removedTotal += purgeStaleInstanceEntities(level, stale, radius);
            } catch (Throwable error) {
                LOGGER.error("清理异常中断副本实体失败：instance={} dungeon={}",
                        stale.id(), stale.dungeonId(), error);
            }
        }

        if (removedTotal > 0) {
            LOGGER.warn("YellowDuck 启动恢复已清理 {} 个异常中断副本残留实体。", removedTotal);
        }
    }

    private static void loadArenaChunks(ServerLevel level, BlockPos origin, int radius) {
        int minX = (origin.getX() - radius) >> 4;
        int maxX = (origin.getX() + radius) >> 4;
        int minZ = (origin.getZ() - radius) >> 4;
        int maxZ = (origin.getZ() + radius) >> 4;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static int purgeStaleInstanceEntities(ServerLevel level,
                                                   DungeonSavedData.StaleInstance stale,
                                                   int radius) {
        BlockPos origin = stale.origin();
        AABB box = new AABB(
                origin.getX() - radius, origin.getY() - 24, origin.getZ() - radius,
                origin.getX() + radius + 1, origin.getY() + 96, origin.getZ() + radius + 1
        );

        ResourceLocation configuredBoss = null;
        DungeonDefinition definition = DungeonConfig.get(stale.dungeonId());
        if (definition != null) {
            try {
                configuredBoss = new ResourceLocation(definition.bossEntity());
            } catch (Exception ignored) {
            }
        }

        ResourceLocation finalConfiguredBoss = configuredBoss;
        List<Entity> remove = level.getEntities((Entity) null, box, entity -> {
            if (entity == null || entity instanceof Player || entity.isRemoved()) return false;
            if (entity.getPersistentData().hasUUID(DUNGEON_TAG)
                    && stale.id().equals(entity.getPersistentData().getUUID(DUNGEON_TAG))) {
                return true;
            }
            if (finalConfiguredBoss != null) {
                ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                return finalConfiguredBoss.equals(entityId);
            }
            return false;
        });

        int removed = 0;
        for (Entity entity : new ArrayList<>(remove)) {
            if (entity == null || entity.isRemoved()) continue;
            entity.discard();
            removed++;
        }
        return removed;
    }

    /**
     * 新主 Boss 真正加入副本维度前做一次同场检查。
     * 如果本场已经登记了存活主 Boss，则拒绝第二次生成；
     * 如果还没登记而场内存在同类型旧 Boss，则把旧 Boss 当作重启残留清掉。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(DungeonManager.DUNGEON_LEVEL)) return;
        Entity joining = event.getEntity();
        if (!(joining instanceof LivingEntity) || joining instanceof Player) return;
        if (!joining.getPersistentData().hasUUID(DUNGEON_TAG)) return;

        UUID instanceId = joining.getPersistentData().getUUID(DUNGEON_TAG);
        DungeonInstance instance = findActiveInstance(level.getServer(), instanceId);
        if (instance == null || instance.state == DungeonInstance.State.CLOSING) return;

        ResourceLocation actualId = ForgeRegistries.ENTITY_TYPES.getKey(joining.getType());
        ResourceLocation expectedId;
        try {
            expectedId = new ResourceLocation(instance.definition.bossEntity());
        } catch (Exception ignored) {
            return;
        }
        if (!expectedId.equals(actualId)) return;

        if (instance.mainBossId != null) {
            Entity current = level.getEntity(instance.mainBossId);
            if (current != null && current != joining && current.isAlive() && !current.isRemoved()) {
                LOGGER.warn("阻止副本重复生成主 Boss：instance={} dungeon={} old={} new={}",
                        instance.id, instance.definition.id(), current.getUUID(), joining.getUUID());
                event.setCanceled(true);
                return;
            }
        }

        int radius = instance.arenaRadius + 16;
        AABB box = new AABB(
                instance.origin.getX() - radius, instance.origin.getY() - 24, instance.origin.getZ() - radius,
                instance.origin.getX() + radius + 1, instance.origin.getY() + 96, instance.origin.getZ() + radius + 1
        );
        List<Entity> duplicates = level.getEntities((Entity) null, box,
                entity -> entity != null
                        && entity != joining
                        && !entity.isRemoved()
                        && entity.getType() == joining.getType());

        for (Entity duplicate : new ArrayList<>(duplicates)) {
            if (duplicate == null || duplicate.isRemoved()) continue;
            LOGGER.warn("清理副本场地遗留主 Boss：instance={} dungeon={} staleBoss={}",
                    instance.id, instance.definition.id(), duplicate.getUUID());
            duplicate.discard();
        }
    }

    /**
     * 记录加姆已经收到“足以致死”的最终 LivingDamageEvent。
     * 正常情况下 LivingDeathEvent 会立即完成副本；这里仅作为同 tick 末尾的第二道保险。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onGarmrFatalDamage(LivingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof GarmrBoss boss)) return;
        if (!(boss.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(DungeonManager.DUNGEON_LEVEL)) return;
        if (!boss.getPersistentData().hasUUID(DUNGEON_TAG)) return;
        if (event.getAmount() + 0.0001F < boss.getHealth()) return;

        UUID instanceId = boss.getPersistentData().getUUID(DUNGEON_TAG);
        PENDING_GARMR_FATAL.put(boss.getUUID(),
                new PendingGarmrFatal(instanceId, boss.getUUID(), level.getServer().getTickCount()));
    }

    private static void finishPendingGarmrDeaths(MinecraftServer server) {
        ServerLevel level = server.getLevel(DungeonManager.DUNGEON_LEVEL);
        if (level == null) return;
        int now = server.getTickCount();

        var iterator = PENDING_GARMR_FATAL.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingGarmrFatal pending = iterator.next().getValue();
            if (now < pending.checkTick()) continue;

            Entity raw = level.getEntity(pending.bossId());
            if (raw instanceof GarmrBoss boss
                    && boss.isAlive()
                    && !boss.isDeadOrDying()
                    && boss.getHealth() > 0.0F) {
                iterator.remove();
                continue;
            }

            DungeonInstance instance = findActiveInstance(server, pending.instanceId());
            if (instance != null
                    && instance.state == DungeonInstance.State.FIGHTING
                    && pending.bossId().equals(instance.mainBossId)) {
                instance.mainBossDead = true;
                LOGGER.warn("加姆死亡事件未及时完成副本，已由致死伤害兜底结算：instance={}", instance.id);
                DungeonCompletionControllers.forInstance(instance)
                        .onMainBossDeath(instance, server, level);
            }
            iterator.remove();
        }
    }

    private static DungeonInstance findActiveInstance(MinecraftServer server, UUID instanceId) {
        if (server == null || instanceId == null) return null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DungeonInstance instance = DungeonManager.instanceOf(player);
            if (instance != null && instance.id.equals(instanceId)) return instance;
        }
        return null;
    }

    private record PendingGarmrFatal(UUID instanceId, UUID bossId, int checkTick) {
    }
}
