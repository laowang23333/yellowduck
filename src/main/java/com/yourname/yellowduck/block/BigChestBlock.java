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

import java.lang.reflect.Method;

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
            if (!ResidenceProtection.canOpen((ServerPlayer) player, pos)) {
                player.displayClientMessage(Component.literal("§c你没有权限打开这个海盗箱！"), true);
                return InteractionResult.CONSUME;
            }

            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override public Component getDisplayName() { return Component.literal("海盗箱"); }
                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BigChestMenu(id, inv, be);
                }
            }, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Residence 兼容保护。
     *
     * 不直接依赖 Residence API，避免把 Residence 强制加入 Forge 编译依赖。
     * 在混合端存在 Residence 时，调用 Residence 官方 API 检查 container 权限；
     * 没有 Residence 时保持原来的海盗箱行为。
     */
    private static final class ResidenceProtection {
        private static final String RESIDENCE_API =
                "com.bekvon.bukkit.residence.api.ResidenceApi";
        private static final String FLAGS =
                "com.bekvon.bukkit.residence.containers.Flags";

        private static boolean canOpen(ServerPlayer player, BlockPos pos) {
            try {
                Object bukkitPlayer = getBukkitPlayer(player);
                if (bukkitPlayer == null) {
                    return true;
                }

                Class<?> apiClass = Class.forName(RESIDENCE_API);
                Object residenceManager = apiClass.getMethod("getResidenceManager").invoke(null);
                Object bukkitLocation = createBukkitLocation(bukkitPlayer, pos);
                if (bukkitLocation == null) {
                    // Residence 存在但无法建立 Bukkit Location 时拒绝打开，
                    // 避免兼容检查失败造成权限绕过。
                    return false;
                }

                Method getByLoc = findMethod(residenceManager.getClass(), "getByLoc", 1);
                if (getByLoc == null) {
                    return true;
                }

                Object residence = getByLoc.invoke(residenceManager, bukkitLocation);
                if (residence == null) {
                    return true;
                }

                Method getPermissions = findMethod(residence.getClass(), "getPermissions", 0);
                if (getPermissions == null) {
                    return false;
                }
                Object permissions = getPermissions.invoke(residence);

                Class<?> flagsClass = Class.forName(FLAGS);
                Object containerFlag = flagsClass.getField("container").get(null);
                Method playerHas = findPlayerHas(permissions.getClass());
                if (playerHas == null) {
                    return false;
                }

                Object result = playerHas.invoke(permissions, bukkitPlayer, containerFlag, true);
                return result instanceof Boolean && (Boolean) result;
            } catch (ClassNotFoundException e) {
                // 没有 Bukkit/Residence：纯 Forge 或单机环境，保持原行为。
                return true;
            } catch (Throwable e) {
                // Residence 已存在但检查失败时拒绝打开，避免出现权限绕过。
                return false;
            }
        }

        private static Object getBukkitPlayer(ServerPlayer player) {
            try {
                Method method = player.getClass().getMethod("getBukkitEntity");
                return method.invoke(player);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object createBukkitLocation(Object bukkitPlayer, BlockPos pos) {
            try {
                Object world = bukkitPlayer.getClass().getMethod("getWorld").invoke(bukkitPlayer);
                Class<?> worldClass = Class.forName("org.bukkit.World");
                Class<?> locationClass = Class.forName("org.bukkit.Location");
                return locationClass
                        .getConstructor(worldClass, double.class, double.class, double.class)
                        .newInstance(world, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findMethod(Class<?> type, String name, int parameterCount) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            return null;
        }

        private static Method findPlayerHas(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("playerHas")) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();

                // 精确寻找 playerHas(Player, Flags, boolean)
                // 避免误选 Residence 其它三个参数的重载。
                if (params.length == 3 && params[2] == boolean.class) {
                    return method;
                }
            }
            return null;
        }
    }
}
