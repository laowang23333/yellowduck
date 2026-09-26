package com.yourname.yellowduck.rabbitbox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 兔兔宝箱方块；放置后由 BlockEntity 负责 10 秒倒计时。 */
public final class RabbitBoxBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 按 GLB 实际比例制作的近似碰撞：约 0.95 × 0.73 × 0.67 格。
    private static final VoxelShape SHAPE_NORTH =
            Block.box(0.4D, 0.0D, 2.7D, 15.6D, 11.7D, 13.3D);
    private static final VoxelShape SHAPE_WEST = rotateY90(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotateY90(SHAPE_WEST);
    private static final VoxelShape SHAPE_EAST = rotateY90(SHAPE_SOUTH);

    public RabbitBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RabbitBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide) return null;
        return createTickerHelper(
                type,
                RabbitBoxContent.JADE_RABBIT_BOX_BE.get(),
                RabbitBoxBlockEntity::serverTick
        );
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    private static VoxelShape rotateY90(VoxelShape source) {
        VoxelShape result = Shapes.empty();
        for (AABB box : source.toAabbs()) {
            double minX = box.minZ;
            double maxX = box.maxZ;
            double minZ = 1.0D - box.maxX;
            double maxZ = 1.0D - box.minX;
            result = Shapes.or(
                    result,
                    Block.box(
                            minX * 16.0D,
                            box.minY * 16.0D,
                            minZ * 16.0D,
                            maxX * 16.0D,
                            box.maxY * 16.0D,
                            maxZ * 16.0D
                    )
            );
        }
        return result.optimize();
    }
}
