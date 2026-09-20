package com.yourname.yellowduck.dungeon;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.party.AdventureParty;
import com.yourname.yellowduck.party.PartyManager;
import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.yourname.yellowduck.registry.ModBlocks;
import com.yourname.yellowduck.network.MountNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 公共虚空维度副本管理器。所有队伍共用 yellowduck:dungeon，实例之间按 X 方向分隔。
 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DungeonManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> DUNGEON_LEVEL = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation(YellowDuckMod.MOD_ID, "dungeon"));

    private static final Map<UUID, DungeonInstance> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, UUID> PLAYER_INSTANCE = new HashMap<>();
    private static final Map<UUID, UUID> PARTY_INSTANCE = new HashMap<>();
    private static final Set<Integer> USED_SLOTS = new HashSet<>();

    private static MinecraftServer recoveryServer;
    private static boolean recoveryDone;

    private DungeonManager() {}

    public static DungeonInstance instanceOf(ServerPlayer player) {
        UUID id = PLAYER_INSTANCE.get(player.getUUID());
        return id == null ? null : ACTIVE.get(id);
    }

    /** 即使某个队员已经主动离开实例，只要这个队伍的副本还没结束，队伍仍然锁定。 */
    public static boolean isPartyInDungeon(UUID partyId) {
        return partyId != null && PARTY_INSTANCE.containsKey(partyId);
    }

    /** 旧的“按队伍选择直接开本”入口彻底禁用，防止绕过副本柱子。 */
    public static boolean startSelectedDungeon(ServerPlayer leader) {
        leader.sendSystemMessage(Component.literal("§c副本只能通过已绑定的副本柱子开始。"));
        return false;
    }

    /**
     * 唯一正常开本入口：服务端重新读取玩家正在使用的 meet_stone 的 DungeonId。
     * 客户端不能通过伪造菜单按钮把艳后柱子改成小樱或其他副本。
     */
    public static boolean startDungeonFromPillar(ServerPlayer leader, BlockPos pillarPos) {
        recoverIfNeeded(leader.server);
        if (pillarPos == null || !leader.level().getBlockState(pillarPos).is(ModBlocks.MEET_STONE.get())) {
            leader.sendSystemMessage(Component.literal("§c副本柱子已经不存在。"));
            return false;
        }
        double cx = pillarPos.getX() + 0.5D;
        double cy = pillarPos.getY() + 0.5D;
        double cz = pillarPos.getZ() + 0.5D;
        if (leader.distanceToSqr(cx, cy, cz) > 64.0D) {
            leader.sendSystemMessage(Component.literal("§c你离副本柱子太远，无法开始挑战。"));
            return false;
        }
        if (!(leader.level().getBlockEntity(pillarPos) instanceof MeetStoneBlockEntity stone) || !stone.isBound()) {
            leader.sendSystemMessage(Component.literal("§c这根副本柱子尚未绑定副本。"));
            return false;
        }
        DungeonDefinition def = DungeonConfig.get(stone.getDungeonId());
        if (def == null || !def.enabled()) {
            leader.sendSystemMessage(Component.literal("§c这根柱子绑定的副本不存在或当前未启用。"));
            return false;
        }
        return startDungeon(leader, def);
    }

    private static boolean startDungeon(ServerPlayer leader, DungeonDefinition def) {
        recoverIfNeeded(leader.server);

        AdventureParty party = PartyManager.getParty(leader);
        if (party == null) {
            leader.sendSystemMessage(Component.literal("§c请先在这根副本柱子创建冒险队伍。"));
            return false;
        }
        if (!party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c只有队长可以开始副本。"));
            return false;
        }
        if (PARTY_INSTANCE.containsKey(party.id())) {
            leader.sendSystemMessage(Component.literal("§c你的队伍已经在副本中。"));
            return false;
        }
        if (!def.id().equalsIgnoreCase(party.selectedDungeon())) {
            leader.sendSystemMessage(Component.literal("§c当前队伍不是在这根副本柱子创建/切换的，请重新打开正确的柱子。"));
            return false;
        }
        int size = party.members().size();
        if (size < def.minPlayers() || size > def.maxPlayers()) {
            leader.sendSystemMessage(Component.literal("§c该副本人数要求：" + def.minPlayers() + "～" + def.maxPlayers() + "人。"));
            return false;
        }
        if (!party.allReady()) {
            leader.sendSystemMessage(Component.literal("§c所有队员都必须准备后才能开始。"));
            return false;
        }

        List<ServerPlayer> players = new ArrayList<>();
        for (UUID uuid : party.members()) {
            ServerPlayer player = leader.server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                leader.sendSystemMessage(Component.literal("§c有队员不在线，无法开始副本。"));
                return false;
            }
            players.add(player);
        }

        ServerLevel level = leader.server.getLevel(DUNGEON_LEVEL);
        if (level == null) {
            leader.sendSystemMessage(Component.literal("§c副本维度 yellowduck:dungeon 未加载，请检查数据包资源。"));
            return false;
        }
        int slot = allocateSlot();
        if (slot < 0) {
            leader.sendSystemMessage(Component.literal("§c当前同时进行的副本数量已达到上限。"));
            return false;
        }

        BlockPos origin = findSafeOrigin(slot, DungeonConfig.arenaRadius());
        if (origin == null) {
            USED_SLOTS.remove(slot);
            leader.sendSystemMessage(Component.literal("§c找不到安全的副本实例区域，请稍后再试。"));
            return false;
        }
        DungeonInstance instance = new DungeonInstance(slot, party.id(), party.leader(), def, origin);
        instance.revivesRemaining = def.initialRevives(players.size());
        instance.initialRevives = instance.revivesRemaining;
        for (ServerPlayer player : players) {
            instance.participants.add(player.getUUID());
            instance.rewardEligible.add(player.getUUID());
            instance.life.put(player.getUUID(), new DungeonInstance.LifeState());
            instance.returns.put(player.getUUID(), new DungeonInstance.ReturnPoint(
                    player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer()));
        }

        try {
            DungeonArenaBuilder.prepare(level, instance);
        } catch (Throwable t) {
            USED_SLOTS.remove(slot);
            leader.sendSystemMessage(Component.literal("§c创建副本竞技场失败：" + t.getMessage()));
            return false;
        }

        ACTIVE.put(instance.id, instance);
        PARTY_INSTANCE.put(party.id(), instance.id);
        DungeonSavedData.get(leader.server).trackInstance(instance);
        PartyManager.invalidateInvitesForParty(party.id());

        for (ServerPlayer player : players) {
            PLAYER_INSTANCE.put(player.getUUID(), instance.id);
            player.closeContainer();
            // 副本战斗统一使用生存模式；离本时按 ReturnPoint 恢复玩家原游戏模式。
            player.setGameMode(GameType.SURVIVAL);
            teleportInto(level, instance, player);
        }
        instance.state = DungeonInstance.State.COUNTDOWN;
        instance.stateTicks = 0;
        PartyManager.clearReady(leader.server, party);
        broadcast(instance, leader.server, Component.literal("§6[副本] §f已进入 §e" + def.displayName()
                + "§f，Boss将在 §e" + def.bossSpawnDelaySeconds() + "秒 §f后出现。"));
        broadcast(instance, leader.server, Component.literal("§6[副本] §f团队复活次数：§a" + instance.revivesRemaining));
        syncHud(instance, leader.server);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        DungeonInstance instance = instanceOf(player);
        if (instance == null) return false;
        boolean rewardStage = instance.state == DungeonInstance.State.REWARD;
        if (!rewardStage) instance.rewardEligible.remove(player.getUUID());
        instance.participants.remove(player.getUUID());
        PLAYER_INSTANCE.remove(player.getUUID());
        player.closeContainer();
        returnPlayer(player, instance.returns.get(player.getUUID()));
        DungeonSavedData.get(player.server).clearReturn(player.getUUID());
        restoreDeathItems(player);
        if (rewardStage) DungeonRewardManager.deliverPending(player);
        clearHud(player);

        player.sendSystemMessage(Component.literal(rewardStage
                ? "§a你已离开副本，通关奖励资格保留。"
                : "§e你已主动离开副本，本场不再获得副本奖励。"));
        if (instance.participants.isEmpty()) {
            close(instance, player.server, rewardStage, rewardStage ? "奖励领取完成" : "队伍已经离开副本");
        }
        return true;
    }

    public static void reopenRewards(ServerPlayer player) {
        DungeonInstance instance = instanceOf(player);
        if (instance != null && instance.state == DungeonInstance.State.REWARD) {
            // 预览菜单是服务端只读的，允许误关后重新查看，但不会重新Roll或复制奖励。
            DungeonRewardManager.openPreview(player, instance);
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        recoverIfNeeded(server);
        if (ACTIVE.isEmpty()) return;
        for (DungeonInstance instance : new ArrayList<>(ACTIVE.values())) tickInstance(instance, server);
    }

    private static void recoverIfNeeded(MinecraftServer server) {
        if (recoveryServer != server) {
            recoveryServer = server;
            recoveryDone = false;
            ACTIVE.clear();
            PLAYER_INSTANCE.clear();
            PARTY_INSTANCE.clear();
            USED_SLOTS.clear();
        }
        if (recoveryDone) return;

        ServerLevel level = server.getLevel(DUNGEON_LEVEL);
        if (level == null) return;
        DungeonSavedData data = DungeonSavedData.get(server);
        try {
            // 先解除旧版本可能残留的永久强加载，再清理上次异常退出时记录的实例区域。
            DungeonArenaBuilder.releaseConfiguredForcedChunks(level);
            for (DungeonSavedData.StaleInstance stale : data.staleInstances()) {
                try {
                    // 先访问/清理方块使旧实例相关区块加载，再删除其中残留实体。
                    DungeonArenaBuilder.cleanupRecovered(level, stale);
                    cleanupRecoveredEntities(level, stale);
                    data.untrackInstance(stale.id());
                } catch (Throwable t) {
                    LOGGER.error("清理异常重启残留副本失败：{}", stale.id(), t);
                }
            }
            recoveryDone = true;
        } catch (Throwable t) {
            LOGGER.error("YellowDuck 副本重启恢复检查失败", t);
        }
    }

    private static void tickInstance(DungeonInstance instance, MinecraftServer server) {
        ServerLevel level = server.getLevel(DUNGEON_LEVEL);
        if (level == null) {
            close(instance, server, false, "副本维度不可用");
            return;
        }
        instance.ageTicks++;
        instance.stateTicks++;
        if (instance.state == DungeonInstance.State.COUNTDOWN || instance.state == DungeonInstance.State.FIGHTING) {
            checkRevives(instance, server, level);
            if (instance.ageTicks % 10 == 0) syncHud(instance, server);
        }

        if (instance.state == DungeonInstance.State.COUNTDOWN) {
            if (instance.stateTicks >= instance.definition.bossSpawnDelaySeconds() * 20) spawnBoss(instance, server, level);
            return;
        }
        if (instance.state == DungeonInstance.State.FIGHTING) {
            instance.fightTicks++;
            if (instance.fightTicks >= instance.definition.timeLimitSeconds() * 20) {
                close(instance, server, false, "挑战超时");
                return;
            }
            DungeonCompletionControllers.forInstance(instance).tick(instance, server, level);
            // 通关控制器可能已经把状态切到REWARD，不能同一tick继续走团灭失败判定。
            if (instance.state != DungeonInstance.State.FIGHTING) return;
            checkWipe(instance, server);
            return;
        }
        if (instance.state == DungeonInstance.State.REWARD) {
            if (instance.stateTicks >= instance.definition.rewardPreviewSeconds() * 20) {
                close(instance, server, true, "奖励阶段结束");
            }
        }
    }

    private static void spawnBoss(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
        ResourceLocation id;
        try {
            id = new ResourceLocation(instance.definition.bossEntity());
        } catch (Exception ex) {
            close(instance, server, false, "Boss实体ID无效");
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            close(instance, server, false, "Boss实体不存在：" + id);
            return;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity boss)) {
            close(instance, server, false, "Boss实体不是LivingEntity：" + id);
            return;
        }
        BlockPos spawn = DungeonArenaBuilder.findBossSpawn(level, instance);
        boss.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 180F, 0F);
        boss.getPersistentData().putUUID("YellowDuckDungeon", instance.id);
        if (!level.addFreshEntity(boss)) {
            close(instance, server, false, "Boss生成失败");
            return;
        }
        instance.mainBossId = boss.getUUID();
        instance.mainBossSpawned = true;
        instance.state = DungeonInstance.State.FIGHTING;
        instance.stateTicks = 0;
        instance.fightTicks = 0;
        broadcast(instance, server, Component.literal("§4[副本] §cBoss已出现：§f" + instance.definition.displayName()));
    }

    private static void checkRevives(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
        for (UUID uuid : new ArrayList<>(instance.participants)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            DungeonInstance.LifeState life = instance.life.computeIfAbsent(uuid, k -> new DungeonInstance.LifeState());
            if (life.forceSpectator && player.isAlive() && !player.isSpectator()) {
                player.setGameMode(GameType.SPECTATOR);
                teleportInto(level, instance, player);
                continue;
            }
            if ((!player.isAlive() || player.isDeadOrDying()) && !life.deathSeen) {
                life.deathSeen = true;
                life.deathAge = instance.ageTicks;
            }
            if (life.deathSeen && player.isAlive() && !player.isDeadOrDying()) {
                boolean lastChanceWipeRescue = instance.revivesRemaining <= 0
                        && !life.vanillaRespawned
                        && (instance.wipeTicks >= 0 || noOtherCombatantWasAlive(instance, server, uuid));

                if (instance.revivesRemaining > 0) {
                    instance.revivesRemaining--;
                    life.deathSeen = false;
                    life.vanillaRespawned = false;
                    life.forceSpectator = false;
                    player.setGameMode(GameType.SURVIVAL);
                    restoreDeathItems(player);
                    if (!player.level().dimension().equals(DUNGEON_LEVEL)) teleportInto(level, instance, player);
                    instance.wipeTicks = -1;
                    broadcast(instance, server, Component.literal("§6[副本] §f" + player.getGameProfile().getName()
                            + " 已复活，团队剩余复活次数：§a" + instance.revivesRemaining));
                } else if (lastChanceWipeRescue) {
                    // 复活次数已经为0，但如果此前已经全灭，则30秒救场窗口仍允许外部复活机制把队伍救回来。
                    // 这次救场不把次数扣成负数。
                    life.deathSeen = false;
                    life.vanillaRespawned = false;
                    life.forceSpectator = false;
                    player.setGameMode(GameType.SURVIVAL);
                    restoreDeathItems(player);
                    if (!player.level().dimension().equals(DUNGEON_LEVEL)) teleportInto(level, instance, player);
                    instance.wipeTicks = -1;
                    broadcast(instance, server, Component.literal("§6[副本] §a" + player.getGameProfile().getName()
                            + " 在全灭关闭前被成功救起，关闭倒计时已取消。"));
                } else {
                    life.deathSeen = false;
                    life.vanillaRespawned = false;
                    life.forceSpectator = true;
                    restoreDeathItems(player);
                    player.setGameMode(GameType.SPECTATOR);
                    teleportInto(level, instance, player);
                    player.sendSystemMessage(Component.literal("§c团队复活次数已用尽，你将旁观到副本结束。"));
                }
            }
        }
    }

    /**
     * 把当前刚刚重新存活的玩家仍按“死亡状态”计算，判断他倒下时是否已经没有其他可战斗队友。
     * 这样即使某个Mod在同一tick完成原地救援，也能正确进入30秒全灭救场规则。
     */
    private static boolean noOtherCombatantWasAlive(DungeonInstance instance, MinecraftServer server, UUID revivedPlayer) {
        for (UUID uuid : instance.participants) {
            if (uuid.equals(revivedPlayer)) continue;
            ServerPlayer other = server.getPlayerList().getPlayer(uuid);
            if (other == null || !other.isAlive() || other.isDeadOrDying() || other.isSpectator()) continue;
            DungeonInstance.LifeState otherLife = instance.life.get(uuid);
            if (otherLife == null || !otherLife.deathSeen) return false;
        }
        return true;
    }

    private static void checkWipe(DungeonInstance instance, MinecraftServer server) {
        boolean anyAlive = false;
        for (UUID uuid : instance.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.isAlive() && !player.isSpectator()) {
                DungeonInstance.LifeState life = instance.life.get(uuid);
                if (life == null || !life.deathSeen) {
                    anyAlive = true;
                    break;
                }
            }
        }
        if (anyAlive) {
            instance.wipeTicks = -1;
            return;
        }
        if (instance.wipeTicks < 0) {
            instance.wipeTicks = instance.definition.wipeCloseSeconds() * 20;
            broadcast(instance, server, Component.literal("§4[副本] §c全队已死亡，若无人复活，副本将在 §f"
                    + instance.definition.wipeCloseSeconds() + "秒 §c后关闭。"));
            return;
        }
        instance.wipeTicks--;
        if (instance.wipeTicks == 200) {
            broadcast(instance, server, Component.literal("§4[副本] §c副本将在10秒后关闭。"));
        }
        if (instance.wipeTicks <= 0) close(instance, server, false, "全队死亡");
    }

    static void tickCleopatraCompletion(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
        if (instance.cleopatraBodyDeadAge < 0 || instance.ageTicks - instance.cleopatraBodyDeadAge < 220) return;
        int searchRadius = instance.arenaRadius + 16;
        AABB box = arenaBox(instance, searchRadius, 40);
        boolean hasSnakeOrSummoner = false;
        for (Entity entity : level.getEntities((Entity) null, box, e -> e.isAlive() && belongsToInstance(e, instance))) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (id == null || !YellowDuckMod.MOD_ID.equals(id.getNamespace())) continue;
            String path = id.getPath();
            if (path.startsWith("cleopatra_snake_") || path.equals("cleopatra_snake_summoner")) {
                hasSnakeOrSummoner = true;
                break;
            }
        }
        if (!hasSnakeOrSummoner) complete(instance, server);
    }

    static void completeFromController(DungeonInstance instance, MinecraftServer server) {
        complete(instance, server);
    }

    private static void complete(DungeonInstance instance, MinecraftServer server) {
        if (instance.state == DungeonInstance.State.REWARD || instance.state == DungeonInstance.State.CLOSING) return;
        instance.state = DungeonInstance.State.REWARD;
        instance.stateTicks = 0;
        for (UUID uuid : instance.participants) {
            ServerPlayer hudPlayer = server.getPlayerList().getPlayer(uuid);
            if (hudPlayer != null) clearHud(hudPlayer);
        }
        ServerLevel dungeonLevel = server.getLevel(DUNGEON_LEVEL);
        if (dungeonLevel != null) {
            AABB box = arenaBox(instance, instance.arenaRadius + 16, 40);
            for (Entity entity : dungeonLevel.getEntities((Entity) null, box,
                    e -> !(e instanceof Player) && belongsToInstance(e, instance))) {
                entity.discard();
            }
        }
        instance.rolledRewards.clear();
        instance.rolledRewards.addAll(DungeonRewardManager.roll(instance));
        DungeonRewardManager.stage(instance, server);
        broadcast(instance, server, Component.literal("§6[副本] §a挑战成功！正在展示本次副本奖励。"));
        for (UUID uuid : instance.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.isAlive()) DungeonRewardManager.openPreview(player, instance);
        }
    }

    /**
     * 记录足以致死的最终伤害。这样即使其他复活机制在真正死亡前把玩家救回，
     * 下一tick检测到重新存活时也会消耗团队复活次数。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fatalDamage(LivingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DungeonInstance instance = instanceOf(player);
        if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;
        if (event.getAmount() + 0.0001F < player.getHealth()) return;
        DungeonInstance.LifeState life = instance.life.computeIfAbsent(player.getUUID(), k -> new DungeonInstance.LifeState());
        if (!life.deathSeen) {
            life.deathSeen = true;
            life.deathAge = instance.ageTicks;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void livingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            DungeonInstance instance = instanceOf(player);
            if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;
            DungeonInstance.LifeState life = instance.life.computeIfAbsent(player.getUUID(), k -> new DungeonInstance.LifeState());
            if (!life.deathSeen) {
                life.deathSeen = true;
                life.deathAge = instance.ageTicks;
            }
            return;
        }
        // 玩家死亡事件即使被其他复活机制取消，也要保留 deathSeen 用于统计一次复活；
        // 但 Boss / 怪物的死亡如果被其他 Mod 取消，就绝不能提前判定副本通关。
        if (event.isCanceled()) return;
        if (!(entity.level() instanceof ServerLevel level) || !level.dimension().equals(DUNGEON_LEVEL)) return;
        DungeonInstance instance = findForEntity(entity);
        if (instance == null || instance.state != DungeonInstance.State.FIGHTING) return;
        if (entity.getUUID().equals(instance.mainBossId)) {
            instance.mainBossDead = true;
            DungeonCompletionControllers.forInstance(instance).onMainBossDeath(instance, level.getServer(), level);
        }
    }

    /** 给副本内后续召唤出来的蛇、毒池、投射物等自动继承实例ID，减少多队并发串实例。 */
    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(DUNGEON_LEVEL)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player || entity.getPersistentData().hasUUID("YellowDuckDungeon")) return;
        DungeonInstance instance = findByPosition(entity.getX(), entity.getY(), entity.getZ());
        if (instance != null) entity.getPersistentData().putUUID("YellowDuckDungeon", instance.id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void suppressDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return; // 其他Mod已经接管掉落时不重复处理，避免复制。

        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonInstance instance = instanceOf(player);
            if (instance == null) return;
            List<ItemStack> items = new ArrayList<>();
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (!stack.isEmpty()) items.add(stack.copy());
            }
            if (!items.isEmpty()) {
                DungeonSavedData.get(player.server).addPendingDeathItems(player.getUUID(), items);
                event.getDrops().clear();
                event.setCanceled(true);
            }
            return;
        }

        if (!(event.getEntity().level() instanceof ServerLevel level) || !level.dimension().equals(DUNGEON_LEVEL)) return;
        if (findForEntity(event.getEntity()) != null) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void suppressExperience(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (!(event.getEntity().level() instanceof ServerLevel level) || !level.dimension().equals(DUNGEON_LEVEL)) return;
        if (findForEntity(event.getEntity()) != null) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DungeonInstance active = instanceOf(player);
        if (active != null) {
            ServerLevel level = player.server.getLevel(DUNGEON_LEVEL);
            if (active.state == DungeonInstance.State.REWARD) {
                player.setGameMode(GameType.SURVIVAL);
                if (level != null) teleportInto(level, active, player);
                restoreDeathItems(player);
                clearHud(player);
                DungeonRewardManager.openPreview(player, active);
                return;
            }
            DungeonInstance.LifeState life = active.life.computeIfAbsent(player.getUUID(), k -> new DungeonInstance.LifeState());
            life.vanillaRespawned = true;
            if (level != null) checkRevives(active, player.server, level);
            return;
        }
        clearHud(player);
        DungeonInstance.ReturnPoint point = DungeonSavedData.get(player.server).takeReturn(player.getUUID());
        if (point != null) returnPlayer(player, point);
        restoreDeathItems(player);
        DungeonRewardManager.deliverPending(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        recoverIfNeeded(player.server);

        DungeonInstance active = instanceOf(player);
        if (active != null) {
            ServerLevel level = player.server.getLevel(DUNGEON_LEVEL);
            DungeonInstance.LifeState life = active.life.get(player.getUUID());
            player.setGameMode(life != null && life.forceSpectator ? GameType.SPECTATOR : GameType.SURVIVAL);
            if (level != null) teleportInto(level, active, player);
            restoreDeathItems(player);
            syncHud(active, player.server);
            return;
        }

        clearHud(player);
        DungeonInstance.ReturnPoint point = DungeonSavedData.get(player.server).takeReturn(player.getUUID());
        if (point != null) {
            returnPlayer(player, point);
        } else if (player.level().dimension().equals(DUNGEON_LEVEL)) {
            // 兼容安装修复包之前已经发生过的异常重启：旧版本没有SavedData时仍不能把玩家永久留在虚空维度。
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY() + 1D, spawn.getZ() + 0.5D, 0F, 0F);
        }
        // 必须在回到正常世界以后先恢复死亡物品，再补发副本奖励；奖励背包满时才会掉在正常世界。
        restoreDeathItems(player);
        DungeonRewardManager.deliverPending(player);
    }

    private static void close(DungeonInstance instance, MinecraftServer server, boolean success, String reason) {
        if (!ACTIVE.containsKey(instance.id)) return;
        instance.state = DungeonInstance.State.CLOSING;
        broadcast(instance, server, Component.literal(success
                ? "§6[副本] §a副本结束，正在返回原位置。"
                : "§4[副本] §c副本失败：§f" + reason));

        DungeonSavedData saved = DungeonSavedData.get(server);
        for (UUID uuid : new ArrayList<>(instance.participants)) {
            PLAYER_INSTANCE.remove(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            DungeonInstance.ReturnPoint point = instance.returns.get(uuid);
            if (player != null) clearHud(player);
            if (player != null && player.isAlive()) {
                player.closeContainer();
                returnPlayer(player, point);
                saved.clearReturn(uuid);
                restoreDeathItems(player);
            }
            // 离线或仍处于死亡界面的玩家不删除SavedData中的回程点，登录/重生时再安全返回。
        }

        if (success) DungeonRewardManager.deliver(instance, server);

        ServerLevel level = server.getLevel(DUNGEON_LEVEL);
        boolean cleanupSucceeded = false;
        if (level != null) {
            try {
                AABB box = arenaBox(instance, instance.arenaRadius + 16, 40);
                for (Entity entity : level.getEntities((Entity) null, box,
                        e -> !(e instanceof Player) && belongsToInstance(e, instance))) {
                    entity.discard();
                }
                DungeonArenaBuilder.cleanup(level, instance);
                cleanupSucceeded = true;
            } catch (Throwable t) {
                LOGGER.error("清理副本实例失败，保留SavedData记录供后续恢复清理：{}", instance.id, t);
            }
        }

        // 只有实体和场地真正清理成功后才移除持久化实例标记。
        // 如果维度暂不可用或清理抛错，则保留记录，并让恢复逻辑在后续tick/下次启动继续处理。
        if (cleanupSucceeded) saved.untrackInstance(instance.id);
        else recoveryDone = false;
        ACTIVE.remove(instance.id);
        PARTY_INSTANCE.remove(instance.partyId);
        USED_SLOTS.remove(instance.slot);
        AdventureParty party = PartyManager.getParty(server, instance.partyId);
        if (party != null) PartyManager.clearReady(server, party);
    }

    private static int allocateSlot() {
        for (int i = 0; i < DungeonConfig.maxInstances(); i++) {
            if (USED_SLOTS.add(i)) return i;
        }
        return -1;
    }

    /** /yd reload 改过实例间距后，也必须避开已经按旧间距存在的实例。 */
    private static BlockPos findSafeOrigin(int slot, int radius) {
        int spacing = DungeonConfig.instanceSpacing();
        int y = DungeonConfig.instanceY();
        int x = slot * spacing;
        int attempts = Math.max(16, DungeonConfig.maxInstances() + ACTIVE.size() + 8);
        for (int i = 0; i < attempts; i++, x += spacing) {
            BlockPos candidate = new BlockPos(x, y, 0);
            boolean overlaps = false;
            for (DungeonInstance other : ACTIVE.values()) {
                int safeDistance = radius + other.arenaRadius + 32;
                if (Math.abs(candidate.getX() - other.origin.getX()) <= safeDistance
                        && Math.abs(candidate.getZ() - other.origin.getZ()) <= safeDistance) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) return candidate;
        }
        return null;
    }

    private static void cleanupRecoveredEntities(ServerLevel level, DungeonSavedData.StaleInstance stale) {
        BlockPos o = stale.origin();
        int r = stale.radius() + 16;
        AABB box = new AABB(o.getX() - r, o.getY() - 16, o.getZ() - r,
                o.getX() + r + 1, o.getY() + 64, o.getZ() + r + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    /**
     * 恢复副本真正死亡时暂存的物品。绝不把塞不下的部分丢回副本地面，
     * 剩余物品继续保存在 SavedData，之后登录/重生/离本时再次尝试。
     */
    private static void restoreDeathItems(ServerPlayer player) {
        DungeonSavedData data = DungeonSavedData.get(player.server);
        List<ItemStack> stored = data.takePendingDeathItems(player.getUUID());
        if (stored.isEmpty()) return;

        List<ItemStack> leftovers = new ArrayList<>();
        int restoredStacks = 0;
        for (ItemStack original : stored) {
            ItemStack stack = original.copy();
            player.getInventory().add(stack);
            if (stack.isEmpty()) restoredStacks++;
            else leftovers.add(stack.copy());
        }
        if (!leftovers.isEmpty()) data.addPendingDeathItems(player.getUUID(), leftovers);
        player.containerMenu.broadcastChanges();
        if (restoredStacks > 0) player.sendSystemMessage(Component.literal("§a已恢复本次副本死亡时的物品。"));
        if (!leftovers.isEmpty()) player.sendSystemMessage(Component.literal("§e背包空间不足，剩余死亡物品已安全保存，稍后会继续恢复。"));
    }

    private static void syncHud(DungeonInstance instance, MinecraftServer server) {
        boolean active = instance.state == DungeonInstance.State.COUNTDOWN || instance.state == DungeonInstance.State.FIGHTING;
        int remaining = Math.max(0, instance.definition.timeLimitSeconds() - instance.fightTicks / 20);
        List<MountNetwork.DungeonHudMember> members = new ArrayList<>();
        for (UUID uuid : instance.participants) {
            ServerPlayer member = server.getPlayerList().getPlayer(uuid);
            String name = resolvePlayerName(server, uuid);
            int status;
            if (member == null) status = 2;
            else {
                DungeonInstance.LifeState life = instance.life.get(uuid);
                status = member.isSpectator() || !member.isAlive() || member.isDeadOrDying()
                        || (life != null && (life.deathSeen || life.forceSpectator)) ? 1 : 0;
            }
            members.add(new MountNetwork.DungeonHudMember(name, status));
        }
        MountNetwork.DungeonHudPacket packet = new MountNetwork.DungeonHudPacket(active,
                instance.definition.displayName(), remaining, instance.revivesRemaining,
                instance.initialRevives, members);
        for (UUID uuid : instance.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) MountNetwork.sendDungeonHud(player, packet);
        }
    }

    private static void clearHud(ServerPlayer player) {
        MountNetwork.sendDungeonHud(player, new MountNetwork.DungeonHudPacket(false, "", 0, 0, 0, List.of()));
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(uuid);
            if (profile.isPresent() && profile.get().getName() != null) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private static DungeonInstance findForEntity(Entity entity) {
        if (entity.getPersistentData().hasUUID("YellowDuckDungeon")) {
            DungeonInstance tagged = ACTIVE.get(entity.getPersistentData().getUUID("YellowDuckDungeon"));
            if (tagged != null) return tagged;
        }
        return findByPosition(entity.getX(), entity.getY(), entity.getZ());
    }

    private static boolean belongsToInstance(Entity entity, DungeonInstance instance) {
        if (entity.getPersistentData().hasUUID("YellowDuckDungeon")) {
            return instance.id.equals(entity.getPersistentData().getUUID("YellowDuckDungeon"));
        }
        DungeonInstance byPosition = findByPosition(entity.getX(), entity.getY(), entity.getZ());
        return byPosition == instance;
    }

    private static DungeonInstance findByPosition(double x, double y, double z) {
        for (DungeonInstance instance : ACTIVE.values()) {
            int r = instance.arenaRadius + 16;
            if (Math.abs(x - instance.origin.getX()) <= r
                    && Math.abs(z - instance.origin.getZ()) <= r
                    && Math.abs(y - instance.origin.getY()) <= 64) {
                return instance;
            }
        }
        return null;
    }

    private static AABB arenaBox(DungeonInstance instance, int horizontal, int vertical) {
        BlockPos o = instance.origin;
        return new AABB(o.getX() - horizontal, o.getY() - 8, o.getZ() - horizontal,
                o.getX() + horizontal + 1, o.getY() + vertical, o.getZ() + horizontal + 1);
    }

    private static void teleportInto(ServerLevel level, DungeonInstance instance, ServerPlayer player) {
        List<BlockPos> entrances = DungeonArenaBuilder.findEntrances(level, instance);
        BlockPos target;
        if (entrances.isEmpty()) {
            target = instance.origin.offset(0, 0, -instance.arenaRadius + 8);
        } else {
            int index = 0;
            int i = 0;
            for (UUID uuid : instance.participants) {
                if (uuid.equals(player.getUUID())) {
                    index = i;
                    break;
                }
                i++;
            }
            target = entrances.get(Math.min(index, entrances.size() - 1));
        }
        player.teleportTo(level, target.getX() + 0.5D, target.getY() + 1.05D, target.getZ() + 0.5D, 0F, 0F);
    }

    private static void returnPlayer(ServerPlayer player, DungeonInstance.ReturnPoint point) {
        if (point == null) {
            player.teleportTo(player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D,
                    player.server.overworld().getSharedSpawnPos().getY() + 1D,
                    player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, 0F, 0F);
            return;
        }
        ServerLevel level = player.server.getLevel(point.level());
        if (level == null) level = player.server.overworld();
        player.setGameMode(point.gameType());
        player.teleportTo(level, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    private static void broadcast(DungeonInstance instance, MinecraftServer server, Component message) {
        for (UUID uuid : instance.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }
}
