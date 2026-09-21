package com.yourname.yellowduck.cleopatra;

import com.yourname.yellowduck.dungeon.DungeonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** 艳后死亡后的三蛇召唤器。 */
public class CleopatraSnakeSummoner extends Entity {
    private int timer;
    private boolean poisonSpawned;
    private boolean fireSpawned;
    private boolean iceSpawned;
    private List<BlockPos> pads;

    public CleopatraSnakeSummoner(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        timer++;
        if (pads == null) pads = findGoldPads();

        if (timer >= CleopatraConfig.summonPoisonTick.get() && !poisonSpawned) {
            poisonSpawned = spawnOnPad(0, CleopatraEntities.SNAKE_POISON.get());
        }
        if (timer >= CleopatraConfig.summonFireTick.get() && !fireSpawned) {
            fireSpawned = spawnOnPad(1, CleopatraEntities.SNAKE_FIRE.get());
        }
        if (timer >= CleopatraConfig.summonIceTick.get() && !iceSpawned) {
            iceSpawned = spawnOnPad(2, CleopatraEntities.SNAKE_ICE.get());
        }
        // 三条都真实生成后才允许召唤器消失；出生点异常时宁可让副本失败，也绝不能误判通关发奖励。
        if (timer >= CleopatraConfig.summonerDiscardTick.get() && poisonSpawned && fireSpawned && iceSpawned) discard();
    }

    private List<BlockPos> findGoldPads() {
        BlockPos origin = blockPosition();
        List<BlockPos> strict = new ArrayList<>();
        List<BlockPos> fallback = new ArrayList<>();
        int radius = CleopatraConfig.goldPadSearchRadius.get();
        int verticalRange = CleopatraConfig.goldPadVerticalRange.get();
        int radiusSq = radius * radius;

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            int dx = x - origin.getX();
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                int dz = z - origin.getZ();
                if (dx * dx + dz * dz > radiusSq) continue;

                // 同一 X/Z 只取最高的可用金块，避免堆叠金块被识别成多个出生点。
                for (int y = origin.getY() + verticalRange; y >= origin.getY() - verticalRange; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level().getBlockState(pos).is(Blocks.GOLD_BLOCK)
                            || !level().getBlockState(pos.above()).isAir()) continue;
                    fallback.add(pos);
                    if (isGreenSnakePad(pos)) strict.add(pos);
                    break;
                }
            }
        }

        java.util.Comparator<BlockPos> byDistance = (a, b) -> {
            long da = distanceSq(a, origin);
            long db = distanceSq(b, origin);
            int cmp = Long.compare(da, db);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.getX(), b.getX());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.getZ(), b.getZ());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getY(), b.getY());
        };
        strict.sort(byDistance);
        fallback.sort(byDistance);

        // 优先使用绿色区域明确标记的金块；地图版本较旧、绿色标记不完整时，
        // 再用同范围内最近的其它金块补足三处，避免三蛇根本没刷出来却被旧通关检测误判为完成。
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : strict) {
            if (result.size() >= 3) break;
            if (!result.contains(pos)) result.add(pos);
        }
        for (BlockPos pos : fallback) {
            if (result.size() >= 3) break;
            if (!result.contains(pos)) result.add(pos);
        }
        return result;
    }

    /**
     * 只有被绿色混凝土区域包围的金块才允许作为三蛇出生点。
     * 这是第二层保险：以后地图里即使又加了装饰金块，也不会把蛇刷到外面。
     */
    private boolean isGreenSnakePad(BlockPos pos) {
        int limeNeighbors = 0;
        if (level().getBlockState(pos.north()).is(Blocks.LIME_CONCRETE)) limeNeighbors++;
        if (level().getBlockState(pos.south()).is(Blocks.LIME_CONCRETE)) limeNeighbors++;
        if (level().getBlockState(pos.east()).is(Blocks.LIME_CONCRETE)) limeNeighbors++;
        if (level().getBlockState(pos.west()).is(Blocks.LIME_CONCRETE)) limeNeighbors++;
        return limeNeighbors >= 3;
    }

    private static long distanceSq(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean spawnOnPad(int index, EntityType<CleopatraVenomSnake> type) {
        if (pads == null || index < 0 || index >= pads.size()) return false;

        CleopatraVenomSnake snake = type.create(level());
        if (snake == null) return false;

        BlockPos pad = pads.get(index);
        snake.moveTo(pad.getX() + 0.5D, pad.getY() + 1.0D, pad.getZ() + 0.5D, getYRot(), 0.0F);
        // 明确继承副本实例 ID，不再完全依赖位置推断，防止并发副本或边界位置下三蛇漏记。
        if (getPersistentData().hasUUID("YellowDuckDungeon")) {
            snake.getPersistentData().putUUID("YellowDuckDungeon",
                    getPersistentData().getUUID("YellowDuckDungeon"));
        }
        boolean added = level().addFreshEntity(snake);
        if (added) {
            // 只有实体真正成功加入世界后才登记“已生成”。
            // EntityJoinLevelEvent 之后仍可能被其它 Mod/插件取消，不能在那里提前记 UUID。
            DungeonManager.recordCleopatraSnakeSpawned(snake);
        }
        return added;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("SummonTimer", timer);
        tag.putBoolean("PoisonSpawned", poisonSpawned);
        tag.putBoolean("FireSpawned", fireSpawned);
        tag.putBoolean("IceSpawned", iceSpawned);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        timer = tag.getInt("SummonTimer");
        poisonSpawned = tag.getBoolean("PoisonSpawned");
        fireSpawned = tag.getBoolean("FireSpawned");
        iceSpawned = tag.getBoolean("IceSpawned");
    }
}
