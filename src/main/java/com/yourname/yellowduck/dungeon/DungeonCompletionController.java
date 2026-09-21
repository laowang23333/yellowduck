package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.entity.ToyBearEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 通关控制器注册表。以后新增特殊Boss时只需注册新的 key，不需要继续往 DungeonManager 堆 if/else。 */
public final class DungeonCompletionControllers {
    private static final Map<String, DungeonCompletionController> CONTROLLERS = new LinkedHashMap<>();

    static {
        register("boss_death", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                // 兼容已有服务器配置：即便旧配置仍写 boss_death，
                // 小樱副本也必须等她召唤出来的布偶熊一起结束后才能结算。
                if (isSakura(instance) && hasLivingSakuraBear(instance, level)) {
                    announce(instance, server, "§6[副本] §c小樱已经倒下，但布偶熊仍在战斗！");
                    return;
                }
                DungeonManager.completeFromController(instance, server);
            }

            @Override
            public void tick(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                if (!isSakura(instance) || !instance.mainBossDead) return;
                if (!hasLivingSakuraBear(instance, level)) {
                    DungeonManager.completeFromController(instance, server);
                }
            }
        });

        // 新配置推荐显式使用 sakura_bear；行为与上面对旧 boss_death 的兼容逻辑一致。
        register("sakura_bear", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                if (hasLivingSakuraBear(instance, level)) {
                    announce(instance, server, "§6[副本] §c小樱已经倒下，但布偶熊仍在战斗！");
                    return;
                }
                DungeonManager.completeFromController(instance, server);
            }

            @Override
            public void tick(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                if (instance.mainBossDead && !hasLivingSakuraBear(instance, level)) {
                    DungeonManager.completeFromController(instance, server);
                }
            }
        });

        register("cleopatra_snakes", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                instance.waitingCleopatraSnakes = true;
                instance.cleopatraBodyDeadAge = instance.ageTicks;
            }

            @Override
            public void tick(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
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

    private static boolean isSakura(DungeonInstance instance) {
        return instance != null && instance.definition != null
                && "yellowduck:sakurawitch".equalsIgnoreCase(instance.definition.bossEntity());
    }

    private static boolean hasLivingSakuraBear(DungeonInstance instance, ServerLevel level) {
        if (instance == null || level == null) return false;

        int r = Math.max(24, instance.arenaRadius + 16);
        AABB box = new AABB(
                instance.origin.getX() - r, instance.origin.getY() - 8, instance.origin.getZ() - r,
                instance.origin.getX() + r + 1, instance.origin.getY() + 72, instance.origin.getZ() + r + 1
        );

        for (ToyBearEntity bear : level.getEntitiesOfClass(ToyBearEntity.class, box)) {
            // 熊一旦进入 DYING 死亡动画，就已经算“被击败”。
            // 旧判断会把 isAlive=false、DYING=true 的熊继续当成存活，
            // 导致小樱副本永远无法进入 REWARD 状态。
            if (bear.isRemoved()
                    || !bear.isAlive()
                    || bear.getEntityData().get(ToyBearEntity.DYING)) {
                continue;
            }

            if (bear.getPersistentData().hasUUID("YellowDuckDungeon")) {
                if (instance.id.equals(bear.getPersistentData().getUUID("YellowDuckDungeon"))) {
                    return true;
                }
            } else {
                return true;
            }
        }

        return false;
    }

    private static void announce(DungeonInstance instance, MinecraftServer server, String message) {
        for (UUID uuid : instance.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(message),
                        false
                );
            }
        }
    }
}
