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

    /*
     * 图片里的祈福树是明显的“伞状大树冠”：
     * - 地面有宽大的根座
     * - 中间单主干
     * - 树冠下有向四周伸展的粗枝
     * - 树冠宽、扁、分层，并带少量凸起
     * - 树冠底部大量悬挂祈福缎带
     */
    private static final int TRUNK_TOP_Y = 8;
    private static final int CANOPY_BOTTOM_Y = 8;
    private static final int CANOPY_TOP_Y = 12;

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
        Set<BlockPos> logs = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();

        buildRootAndTrunk(base, logs);
        buildBranches(base, logs, random);
        buildCanopy(base, logs, leaves, random);

        if (!hasRoom(level, logs, leaves)) {
            return;
        }

        BlockState verticalLog = PrayTreeContent.PRAY_TREE.get()
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

        for (BlockPos pos : logs) {
            Direction.Axis axis = chooseLogAxis(base, pos);
            level.setBlock(
                    pos,
                    verticalLog.setValue(RotatedPillarBlock.AXIS, axis),
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
            placedLeaves.add(pos);
        }

        placeRibbons(level, placedLeaves, logs, random);
    }

    private void buildRootAndTrunk(BlockPos base, Set<BlockPos> logs) {
        // 最底层 3×3 根座。
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                logs.add(base.offset(dx, 0, dz));
            }
        }

        // 第二层十字收口，形成图片里阶梯状树根。
        logs.add(base.above());
        logs.add(base.offset(1, 1, 0));
        logs.add(base.offset(-1, 1, 0));
        logs.add(base.offset(0, 1, 1));
        logs.add(base.offset(0, 1, -1));

        // 单主干。
        for (int y = 2; y <= TRUNK_TOP_Y; y++) {
            logs.add(base.above(y));
        }
    }

    private void buildBranches(BlockPos base, Set<BlockPos> logs, RandomSource random) {
        int rotation = random.nextInt(4);
        Direction[] dirs = {
                Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST
        };

        // 四条主要粗枝，贴着树冠下表面向四周伸展。
        for (int n = 0; n < 4; n++) {
            Direction dir = dirs[(rotation + n) & 3];
            int length = 4 + (n % 2);

            for (int i = 1; i <= length; i++) {
                int rise = i >= length - 1 ? 1 : 0;
                logs.add(base.relative(dir, i).above(7 + rise));
            }

            // 每条主枝末端再向左右分叉一格。
            BlockPos tip = base.relative(dir, length).above(8);
            logs.add(tip.relative(dir.getClockWise()));
            logs.add(tip.relative(dir.getCounterClockWise()));
        }

        // 四条较短的斜向枝，让树冠底面和截图一样更饱满。
        int[][] diagonals = {
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] d : diagonals) {
            for (int i = 1; i <= 3; i++) {
                int dx = d[0] * i;
                int dz = d[1] * i;
                int y = 7 + (i >= 3 ? 1 : 0);
                logs.add(base.offset(dx, y, dz));
            }
        }
    }

    private void buildCanopy(BlockPos base,
                             Set<BlockPos> logs,
                             Set<BlockPos> leaves,
                             RandomSource random) {
        // 下面三层形成图片里宽而扁的伞状主体。
        addCanopyLayer(base, leaves, 8, 6, 9);
        addCanopyLayer(base, leaves, 9, 7, 10);
        addCanopyLayer(base, leaves, 10, 6, 9);

        // 第四层缩小，形成顶部起伏。
        addCanopyLayer(base, leaves, 11, 4, 6);

        // 顶部不是一个尖顶，而是几个错开的“小包”，对应截图俯视的凸起。
        int omitted = random.nextInt(4);
        int[][] caps = {
                {3, 0}, {-3, 0}, {0, 3}, {0, -3}
        };

        addLeafPatch(base.offset(0, 12, 0), leaves, 2);

        for (int i = 0; i < caps.length; i++) {
            if (i == omitted) {
                continue;
            }
            addLeafPatch(base.offset(caps[i][0], 12, caps[i][1]), leaves, 1);
        }

        // 清掉和木头重叠的位置，保证粗枝在树冠底下能够看见。
        leaves.removeAll(logs);
    }

    private void addCanopyLayer(BlockPos base,
                                Set<BlockPos> leaves,
                                int y,
                                int radius,
                                int maxManhattan) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int ax = Math.abs(dx);
                int az = Math.abs(dz);

                if (ax > radius || az > radius) {
                    continue;
                }

                // 阶梯式八边形边缘，比圆球树冠更像截图里的大平顶树冠。
                if (ax + az > maxManhattan) {
                    continue;
                }

                leaves.add(base.offset(dx, y, dz));
            }
        }
    }

    private void addLeafPatch(BlockPos center, Set<BlockPos> leaves, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= radius + 1) {
                    leaves.add(center.offset(dx, 0, dz));
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

    private Direction.Axis chooseLogAxis(BlockPos base, BlockPos pos) {
        int dx = pos.getX() - base.getX();
        int dz = pos.getZ() - base.getZ();

        // 根座和主干都保持竖纹。
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            return Direction.Axis.Y;
        }

        // 横枝按主要延伸方向旋转树干纹理。
        return Math.abs(dx) >= Math.abs(dz)
                ? Direction.Axis.X
                : Direction.Axis.Z;
    }

    private void placeRibbons(ServerLevel level,
                              List<BlockPos> placedLeaves,
                              Set<BlockPos> logs,
                              RandomSource random) {
        List<BlockPos> candidates = new ArrayList<>();

        // 只从树冠最下表面开始挂，避免缎带穿进树叶内部。
        for (BlockPos leafPos : placedLeaves) {
            BlockPos below = leafPos.below();

            if (!level.isEmptyBlock(below)) {
                continue;
            }
            if (logs.contains(below)) {
                continue;
            }

            candidates.add(below);
        }

        Collections.shuffle(
                candidates,
                new java.util.Random(random.nextLong())
        );

        // 截图里缎带非常密集，所以数量明显多于普通装饰树。
        int targetGroups = Math.min(
                candidates.size(),
                38 + random.nextInt(18)
        );

        for (int i = 0; i < targetGroups; i++) {
            BlockPos start = candidates.get(i);

            // 大部分 1~3 格，少量会达到 4 格，做出长短交错的垂挂效果。
            int length = 1 + random.nextInt(3);
            if (random.nextInt(5) == 0) {
                length++;
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
}
