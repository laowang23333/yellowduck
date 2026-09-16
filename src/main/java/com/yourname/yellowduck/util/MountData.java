package com.yourname.yellowduck.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** 玩家坐骑收藏数据。使用字符串 ID，后续新增坐骑无需改 GUI 数据结构。 */
public final class MountData {
    private static final String ROOT = "yellowduck_mounts";
    private static final String GHOST_WOLF = "ghost_wolf_stars";

    private MountData() {}

    public static boolean hasMount(Player player, String id) {
        return player != null && id != null && !id.isBlank()
                && player.getPersistentData().getCompound(ROOT).getBoolean(id);
    }

    public static void bindMount(Player player, String id) {
        if (player == null || id == null || id.isBlank()) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        root.putBoolean(id, true);
        player.getPersistentData().put(ROOT, root);
    }

    public static boolean hasGhostWolf(Player player) {
        return hasMount(player, GHOST_WOLF);
    }

    public static void bindGhostWolf(Player player) {
        bindMount(player, GHOST_WOLF);
    }

    public static void copyOnClone(Player original, Player clone) {
        CompoundTag oldRoot = original.getPersistentData().getCompound(ROOT).copy();
        if (!oldRoot.isEmpty()) {
            clone.getPersistentData().put(ROOT, oldRoot);
        }
    }
}
