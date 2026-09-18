package com.yourname.yellowduck.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** 玩家坐骑收藏数据。使用字符串 ID，后续新增坐骑无需改 GUI 数据结构。 */
public final class MountData {
    private static final String ROOT = "yellowduck_mounts";

    /** 当前正式使用的坐骑 ID。 */
    public static final String DEMON_TENGU_ID = "demon_tengu";
    public static final String ALPACA_ID = "alpaca";

    /**
     * 旧版本曾把魔化天狗保存为这个 ID。
     * 只用于读取/迁移老玩家数据，不能删除，否则旧服玩家会丢失已绑定坐骑。
     */
    private static final String LEGACY_DEMON_TENGU_ID = "ghost_wolf_stars";

    private MountData() {}

    /** 把旧版 ID 统一映射到当前 ID，保证旧数据和旧调用不会失效。 */
    public static String canonicalizeMountId(String id) {
        if (LEGACY_DEMON_TENGU_ID.equals(id)) {
            return DEMON_TENGU_ID;
        }
        return id;
    }

    public static boolean hasMount(Player player, String id) {
        if (player == null || id == null || id.isBlank()) return false;

        String canonicalId = canonicalizeMountId(id);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);

        if (DEMON_TENGU_ID.equals(canonicalId)) {
            if (root.getBoolean(DEMON_TENGU_ID)) {
                return true;
            }

            // 兼容旧服存档：发现旧 key 后补写新 key，但保留旧 key 方便回滚旧版本。
            if (root.getBoolean(LEGACY_DEMON_TENGU_ID)) {
                root.putBoolean(DEMON_TENGU_ID, true);
                player.getPersistentData().put(ROOT, root);
                return true;
            }
            return false;
        }

        return root.getBoolean(canonicalId);
    }

    public static void bindMount(Player player, String id) {
        if (player == null || id == null || id.isBlank()) return;

        String canonicalId = canonicalizeMountId(id);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        root.putBoolean(canonicalId, true);
        player.getPersistentData().put(ROOT, root);
    }

    public static boolean hasDemonTengu(Player player) {
        return hasMount(player, DEMON_TENGU_ID);
    }

    public static void bindDemonTengu(Player player) {
        bindMount(player, DEMON_TENGU_ID);
    }

    public static void copyOnClone(Player original, Player clone) {
        CompoundTag oldRoot = original.getPersistentData().getCompound(ROOT).copy();
        if (!oldRoot.isEmpty()) {
            clone.getPersistentData().put(ROOT, oldRoot);
        }
    }
}
