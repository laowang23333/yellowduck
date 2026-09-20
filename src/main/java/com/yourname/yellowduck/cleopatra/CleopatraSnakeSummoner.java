package com.yourname.yellowduck.cleopatra;

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
            poisonSpawned = true;
            spawnOnPad(0, CleopatraEntities.SNAKE_POISON.get());
        }
        if (timer >= CleopatraConfig.summonFireTick.get() && !fireSpawned) {
            fireSpawned = true;
            spawnOnPad(1, CleopatraEntities.SNAKE_FIRE.get());
        }
        if (timer >= CleopatraConfig.summonIceTick.get() && !iceSpawned) {
            iceSpawned = true;
            spawnOnPad(2, CleopatraEntities.SNAKE_ICE.get());
        }
        if (timer >= CleopatraConfig.summonerDiscardTick.get()) discard();
    }

    private List<BlockPos> findGoldPads() {
        BlockPos origin = blockPosition();
        List<BlockPos> found = new ArrayList<>();
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
                    if (level().getBlockState(pos).is(Blocks.GOLD_BLOCK)
                            && level().getBlockState(pos.above()).isAir()
                            && isGreenSnakePad(pos)) {
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

        if (found.size() > 3) return new ArrayList<>(found.subList(0, 3));
        return found;
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

    private void spawnOnPad(int index, EntityType<CleopatraVenomSnake> type) {
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
