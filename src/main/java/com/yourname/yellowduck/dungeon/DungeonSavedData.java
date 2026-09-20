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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 副本异常重启保护数据。
 * 保存玩家回程坐标、待补发奖励，以及崩服后需要清理的旧实例区域。
 */
public final class DungeonSavedData extends SavedData {
    private static final String ID = "yellowduck_dungeon_state";

    private final Map<UUID, DungeonInstance.ReturnPoint> pendingReturns = new LinkedHashMap<>();
    private final Map<UUID, PendingReward> pendingRewards = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeathItems = new LinkedHashMap<>();
    private final Map<UUID, StaleInstance> trackedInstances = new LinkedHashMap<>();

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
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                ItemStack stack = ItemStack.of(itemList.getCompound(j));
                if (!stack.isEmpty()) items.add(stack);
            }
            data.pendingRewards.put(entry.getUUID("Player"), new PendingReward(items, Math.max(0, entry.getInt("Xp"))));
        }

        ListTag deathItems = tag.getList("DeathItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < deathItems.size(); i++) {
            CompoundTag entry = deathItems.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                ItemStack stack = ItemStack.of(itemList.getCompound(j));
                if (!stack.isEmpty()) items.add(stack);
            }
            if (!items.isEmpty()) data.pendingDeathItems.put(entry.getUUID("Player"), items);
        }

        ListTag instances = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instances.size(); i++) {
            CompoundTag entry = instances.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            UUID id = entry.getUUID("Id");
            BlockPos origin = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            data.trackedInstances.put(id, new StaleInstance(id, entry.getInt("Slot"), origin, Math.max(16, entry.getInt("Radius"))));
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
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", entry.getKey());
            row.putInt("Xp", entry.getValue().xp());
            ListTag itemList = new ListTag();
            for (ItemStack stack : entry.getValue().items()) {
                if (stack.isEmpty()) continue;
                itemList.add(stack.copy().save(new CompoundTag()));
            }
            row.put("Items", itemList);
            rewards.add(row);
        }
        tag.put("Rewards", rewards);

        ListTag deathItems = new ListTag();
        for (var entry : pendingDeathItems.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", entry.getKey());
            ListTag itemList = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) itemList.add(stack.copy().save(new CompoundTag()));
            }
            row.put("Items", itemList);
            deathItems.add(row);
        }
        tag.put("DeathItems", deathItems);

        ListTag instances = new ListTag();
        for (StaleInstance instance : trackedInstances.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", instance.id());
            row.putInt("Slot", instance.slot());
            row.putInt("X", instance.origin().getX());
            row.putInt("Y", instance.origin().getY());
            row.putInt("Z", instance.origin().getZ());
            row.putInt("Radius", instance.radius());
            instances.add(row);
        }
        tag.put("Instances", instances);
        return tag;
    }

    public void trackInstance(DungeonInstance instance) {
        trackedInstances.put(instance.id, new StaleInstance(instance.id, instance.slot, instance.origin, instance.arenaRadius));
        pendingReturns.putAll(instance.returns);
        setDirty();
    }

    public void untrackInstance(UUID instanceId) {
        if (trackedInstances.remove(instanceId) != null) setDirty();
    }

    public List<StaleInstance> staleInstances() {
        return new ArrayList<>(trackedInstances.values());
    }

    public DungeonInstance.ReturnPoint takeReturn(UUID playerId) {
        DungeonInstance.ReturnPoint point = pendingReturns.remove(playerId);
        if (point != null) setDirty();
        return point;
    }

    public void clearReturn(UUID playerId) {
        if (pendingReturns.remove(playerId) != null) setDirty();
    }

    public void addPendingReward(UUID playerId, List<ItemStack> items, int xp) {
        PendingReward old = pendingRewards.get(playerId);
        List<ItemStack> merged = new ArrayList<>();
        int totalXp = Math.max(0, xp);
        if (old != null) {
            for (ItemStack stack : old.items()) merged.add(stack.copy());
            totalXp += old.xp();
        }
        if (items != null) for (ItemStack stack : items) if (!stack.isEmpty()) merged.add(stack.copy());
        pendingRewards.put(playerId, new PendingReward(merged, totalXp));
        setDirty();
    }

    public PendingReward takePendingReward(UUID playerId) {
        PendingReward reward = pendingRewards.remove(playerId);
        if (reward != null) setDirty();
        return reward;
    }

    /** 保存玩家在副本真正死亡时被原版准备丢出的物品，避免掉在实例里后被清理。 */
    public void addPendingDeathItems(UUID playerId, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        List<ItemStack> merged = new ArrayList<>();
        List<ItemStack> old = pendingDeathItems.get(playerId);
        if (old != null) for (ItemStack stack : old) if (!stack.isEmpty()) merged.add(stack.copy());
        for (ItemStack stack : items) if (!stack.isEmpty()) merged.add(stack.copy());
        if (!merged.isEmpty()) {
            pendingDeathItems.put(playerId, merged);
            setDirty();
        }
    }

    public List<ItemStack> takePendingDeathItems(UUID playerId) {
        List<ItemStack> items = pendingDeathItems.remove(playerId);
        if (items != null) setDirty();
        if (items == null) return List.of();
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : items) if (!stack.isEmpty()) copy.add(stack.copy());
        return copy;
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

    public record PendingReward(List<ItemStack> items, int xp) {}
    public record StaleInstance(UUID id, int slot, BlockPos origin, int radius) {}
}
