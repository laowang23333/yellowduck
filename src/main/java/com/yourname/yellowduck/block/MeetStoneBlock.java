package com.yourname.yellowduck.block;

import com.yourname.yellowduck.dungeon.DungeonConfig;
import com.yourname.yellowduck.dungeon.DungeonDefinition;
import com.yourname.yellowduck.dungeon.DungeonManager;
import com.yourname.yellowduck.party.AdventureParty;
import com.yourname.yellowduck.party.PartyManager;
import com.yourname.yellowduck.party.PartyMenu;
import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class MeetStoneBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D);

    public MeetStoneBlock(Properties properties) { super(properties); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof MeetStoneBlockEntity stone) || !stone.isBound()) {
            serverPlayer.sendSystemMessage(Component.literal(serverPlayer.hasPermissions(2)
                    ? "§e这个副本柱子还没有绑定副本。看着柱子执行：§f/yd pillar bind <副本ID>"
                    : "§c这个副本柱子尚未配置，请联系管理员。"));
            return InteractionResult.CONSUME;
        }

        String dungeonId = stone.getDungeonId();
        DungeonDefinition def = DungeonConfig.get(dungeonId);
        if (def == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c这根柱子绑定的副本不存在：§f" + dungeonId
                    + "§c。管理员可用 /yd pillar bind <副本ID> 重新绑定。"));
            return InteractionResult.CONSUME;
        }
        if (!def.enabled()) {
            serverPlayer.sendSystemMessage(Component.literal("§c副本“" + def.displayName() + "”当前已禁用。"));
            return InteractionResult.CONSUME;
        }

        AdventureParty party = PartyManager.getParty(serverPlayer);
        if (party != null && !dungeonId.equalsIgnoreCase(party.selectedDungeon())) {
            if (DungeonManager.isPartyInDungeon(party.id())) {
                serverPlayer.sendSystemMessage(Component.literal("§c你的队伍正在其他副本中，不能切换副本柱子。"));
                return InteractionResult.CONSUME;
            }
            if (!party.isLeader(serverPlayer.getUUID())) {
                DungeonDefinition current = DungeonConfig.get(party.selectedDungeon());
                serverPlayer.sendSystemMessage(Component.literal("§c你的队伍当前绑定的是“"
                        + (current == null ? party.selectedDungeon() : current.displayName())
                        + "”，请到对应副本柱子操作。"));
                return InteractionResult.CONSUME;
            }
            // 队长主动右键另一根柱子，就把未开本队伍切换到这根柱子的副本，并清空准备状态。
            PartyManager.bindPartyToDungeon(serverPlayer, dungeonId);
        }

        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                (id, inv, p) -> new PartyMenu(id, inv, pos),
                Component.literal(def.displayName() + " · 冒险队伍")
        ), pos);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeetStoneBlockEntity(ModBlockEntities.MEET_STONE.get(), pos, state);
    }
}
