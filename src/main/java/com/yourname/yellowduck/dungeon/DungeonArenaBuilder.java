package com.yourname.yellowduck.dungeon;

import com.yourname.yellowduck.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 副本永久模板场地与旧版测试竞技场。 */
public final class DungeonArenaBuilder {
    private DungeonArenaBuilder() {}

    /**
     * 准备实例场地。
     * 模板副本只有永久槽第一次使用时才真正放置建筑；后续每局只复用现有建筑。
     */
    public static void prepare(ServerLevel level, DungeonInstance instance, boolean initializePermanentArena) {
        DungeonArenaTemplates.ArenaTemplate arena = DungeonArenaTemplates.get(instance.definition.id());
        if (arena != null) {
            if (initializePermanentArena) placePermanentTemplate(level, instance, arena);
            migratePermanentArena(level, instance, arena);
            return;
        }
        prepareLegacyArena(level, instance);
    }

    private static void placePermanentTemplate(ServerLevel level, DungeonInstance instance,
                                               DungeonArenaTemplates.ArenaTemplate arena) {
        Optional<StructureTemplate> optional = level.getServer().getStructureManager().get(arena.structureId());
        if (optional.isEmpty()) {
            throw new IllegalStateException("找不到副本结构模板：" + arena.structureId());
        }
        StructureTemplate template = optional.get();
        BlockPos corner = instance.origin.offset(arena.placementOffset());
        boolean placed = template.placeInWorld(
                level,
                corner,
                corner,
                new StructurePlaceSettings(),
                level.getRandom(),
                2
        );
        if (!placed) throw new IllegalStateException("副本结构模板放置失败：" + arena.structureId());
    }

