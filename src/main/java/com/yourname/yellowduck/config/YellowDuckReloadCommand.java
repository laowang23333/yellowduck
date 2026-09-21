package com.yourname.yellowduck.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import com.yourname.yellowduck.cleopatra.CleopatraBoss;
import com.yourname.yellowduck.cleopatra.CleopatraConfig;
import com.yourname.yellowduck.cleopatra.CleopatraSandworm;
import com.yourname.yellowduck.cleopatra.CleopatraScorpion;
import com.yourname.yellowduck.cleopatra.CleopatraVenomSnake;
import com.yourname.yellowduck.dungeon.DungeonConfig;
import com.yourname.yellowduck.dungeon.DungeonDefinition;
import com.yourname.yellowduck.dungeon.DungeonManager;
import com.yourname.yellowduck.silk.SilkConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** YellowDuck 主命令：配置重载；副本玩家命令只保留安全退出与奖励查看。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class YellowDuckReloadCommand {
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        registerRoot(event.getDispatcher(), "yellowduck");
        registerRoot(event.getDispatcher(), "yd");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);

        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> reload(ctx.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> dungeon = Commands.literal("dungeon");
        dungeon.then(Commands.literal("leave").executes(ctx -> DungeonManager.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0));
        dungeon.then(Commands.literal("rewards").executes(ctx -> {
            DungeonManager.reopenRewards(ctx.getSource().getPlayerOrException());
            return 1;
        }));
        root.then(dungeon);

        LiteralArgumentBuilder<CommandSourceStack> pillar = Commands.literal("pillar").requires(s -> s.hasPermission(2));
        pillar.then(Commands.literal("bind")
                .then(Commands.argument("dungeon_id", StringArgumentType.word())
                        .executes(ctx -> bindPillar(ctx.getSource(), StringArgumentType.getString(ctx, "dungeon_id")))));
        pillar.then(Commands.literal("info").executes(ctx -> pillarInfo(ctx.getSource())));
        pillar.then(Commands.literal("unbind").executes(ctx -> unbindPillar(ctx.getSource())));
        pillar.then(Commands.literal("list").executes(ctx -> listDungeons(ctx.getSource())));
        root.then(pillar);

        dispatcher.register(root);
    }

    private static MeetStoneBlockEntity lookedPillar(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult result = player.pick(6.0D, 0.0F, false);
        if (result.getType() != HitResult.Type.BLOCK || !(result instanceof BlockHitResult hit)) return null;
        return player.level().getBlockEntity(hit.getBlockPos()) instanceof MeetStoneBlockEntity stone ? stone : null;
    }

    private static int bindPillar(CommandSourceStack source, String dungeonId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DungeonDefinition def = DungeonConfig.get(dungeonId);
        if (def == null) {
            source.sendFailure(Component.literal("找不到副本ID：" + dungeonId + "。使用 /yd pillar list 查看可绑定副本。"));
            return 0;
        }
        MeetStoneBlockEntity stone = lookedPillar(source);
        if (stone == null) {
            source.sendFailure(Component.literal("请看着 6 格内的副本柱子（yellowduck:meet_stone）再执行绑定命令。"));
            return 0;
        }
        stone.setDungeonId(def.id());
        source.sendSuccess(() -> Component.literal("副本柱子已绑定：" + def.displayName() + " [" + def.id() + "]"
                + (def.enabled() ? "" : "（当前配置为禁用）")), true);
        return 1;
    }

    private static int pillarInfo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MeetStoneBlockEntity stone = lookedPillar(source);
        if (stone == null) {
            source.sendFailure(Component.literal("请看着 6 格内的副本柱子。"));
            return 0;
        }
        if (!stone.isBound()) {
            source.sendSuccess(() -> Component.literal("这根副本柱子尚未绑定任何副本。"), false);
            return 1;
        }
        DungeonDefinition def = DungeonConfig.get(stone.getDungeonId());
        source.sendSuccess(() -> Component.literal("柱子绑定ID：" + stone.getDungeonId() + "；副本："
                + (def == null ? "配置中不存在" : def.displayName() + (def.enabled() ? "（启用）" : "（禁用）"))), false);
        return 1;
    }

    private static int unbindPillar(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MeetStoneBlockEntity stone = lookedPillar(source);
        if (stone == null) {
            source.sendFailure(Component.literal("请看着 6 格内的副本柱子。"));
            return 0;
        }
        String old = stone.getDungeonId();
        stone.clearDungeonId();
        source.sendSuccess(() -> Component.literal(old.isBlank() ? "这根柱子本来就没有绑定副本。" : "已解除副本柱子绑定：" + old), true);
        return 1;
    }

    private static int listDungeons(CommandSourceStack source) {
        var list = DungeonConfig.enabledDungeons();
        if (list.isEmpty()) {
            source.sendFailure(Component.literal("当前没有启用的副本配置。"));
            return 0;
        }
        StringBuilder text = new StringBuilder("可绑定副本：");
        for (DungeonDefinition def : list) text.append("\n - ").append(def.id()).append(" = ").append(def.displayName());
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return list.size();
    }

    private static int reload(CommandSourceStack source) {
        try {
            /*
             * 三份 YellowDuck 配置均自行解析，不经过 ForgeConfigSpec。
             * 任一文件解析失败时，该文件继续使用上一份有效值，不会被自动改回默认。
             */
            boolean entityOk = EntityTuningConfig.reload();
            boolean dungeonOk = DungeonConfig.reload();
            boolean silkOk = SilkConfig.reload();

            int count = 0;
            if (entityOk || silkOk) {
                for (ServerLevel level : source.getServer().getAllLevels()) {
                    for (var entity : level.getAllEntities()) {
                        if (!(entity instanceof LivingEntity living)) continue;

                        if (entityOk) {
                            EntityTuningConfig.reapply(living);

                            // 艳后的战斗配置仍在 yellowduck-entities.toml 中。
                            if (living instanceof CleopatraBoss) {
                                apply(living, CleopatraConfig.bossHealth.get(), CleopatraConfig.bossAttack.get());
                            } else if (living instanceof CleopatraSandworm) {
                                apply(living, CleopatraConfig.sandwormHealth.get(), null);
                            } else if (living instanceof CleopatraScorpion) {
                                apply(living, CleopatraConfig.scorpionHealth.get(), null);
                            } else if (living instanceof CleopatraVenomSnake) {
                                apply(living, CleopatraConfig.snakeHealth.get(), CleopatraConfig.snakeAttack.get());
                            }
                        }

                        // 教授战斗配置与生物属性共用 yellowduck-entities.toml；最后同步教授及新召唤物属性。
                        if (silkOk) SilkConfig.reapply(living);
                        count++;
                    }
                }
            }

            if (!entityOk) {
                source.sendFailure(Component.literal(
                        "yellowduck-entities.toml 读取失败：已保留上一份有效生物/艳后配置，文件没有被自动改回默认。请查看日志。"));
            }
            if (!silkOk) {
                source.sendFailure(Component.literal(
                        "yellowduck-entities.toml 读取失败：已保留上一份有效教授配置，文件没有被自动改回默认。请查看日志。"));
            }

            source.sendSuccess(() -> Component.literal(
                    "YellowDuck 重载完成："
                            + (entityOk ? "生物/艳后配置已更新" : "生物/艳后配置保持上一份有效值")
                            + "；"
                            + (silkOk ? "教授配置已更新" : "教授配置保持上一份有效值")
                            + "；"
                            + (dungeonOk ? "副本配置已更新" : "副本配置读取失败")
                            + "。"), true);

            return entityOk || dungeonOk || silkOk ? Math.max(1, count) : 0;
        } catch (Throwable t) {
            source.sendFailure(Component.literal("YellowDuck reload 失败: " + t.getMessage()));
            return 0;
        }
    }

    private static void apply(LivingEntity entity, Double hp, Double attack) {
        float ratio = entity.getMaxHealth() > 0 ? entity.getHealth() / entity.getMaxHealth() : 1;

        if (hp != null && entity.getAttribute(Attributes.MAX_HEALTH) != null) {
            var attr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (Math.abs(attr.getBaseValue() - hp) > 1.0E-9D) {
                attr.setBaseValue(hp);
                entity.setHealth(Math.max(.1f,
                        Math.min(entity.getMaxHealth(), entity.getMaxHealth() * ratio)));
            }
        }

        if (attack != null && entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
        }
    }

    private YellowDuckReloadCommand() {}
}
