package com.yourname.yellowduck.block;

import com.yourname.yellowduck.menu.BigChestMenu;
import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class BigChestBlock extends BaseEntityBlock {

    public BigChestBlock(Properties props) { super(props); }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigChestBlockEntity(ModBlockEntities.BIG_CHEST.get(), pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BigChestBlockEntity be) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override public Component getDisplayName() { return Component.literal("海盗箱"); }
                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BigChestMenu(id, inv, be);
                }
            }, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
