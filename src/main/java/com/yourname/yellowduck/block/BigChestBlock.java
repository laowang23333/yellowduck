package com.yourname.yellowduck.block;

import com.yourname.yellowduck.menu.BigChestMenu;
import com.yourname.yellowduck.registry.ModBlockEntities;
import com.yourname.yellowduck.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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
import java.util.List;

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
     * 海盗箱：
     * 1. 箱内有物品时禁止挖掘。
     * 2. 空箱才允许挖掘。
     * 3. 空箱挖掉后只掉落海盗箱本身。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                       Player player, boolean willHarvest, FluidState fluidState) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof BigChestBlockEntity chest && !chest.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.literal("§c海盗箱内还有物品，无法挖掘！"), true);
            }
            return false;
        }

        // 海盗箱的破坏权限必须遵守 Residence。
        // OP 无视 Residence；普通玩家需要 build 权限。
        if (player instanceof ServerPlayer serverPlayer
                && !ResidenceProtection.canBreak(serverPlayer, pos)) {
            serverPlayer.displayClientMessage(
                    Component.literal("§c你没有权限破坏这个海盗箱！"), true);
            return false;
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluidState);
    }

    /**
     * 海盗箱没有使用原版 loot table，空箱破坏时手动提供海盗箱物品。
     * 有物品的箱子已经在 onDestroyedByPlayer() 中被拦截。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state,
                                    net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return List.of(new ItemStack(ModBlocks.BIG_CHEST_ITEM.get()));
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
                // OP 无视 Residence，任何位置都可以打开海盗箱。
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

                    Object result = method.invoke(
                            permissions, bukkitPlayer, containerFlag, true);
                    return result instanceof Boolean && (Boolean) result;
                }

                return true;
            } catch (Throwable ignored) {
                return true;
            }
        }

        private static boolean canBreak(ServerPlayer player, BlockPos pos) {
            try {
                // OP 无视 Residence，任何位置都可以破坏海盗箱。
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
                Field buildField = flagsClass.getField("build");
                Object buildFlag = buildField.get(null);

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
                    if (!params[1].isInstance(buildFlag)) {
                        continue;
                    }

                    Object result = method.invoke(
                            permissions, bukkitPlayer, buildFlag, true);
                    return result instanceof Boolean && (Boolean) result;
                }

                return true;
            } catch (Throwable ignored) {
                return true;
            }
        }
    }
}
