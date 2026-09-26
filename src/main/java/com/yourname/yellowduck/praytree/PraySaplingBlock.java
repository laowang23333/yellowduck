package com.yourname.yellowduck.praytree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PraySaplingBlock extends BushBlock implements BonemealableBlock {
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;

    private static final int SNAPSHOT_RADIUS = 10;
    private static final int SNAPSHOT_DOWN = 2;
    private static final int SNAPSHOT_UP = 20;

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

        growVanillaCherryShape(level, pos, state, random);
    }

    /**
     * 直接调用 Minecraft 1.20.1 原版樱花树 ConfiguredFeature 来决定
     * 主干、分叉、树冠以及悬垂树叶的结构。
     *
     * 生成完成后，只把这次新生成出来的樱花原木和樱花树叶替换成
     * 祈福树自己的树干和树叶，最后再在树冠下方添加祈福缎带。
     */
    private void growVanillaCherryShape(ServerLevel level, BlockPos pos,
                                       BlockState saplingState,
                                       RandomSource random) {
        Map<BlockPos, BlockState> before = snapshot(level, pos);

        ConfiguredFeature<?, ?> cherry = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(TreeFeatures.CHERRY)
                .value();

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 4);

        boolean placed = cherry.place(
                level,
                level.getChunkSource().getGenerator(),
                random,
                pos
        );

        if (!placed) {
            level.setBlock(pos, saplingState, 4);
            return;
        }

        List<BlockPos> newPrayLeaves = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockPos checkPos = entry.getKey();
            BlockState oldState = entry.getValue();
            BlockState newState = level.getBlockState(checkPos);

            if (newState.is(Blocks.CHERRY_LOG)
                    && !oldState.is(Blocks.CHERRY_LOG)) {
                Direction.Axis axis = newState.getValue(RotatedPillarBlock.AXIS);

                level.setBlock(
                        checkPos,
                        PrayTreeContent.PRAY_TREE.get()
                                .defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, axis),
                        3
                );
                continue;
            }

            if (newState.is(Blocks.CHERRY_LEAVES)
                    && !oldState.is(Blocks.CHERRY_LEAVES)) {
                BlockState prayLeaves = PrayTreeContent.PRAY_LEAVES.get()
                        .defaultBlockState();

                if (newState.hasProperty(LeavesBlock.DISTANCE)) {
                    prayLeaves = prayLeaves.setValue(
                            LeavesBlock.DISTANCE,
                            newState.getValue(LeavesBlock.DISTANCE)
                    );
                }

                if (newState.hasProperty(LeavesBlock.PERSISTENT)) {
                    prayLeaves = prayLeaves.setValue(
                            LeavesBlock.PERSISTENT,
                            newState.getValue(LeavesBlock.PERSISTENT)
                    );
                }

                level.setBlock(checkPos, prayLeaves, 3);
                newPrayLeaves.add(checkPos.immutable());
            }
        }

        placeRibbons(level, newPrayLeaves, random);
    }

    private Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockState> result = new HashMap<>();

        for (int y = -SNAPSHOT_DOWN; y <= SNAPSHOT_UP; y++) {
            for (int x = -SNAPSHOT_RADIUS; x <= SNAPSHOT_RADIUS; x++) {
                for (int z = -SNAPSHOT_RADIUS; z <= SNAPSHOT_RADIUS; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    result.put(p.immutable(), level.getBlockState(p));
                }
            }
        }

        return result;
    }

    private void placeRibbons(ServerLevel level,
                              List<BlockPos> generatedLeaves,
                              RandomSource random) {
        if (generatedLeaves.isEmpty()) {
            return;
        }

        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos leafPos : generatedLeaves) {
            BlockPos below = leafPos.below();

            if (level.isEmptyBlock(below)
                    && level.getBlockState(leafPos)
                    .is(PrayTreeContent.PRAY_LEAVES.get())) {
                candidates.add(below);
            }
        }

        Collections.shuffle(
                candidates,
                new java.util.Random(random.nextLong())
        );

        int ribbonGroups = Math.min(
                candidates.size(),
                5 + random.nextInt(4)
        );

        for (int i = 0; i < ribbonGroups; i++) {
            BlockPos start = candidates.get(i);
            int length = 1 + random.nextInt(3);

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
