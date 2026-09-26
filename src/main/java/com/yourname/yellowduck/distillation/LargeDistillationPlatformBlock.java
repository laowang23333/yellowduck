package com.yourname.yellowduck.distillation;

import com.yourname.yellowduck.compat.ResidenceCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LargeDistillationPlatformBlock extends BaseEntityBlock {
    public static final BooleanProperty MAIN = BooleanProperty.create("main");
    private static boolean dismantling;

    public LargeDistillationPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MAIN, true));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MAIN); }
    @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(MAIN) ? RenderShape.MODEL : RenderShape.INVISIBLE; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAIN) ? new LargeDistillationPlatformBlockEntity(pos, state) : null;
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(MAIN)) return null;
        return createTickerHelper(type, DistillationContent.LARGE_DISTILLATION_PLATFORM_BE.get(),
                LargeDistillationPlatformBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockPos main = pos;
        if (canRaise(level, pos)) {
            main = pos.above();
            dismantling = true;
            try {
                level.removeBlock(pos, false);
                level.setBlock(main, defaultBlockState(), 3);
            } finally { dismantling = false; }
        }
        if (placer instanceof Player player && level.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be) {
            be.setOwner(player);
        }
        buildShell(level, main);
    }

    private static boolean canRaise(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).canBeReplaced()
                && level.getBlockState(pos.above(2)).canBeReplaced();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos main = state.getValue(MAIN) ? pos : findMain(level, pos);
        if (main == null || !(level.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            if (!be.isOwner(sp) && !ResidenceCompat.canOpen(sp, main)) {
                sp.displayClientMessage(Component.literal("§c你没有权限打开这个大型蒸馏台！"), true);
                return InteractionResult.FAIL;
            }
            NetworkHooks.openScreen(sp, be, main);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluidState) {
        if (player instanceof ServerPlayer sp && !ResidenceCompat.canBreak(sp, pos)) {
            sp.displayClientMessage(Component.literal("§c你没有权限破坏这个大型蒸馏台！"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluidState);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity be, ItemStack tool) {
        if (!level.isClientSide && !player.isCreative() && !state.getValue(MAIN)) {
            BlockPos main = findMain(level, pos);
            if (main != null) popResource(level, pos, new ItemStack(DistillationContent.LARGE_DISTILLATION_PLATFORM.get()));
        }
        super.playerDestroy(level, player, pos, state, be, tool);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !isMoving && !dismantling) {
            BlockPos main = state.getValue(MAIN) ? pos : findMain(level, pos);
            if (main != null) {
                if (level.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be) {
                    Containers.dropContents(level, pos, be);
                }
                dismantling = true;
                try {
                    for (BlockPos p : BlockPos.betweenClosed(main.offset(-1,-1,-1), main.offset(1,1,1))) {
                        if (p.equals(pos)) continue;
                        if (level.getBlockState(p).is(this)) level.removeBlock(p.immutable(), false);
                    }
                } finally { dismantling = false; }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return state.getValue(MAIN) ? super.getDrops(state, builder) : List.of();
    }

    private void buildShell(Level level, BlockPos main) {
        BlockState shell = defaultBlockState().setValue(MAIN, false);
        for (BlockPos p : BlockPos.betweenClosed(main.offset(-1,-1,-1), main.offset(1,1,1))) {
            if (p.equals(main)) continue;
            if (level.getBlockState(p).isAir()) level.setBlock(p.immutable(), shell, 3);
        }
    }

    public static BlockPos findMain(Level level, BlockPos around) {
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-1,-1,-1), around.offset(1,1,1))) {
            BlockState s = level.getBlockState(p);
            if (s.is(DistillationContent.LARGE_DISTILLATION_PLATFORM.get()) && s.getValue(MAIN)) return p.immutable();
        }
        return null;
    }
}
