package com.yourname.yellowduck.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 一场正在进行的副本。 */
public final class DungeonInstance {
    public enum State { PREPARING, COUNTDOWN, FIGHTING, REWARD, FAILED, CLOSING }

    public final UUID id = UUID.randomUUID();
    public final int slot;
    public final UUID partyId;
    public final UUID leaderId;
    public final DungeonDefinition definition;
    public final BlockPos origin;
    public final int arenaRadius;
    public final LinkedHashSet<UUID> participants = new LinkedHashSet<>();
    public final LinkedHashSet<UUID> rewardEligible = new LinkedHashSet<>();
    public final Map<UUID, ReturnPoint> returns = new LinkedHashMap<>();
    public final Map<UUID, LifeState> life = new LinkedHashMap<>();
    public final List<ItemStack> rolledRewards = new ArrayList<>();

    public State state = State.PREPARING;
    public int ageTicks;
    public int fightTicks;
    public int stateTicks;
    public int revivesRemaining;
    public int initialRevives;
    public int wipeTicks = -1;
    public UUID mainBossId;
    public boolean mainBossSpawned;
    public boolean mainBossDead;
    public boolean waitingCleopatraSnakes;
    public int cleopatraBodyDeadAge = -1;
    public boolean rewardStaged;
    public boolean rewardDelivered;

    public DungeonInstance(int slot, UUID partyId, UUID leaderId, DungeonDefinition definition, BlockPos origin) {
        this.slot = slot;
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.definition = definition;
        this.origin = origin;
        DungeonArenaTemplates.ArenaTemplate arena = DungeonArenaTemplates.get(definition.id());
        this.arenaRadius = arena == null ? DungeonConfig.arenaRadius() : Math.max(16, arena.horizontalRadius());
    }

    public record ReturnPoint(ResourceKey<Level> level, double x, double y, double z,
                              float yaw, float pitch, GameType gameType) {}

    /** player 在本场副本中的死亡/复活状态。 */
    public static final class LifeState {
        public boolean deathSeen;
        public boolean forceSpectator;
        /** 本次重新存活是否经过了原版/Forge PlayerRespawnEvent；用于防止0复活时靠死亡界面绕过次数。 */
        public boolean vanillaRespawned;
        public int deathAge;
    }
}
