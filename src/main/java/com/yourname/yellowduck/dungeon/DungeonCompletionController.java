package com.yourname.yellowduck.dungeon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 副本通关控制器。
 * 普通Boss使用 boss_death；需要多阶段通关的Boss注册自己的控制器即可。
 */
public interface DungeonCompletionController {
    void onMainBossDeath(DungeonInstance instance, MinecraftServer server, ServerLevel level);

    default void tick(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
    }
}
