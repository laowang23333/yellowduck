package com.yourname.yellowduck.piratechest;

import com.yourname.yellowduck.compat.ResidenceCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DragonPalacePirateChestBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public DragonPalacePirateChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DragonPalacePirateChestBlockEntity(
                DragonPalacePirateChestContent.DRAGON_PALACE_PIRATE_CHEST_BE.get(), pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof DragonPalacePirateChestBlockEntity chest)) {
            return InteractionResult.CONSUME;
        }
        if (!ResidenceCompat.canOpen(serverPlayer, pos)) {
            serverPlayer.displayClientMessage(Component.literal("§c你没有权限打开这个龙宫海盗箱！"), true);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("龙宫海盗箱"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new DragonPalacePirateChestMenu(id, inv, chest);
            }
        }, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluidState) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DragonPalacePirateChestBlockEntity chest && !chest.isEmpty()) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("§c龙宫海盗箱内还有物品，无法挖掘！"), true);
            }
            return false;
        }
        if (player instanceof ServerPlayer sp && !ResidenceCompat.canBreak(sp, pos)) {
            sp.displayClientMessage(Component.literal("§c你没有权限破坏这个龙宫海盗箱！"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluidState);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(DragonPalacePirateChestContent.DRAGON_PALACE_PIRATE_CHEST_ITEM.get()));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DragonPalacePirateChestBlockEntity chest && !chest.isEmpty()) {
                Containers.dropContents(level, pos, chest);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
