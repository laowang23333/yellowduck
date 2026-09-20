package com.yourname.yellowduck.cleopatra;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 艳后死亡后的三蛇召唤器。
 * 三条蛇只会生成在艳后死亡点附近最近的三个金块上，不再使用固定世界坐标或随机回退位置。
 */
public class CleopatraSnakeSummoner extends Entity {
    private static final int GOLD_PAD_SEARCH_RADIUS = 32;
    private static final int GOLD_PAD_VERTICAL_RANGE = 12;

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

        if (timer >= 60 && !poisonSpawned) {
            poisonSpawned = true;
            spawnOnPad(0, CleopatraEntities.SNAKE_POISON.get());
        }
        if (timer >= 120 && !fireSpawned) {
            fireSpawned = true;
            spawnOnPad(1, CleopatraEntities.SNAKE_FIRE.get());
        }
        if (timer >= 180 && !iceSpawned) {
            iceSpawned = true;
            spawnOnPad(2, CleopatraEntities.SNAKE_ICE.get());
        }
        if (timer >= 200) discard();
    }

    private List<BlockPos> findGoldPads() {
        BlockPos origin = blockPosition();
        List<BlockPos> found = new ArrayList<>();
        int radiusSq = GOLD_PAD_SEARCH_RADIUS * GOLD_PAD_SEARCH_RADIUS;

        for (int x = origin.getX() - GOLD_PAD_SEARCH_RADIUS; x <= origin.getX() + GOLD_PAD_SEARCH_RADIUS; x++) {
            int dx = x - origin.getX();
            for (int z = origin.getZ() - GOLD_PAD_SEARCH_RADIUS; z <= origin.getZ() + GOLD_PAD_SEARCH_RADIUS; z++) {
                int dz = z - origin.getZ();
                if (dx * dx + dz * dz > radiusSq) continue;

                // 同一 X/Z 只取最高的可用金块，避免堆叠金块被当成多个出生点。
                for (int y = origin.getY() + GOLD_PAD_VERTICAL_RANGE; y >= origin.getY() - GOLD_PAD_VERTICAL_RANGE; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level().getBlockState(pos).is(Blocks.GOLD_BLOCK)
                            && level().getBlockState(pos.above()).isAir()) {
                        found.add(pos);
                        break;
                    }
                }
            }
        }

        found.sort((a, b) -> {
            long da = distanceSq(a, origin);
            long db = distanceSq(b, origin);
            int cmp = Long.compare(da, db);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.getX(), b.getX());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.getZ(), b.getZ());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getY(), b.getY());
        });

        if (found.size() > 3) {
            return new ArrayList<>(found.subList(0, 3));
        }
        return found;
    }

    private static long distanceSq(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void spawnOnPad(int index, EntityType<CleopatraVenomSnake> type) {
        // 明确禁止随机/临时位置：没有对应金块就不生成该蛇。
        if (pads == null || index < 0 || index >= pads.size()) return;

        CleopatraVenomSnake snake = type.create(level());
        if (snake == null) return;

        BlockPos pad = pads.get(index);
        snake.moveTo(pad.getX() + 0.5D, pad.getY() + 1.0D, pad.getZ() + 0.5D, getYRot(), 0.0F);
        level().addFreshEntity(snake);
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
