package com.yourname.yellowduck.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端持久化“每个玩家当前唯一激活的坐骑”。
 *
 * 不再靠玩家周围 256 格扫描判断坐骑是否存在，因此跨距离、跨维度也不会重复召唤。
 * 数据保存在主世界 data/yellowduck_active_mounts.dat。
 */
public final class MountSavedData extends SavedData {
    private static final String ID = "yellowduck_active_mounts";

    private final Map<UUID, ActiveMount> activeMounts = new LinkedHashMap<>();

    public static MountSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MountSavedData::load,
                MountSavedData::new,
                ID
        );
    }

    public static MountSavedData load(CompoundTag tag) {
        MountSavedData data = new MountSavedData();
        ListTag list = tag.getList("ActiveMounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("Owner") || !row.hasUUID("Entity")) continue;
            String mountId = normalize(row.getString("MountId"));
            if (mountId.isEmpty()) continue;
            data.activeMounts.put(
                    row.getUUID("Owner"),
                    new ActiveMount(row.getUUID("Entity"), mountId)
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var entry : activeMounts.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Owner", entry.getKey());
            row.putUUID("Entity", entry.getValue().entityId());
            row.putString("MountId", entry.getValue().mountId());
            list.add(row);
        }
        tag.put("ActiveMounts", list);
        return tag;
    }

    public ActiveMount getActive(UUID ownerId) {
        return ownerId == null ? null : activeMounts.get(ownerId);
    }

    public boolean hasActive(UUID ownerId) {
        return ownerId != null && activeMounts.containsKey(ownerId);
    }

    public void register(UUID ownerId, UUID entityId, String mountId) {
        if (ownerId == null || entityId == null) return;
        String normalized = normalize(mountId);
        if (normalized.isEmpty()) return;
        ActiveMount next = new ActiveMount(entityId, normalized);
        ActiveMount old = activeMounts.put(ownerId, next);
        if (!next.equals(old)) setDirty();
    }

    /** 玩家主动放生：解除唯一激活记录。未加载的旧实体以后重新加载时会因记录不存在而自动删除。 */
    public ActiveMount clear(UUID ownerId) {
        if (ownerId == null) return null;
        ActiveMount removed = activeMounts.remove(ownerId);
        if (removed != null) setDirty();
        return removed;
    }

    /** 实体自然死亡/被清理时只删除与它自己完全匹配的记录，不能误删后来重新召唤的新坐骑。 */
    public void clearIfMatches(UUID ownerId, UUID entityId) {
        if (ownerId == null || entityId == null) return;
        ActiveMount current = activeMounts.get(ownerId);
        if (current != null && entityId.equals(current.entityId())) {
            activeMounts.remove(ownerId);
            setDirty();
        }
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String value = id.trim().toLowerCase(java.util.Locale.ROOT);
        // MountData 内部正式 ID 已迁移为 demon_tengu，但 GUI/实体目录仍使用旧兼容 ID。
        return "demon_tengu".equals(value) ? "ghost_wolf_stars" : value;
    }

    public record ActiveMount(UUID entityId, String mountId) {}
}
