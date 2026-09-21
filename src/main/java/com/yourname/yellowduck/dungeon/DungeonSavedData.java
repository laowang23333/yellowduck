package com.yourname.yellowduck.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 副本持久化数据。
 * 保存玩家回程坐标、待补发奖励、死亡物品、异常重启实例，以及永久副本场地槽位。
 */
public final class DungeonSavedData extends SavedData {
    private static final String ID = "yellowduck_dungeon_state";

    private final Map<UUID, DungeonInstance.ReturnPoint> pendingReturns = new LinkedHashMap<>();
    /** 每名玩家可同时存在多批待发奖励；batchId 用于崩服后的幂等确认。 */
    private final Map<UUID, List<PendingReward>> pendingRewards = new LinkedHashMap<>();
    /** 每次副本死亡单独保存为一批，避免背包不足/再次死亡时把旧批次合并后重复发放。 */
    private final Map<UUID, List<PendingDeathItems>> pendingDeathItems = new LinkedHashMap<>();
    /** player -> (dungeon id -> epoch millis). 成功通关冷却跨重启保存。 */
    private final Map<UUID, Map<String, Long>> dungeonCooldowns = new LinkedHashMap<>();
    private final Map<UUID, StaleInstance> trackedInstances = new LinkedHashMap<>();
    /** slot -> 永久地图槽。只给拥有 Structure NBT 模板的副本使用。 */
    private final Map<Integer, ArenaSlot> arenaSlots = new LinkedHashMap<>();

