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
import net.minecraft.world.level.material.FluidState;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BigChestBlock extends BaseEntityBlock {

    public BigChestBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BigChestBlockEntity(ModBlockEntities.BIG_CHEST.get(), pos, state);
    }

    /**
     * 海盗箱内有物品时禁止破坏。
     * 空箱则正常破坏，并交给原版/Forge 的掉落机制掉落海盗箱本身。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                       Player player, boolean willHarvest, FluidState fluidState) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof BigChestBlockEntity be && hasAnyItem(be)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.literal("§c海盗箱内还有物品，无法挖掘！"), true);
            }
            return false;
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluidState);
    }

    /**
     * 通过反射检查海盗箱库存，避免依赖 BigChestBlockEntity 的具体容器接口实现。
     */
    private static boolean hasAnyItem(BigChestBlockEntity be) {
        try {
            Method getContainerSize = be.getClass().getMethod("getContainerSize");
            Method getItem = be.getClass().getMethod("getItem", int.class);
            int size = ((Number) getContainerSize.invoke(be)).intValue();

            for (int i = 0; i < size; i++) {
                Object stack = getItem.invoke(be, i);
                if (stack instanceof net.minecraft.world.item.ItemStack itemStack
                        && !itemStack.isEmpty()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 如果当前箱子实现没有标准容器方法，不阻止正常破坏。
        }
        return false;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        if (!(level.getBlockEntity(pos) instanceof BigChestBlockEntity be)) {
            return InteractionResult.CONSUME;
        }

        // Residence is a Bukkit/Mohist plugin.  Do not reference its classes
        // directly here: on Mohist the Forge mod classloader may not be able to
        // resolve ResidenceApi even when the Residence plugin itself is loaded.
        if (!ResidenceProtection.canOpen(serverPlayer, pos)) {
            serverPlayer.displayClientMessage(
                    Component.literal("§c你没有权限打开这个海盗箱！"), true);
            return InteractionResult.CONSUME;
        }

        NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("海盗箱");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BigChestMenu(id, inv, be);
            }
        }, pos);

        return InteractionResult.CONSUME;
    }

    /**
     * Residence protection for Mohist, deliberately isolated behind reflection.
     *
     * The important detail is that ResidenceApi is loaded with Residence's own
     * Bukkit plugin classloader rather than the Forge mod classloader.  This
     * prevents ClassNotFoundException from taking down the server when a Forge
     * class touches the chest.
     */
    private static final class ResidenceProtection {
        private static final String RESIDENCE_PLUGIN = "Residence";
        private static final String RESIDENCE_API =
                "com.bekvon.bukkit.residence.api.ResidenceApi";
        private static final String FLAGS =
                "com.bekvon.bukkit.residence.containers.Flags";

        private ResidenceProtection() {
        }

        private static boolean canOpen(ServerPlayer player, BlockPos pos) {
            try {
                // OP 无视 Residence 领地容器权限，任何位置都可以打开海盗箱。
                if (player.hasPermissions(2)) {
                    return true;
                }
                if (!Bukkit.getPluginManager().isPluginEnabled(RESIDENCE_PLUGIN)) {
                    return true;
                }

                org.bukkit.plugin.Plugin residencePlugin =
                        Bukkit.getPluginManager().getPlugin(RESIDENCE_PLUGIN);
                if (residencePlugin == null) {
                    return true;
                }

                org.bukkit.entity.Player bukkitPlayer =
                        Bukkit.getPlayer(player.getUUID());
                if (bukkitPlayer == null) {
                    return true;
                }

                World world = bukkitPlayer.getWorld();
                Location location = new Location(
                        world,
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D
                );

                ClassLoader residenceLoader = residencePlugin.getClass().getClassLoader();
                Class<?> apiClass = Class.forName(RESIDENCE_API, true, residenceLoader);
                Method getResidenceManager = apiClass.getMethod("getResidenceManager");
                Object manager = getResidenceManager.invoke(null);
                if (manager == null) {
                    return true;
                }

                Method getByLoc = manager.getClass().getMethod("getByLoc", Location.class);
                Object residence = getByLoc.invoke(manager, location);
                if (residence == null) {
                    return true;
                }

                Method getPermissions = residence.getClass().getMethod("getPermissions");
                Object permissions = getPermissions.invoke(residence);
                if (permissions == null) {
                    return false;
                }

                Class<?> flagsClass = Class.forName(FLAGS, true, residenceLoader);
                Field containerField = flagsClass.getField("container");
                Object containerFlag = containerField.get(null);

                for (Method method : permissions.getClass().getMethods()) {
                    if (!method.getName().equals("playerHas")) {
                        continue;
                    }

                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 3 || params[2] != boolean.class) {
                        continue;
                    }

                    if (!params[0].isAssignableFrom(bukkitPlayer.getClass())) {
                        continue;
                    }
                    if (!params[1].isInstance(containerFlag)) {
                        continue;
                    }

                    Object result = method.invoke(permissions, bukkitPlayer, containerFlag, true);
                    return result instanceof Boolean && (Boolean) result;
                }

                // Residence is present but its API shape is different from the
                // expected version.  Keep the chest usable instead of crashing.
                return true;
            } catch (Throwable ignored) {
                // Never let Residence compatibility problems crash the server.
                // The chest remains usable if the protection hook cannot be read.
                return true;
            }
        }
    }
}
