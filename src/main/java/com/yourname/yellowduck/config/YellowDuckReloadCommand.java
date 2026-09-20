package com.yourname.yellowduck.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yourname.yellowduck.YellowDuckMod;
import com.yourname.yellowduck.cleopatra.CleopatraBoss;
import com.yourname.yellowduck.cleopatra.CleopatraConfig;
import com.yourname.yellowduck.cleopatra.CleopatraSandworm;
import com.yourname.yellowduck.cleopatra.CleopatraScorpion;
import com.yourname.yellowduck.cleopatra.CleopatraVenomSnake;
import com.yourname.yellowduck.dungeon.DungeonConfig;
import com.yourname.yellowduck.dungeon.DungeonManager;
import com.yourname.yellowduck.dungeon.DungeonDefinition;
import com.yourname.yellowduck.block.MeetStoneBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

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

        // 组队/开本不再提供玩家命令入口。
        // 玩家必须右键副本柱子（meet_stone）打开组队界面，并通过GUI开始副本。

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
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
            boolean dungeonOk = DungeonConfig.reload();
            int count = 0;
            for (ServerLevel level : source.getServer().getAllLevels()) {
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof LivingEntity living) {
                        EntityTuningConfig.reapply(living);
                        if (living instanceof CleopatraBoss) apply(living, CleopatraConfig.bossHealth.get(), CleopatraConfig.bossAttack.get());
                        else if (living instanceof CleopatraSandworm) apply(living, CleopatraConfig.sandwormHealth.get(), null);
                        else if (living instanceof CleopatraScorpion) apply(living, CleopatraConfig.scorpionHealth.get(), null);
                        else if (living instanceof CleopatraVenomSnake) apply(living, CleopatraConfig.snakeHealth.get(), CleopatraConfig.snakeAttack.get());
                        count++;
                    }
                }
            }
            source.sendSuccess(() -> Component.literal("YellowDuck 配置已重新读取；实体配置和副本配置已刷新。"
                    + (dungeonOk ? "" : "（副本配置读取失败，请查看日志）")), true);
            return count;
        } catch (Throwable t) {
            source.sendFailure(Component.literal("YellowDuck reload 失败: " + t.getMessage()));
            return 0;
        }
    }

    private static void apply(LivingEntity entity, Double hp, Double attack) {
        float ratio = entity.getMaxHealth() > 0 ? entity.getHealth() / entity.getMaxHealth() : 1;
        if (hp != null && entity.getAttribute(Attributes.MAX_HEALTH) != null) {
            entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
            entity.setHealth(Math.max(.1f, Math.min(entity.getMaxHealth(), entity.getMaxHealth() * ratio)));
        }
        if (attack != null && entity.getAttribute(Attributes.ATTACK_DAMAGE) != null)
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
    }

    private YellowDuckReloadCommand() {}
}