    public static DungeonSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DungeonSavedData::load, DungeonSavedData::new, ID);
    }

    public static DungeonSavedData load(CompoundTag tag) {
        DungeonSavedData data = new DungeonSavedData();

        ListTag returns = tag.getList("Returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < returns.size(); i++) {
            CompoundTag entry = returns.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            DungeonInstance.ReturnPoint point = readReturn(entry.getCompound("Point"));
            if (point != null) data.pendingReturns.put(entry.getUUID("Player"), point);
        }

        ListTag rewards = tag.getList("Rewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < rewards.size(); i++) {
            CompoundTag entry = rewards.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            UUID playerId = entry.getUUID("Player");
            UUID batchId = entry.hasUUID("Batch")
                    ? entry.getUUID("Batch")
                    : legacyBatch("reward", playerId, i);
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                ItemStack stack = ItemStack.of(itemList.getCompound(j));
                if (!stack.isEmpty()) items.add(stack);
            }
            int xp = Math.max(0, entry.getInt("Xp"));
            if (!items.isEmpty() || xp > 0) {
                data.pendingRewards
                        .computeIfAbsent(playerId, k -> new ArrayList<>())
                        .add(new PendingReward(batchId, items, xp));
            }
        }

        ListTag deathItems = tag.getList("DeathItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < deathItems.size(); i++) {
            CompoundTag entry = deathItems.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            UUID playerId = entry.getUUID("Player");
            UUID batchId = entry.hasUUID("Batch")
                    ? entry.getUUID("Batch")
                    : legacyBatch("death", playerId, i);
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                ItemStack stack = ItemStack.of(itemList.getCompound(j));
                if (!stack.isEmpty()) items.add(stack);
            }
            if (!items.isEmpty()) {
                data.pendingDeathItems
                        .computeIfAbsent(playerId, k -> new ArrayList<>())
                        .add(new PendingDeathItems(batchId, items));
            }
        }

        ListTag cooldowns = tag.getList("Cooldowns", Tag.TAG_COMPOUND);
        long now = System.currentTimeMillis();
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag entry = cooldowns.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            String dungeonId = normalizeDungeonId(entry.getString("Dungeon"));
            long until = entry.getLong("Until");
            if (dungeonId.isEmpty() || until <= now) continue;
            data.dungeonCooldowns
                    .computeIfAbsent(entry.getUUID("Player"), k -> new LinkedHashMap<>())
                    .put(dungeonId, until);
        }

        ListTag instances = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instances.size(); i++) {
            CompoundTag entry = instances.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            UUID id = entry.getUUID("Id");
            BlockPos origin = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            String dungeonId = entry.getString("Dungeon");
            data.trackedInstances.put(id, new StaleInstance(
                    id,
                    entry.getInt("Slot"),
                    origin,
                    Math.max(16, entry.getInt("Radius")),
                    dungeonId == null ? "" : dungeonId
            ));
        }

        ListTag slots = tag.getList("ArenaSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            int slot = entry.getInt("Slot");
            String dungeonId = entry.getString("Dungeon");
            if (slot < 0 || dungeonId == null || dungeonId.isBlank()) continue;
            BlockPos origin = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            data.arenaSlots.put(slot, new ArenaSlot(
                    slot,
                    dungeonId,
                    origin,
                    Math.max(16, entry.getInt("Radius")),
                    entry.getBoolean("Initialized")
            ));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag returns = new ListTag();
        for (var entry : pendingReturns.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", entry.getKey());
            row.put("Point", writeReturn(entry.getValue()));
            returns.add(row);
        }
        tag.put("Returns", returns);

        ListTag rewards = new ListTag();
        for (var entry : pendingRewards.entrySet()) {
            for (PendingReward pending : entry.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Player", entry.getKey());
                row.putUUID("Batch", pending.batchId());
                row.putInt("Xp", pending.xp());
                ListTag itemList = new ListTag();
                for (ItemStack stack : pending.items()) {
                    if (stack.isEmpty()) continue;
                    itemList.add(stack.copy().save(new CompoundTag()));
                }
                row.put("Items", itemList);
                rewards.add(row);
            }
        }
        tag.put("Rewards", rewards);

        ListTag deathItems = new ListTag();
        for (var entry : pendingDeathItems.entrySet()) {
            for (PendingDeathItems pending : entry.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Player", entry.getKey());
                row.putUUID("Batch", pending.batchId());
                ListTag itemList = new ListTag();
                for (ItemStack stack : pending.items()) {
                    if (!stack.isEmpty()) itemList.add(stack.copy().save(new CompoundTag()));
                }
                row.put("Items", itemList);
                deathItems.add(row);
            }
        }
        tag.put("DeathItems", deathItems);

        ListTag cooldowns = new ListTag();
        long now = System.currentTimeMillis();
        for (var playerEntry : dungeonCooldowns.entrySet()) {
            for (var dungeonEntry : playerEntry.getValue().entrySet()) {
                long until = dungeonEntry.getValue() == null ? 0L : dungeonEntry.getValue();
                if (until <= now) continue;
                CompoundTag row = new CompoundTag();
                row.putUUID("Player", playerEntry.getKey());
                row.putString("Dungeon", dungeonEntry.getKey());
                row.putLong("Until", until);
                cooldowns.add(row);
            }
        }
        tag.put("Cooldowns", cooldowns);

        ListTag instances = new ListTag();
        for (StaleInstance instance : trackedInstances.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", instance.id());
            row.putInt("Slot", instance.slot());
            row.putInt("X", instance.origin().getX());
            row.putInt("Y", instance.origin().getY());
            row.putInt("Z", instance.origin().getZ());
            row.putInt("Radius", instance.radius());
            row.putString("Dungeon", instance.dungeonId());
            instances.add(row);
        }
        tag.put("Instances", instances);

        ListTag slots = new ListTag();
        for (ArenaSlot slot : arenaSlots.values()) {
            CompoundTag row = new CompoundTag();
            row.putInt("Slot", slot.slot());
            row.putString("Dungeon", slot.dungeonId());
            row.putInt("X", slot.origin().getX());
            row.putInt("Y", slot.origin().getY());
            row.putInt("Z", slot.origin().getZ());
            row.putInt("Radius", slot.radius());
            row.putBoolean("Initialized", slot.initialized());
            slots.add(row);
        }
        tag.put("ArenaSlots", slots);
        return tag;
    }

    public void trackInstance(DungeonInstance instance) {
        trackedInstances.put(instance.id, new StaleInstance(
                instance.id,
                instance.slot,
                instance.origin,
                instance.arenaRadius,
                instance.definition.id()
        ));
        pendingReturns.putAll(instance.returns);
        setDirty();
    }

    public void untrackInstance(UUID instanceId) {
        if (trackedInstances.remove(instanceId) != null) setDirty();
    }

    public List<StaleInstance> staleInstances() {
        return new ArrayList<>(trackedInstances.values());
    }

    public ArenaSlot arenaSlot(int slot) {
        return arenaSlots.get(slot);
    }

    public List<ArenaSlot> arenaSlots() {
        return new ArrayList<>(arenaSlots.values());
    }

    /** 第一次给某个地图分配永久槽。建筑尚未放置时 initialized=false。 */
    public void bindArenaSlot(int slot, String dungeonId, BlockPos origin, int radius, boolean initialized) {
        arenaSlots.put(slot, new ArenaSlot(slot, dungeonId, origin.immutable(), Math.max(16, radius), initialized));
        setDirty();
    }

    /** Structure NBT 成功放置以后再标记，避免半途报错却误认为地图已经存在。 */
    public void markArenaInitialized(int slot) {
        ArenaSlot old = arenaSlots.get(slot);
        if (old == null || old.initialized()) return;
        arenaSlots.put(slot, new ArenaSlot(old.slot(), old.dungeonId(), old.origin(), old.radius(), true));
        setDirty();
    }

    public DungeonInstance.ReturnPoint takeReturn(UUID playerId) {
        DungeonInstance.ReturnPoint point = pendingReturns.remove(playerId);
        if (point != null) setDirty();
        return point;
    }

    public void clearReturn(UUID playerId) {
        if (pendingReturns.remove(playerId) != null) setDirty();
    }

    public void addPendingReward(UUID playerId, UUID batchId, List<ItemStack> items, int xp) {
        if (playerId == null || batchId == null) return;
        List<ItemStack> copied = copyItems(items);
        int safeXp = Math.max(0, xp);
        if (copied.isEmpty() && safeXp <= 0) return;

        List<PendingReward> list = pendingRewards.computeIfAbsent(playerId, k -> new ArrayList<>());
        for (PendingReward existing : list) {
            if (existing.batchId().equals(batchId)) return;
        }
        list.add(new PendingReward(batchId, copied, safeXp));
        setDirty();
    }

    public void addPendingReward(UUID playerId, List<ItemStack> items, int xp) {
        addPendingReward(playerId, UUID.randomUUID(), items, xp);
    }

    public List<PendingReward> pendingRewards(UUID playerId) {
        List<PendingReward> list = pendingRewards.get(playerId);
        if (list == null || list.isEmpty()) return List.of();
        List<PendingReward> copy = new ArrayList<>(list.size());
        for (PendingReward pending : list) {
            copy.add(new PendingReward(pending.batchId(), copyItems(pending.items()), pending.xp()));
        }
        return copy;
    }

    public void acknowledgePendingReward(UUID playerId, UUID batchId) {
        List<PendingReward> list = pendingRewards.get(playerId);
        if (list == null || batchId == null) return;
        if (list.removeIf(pending -> batchId.equals(pending.batchId()))) {
            if (list.isEmpty()) pendingRewards.remove(playerId);
            setDirty();
        }
    }

    @Deprecated
    public PendingReward takePendingReward(UUID playerId) {
        List<PendingReward> list = pendingRewards.get(playerId);
        if (list == null || list.isEmpty()) return null;
        PendingReward reward = list.remove(0);
        if (list.isEmpty()) pendingRewards.remove(playerId);
        setDirty();
        return new PendingReward(reward.batchId(), copyItems(reward.items()), reward.xp());
    }

    /** 成功通关后开始该玩家对指定副本的冷却。seconds=0 表示不启用。 */
    public void startCooldown(UUID playerId, String dungeonId, int seconds) {
        if (playerId == null || seconds <= 0) return;
        String id = normalizeDungeonId(dungeonId);
        if (id.isEmpty()) return;
        long until = System.currentTimeMillis() + Math.max(0L, (long) seconds) * 1000L;
        Map<String, Long> perDungeon = dungeonCooldowns.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
        Long old = perDungeon.get(id);
        if (old == null || until > old) {
            perDungeon.put(id, until);
            setDirty();
        }
    }

    /** 返回剩余冷却秒数；已过期会顺手清理。 */
    public long cooldownRemainingSeconds(UUID playerId, String dungeonId) {
        if (playerId == null) return 0L;
        String id = normalizeDungeonId(dungeonId);
        if (id.isEmpty()) return 0L;
        Map<String, Long> perDungeon = dungeonCooldowns.get(playerId);
        if (perDungeon == null) return 0L;
        Long until = perDungeon.get(id);
        if (until == null) return 0L;
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            perDungeon.remove(id);
            if (perDungeon.isEmpty()) dungeonCooldowns.remove(playerId);
            setDirty();
            return 0L;
        }
        return (remainingMs + 999L) / 1000L;
    }

    private static String normalizeDungeonId(String dungeonId) {
        return dungeonId == null ? "" : dungeonId.trim().toLowerCase(Locale.ROOT);
    }

    public UUID addPendingDeathItems(UUID playerId, List<ItemStack> items) {
        UUID batchId = UUID.randomUUID();
        addPendingDeathItems(playerId, batchId, items);
        return batchId;
    }

    public void addPendingDeathItems(UUID playerId, UUID batchId, List<ItemStack> items) {
        if (playerId == null || batchId == null) return;
        List<ItemStack> copied = copyItems(items);
        if (copied.isEmpty()) return;

        List<PendingDeathItems> list = pendingDeathItems.computeIfAbsent(playerId, k -> new ArrayList<>());
        for (PendingDeathItems existing : list) {
            if (existing.batchId().equals(batchId)) return;
        }
        list.add(new PendingDeathItems(batchId, copied));
        setDirty();
    }

    public List<PendingDeathItems> pendingDeathItems(UUID playerId) {
        List<PendingDeathItems> list = pendingDeathItems.get(playerId);
        if (list == null || list.isEmpty()) return List.of();
        List<PendingDeathItems> copy = new ArrayList<>(list.size());
        for (PendingDeathItems pending : list) {
            copy.add(new PendingDeathItems(pending.batchId(), copyItems(pending.items())));
        }
        return copy;
    }

    public void acknowledgePendingDeathItems(UUID playerId, UUID batchId) {
        List<PendingDeathItems> list = pendingDeathItems.get(playerId);
        if (list == null || batchId == null) return;
        if (list.removeIf(pending -> batchId.equals(pending.batchId()))) {
            if (list.isEmpty()) pendingDeathItems.remove(playerId);
            setDirty();
        }
    }

    @Deprecated
    public List<ItemStack> takePendingDeathItems(UUID playerId) {
        List<PendingDeathItems> list = pendingDeathItems.remove(playerId);
        if (list == null || list.isEmpty()) return List.of();
        setDirty();
        List<ItemStack> copy = new ArrayList<>();
        for (PendingDeathItems pending : list) copy.addAll(copyItems(pending.items()));
        return copy;
    }

    private static List<ItemStack> copyItems(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>();
        if (items != null) {
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static UUID legacyBatch(String kind, UUID playerId, int rowIndex) {
        String source = "yellowduck:" + kind + ":" + playerId + ":" + rowIndex;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static CompoundTag writeReturn(DungeonInstance.ReturnPoint point) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", point.level().location().toString());
        tag.putDouble("X", point.x());
        tag.putDouble("Y", point.y());
        tag.putDouble("Z", point.z());
        tag.putFloat("Yaw", point.yaw());
        tag.putFloat("Pitch", point.pitch());
        tag.putString("GameType", point.gameType().getName());
        return tag;
    }

    private static DungeonInstance.ReturnPoint readReturn(CompoundTag tag) {
        try {
            ResourceLocation dimension = new ResourceLocation(tag.getString("Dimension"));
            ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION, dimension);
            GameType gameType = GameType.SURVIVAL;
            String gameTypeName = tag.getString("GameType");
            for (GameType value : GameType.values()) {
                if (value.getName().equalsIgnoreCase(gameTypeName)) {
                    gameType = value;
                    break;
                }
            }
            return new DungeonInstance.ReturnPoint(level,
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"), gameType);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record PendingReward(UUID batchId, List<ItemStack> items, int xp) {}
    public record PendingDeathItems(UUID batchId, List<ItemStack> items) {}
    public record StaleInstance(UUID id, int slot, BlockPos origin, int radius, String dungeonId) {}
    public record ArenaSlot(int slot, String dungeonId, BlockPos origin, int radius, boolean initialized) {}
}
