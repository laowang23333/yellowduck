package com.yourname.yellowduck.block;

import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        // Residence protection is checked only when opening the container.
        // Placement is intentionally left to the normal Minecraft/Mohist/Residence flow.
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof BigChestBlockEntity be) {

            if (!ResidenceProtection.canOpen(serverPlayer, pos)) {
                player.displayClientMessage(
                        Component.literal("§c你没有权限打开这个海盗箱！"), true);
                return InteractionResult.CONSUME;
            }

            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override public Component getDisplayName() {
                    return Component.literal("海盗箱");
                }

                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BigChestMenu(id, inv, be);
                }
            }, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Direct Residence 6.0.2.4 API integration for Mohist 1.20.1.
     * This check is only used on the server-side right-click/open path.
     */
    private static final class ResidenceProtection {

        private static boolean canOpen(ServerPlayer player, BlockPos pos) {
            if (!Bukkit.getPluginManager().isPluginEnabled("Residence")) {
                return true;
            }

            org.bukkit.entity.Player bukkitPlayer = Bukkit.getPlayer(player.getUUID());
            if (bukkitPlayer == null) {
                // The player is a real server player but Bukkit cannot resolve it;
                // don't interfere with normal container behavior.
                return true;
            }

            World world = bukkitPlayer.getWorld();
            Location location = new Location(
                    world,
                    pos.getX() + 0.5D,
                    pos.getY(),
                    pos.getZ() + 0.5D
            );

            ClaimedResidence residence =
                    ResidenceApi.getResidenceManager().getByLoc(location);

            if (residence == null) {
                return true;
            }

            return residence.getPermissions()
                    .playerHas(bukkitPlayer, Flags.container, true);
        }
    }
}
