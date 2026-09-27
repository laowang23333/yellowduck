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

        buildRootAndTrunk(base, logs, spec);
        buildBranches(base, logs, spec, random);
        buildCanopy(base, leaves, spec, random);

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

    private void buildBranches(BlockPos base, Set<BlockPos> logs, TreeSpec spec, RandomSource random) {
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        int rotated = random.nextInt(4);

        for (int i = 0; i < directions.length; i++) {
            int[] d = directions[(i + rotated) % directions.length];
            boolean diagonal = d[0] != 0 && d[1] != 0;
            int length = diagonal
                    ? 2 + random.nextInt(2)
                    : 3 + random.nextInt(2);

            int startY = spec.undersideY + (diagonal ? 0 : 1);

            for (int step = 1; step <= length; step++) {
                int x = d[0] * step;
                int z = d[1] * step;
                int y = startY + (step >= length && !diagonal ? 1 : 0);

                logs.add(base.offset(x, y, z));

                if (!diagonal && step == length && random.nextBoolean()) {
                    logs.add(base.offset(x + Integer.signum(d[0]), y, z + Integer.signum(d[1])));
                }
            }
        }
    }

    private void buildCanopy(BlockPos base,
                             Set<BlockPos> leaves,
                             TreeSpec spec,
                             RandomSource random) {
        int bottom = spec.undersideY;
        int top = spec.topY;

        for (int y = bottom; y <= top; y++) {
            double layer = y - bottom;
            double heightNorm = spec.height == 0 ? 0.0D : layer / (double) spec.height;

            // 底部宽平，中间最厚，顶部收窄，不再是整齐“蛋糕层”。
            double baseRadius;
            if (heightNorm < 0.26D) {
                baseRadius = spec.outerRadius;
            } else if (heightNorm < 0.56D) {
                baseRadius = spec.outerRadius - 0.35D;
            } else if (heightNorm < 0.82D) {
                baseRadius = spec.outerRadius - 1.15D;
            } else {
                baseRadius = spec.outerRadius - 2.15D;
            }

            for (int dx = -(spec.outerRadius + 2); dx <= spec.outerRadius + 2; dx++) {
                for (int dz = -(spec.outerRadius + 2); dz <= spec.outerRadius + 2; dz++) {
                    double px = dx - spec.centerOffsetX * heightNorm * 0.35D;
                    double pz = dz - spec.centerOffsetZ * heightNorm * 0.35D;

                    double angle = Math.atan2(pz, px);
                    double distance = Math.sqrt(px * px + pz * pz);

                    double edgeNoise =
                            Math.sin(angle * spec.lobeCount + spec.phase1) * spec.lobeAmp1
                          + Math.cos(angle * (spec.lobeCount + 2) + spec.phase2) * spec.lobeAmp2;

                    double radius = baseRadius + edgeNoise;

                    // 顶层整体再收一点，让顶部不是大平面。
                    if (y == top) {
                        radius -= 0.8D;
                    }

                    if (distance <= radius) {
                        leaves.add(base.offset(dx, y, dz));
                    }
                }
            }
        }

        // 顶部做 3~5 个随机鼓包，接近视频里自然起伏，而不是固定一圈一圈。
        int bumps = 3 + random.nextInt(3);
        for (int i = 0; i < bumps; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = random.nextDouble() * 2.2D;
            int cx = (int) Math.round(Math.cos(angle) * dist);
            int cz = (int) Math.round(Math.sin(angle) * dist);
            int cy = spec.topY - 1 + random.nextInt(2);
            int radius = 1 + random.nextInt(2);
            addLeafPatch(base.offset(cx, cy, cz), leaves, radius);
        }

        // 树冠下沿补一点垂边，让轮廓更像视频里自然下坠，而不是纯水平切面。
        int droops = 10 + random.nextInt(8);
        for (int i = 0; i < droops; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = spec.outerRadius - 0.5D + random.nextDouble() * 0.8D;
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            BlockPos p = base.offset(x, bottom - 1, z);
            if (Math.abs(x) + Math.abs(z) > 2) {
                leaves.add(p);
            }
        }

        // 中间稍微掏掉一点点顶面，让顶部更自然。
        if (random.nextBoolean()) {
            carveTopPocket(base.above(spec.topY), leaves, 1 + random.nextInt(2));
        }
    }

    private void addLeafPatch(BlockPos center, Set<BlockPos> leaves, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    leaves.add(center.offset(dx, 0, dz));
                }
            }
        }
    }

    private void carveTopPocket(BlockPos center, Set<BlockPos> leaves, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    leaves.remove(center.offset(dx, 0, dz));
                }
            }
        }
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

        if (pos.getY() <= 2 || (dx == 0 && dz == 0)) {
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

            // 只优先从树冠下表面和边缘位置挂缎带。
            if (!isBottomSurfaceLeaf(level, leafPos)) {
                continue;
            }

            int edgeScore = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.isEmptyBlock(leafPos.relative(d))) {
                    edgeScore++;
                }
            }

            candidates.add(below);
            if (edgeScore >= 2 && random.nextBoolean()) {
                candidates.add(below); // 边缘位置更容易被抽中，接近视频里的外围垂挂感
            }
        }

        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));

        int targetGroups = Math.min(
                candidates.size(),
                20 + random.nextInt(14)
        );

        Set<BlockPos> used = new HashSet<>();
        for (int i = 0; i < candidates.size() && used.size() < targetGroups; i++) {
            BlockPos start = candidates.get(i);
            if (!used.add(start)) {
                continue;
            }

            int lengthRoll = random.nextInt(100);
            int length;
            if (lengthRoll < 45) {
                length = 1;
            } else if (lengthRoll < 75) {
                length = 2;
            } else if (lengthRoll < 93) {
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

    private boolean isBottomSurfaceLeaf(ServerLevel level, BlockPos leafPos) {
        if (!level.isEmptyBlock(leafPos.below())) {
            return false;
        }

        int solidSides = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(leafPos.relative(d)).is(PrayTreeContent.PRAY_LEAVES.get())) {
                solidSides++;
            }
        }
        return solidSides <= 3;
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

    private static final class TreeSpec {
        final int undersideY;
        final int topY;
        final int height;
        final int trunkTopY;
        final int outerRadius;
        final int lobeCount;
        final double lobeAmp1;
        final double lobeAmp2;
        final double phase1;
        final double phase2;
        final int centerOffsetX;
        final int centerOffsetZ;

        private TreeSpec(int undersideY,
                         int topY,
                         int trunkTopY,
                         int outerRadius,
                         int lobeCount,
                         double lobeAmp1,
                         double lobeAmp2,
                         double phase1,
                         double phase2,
                         int centerOffsetX,
                         int centerOffsetZ) {
            this.undersideY = undersideY;
            this.topY = topY;
            this.height = topY - undersideY;
            this.trunkTopY = trunkTopY;
            this.outerRadius = outerRadius;
            this.lobeCount = lobeCount;
            this.lobeAmp1 = lobeAmp1;
            this.lobeAmp2 = lobeAmp2;
            this.phase1 = phase1;
            this.phase2 = phase2;
            this.centerOffsetX = centerOffsetX;
            this.centerOffsetZ = centerOffsetZ;
        }

        static TreeSpec create(RandomSource random) {
            int undersideY = 7 + random.nextInt(2);      // 7~8
            int topY = undersideY + 3 + random.nextInt(2); // 总高 4~5 层
            int trunkTopY = undersideY - 1;
            int outerRadius = 5 + random.nextInt(2);     // 5~6
            int lobeCount = 3 + random.nextInt(3);       // 3~5 个外轮廓波瓣
            double lobeAmp1 = 0.35D + random.nextDouble() * 0.75D;
            double lobeAmp2 = 0.15D + random.nextDouble() * 0.45D;
            double phase1 = random.nextDouble() * Math.PI * 2.0D;
            double phase2 = random.nextDouble() * Math.PI * 2.0D;
            int centerOffsetX = random.nextInt(3) - 1;
            int centerOffsetZ = random.nextInt(3) - 1;

            return new TreeSpec(
                    undersideY, topY, trunkTopY, outerRadius,
                    lobeCount, lobeAmp1, lobeAmp2, phase1, phase2,
                    centerOffsetX, centerOffsetZ
            );
        }
    }
}