    /**
     * 永久副本地图的兼容迁移。
     * 艳后旧地图里有大量装饰金块，三蛇召唤器会误把它们当作出生点。
     * 现在只保留绿色区域里的 3 个金块，其余金块一次性替换成海晶灯。
     *
     * 已经迁移过的永久槽用一个固定装饰位作为哨兵判断，后续开本只检查几个方块，
     * 不会每次都扫描整栋建筑。
     */
    private static void migratePermanentArena(ServerLevel level, DungeonInstance instance,
                                              DungeonArenaTemplates.ArenaTemplate arena) {
        if (!"cleopatra".equalsIgnoreCase(instance.definition.id())) return;

        BlockPos corner = instance.origin.offset(arena.placementOffset());

        BlockPos poisonPad = corner.offset(42, 0, 20);
        BlockPos firePad = corner.offset(42, 0, 29);
        BlockPos icePad = corner.offset(42, 0, 38);

        // 三个真正的蛇出生点始终保持为金块。
        ensureGoldPad(level, poisonPad);
        ensureGoldPad(level, firePad);
        ensureGoldPad(level, icePad);

        // 这个位置在旧模板中是装饰金块；已经变成海晶灯就说明迁移做过了。
        BlockPos sentinel = corner.offset(29, 0, 11);
        if (!level.getBlockState(sentinel).is(Blocks.GOLD_BLOCK)) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 59; x++) {
            for (int y = 0; y < 39; y++) {
                for (int z = 0; z < 59; z++) {
                    if (isCleopatraSnakePadLocal(x, y, z)) continue;
                    pos.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z);
                    if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                        level.setBlock(pos, Blocks.SEA_LANTERN.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static boolean isCleopatraSnakePadLocal(int x, int y, int z) {
        return y == 0 && x == 42 && (z == 20 || z == 29 || z == 38);
    }

    private static void ensureGoldPad(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        }
    }

    /** 没有独立地图模板的副本继续使用旧版测试场地，方便后续逐个替换。 */
    private static void prepareLegacyArena(ServerLevel level, DungeonInstance instance) {
        int r = instance.arenaRadius;
        BlockPos o = instance.origin;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean border = Math.abs(x) == r || Math.abs(z) == r;
                level.setBlock(o.offset(x, -1, z), (border ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState(), 2);
                if (border) {
                    for (int dy = 0; dy <= 3; dy++) level.setBlock(o.offset(x, dy, z), Blocks.CUT_SANDSTONE.defaultBlockState(), 2);
                }
            }
        }

        placeEntrances(level, instance);

        int bossZ = Math.min(14, Math.max(6, r - 2));
        level.setBlock(o.offset(0, 0, bossZ), ModBlocks.DUNGEON_BOSS_SPAWN_MARKER.get().defaultBlockState(), 2);

        int snakeZNear = Math.max(6, r - 10);
        int snakeZFar = Math.max(8, r - 4);
        int snakeX = Math.max(4, Math.min(14, r / 3));
        level.setBlock(o.offset(-snakeX, 0, snakeZNear), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(o.offset(0, 0, snakeZFar), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(o.offset(snakeX, 0, snakeZNear), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
    }

    private static void placeEntrances(ServerLevel level, DungeonInstance instance) {
        int r = instance.arenaRadius;
        int needed = Math.max(1, instance.definition.maxPlayers());
        int capacityPerRow = Math.max(1, ((r - 4) * 2) / 2 + 1);
        int columns = Math.min(needed, capacityPerRow);
        int placed = 0;
        int row = 0;
        while (placed < needed) {
            int rowCount = Math.min(columns, needed - placed);
            int startX = -(rowCount - 1);
            int z = -r + 8 + row * 2;
            for (int col = 0; col < rowCount; col++) {
                int x = startX + col * 2;
                level.setBlock(instance.origin.offset(x, 0, z), ModBlocks.DUNGEON_ENTRANCE_MARKER.get().defaultBlockState(), 2);
                placed++;
            }
            row++;
        }
    }

    /**
     * 正常关本时，永久模板建筑完全不删除；只由 DungeonManager 清理本场实体。
     * 旧版测试场地仍按原逻辑清除。
     */
    public static void cleanup(ServerLevel level, DungeonInstance instance) {
        if (!DungeonArenaTemplates.has(instance.definition.id())) {
            cleanupArea(level, instance.origin, instance.arenaRadius);
        }
        forceChunks(level, instance.origin, instance.arenaRadius, false);
    }

    /** 崩服重启后仅清除旧版临时竞技场；永久模板场地保留。 */
    public static void cleanupRecovered(ServerLevel level, DungeonSavedData.StaleInstance instance) {
        if (!DungeonArenaTemplates.has(instance.dungeonId())) {
            cleanupArea(level, instance.origin(), instance.radius());
        }
        forceChunks(level, instance.origin(), instance.radius(), false);
    }

    /** 启动时解除旧版本可能遗留在副本维度里的强加载区块。 */
    public static void releaseConfiguredForcedChunks(ServerLevel level) {
        int radius = DungeonConfig.arenaRadius();
        for (int slot = 0; slot < DungeonConfig.maxInstances(); slot++) {
            BlockPos origin = new BlockPos(slot * DungeonConfig.instanceSpacing(), DungeonConfig.instanceY(), 0);
            forceChunks(level, origin, radius, false);
        }
        // 配置可能修改过，持久化永久槽也单独解除一次。
        for (DungeonSavedData.ArenaSlot slot : DungeonSavedData.get(level.getServer()).arenaSlots()) {
            forceChunks(level, slot.origin(), slot.radius(), false);
        }
    }

    private static void cleanupArea(ServerLevel level, BlockPos o, int r) {
        int minY = Math.max(level.getMinBuildHeight(), o.getY() - 1);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, o.getY() + 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = o.getX() - r; x <= o.getX() + r; x++) {
            for (int z = o.getZ() - r; z <= o.getZ() + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void forceChunks(ServerLevel level, BlockPos o, int r, boolean forced) {
        int minX = (o.getX() - r) >> 4;
        int maxX = (o.getX() + r) >> 4;
        int minZ = (o.getZ() - r) >> 4;
        int maxZ = (o.getZ() + r) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) level.setChunkForced(x, z, forced);
        }
    }

    public static List<BlockPos> findEntrances(ServerLevel level, DungeonInstance instance) {
        DungeonArenaTemplates.ArenaTemplate arena = DungeonArenaTemplates.get(instance.definition.id());
        if (arena != null) {
            List<BlockPos> result = new ArrayList<>();
            for (BlockPos offset : arena.entranceOffsets()) result.add(instance.origin.offset(offset));
            return result;
        }
        return findBlocks(level, instance, true);
    }

    public static BlockPos findBossSpawn(ServerLevel level, DungeonInstance instance) {
        DungeonArenaTemplates.ArenaTemplate arena = DungeonArenaTemplates.get(instance.definition.id());
        if (arena != null) return instance.origin.offset(arena.bossOffset());
        List<BlockPos> list = findBlocks(level, instance, false);
        int bossZ = Math.min(14, Math.max(6, instance.arenaRadius - 2));
        return list.isEmpty() ? instance.origin.offset(0, 0, bossZ) : list.get(0);
    }

    private static List<BlockPos> findBlocks(ServerLevel level, DungeonInstance instance, boolean entrance) {
        int r = instance.arenaRadius;
        BlockPos o = instance.origin;
        List<BlockPos> found = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 8; y++) {
                    BlockPos p = o.offset(x, y, z);
                    boolean matches = entrance
                            ? level.getBlockState(p).is(ModBlocks.DUNGEON_ENTRANCE_MARKER.get())
                            : level.getBlockState(p).is(ModBlocks.DUNGEON_BOSS_SPAWN_MARKER.get());
                    if (matches) found.add(p.immutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(o)));
        return found;
    }
}
