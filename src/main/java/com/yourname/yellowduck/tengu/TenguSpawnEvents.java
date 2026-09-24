package com.yourname.yellowduck.tengu;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Random;

/** 主世界夜间自然随机刷新天狗 Boss。 */
@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TenguSpawnEvents {
    private static final Random RANDOM = new Random();

    private TenguSpawnEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        if (level.getGameTime() % TenguConfig.NATURAL_SPAWN_CHECK_INTERVAL != 0L) return;

        long time = Math.floorMod(level.getDayTime(), 24_000L);
        if (time < 13_000L || time > 23_000L) return;

        // 防止世界里同时刷出一堆世界 Boss：存活天狗存在时不再自然刷新第二只。
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TenguBoss boss && boss.isAlive() && !boss.isRemoved()) {
                return;
            }
        }

        List<ServerPlayer> players = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator() && !player.isCreative())
                .toList();
        if (players.isEmpty()) return;
        if (RANDOM.nextDouble() >= TenguConfig.NATURAL_SPAWN_CHANCE) return;

        ServerPlayer anchor = players.get(RANDOM.nextInt(players.size()));
        trySpawnAround(level, anchor);
    }

    private static void trySpawnAround(ServerLevel level, ServerPlayer anchor) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            int distance = TenguConfig.NATURAL_SPAWN_MIN_DISTANCE
                    + RANDOM.nextInt(TenguConfig.NATURAL_SPAWN_MAX_DISTANCE
                    - TenguConfig.NATURAL_SPAWN_MIN_DISTANCE + 1);

            int x = (int) Math.floor(anchor.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(anchor.getZ() + Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
            if (level.getBlockState(pos.below()).isAir()) continue;
            if (!level.getFluidState(pos.below()).isEmpty()) continue;

            TenguBoss boss = TenguContent.BOSS.get().create(level);
            if (boss == null) return;
            boss.moveTo(x + 0.5D, y, z + 0.5D, RANDOM.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(boss, boss.getBoundingBox())) continue;

            level.addFreshEntity(boss);
            return;
        }
    }
}
