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
        List<CanopyLobe> lobes = new ArrayList<>();

        buildRootAndTrunk(base, logs, spec);
        buildCanopy(base, leaves, lobes, spec, random);
        buildBranches(base, logs, lobes, spec, random);
        coverBranches(base, logs, leaves, spec, random);
        fillSmallCanopyGaps(base, logs, leaves, spec);

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
            level.setBlock(pos, leafState, 3);
            placedLeaves.add(pos.immutable());
        }

        placeRibbons(level, base, placedLeaves, logs, spec, random);
    }

    private void buildRootAndTrunk(BlockPos base, Set<BlockPos> logs, TreeSpec spec) {
        // 视频里的根座：底部 3×3，上面十字收口。
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

        // 主干恢复到视频里的中等高度：
        // 比上一版明显高，但不会回到最早那种光秃秃的长杆。
        for (int y = 2; y <= spec.trunkTopY; y++) {
            logs.add(base.above(y));
        }
    }

    /**
     * 树冠主体改成连续的大型扁椭球，再叠加少数大叶瓣。
     *
     * 这样：
     * - 中间不会出现一层一层的大空隙
     * - 底部是圆弧/波浪形，不是一张平板
     * - 整体仍然很大
     * - 每棵树轮廓会有随机差异
     */
    private void buildCanopy(BlockPos base,
                             Set<BlockPos> leaves,
                             List<CanopyLobe> lobes,
                             TreeSpec spec,
                             RandomSource random) {

        // 一整个连续的主体树冠。
        addIrregularEllipsoid(
                base,
                leaves,
                spec.centerOffsetX,
                spec.canopyCenterY,
                spec.centerOffsetZ,
                spec.radiusX,
                spec.radiusZ,
                spec.radiusY,
                spec,
                random
        );

        // 外围 5~7 个大叶瓣，全部和主体重叠，不再形成独立层。
        int lobeCount = 5 + random.nextInt(3);
        double startAngle = random.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < lobeCount; i++) {
            double angle = startAngle
                    + Math.PI * 2.0D * i / lobeCount
                    + (random.nextDouble() - 0.5D) * 0.28D;

            double distance = 3.4D + random.nextDouble() * 1.8D;
            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * distance);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * distance);

            // 叶瓣高度只上下浮动 1 格，避免明显的“楼层感”。
            int cy = spec.canopyCenterY - 1 + random.nextInt(3);
            int rx = 3 + random.nextInt(2);
            int rz = 3 + random.nextInt(2);
            int ry = 2;

            CanopyLobe lobe = new CanopyLobe(cx, cy, cz, rx, rz, ry);
            lobes.add(lobe);
            addSolidEllipsoid(base, leaves, lobe, random);
        }

        // 顶部 2~3 个柔和鼓包。
        int topBumps = 2 + random.nextInt(2);
        for (int i = 0; i < topBumps; i++) {
            int cx = spec.centerOffsetX + random.nextInt(7) - 3;
            int cz = spec.centerOffsetZ + random.nextInt(7) - 3;
            int cy = spec.canopyCenterY + 2;

            addSolidEllipsoid(
                    base,
                    leaves,
                    new CanopyLobe(cx, cy, cz, 2, 2, 1),
                    random
            );
        }

        // 只在外圈加少量下垂叶团，让底面更像视频里的自然波浪。
        // 最低只比主体底部再低 1 格，不会重新贴近地面。
        int droops = 7 + random.nextInt(6);
        for (int i = 0; i < droops; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = spec.radiusX - 1.0D + random.nextDouble() * 1.4D;

            int cx = spec.centerOffsetX + (int) Math.round(Math.cos(angle) * distance);
            int cz = spec.centerOffsetZ + (int) Math.round(Math.sin(angle) * distance);
            int cy = spec.canopyBottomY;

            addSolidEllipsoid(
                    base,
                    leaves,
                    new CanopyLobe(cx, cy, cz, 2, 2, 1),
                    random
            );
        }
    }

    private void addIrregularEllipsoid(BlockPos base,
                                       Set<BlockPos> leaves,
                                       int centerX,
                                       int centerY,
                                       int centerZ,
                                       int radiusX,
                                       int radiusZ,
                                       int radiusY,
                                       TreeSpec spec,
                                       RandomSource random) {

        for (int dy = -radiusY; dy <= radiusY; dy++) {
            double ny = dy / (double) radiusY;
            double verticalPart = ny * ny;

            for (int dx = -(radiusX + 2); dx <= radiusX + 2; dx++) {
                for (int dz = -(radiusZ + 2); dz <= radiusZ + 2; dz++) {
                    double angle = Math.atan2(dz, dx);

                    // 低频轮廓起伏，让每棵树外围不完全一致，但主体仍是一整团。
                    double wave =
                            Math.sin(angle * spec.waveCount + spec.phase1) * spec.waveAmp1
                          + Math.cos(angle * (spec.waveCount + 2) + spec.phase2) * spec.waveAmp2;

                    double rx = radiusX + wave;
                    double rz = radiusZ + wave * 0.85D;

                    double nx = dx / rx;
                    double nz = dz / rz;
                    double horizontalPart = nx * nx + nz * nz;

                    if (horizontalPart + verticalPart <= 1.04D) {
                        leaves.add(base.offset(
                                centerX + dx,
                                centerY + dy,
                                centerZ + dz
                        ));
                    }
                }
            }
        }
    }

    private void addSolidEllipsoid(BlockPos base,
                                   Set<BlockPos> leaves,
                                   CanopyLobe lobe,
                                   RandomSource random) {

        for (int dx = -lobe.rx; dx <= lobe.rx; dx++) {
            for (int dy = -lobe.ry; dy <= lobe.ry; dy++) {
                for (int dz = -lobe.rz; dz <= lobe.rz; dz++) {
                    double nx = dx / (double) lobe.rx;
                    double ny = dy / (double) lobe.ry;
                    double nz = dz / (double) lobe.rz;
                    double distance = nx * nx + ny * ny + nz * nz;

                    if (distance > 1.12D) {
                        continue;
                    }

                    // 只削极少量最外围方块，避免再次散乱。
                    if (distance > 1.03D && random.nextInt(20) == 0) {
                        continue;
                    }

                    leaves.add(base.offset(
                            lobe.cx + dx,
                            lobe.cy + dy,
                            lobe.cz + dz
                    ));
                }
            }
        }
    }

    /**
     * 枝条放在树冠下半部，向外围大叶瓣伸展。
     * 从树下能看到枝条，但不会高出树冠。
     */
    private void buildBranches(BlockPos base,
                               Set<BlockPos> logs,
                               List<CanopyLobe> lobes,
                               TreeSpec spec,
                               RandomSource random) {

        int built = 0;

        for (CanopyLobe lobe : lobes) {
            if (built >= 6) {
                break;
            }

            int targetX = lobe.cx;
            int targetZ = lobe.cz;
            int steps = Math.max(Math.abs(targetX), Math.abs(targetZ));

            if (steps < 3) {
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

                int y = spec.branchY;

                // 只有末端偶尔抬高 1 格。
                if (step == steps && random.nextBoolean()) {
                    y++;
                }

                logs.add(base.offset(x, y, z));
                lastX = x;
                lastZ = z;
            }

            built++;
        }
    }

    /**
     * 保证从树顶看不到裸露木头，并把枝条和树冠连接成连续体。
     */
    private void coverBranches(BlockPos base,
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

    /**
     * 填掉树冠内部的小孔洞。
     * 只填主体树冠高度范围，不会把树下空间封死。
     */
    private void fillSmallCanopyGaps(BlockPos base,
                                     Set<BlockPos> logs,
                                     Set<BlockPos> leaves,
                                     TreeSpec spec) {

        for (int pass = 0; pass < 2; pass++) {
            Set<BlockPos> toAdd = new HashSet<>();

            for (int y = spec.canopyBottomY; y <= spec.canopyTopY; y++) {
                for (int dx = -(spec.radiusX + 2); dx <= spec.radiusX + 2; dx++) {
                    for (int dz = -(spec.radiusZ + 2); dz <= spec.radiusZ + 2; dz++) {
                        BlockPos pos = base.offset(dx, y, dz);

                        if (leaves.contains(pos) || logs.contains(pos)) {
                            continue;
                        }

                        int leafNeighbors = 0;
                        for (Direction dir : Direction.values()) {
                            if (leaves.contains(pos.relative(dir))) {
                                leafNeighbors++;
                            }
                        }

                        // 被四面以上树叶包围的小洞直接补掉。
                        if (leafNeighbors >= 4) {
                            toAdd.add(pos);
                            continue;
                        }

                        // 上下都有叶子时，也补掉中间断层。
                        if (leaves.contains(pos.above()) && leaves.contains(pos.below())) {
                            toAdd.add(pos);
                        }
                    }
                }
            }

            leaves.addAll(toAdd);
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
                              BlockPos base,
                              List<BlockPos> placedLeaves,
                              Set<BlockPos> logs,
                              TreeSpec spec,
                              RandomSource random) {

        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos leafPos : placedLeaves) {
            int relativeY = leafPos.getY() - base.getY();

            // 只允许树冠下半部和底面挂缎带。
            if (relativeY > spec.canopyCenterY + 1) {
                continue;
            }

            BlockPos below = leafPos.below();

            if (!level.isEmptyBlock(below) || logs.contains(below)) {
                continue;
            }

            candidates.add(below);

            int openSides = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (level.isEmptyBlock(leafPos.relative(dir))) {
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

        int targetGroups = Math.min(
                candidates.size(),
                34 + random.nextInt(18)
        );

        Set<BlockPos> used = new HashSet<>();

        for (int i = 0; i < candidates.size() && used.size() < targetGroups; i++) {
            BlockPos start = candidates.get(i);

            if (!used.add(start)) {
                continue;
            }

            int roll = random.nextInt(100);
            int length;

            if (roll < 30) {
                length = 1;
            } else if (roll < 62) {
                length = 2;
            } else if (roll < 88) {
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

    private static final class CanopyLobe {
        final int cx;
        final int cy;
        final int cz;
        final int rx;
        final int rz;
        final int ry;

        private CanopyLobe(int cx, int cy, int cz,
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
        final int canopyBottomY;
        final int canopyTopY;
        final int radiusX;
        final int radiusZ;
        final int radiusY;
        final int centerOffsetX;
        final int centerOffsetZ;
        final int waveCount;
        final double waveAmp1;
        final double waveAmp2;
        final double phase1;
        final double phase2;

        private TreeSpec(int trunkTopY,
                         int branchY,
                         int canopyCenterY,
                         int canopyBottomY,
                         int canopyTopY,
                         int radiusX,
                         int radiusZ,
                         int radiusY,
                         int centerOffsetX,
                         int centerOffsetZ,
                         int waveCount,
                         double waveAmp1,
                         double waveAmp2,
                         double phase1,
                         double phase2) {
            this.trunkTopY = trunkTopY;
            this.branchY = branchY;
            this.canopyCenterY = canopyCenterY;
            this.canopyBottomY = canopyBottomY;
            this.canopyTopY = canopyTopY;
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            this.radiusY = radiusY;
            this.centerOffsetX = centerOffsetX;
            this.centerOffsetZ = centerOffsetZ;
            this.waveCount = waveCount;
            this.waveAmp1 = waveAmp1;
            this.waveAmp2 = waveAmp2;
            this.phase1 = phase1;
            this.phase2 = phase2;
        }

        static TreeSpec create(RandomSource random) {
            // 根座上方能明显看到一段主干，但不会特别长。
            int trunkTopY = 7 + random.nextInt(2);      // 7~8

            // 树冠主体抬高，底面约在 8~9 格，不再贴地。
            int canopyCenterY = trunkTopY + 3;          // 10~11
            int radiusY = 2 + random.nextInt(2);        // 2~3

            int canopyBottomY = canopyCenterY - radiusY;
            int canopyTopY = canopyCenterY + radiusY + 1;
            int branchY = canopyBottomY;                // 枝条藏在树冠底部内部

            int radiusX = 7 + random.nextInt(2);         // 7~8
            int radiusZ = 7 + random.nextInt(2);         // 7~8

            int centerOffsetX = random.nextInt(3) - 1;
            int centerOffsetZ = random.nextInt(3) - 1;

            int waveCount = 3 + random.nextInt(3);
            double waveAmp1 = 0.35D + random.nextDouble() * 0.55D;
            double waveAmp2 = 0.15D + random.nextDouble() * 0.35D;
            double phase1 = random.nextDouble() * Math.PI * 2.0D;
            double phase2 = random.nextDouble() * Math.PI * 2.0D;

            return new TreeSpec(
                    trunkTopY,
                    branchY,
                    canopyCenterY,
                    canopyBottomY,
                    canopyTopY,
                    radiusX,
                    radiusZ,
                    radiusY,
                    centerOffsetX,
                    centerOffsetZ,
                    waveCount,
                    waveAmp1,
                    waveAmp2,
                    phase1,
                    phase2
            );
        }
    }
}
