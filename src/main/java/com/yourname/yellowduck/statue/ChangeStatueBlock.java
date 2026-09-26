package com.yourname.yellowduck.statue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 静态嫦娥雕像方块。
 *
 * 模型按 1.80 格高渲染，碰撞体积依据模型实际顶点轮廓分成 6 层，
 * 不再使用整格方块碰撞；朝向改变时碰撞轮廓也会一起旋转。
 */
public final class ChangeStatueBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /**
     * NORTH 朝向的 6 层碰撞轮廓。
     *
     * 数值来自 change_statue.glb 的实际顶点分布，并适当忽略极少量装饰尖角，
     * 这样既能贴近雕像主体，又不会因为一颗小装饰把整层碰撞撑得特别大。
     *
     * Block.box 的单位是 1/16 格，因此 Y 最大 28.8 = 1.80 格。
     */
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            // 底座
            Block.box(1.58D, 0.00D, 0.62D, 14.41D, 4.32D, 16.32D),
            // 下半身 / 底部装饰
            Block.box(0.42D, 4.32D, -0.44D, 15.09D, 10.08D, 17.16D),
            // 身体最宽区域
            Block.box(0.83D, 10.08D, 1.38D, 14.88D, 15.84D, 17.06D),
            // 上半身
            Block.box(2.06D, 15.84D, 0.33D, 13.92D, 21.60D, 11.40D),
            // 头部 / 上层装饰
            Block.box(1.53D, 21.60D, 1.38D, 14.46D, 25.92D, 11.45D),
            // 顶部
            Block.box(4.56D, 25.92D, 1.37D, 11.43D, 28.80D, 10.40D)
    );

    private static final VoxelShape SHAPE_WEST = rotateY90(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotateY90(SHAPE_WEST);
    private static final VoxelShape SHAPE_EAST = rotateY90(SHAPE_SOUTH);

    public ChangeStatueBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
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

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
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

    /**
     * 围绕方块中心按 +90° Y 轴旋转碰撞轮廓。
     * 支持模型碰撞超出单个 1×1×1 方块边界。
     */
    private static VoxelShape rotateY90(VoxelShape source) {
        VoxelShape result = Shapes.empty();

        for (AABB box : source.toAabbs()) {
            // +90°: x' = z, z' = 1 - x
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChangeStatueBlockEntity(pos, state);
    }
}
