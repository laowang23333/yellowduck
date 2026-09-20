package com.yourname.yellowduck.dungeon;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 通关控制器注册表。以后新增特殊Boss时只需注册新的 key，不需要继续往 DungeonManager 堆 if/else。 */
public final class DungeonCompletionControllers {
    private static final Map<String, DungeonCompletionController> CONTROLLERS = new LinkedHashMap<>();

    static {
        register("boss_death", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, net.minecraft.server.MinecraftServer server,
                                        net.minecraft.server.level.ServerLevel level) {
                DungeonManager.completeFromController(instance, server);
            }
        });

        register("cleopatra_snakes", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, net.minecraft.server.MinecraftServer server,
                                        net.minecraft.server.level.ServerLevel level) {
                instance.waitingCleopatraSnakes = true;
                instance.cleopatraBodyDeadAge = instance.ageTicks;
            }

            @Override
            public void tick(DungeonInstance instance, net.minecraft.server.MinecraftServer server,
                             net.minecraft.server.level.ServerLevel level) {
                if (instance.waitingCleopatraSnakes) DungeonManager.tickCleopatraCompletion(instance, server, level);
            }
        });
    }

    private DungeonCompletionControllers() {}

    public static void register(String key, DungeonCompletionController controller) {
        if (key == null || key.isBlank() || controller == null) return;
        CONTROLLERS.put(key.trim().toLowerCase(Locale.ROOT), controller);
    }

    public static DungeonCompletionController get(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return CONTROLLERS.getOrDefault(normalized, CONTROLLERS.get("boss_death"));
    }

    public static DungeonCompletionController forInstance(DungeonInstance instance) {
        return get(instance == null || instance.definition == null ? "boss_death" : instance.definition.completionType());
    }
}
