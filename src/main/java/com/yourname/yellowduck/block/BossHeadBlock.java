package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.HorizontalDirectionalBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 头颅展示方块；模型由客户端 BlockEntityRenderer 绘制。 */
public class BossHeadBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BossHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shapeFor(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 方块正面朝向放置者；同一模型在四个水平朝向都能正确保存。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private VoxelShape shapeFor(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(this).getPath();

        // These bounds are the GLB mesh bounds after the Native renderer's 0.1 scale,
        // converted to block coordinates. They deliberately leave empty space around
        // the head instead of claiming the entire 1x1x1 block.
        double minX;
        double maxX;
        double minY;
        double maxY;
        double minZ;
        double maxZ;
        switch (id) {
            case "alpaca_head" -> {
                minX = 2.4D; maxX = 13.6D; minY = 0.0D; maxY = 6.3D;
                minZ = 3.3D; maxZ = 12.7D;
            }
            case "cleopatra_head" -> {
                minX = 1.7D; maxX = 14.3D; minY = 0.0D; maxY = 14.9D;
                minZ = 0.2D; maxZ = 13.1D;
            }
            case "earl_head" -> {
                minX = 1.6D; maxX = 14.4D; minY = 0.0D; maxY = 8.3D;
                minZ = 3.3D; maxZ = 15.8D;
            }
            case "sakura_head" -> {
                minX = 2.0D; maxX = 15.3D; minY = 0.0D; maxY = 6.3D;
                minZ = 2.4D; maxZ = 14.7D;
            }
            case "snake_head" -> {
                minX = 0.1D; maxX = 15.9D; minY = 0.0D; maxY = 10.9D;
                minZ = 0.3D; maxZ = 15.0D;
            }
            case "snow_monster_head" -> {
                minX = 0.0D; maxX = 16.0D; minY = 0.0D; maxY = 14.7D;
                minZ = 4.1D; maxZ = 16.0D;
            }
            case "toy_bear_head" -> {
                minX = 1.6D; maxX = 14.4D; minY = 0.0D; maxY = 8.2D;
                minZ = 2.4D; maxZ = 15.2D;
            }
            default -> {
                minX = 2.0D; maxX = 14.0D; minY = 0.0D; maxY = 12.0D;
                minZ = 2.0D; maxZ = 14.0D;
            }
        }

        // The source GLBs face SOUTH. Swap the horizontal extents for side-facing
        // placements so the rectangular heads keep their approximate footprint.
        Direction facing = state.getValue(FACING);
        if (facing == Direction.EAST || facing == Direction.WEST) {
            return Block.box(minZ, minY, minX, maxZ, maxY, maxX);
        }
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossHeadBlockEntity(ModBlockEntities.BOSS_HEAD.get(), pos, state);
    }
}
