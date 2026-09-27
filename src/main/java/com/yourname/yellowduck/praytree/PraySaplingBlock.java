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

        // 横枝全部压在树冠内部，并在木头正上方补叶子。
        // 从上面看时不会再出现木头戳出树叶的情况。
        coverBranchesFromAbove(base, logs, leaves, spec, random);

        leaves.removeAll(logs);

        if (!hasRoom(level, logs, leaves)) {
            return;
        }

        for (BlockPos pos : logs) {
            level.setBlock(
                    pos,
                    PrayTreeContent.PRAY_TREE.get().defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, chooseLogAxis(base, pos, spec)),
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
        // 树根是下宽上窄的台座。
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
     * 树冠不再按水平层一层层铺。
     * 改成多个大小、位置、高度都不同的叶团相互重叠，
     * 这样顶面和底面都会像视频一样自然起伏。
     */
    private void buildCanopyBlobs(BlockPos base,
                                  List<CanopyBlob> blobs,
                                  TreeSpec spec,
                                  RandomSource random) {
        // 中央主体。
        blobs.add(new CanopyBlob(
                spec.centerOffsetX,
                spec.canopyCenterY,
                spec.centerOffsetZ,
                3 + random.nextInt(2),
                3 + random.nextInt(2),
                2
        ));

        // 外围 5~8 个叶团，每棵树位置和大小都不同。
        int lobeCount = 5 + random.nextInt(4);
        double startAngle = random.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < lobeCount; i++) {
            double angle = startAngle
                    + Math.PI * 2.0D * i / lobeCount
                    + (random.nextDouble() - 0.5D) * 0.55D;

            double distance = 2.6D + random.nextDouble() * 2.0D;
            int cx = spec.centerOffsetX
                    + (int) Math.round(Math.cos(angle) * distance);
            int cz = spec.centerOffsetZ
                    + (int) Math.round(Math.sin(angle) * distance);

            int cy = spec.canopyCenterY - 1 + random.nextInt(3);
            int rx = 2 + random.nextInt(2);
            int rz = 2 + random.nextInt(2);
            int ry = 1 + random.nextInt(2);

            blobs.add(new CanopyBlob(cx, cy, cz, rx, rz, ry));
        }

        // 再随机补 1~2 个偏心叶团
        int extra = 1 + random.nextInt(2);
        for (int i = 0; i < extra; i++) {
            int cx = spec.centerOffsetX + random.nextInt(7) - 3;
            int cz = spec.centerOffsetZ + random.nextInt(7) - 3;
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
     * 主枝只在树冠下部和内部生长，不再往树冠上面抬。
     * 每条枝条朝一个叶团中心延伸，所以树干结构也会跟着树冠随机变化。
     */
    private void buildBranches(BlockPos base,
                               Set<BlockPos> logs,
                               List<CanopyBlob> blobs,
                               TreeSpec spec,
                               RandomSource random) {
        int startY = spec.branchY;

        for (int i = 1; i < blobs.size(); i++) {
            CanopyBlob blob = blobs.get(i);

            int targetX = blob.cx;
            int targetZ = blob.cz;
            int steps = Math.max(Math.abs(targetX), Math.abs(targetZ));

            if (steps <= 1) {
                continue;
            }

            // 并不是每个小叶团都一定有一根明显枝条，避免内部木头过密。
            if (i > 4 && random.nextInt(3) == 0) {
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

                // 只允许末端轻微抬高 1 格，而且仍位于叶团下半部。
                int y = startY;
                if (step >= steps - 1 && blob.cy >= spec.canopyCenterY + 1) {
                    y++;
                }

                logs.add(base.offset(x, y, z));
                lastX = x;
                lastZ = z;
            }
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

        // 树冠下面额外补一些小叶团。
        // 这些叶团高度随机，所以底面不会再变成一张整齐的平板。
        int undersideBlobs = 5 + random.nextInt(6);
        for (int i = 0; i < undersideBlobs; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 1.5D + random.nextDouble() * (spec.outerReach - 1.5D);

            int cx = spec.centerOffsetX
                    + (int) Math.round(Math.cos(angle) * dist);
            int cz = spec.centerOffsetZ
                    + (int) Math.round(Math.sin(angle) * dist);
            int cy = spec.branchY + random.nextInt(2);

            addLeafBlob(
                    base,
                    leaves,
                    new CanopyBlob(cx, cy, cz, 1 + random.nextInt(2),
                            1 + random.nextInt(2), 1),
                    random
            );
        }

        // 顶部补少量小鼓包，但全部是树叶，不再放木头。
        int topBumps = 2 + random.nextInt(4);
        for (int i = 0; i < topBumps; i++) {
            int cx = spec.centerOffsetX + random.nextInt(7) - 3;
            int cz = spec.centerOffsetZ + random.nextInt(7) - 3;
            int cy = spec.canopyCenterY + 2 + random.nextInt(2);

            addLeafBlob(
                    base,
                    leaves,
                    new CanopyBlob(cx, cy, cz, 1 + random.nextInt(2),
                            1 + random.nextInt(2), 1),
                    random
            );
        }
    }

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

                    if (distance > 1.12D) {
                        continue;
                    }

                    // 只在叶团最外边缘少量随机削掉方块，
                    // 保持自然轮廓，同时避免产生大洞。
                    if (distance > 0.82D && random.nextInt(7) == 0) {
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

    /**
     * 树下看到枝条，但俯视时树冠上方看不到裸露木头。
     * 对树冠内的横枝统一在正上方补叶子，并随机补左右叶子包裹。
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

            if (random.nextBoolean()) {
                leaves.add(logPos.above().north());
            }
            if (random.nextBoolean()) {
                leaves.add(logPos.above().south());
            }
            if (random.nextBoolean()) {
                leaves.add(logPos.above().east());
            }
            if (random.nextBoolean()) {
                leaves.add(logPos.above().west());
            }
        }

        // 主干最顶部也封在树叶内部。
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

    private Direction.Axis chooseLogAxis(BlockPos base, BlockPos pos, TreeSpec spec) {
        int dx = pos.getX() - base.getX();
        int dz = pos.getZ() - base.getZ();
        int relativeY = pos.getY() - base.getY();

        if (relativeY <= 2 || (dx == 0 && dz == 0)) {
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

            // 现在所有真正的树冠下表面都可以成为候选点，
            // 不再要求它必须处在一整块平面边缘。
            candidates.add(below);

            int openSides = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.isEmptyBlock(leafPos.relative(d))) {
                    openSides++;
                }
            }

            // 外围叶子稍微提高抽中概率。
            if (openSides >= 2 && random.nextBoolean()) {
                candidates.add(below);
            }
        }

        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));

        int targetGroups = Math.min(
                candidates.size(),
                24 + random.nextInt(15)
        );

        Set<BlockPos> used = new HashSet<>();
        for (int i = 0; i < candidates.size() && used.size() < targetGroups; i++) {
            BlockPos start = candidates.get(i);

            if (!used.add(start)) {
                continue;
            }

            int roll = random.nextInt(100);
            int length;
            if (roll < 38) {
                length = 1;
            } else if (roll < 70) {
                length = 2;
            } else if (roll < 92) {
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

        private CanopyBlob(int cx, int cy, int cz,
                           int rx, int rz, int ry) {
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
            int trunkTopY = 6 + random.nextInt(2);       // 6~7
            int branchY = trunkTopY - 1;                 // 横枝压在树冠内部
            int canopyCenterY = trunkTopY + 2;           // 树冠主体中心
            int outerReach = 5 + random.nextInt(2);      // 5~6 格范围
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
