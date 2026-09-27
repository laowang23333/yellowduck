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
        buildCanopyBlobs(blobs, spec, random);
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

        placeRibbons(level, placedLeaves, logs, random);
    }

    private void buildRootAndTrunk(BlockPos base, Set<BlockPos> logs, TreeSpec spec) {
        // 根座
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

        // 主干压低，减少中间光秃秃的高度
        for (int y = 2; y <= spec.trunkTopY; y++) {
            logs.add(base.above(y));
        }
    }

    private void buildCanopyBlobs(List<CanopyBlob> blobs,
                                  TreeSpec spec,
                                  RandomSource random) {

        // 中央主体：更厚更实，避免中间空太多
        blobs.add(new CanopyBlob(
                spec.centerOffsetX,
                spec.canopyCenterY,
                spec.centerOffsetZ,
                5 + random.nextInt(2),  // 5~6
                5 + random.nextInt(2),  // 5~6
                3                       // 更厚
        ));

        // 次核心叶团，靠得更近，用于把树冠揉成一整团
        int coreExtras = 3 + random.nextInt(2);
        for (int i = 0; i < coreExtras; i++) {
            int cx = spec.centerOffsetX + random.nextInt(5) - 2;
            int cz = spec.centerOffsetZ + random.nextInt(5) - 2;
            int cy = spec.canopyCenterY - 1 + random.nextInt(3);

            blobs.add(new CanopyBlob(
                    cx, cy, cz,
                    4, 4, 2
            ));
        }

        // 外围大叶团：仍保持大树冠，但距离比上一版更近，避免分层断开
        int lobeCount = 6 + random.nextInt(3);
        double startAngle = random.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < lobeCount; i++) {
            double angle = startAngle
                    + Math.PI * 2.0D * i / lobeCount
                    + (random.nextDouble() - 0.5D) * 0.28D;

            double distance = 3.3D + random.nextDouble() * 1.5D;
            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * distance);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * distance);
            int cy = spec.canopyCenterY - 1 + random.nextInt(3);

            blobs.add(new CanopyBlob(
                    cx, cy, cz,
                    3 + random.nextInt(2),  // 3~4
                    3 + random.nextInt(2),  // 3~4
                    2
            ));

            // 在核心和外围之间加过渡叶团，专门消掉明显分层和中空
            int bridgeX = (int) Math.round((spec.centerOffsetX + cx) * 0.5D);
            int bridgeZ = (int) Math.round((spec.centerOffsetZ + cz) * 0.5D);
            int bridgeY = spec.canopyCenterY - 1 + random.nextInt(2);

            blobs.add(new CanopyBlob(
                    bridgeX, bridgeY, bridgeZ,
                    3, 3, 2
            ));
        }

        // 少量偏心大叶团，保留每棵树不一样
        int extra = 1 + random.nextInt(2);
        for (int i = 0; i < extra; i++) {
            int cx = spec.centerOffsetX + random.nextInt(7) - 3;
            int cz = spec.centerOffsetZ + random.nextInt(7) - 3;
            int cy = spec.canopyCenterY + random.nextInt(2);

            blobs.add(new CanopyBlob(cx, cy, cz, 3, 3, 2));
        }
    }

    private void buildBranches(BlockPos base,
                               Set<BlockPos> logs,
                               List<CanopyBlob> blobs,
                               TreeSpec spec,
                               RandomSource random) {
        int built = 0;
        for (int i = 1; i < blobs.size(); i++) {
            CanopyBlob blob = blobs.get(i);

            // 只给较远且较大的叶团接枝，减少内部木头乱露
            int targetX = blob.cx;
            int targetZ = blob.cz;
            int steps = Math.max(Math.abs(targetX), Math.abs(targetZ));
            if (steps <= 1) {
                continue;
            }
            if (blob.rx + blob.rz < 6 && random.nextInt(3) != 0) {
                continue;
            }
            if (built >= 6 && random.nextInt(4) != 0) {
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

                // 枝条高度更贴近树冠中心，不再形成明显“上下分层”
                int y = spec.branchY;
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

        // 下摆改成连续的较厚过渡层，而不是一圈一圈台阶
        int skirtPoints = 16 + random.nextInt(6);
        for (int i = 0; i < skirtPoints; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 4.0D + random.nextDouble() * (spec.outerReach - 4.0D);

            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * dist);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * dist);
            int cy = spec.branchY + random.nextInt(2) - 1; // 更靠近主体，减少分层

            addLeafBlob(base, leaves, new CanopyBlob(
                    cx, cy, cz,
                    2 + random.nextInt(2),
                    2 + random.nextInt(2),
                    1 + random.nextInt(2)
            ), random);
        }

        // 顶部只保留少量柔和鼓包，不再太碎
        int topBumps = 1 + random.nextInt(2);
        for (int i = 0; i < topBumps; i++) {
            int cx = spec.centerOffsetX + random.nextInt(5) - 2;
            int cz = spec.centerOffsetZ + random.nextInt(5) - 2;
            int cy = spec.canopyCenterY + 2;

            addLeafBlob(base, leaves, new CanopyBlob(
                    cx, cy, cz,
                    2, 2, 1
            ), random);
        }

        // 轻微修圆顶部，不再挖明显中空
        if (random.nextInt(5) == 0) {
            carvePocket(base.offset(spec.centerOffsetX, spec.canopyCenterY + 2, spec.centerOffsetZ), leaves, 1);
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

                    if (distance > 1.22D) {
                        continue;
                    }

                    // 边缘只做很轻微削切，避免散乱
                    if (distance > 1.00D && random.nextInt(18) == 0) {
                        continue;
                    }

                    leaves.add(base.offset(blob.cx + dx, blob.cy + dy, blob.cz + dz));
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

            // 上盖树叶，防止俯视露木头
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
                              RandomSource random) {
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos leafPos : placedLeaves) {
            BlockPos below = leafPos.below();

            if (!level.isEmptyBlock(below) || logs.contains(below)) {
                continue;
            }

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

        int targetGroups = Math.min(candidates.size(), 28 + random.nextInt(14));

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
            int trunkTopY = 6 + random.nextInt(2);   // 6~7，压低
            int branchY = trunkTopY - 1;
            int canopyCenterY = trunkTopY + 1;       // 树冠更贴近主干
            int outerReach = 7 + random.nextInt(2);  // 7~8，保持够大
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
