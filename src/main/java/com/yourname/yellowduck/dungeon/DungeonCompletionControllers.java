package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.entity.SakurawitchEntity;
import com.yourname.yellowduck.entity.ToyBearEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 通关控制器注册表。 */
public final class DungeonCompletionControllers {
    private static final Map<String, DungeonCompletionController> CONTROLLERS = new LinkedHashMap<>();

    static {
        register("boss_death", new DungeonCompletionController() {
            @Override
            public void onMainBossDeath(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                if (isSakura(instance) && hasLivingSakuraBear(instance, level)) {
                    announce(instance, server, "§6[副本] §c小樱已经倒下，但布偶熊仍在战斗！");
                    return;
                }
                DungeonManager.completeFromController(instance, server);
            }

            @Override
            public void tick(DungeonInstance instance, MinecraftServer server, ServerLevel level) {
                if (!isSakura(instance)) return;

                // 小樱使用自定义延迟死亡动画：die() 先把血量设为 0，
                // 之后才调用 LivingEntity#die。不要只依赖 LivingDeathEvent，
                // 否则某些环境下 mainBossDead 永远不会被置为 true。
                if (!instance.mainBossDead && sakuraHasBeenDefeated(instance, level)) {
                    instance.mainBossDead = true;
                }

                if (instance.mainBossDead && !hasLivingSakuraBear(instance, level)) {
                    DungeonManager.completeFromController(instance, server);
                }
            }
        });

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
                if (!instance.mainBossDead && sakuraHasBeenDefeated(instance, level)) {
                    instance.mainBossDead = true;
                }
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

    private static boolean sakuraHasBeenDefeated(DungeonInstance instance, ServerLevel level) {
        if (instance == null || level == null || instance.mainBossId == null) return false;

        Entity entity = level.getEntity(instance.mainBossId);
        // Boss 已经被真正移除，也应视为死亡，避免实例永久卡住。
        if (entity == null || entity.isRemoved()) return true;

        if (entity instanceof SakurawitchEntity sakura) {
            return sakura.getEntityData().get(SakurawitchEntity.IS_DYING)
                    || !sakura.isAlive()
                    || sakura.getHealth() <= 0.0F;
        }
        return false;
    }

    private static boolean hasLivingSakuraBear(DungeonInstance instance, ServerLevel level) {
        if (instance == null || level == null) return false;
        int r = Math.max(24, instance.arenaRadius + 16);
        AABB box = new AABB(
                instance.origin.getX() - r, instance.origin.getY() - 8, instance.origin.getZ() - r,
                instance.origin.getX() + r + 1, instance.origin.getY() + 72, instance.origin.getZ() + r + 1
        );

        for (ToyBearEntity bear : level.getEntitiesOfClass(ToyBearEntity.class, box)) {
            // 进入自定义死亡动画后已经算被击败，不能继续阻塞副本结算。
            if (bear.isRemoved()
                    || !bear.isAlive()
                    || bear.getHealth() <= 0.0F
                    || bear.getEntityData().get(ToyBearEntity.DYING)) {
                continue;
            }

            if (bear.getPersistentData().hasUUID("YellowDuckDungeon")) {
                if (instance.id.equals(bear.getPersistentData().getUUID("YellowDuckDungeon"))) {
                    return true;
                }
            } else {
                // 兼容旧存档/极端情况下未及时写入实例标签的熊。
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
                        net.minecraft.network.chat.Component.literal(message), false);
            }
        }
    }
}
