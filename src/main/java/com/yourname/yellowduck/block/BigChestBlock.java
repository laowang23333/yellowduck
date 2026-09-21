package com.yourname.yellowduck.block;

import com.mojang.logging.LogUtils;
import com.yourname.yellowduck.menu.BigChestMenu;
import com.yourname.yellowduck.registry.ModBlockEntities;
import com.yourname.yellowduck.registry.ModBlocks;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class BigChestBlock extends BaseEntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 海盗箱的朝向。
     * FACING 表示箱子的正面（锁扣一侧）朝向哪个方向。
     */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BigChestBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 放置时让箱子正面朝向玩家。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
     * 1. 箱内有物品时禁止玩家挖掘。
     * 2. 空箱才允许挖掘。
     * 3. 普通玩家破坏时必须通过 Residence 的 destroy 权限检查。
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

        if (player instanceof ServerPlayer serverPlayer
                && !ResidenceProtection.canBreak(serverPlayer, pos)) {
            serverPlayer.displayClientMessage(
                    Component.literal("§c你没有权限破坏这个海盗箱！"), true);
            return false;
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluidState);
    }

    /** 空箱正常破坏时只掉落海盗箱自身。 */
    @Override
    public List<ItemStack> getDrops(BlockState state,
                                    net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return List.of(new ItemStack(ModBlocks.BIG_CHEST_ITEM.get()));
    }

    /**
     * 最后的数据安全兜底。
     * 正常玩家挖掘和爆炸已经会在更早阶段拦截非空箱；如果其它 Mod、WorldEdit、命令等
     * 直接把方块替换掉，至少把箱内物品安全掉出来，避免 BlockEntity 被删后直接吞物品。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BigChestBlockEntity chest && !chest.isEmpty()) {
                Containers.dropContents(level, pos, chest);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
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
        private static boolean reflectionWarningPrinted;

        private ResidenceProtection() {
        }

        private static boolean canOpen(ServerPlayer player, BlockPos pos) {
            return checkFlag(player, pos, "container", null);
        }

        private static boolean canBreak(ServerPlayer player, BlockPos pos) {
            // Residence 的 destroy 是“仅破坏方块”权限，并会覆盖 build。
            // 老版本若没有 destroy 字段才回退到 build。
            return checkFlag(player, pos, "destroy", "build");
        }

        private static boolean checkFlag(ServerPlayer player, BlockPos pos,
                                         String primaryFlagName, @Nullable String fallbackFlagName) {
            try {
                // 保留原需求：OP 无视 Residence，可在任何地方维护海盗箱。
                if (player.hasPermissions(2)) {
                    return true;
                }

                // 没装/没启用 Residence 时不额外限制。
                if (!Bukkit.getPluginManager().isPluginEnabled(RESIDENCE_PLUGIN)) {
                    return true;
                }

                org.bukkit.plugin.Plugin residencePlugin =
                        Bukkit.getPluginManager().getPlugin(RESIDENCE_PLUGIN);
                if (residencePlugin == null || !residencePlugin.isEnabled()) {
                    return true;
                }

                // Residence 已启用后，任何 API/桥接异常都必须 fail-closed，不能再默认放行。
                org.bukkit.entity.Player bukkitPlayer = Bukkit.getPlayer(player.getUUID());
                if (bukkitPlayer == null) {
                    return false;
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
                    return false;
                }

                Method getByLoc = manager.getClass().getMethod("getByLoc", Location.class);
                Object residence = getByLoc.invoke(manager, location);

                // 不在任何领地内：按原逻辑允许使用/破坏。
                if (residence == null) {
                    return true;
                }

                Method getPermissions = residence.getClass().getMethod("getPermissions");
                Object permissions = getPermissions.invoke(residence);
                if (permissions == null) {
                    return false;
                }

                Class<?> flagsClass = Class.forName(FLAGS, true, residenceLoader);
                Object flag = getFlag(flagsClass, primaryFlagName, fallbackFlagName);
                if (flag == null) {
                    return false;
                }

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
                    if (!params[1].isInstance(flag)) {
                        continue;
                    }

                    Object result = method.invoke(permissions, bukkitPlayer, flag, true);
                    return result instanceof Boolean && (Boolean) result;
                }

                return false;
            } catch (Throwable error) {
                if (!reflectionWarningPrinted) {
                    reflectionWarningPrinted = true;
                    LOGGER.warn("Residence 海盗箱权限检查失败；为防止权限绕过，本次操作已拒绝。", error);
                }
                return false;
            }
        }

        @Nullable
        private static Object getFlag(Class<?> flagsClass, String primary, @Nullable String fallback) {
            Object flag = getFlagOrNull(flagsClass, primary);
            if (flag != null || fallback == null || fallback.isBlank()) {
                return flag;
            }
            return getFlagOrNull(flagsClass, fallback);
        }

        @Nullable
        private static Object getFlagOrNull(Class<?> flagsClass, String name) {
            try {
                Field field = flagsClass.getField(name);
                return field.get(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
