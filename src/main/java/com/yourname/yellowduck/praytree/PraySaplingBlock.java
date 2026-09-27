package com.yourname.yellowduck.praytree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PraySaplingBlock extends BushBlock implements BonemealableBlock {
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;

    public PraySaplingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level,
                           BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9
                && random.nextInt(7) == 0) {
            advanceTree(level, pos, state, random);
        }
    }

    private void advanceTree(ServerLevel level, BlockPos pos,
                             BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), 4);
            return;
        }

        growPrayTree(level, pos, random);
    }

    private void growPrayTree(ServerLevel level, BlockPos base, RandomSource random) {
        TreeSpec spec = TreeSpec.create(random);

        Set<BlockPos> logs = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        List<CanopyBlob> blobs = new ArrayList<>();

        buildRootAndTrunk(base, logs, spec);
        buildCanopyBlobs(base, blobs, spec, random);
        buildBranches(base, logs, blobs, spec, random);
        buildCanopy(base, leaves, blobs, spec, random);
        coverBranchesFromAbove(base, logs, leaves, spec, random);

        leaves.removeAll(logs);

        if (!hasRoom(level, logs, leaves)) {
            return;
        }

        for (BlockPos pos : logs) {
            level.setBlock(
                    pos,
                    PrayTreeContent.PRAY_TREE.get().defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, chooseLogAxis(base, pos)),
                    3
            );
        }

        BlockState leafState = PrayTreeContent.PRAY_LEAVES.get()
                .defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);

        List<BlockPos> placedLeaves = new ArrayList<>();
        for (BlockPos pos : leaves) {
            if (logs.contains(pos)) {
                continue;
            }
            level.setBlock(pos, leafState, 3);
            placedLeaves.add(pos.immutable());
        }

        placeRibbons(level, placedLeaves, logs, spec, random);
    }

    private void buildRootAndTrunk(BlockPos base, Set<BlockPos> logs, TreeSpec spec) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                logs.add(base.offset(dx, 0, dz));
            }
        }

        logs.add(base.above(1));
        logs.add(base.offset(1, 1, 0));
        logs.add(base.offset(-1, 1, 0));
        logs.add(base.offset(0, 1, 1));
        logs.add(base.offset(0, 1, -1));

        for (int y = 2; y <= spec.trunkTopY; y++) {
            logs.add(base.above(y));
        }
    }

    /**
     * 树冠改成“中心主体 + 大量重叠副叶团”。
     * 目标：
     * 1. 整体更大
     * 2. 轮廓更聚拢，不再散架
     * 3. 每棵树仍有随机差异
     */
    private void buildCanopyBlobs(BlockPos base,
                                  List<CanopyBlob> blobs,
                                  TreeSpec spec,
                                  RandomSource random) {

        // 中央主体：更大更厚，保证树冠不会散。
        blobs.add(new CanopyBlob(
                spec.centerOffsetX,
                spec.canopyCenterY,
                spec.centerOffsetZ,
                4 + random.nextInt(2),   // 4~5
                4 + random.nextInt(2),   // 4~5
                2 + random.nextInt(2)    // 2~3
        ));

        // 次级主体，让整体更像一整团，而不是很多分裂小块。
        int coreExtras = 2 + random.nextInt(2);
        for (int i = 0; i < coreExtras; i++) {
            int cx = spec.centerOffsetX + random.nextInt(5) - 2;
            int cz = spec.centerOffsetZ + random.nextInt(5) - 2;
            int cy = spec.canopyCenterY - 1 + random.nextInt(3);

            blobs.add(new CanopyBlob(
                    cx, cy, cz,
                    3 + random.nextInt(2),
                    3 + random.nextInt(2),
                    2
            ));
        }

        // 外围 6~8 个大叶团，视频里树冠很大，因此外圈也做大。
        int lobeCount = 6 + random.nextInt(3);
        double startAngle = random.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < lobeCount; i++) {
            double angle = startAngle
                    + Math.PI * 2.0D * i / lobeCount
                    + (random.nextDouble() - 0.5D) * 0.35D;

            double distance = 3.6D + random.nextDouble() * 2.0D;
            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * distance);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * distance);
            int cy = spec.canopyCenterY - 1 + random.nextInt(3);

            blobs.add(new CanopyBlob(
                    cx, cy, cz,
                    3 + random.nextInt(2), // 3~4
                    3 + random.nextInt(2), // 3~4
                    2
            ));
        }

        // 额外大叶团：少量偏心，保证变化，但不再做太多小碎块。
        int extra = 1 + random.nextInt(2);
        for (int i = 0; i < extra; i++) {
            int cx = spec.centerOffsetX + random.nextInt(9) - 4;
            int cz = spec.centerOffsetZ + random.nextInt(9) - 4;
            int cy = spec.canopyCenterY + random.nextInt(2);

            blobs.add(new CanopyBlob(
                    cx, cy, cz,
                    2 + random.nextInt(2),
                    2 + random.nextInt(2),
                    1 + random.nextInt(2)
            ));
        }
    }

    /**
     * 枝条朝较大的外叶团延伸，但不再过多。
     * 这样树下仍能看见内部木头支撑，但树冠不会被木头撑得太乱。
     */
    private void buildBranches(BlockPos base,
                               Set<BlockPos> logs,
                               List<CanopyBlob> blobs,
                               TreeSpec spec,
                               RandomSource random) {
        int startY = spec.branchY;
        int built = 0;

        for (int i = 1; i < blobs.size(); i++) {
            CanopyBlob blob = blobs.get(i);

            // 只给较大的叶团优先接枝，小叶团不一定接，防止木头太多。
            if (blob.rx + blob.rz < 6 && random.nextBoolean()) {
                continue;
            }

            int targetX = blob.cx;
            int targetZ = blob.cz;
            int steps = Math.max(Math.abs(targetX), Math.abs(targetZ));
            if (steps <= 1) {
                continue;
            }

            if (built >= 7 && random.nextInt(3) != 0) {
                continue;
            }

            int lastX = 0;
            int lastZ = 0;

            for (int step = 1; step <= steps; step++) {
                double progress = step / (double) steps;

                int x = (int) Math.round(targetX * progress);
                int z = (int) Math.round(targetZ * progress);

                if (x == lastX && z == lastZ) {
                    continue;
                }

                int y = startY;
                if (step >= steps - 1 && blob.cy >= spec.canopyCenterY + 1) {
                    y++;
                }

                logs.add(base.offset(x, y, z));
                lastX = x;
                lastZ = z;
            }

            built++;
        }
    }

    private void buildCanopy(BlockPos base,
                             Set<BlockPos> leaves,
                             List<CanopyBlob> blobs,
                             TreeSpec spec,
                             RandomSource random) {

        for (CanopyBlob blob : blobs) {
            addLeafBlob(base, leaves, blob, random);
        }

        // 补一个更宽的“下裙边”，但不是完全平底。
        // 这一步让树冠从远处看更有视频里那种大面积覆盖感。
        int skirtPoints = 18 + random.nextInt(7);
        for (int i = 0; i < skirtPoints; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 4.2D + random.nextDouble() * (spec.outerReach - 3.8D);

            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * dist);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * dist);
            int cy = spec.branchY - 1 + random.nextInt(2); // 底部高低错开

            addLeafBlob(base, leaves, new CanopyBlob(
                    cx, cy,
                    cz,
                    2 + random.nextInt(2),
                    2 + random.nextInt(2),
                    1
            ), random);
        }

        // 顶部少量鼓包，数量减少，不让顶部变碎。
        int topBumps = 2 + random.nextInt(2);
        for (int i = 0; i < topBumps; i++) {
            int cx = spec.centerOffsetX + random.nextInt(7) - 3;
            int cz = spec.centerOffsetZ + random.nextInt(7) - 3;
            int cy = spec.canopyCenterY + 2 + random.nextInt(2);

            addLeafBlob(base, leaves, new CanopyBlob(
                    cx, cy, cz,
                    2, 2, 1
            ), random);
        }

        // 顶部偶尔挖个小浅坑，让每棵树略不同，但不再大面积镂空。
        if (random.nextInt(4) == 0) {
            int cx = spec.centerOffsetX + random.nextInt(5) - 2;
            int cz = spec.centerOffsetZ + random.nextInt(5) - 2;
            carvePocket(base.offset(cx, spec.canopyCenterY + 2, cz), leaves, 1);
        }
    }

    /**
     * 叶团使用“椭球 + 少量边缘削切”，保留自然感，但大幅减少散乱。
     */
    private void addLeafBlob(BlockPos base,
                             Set<BlockPos> leaves,
                             CanopyBlob blob,
                             RandomSource random) {
        for (int dx = -blob.rx; dx <= blob.rx; dx++) {
            for (int dy = -blob.ry; dy <= blob.ry; dy++) {
                for (int dz = -blob.rz; dz <= blob.rz; dz++) {
                    double nx = dx / (double) blob.rx;
                    double ny = dy / (double) blob.ry;
                    double nz = dz / (double) blob.rz;
                    double distance = nx * nx + ny * ny + nz * nz;

                    if (distance > 1.18D) {
                        continue;
                    }

                    // 只在最边上一小圈偶尔削掉，避免树叶太散。
                    if (distance > 0.97D && random.nextInt(12) == 0) {
                        continue;
                    }

                    leaves.add(base.offset(
                            blob.cx + dx,
                            blob.cy + dy,
                            blob.cz + dz
                    ));
                }
            }
        }
    }

    private void carvePocket(BlockPos center, Set<BlockPos> leaves, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    leaves.remove(center.offset(dx, 0, dz));
                }
            }
        }
    }

    /**
     * 树冠上方不再露木头。
     * 只对树冠内部横枝做覆盖，且覆盖比之前更厚一点。
     */
    private void coverBranchesFromAbove(BlockPos base,
                                        Set<BlockPos> logs,
                                        Set<BlockPos> leaves,
                                        TreeSpec spec,
                                        RandomSource random) {
        List<BlockPos> copy = new ArrayList<>(logs);

        for (BlockPos logPos : copy) {
            int relativeY = logPos.getY() - base.getY();
            int dx = logPos.getX() - base.getX();
            int dz = logPos.getZ() - base.getZ();

            if (relativeY < spec.branchY || (dx == 0 && dz == 0)) {
                continue;
            }

            leaves.add(logPos.above());
            leaves.add(logPos.above().north());
            leaves.add(logPos.above().south());
            leaves.add(logPos.above().east());
            leaves.add(logPos.above().west());

            if (random.nextBoolean()) {
                leaves.add(logPos.above(2));
            }
        }

        BlockPos trunkTop = base.above(spec.trunkTopY);
        leaves.add(trunkTop.above());
        leaves.add(trunkTop.above().north());
        leaves.add(trunkTop.above().south());
        leaves.add(trunkTop.above().east());
        leaves.add(trunkTop.above().west());
    }

    private boolean hasRoom(ServerLevel level,
                            Set<BlockPos> logs,
                            Set<BlockPos> leaves) {
        for (BlockPos pos : logs) {
            if (!canReplace(level.getBlockState(pos))) {
                return false;
            }
        }

        for (BlockPos pos : leaves) {
            if (logs.contains(pos)) {
                continue;
            }
            if (!canReplace(level.getBlockState(pos))) {
                return false;
            }
        }

        return true;
    }

    private Direction.Axis chooseLogAxis(BlockPos base, BlockPos pos) {
        int dx = pos.getX() - base.getX();
        int dz = pos.getZ() - base.getZ();
        if (pos.getY() - base.getY() <= 2 || (dx == 0 && dz == 0)) {
            return Direction.Axis.Y;
        }
        return Math.abs(dx) >= Math.abs(dz)
                ? Direction.Axis.X
                : Direction.Axis.Z;
    }

    private void placeRibbons(ServerLevel level,
                              List<BlockPos> placedLeaves,
                              Set<BlockPos> logs,
                              TreeSpec spec,
                              RandomSource random) {
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos leafPos : placedLeaves) {
            BlockPos below = leafPos.below();

            if (!level.isEmptyBlock(below) || logs.contains(below)) {
                continue;
            }

            // 下表面都可挂，边缘权重更高。
            candidates.add(below);

            int openSides = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.isEmptyBlock(leafPos.relative(d))) {
                    openSides++;
                }
            }
            if (openSides >= 2) {
                candidates.add(below);
                if (random.nextBoolean()) {
                    candidates.add(below);
                }
            }
        }

        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));

        int targetGroups = Math.min(candidates.size(), 30 + random.nextInt(16));

        Set<BlockPos> used = new HashSet<>();
        for (int i = 0; i < candidates.size() && used.size() < targetGroups; i++) {
            BlockPos start = candidates.get(i);
            if (!used.add(start)) {
                continue;
            }

            int roll = random.nextInt(100);
            int length;
            if (roll < 34) {
                length = 1;
            } else if (roll < 67) {
                length = 2;
            } else if (roll < 90) {
                length = 3;
            } else {
                length = 4;
            }

            for (int part = 0; part < length; part++) {
                BlockPos ribbonPos = start.below(part);
                if (!level.isEmptyBlock(ribbonPos)) {
                    break;
                }

                BlockState above = level.getBlockState(ribbonPos.above());
                if (!above.is(PrayTreeContent.PRAY_LEAVES.get())
                        && !above.is(PrayTreeContent.PRAY_RIBBON.get())) {
                    break;
                }

                level.setBlock(
                        ribbonPos,
                        PrayTreeContent.PRAY_RIBBON.get().defaultBlockState(),
                        3
                );
            }
        }
    }

    private boolean canReplace(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        return state.isAir()
                || state.is(this)
                || state.is(PrayTreeContent.PRAY_LEAVES.get())
                || state.is(PrayTreeContent.PRAY_RIBBON.get())
                || state.canBeReplaced();
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos,
                                         BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random,
                                     BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.45F;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random,
                                BlockPos pos, BlockState state) {
        advanceTree(level, pos, state, random);
    }

    private static final class CanopyBlob {
        final int cx;
        final int cy;
        final int cz;
        final int rx;
        final int rz;
        final int ry;

        private CanopyBlob(int cx, int cy, int cz, int rx, int rz, int ry) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.rz = rz;
            this.ry = ry;
        }
    }

    private static final class TreeSpec {
        final int trunkTopY;
        final int branchY;
        final int canopyCenterY;
        final int outerReach;
        final int centerOffsetX;
        final int centerOffsetZ;

        private TreeSpec(int trunkTopY,
                         int branchY,
                         int canopyCenterY,
                         int outerReach,
                         int centerOffsetX,
                         int centerOffsetZ) {
            this.trunkTopY = trunkTopY;
            this.branchY = branchY;
            this.canopyCenterY = canopyCenterY;
            this.outerReach = outerReach;
            this.centerOffsetX = centerOffsetX;
            this.centerOffsetZ = centerOffsetZ;
        }

        static TreeSpec create(RandomSource random) {
            int trunkTopY = 7 + random.nextInt(3);   // 7~9，更高一点
            int branchY = trunkTopY - 1;
            int canopyCenterY = trunkTopY + 2;
            int outerReach = 7 + random.nextInt(2);  // 7~8，更大
            int centerOffsetX = random.nextInt(3) - 1;
            int centerOffsetZ = random.nextInt(3) - 1;

            return new TreeSpec(
                    trunkTopY,
                    branchY,
                    canopyCenterY,
                    outerReach,
                    centerOffsetX,
                    centerOffsetZ
            );
        }
    }
}
