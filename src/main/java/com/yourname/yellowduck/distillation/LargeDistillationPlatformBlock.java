package com.yourname.yellowduck.distillation;

import com.yourname.yellowduck.compat.ResidenceCompat;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class LargeDistillationPlatformBlock extends BaseEntityBlock {
    public static final BooleanProperty MAIN=BooleanProperty.create("main");
    public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    private static boolean dismantling;
    public LargeDistillationPlatformBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(MAIN,true).setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(MAIN,FACING);}
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(MAIN,true).setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState s){return s.getValue(MAIN)?RenderShape.MODEL:RenderShape.INVISIBLE;}
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(MAIN)?new LargeDistillationPlatformBlockEntity(p,s):null;}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){if(l.isClientSide||!s.getValue(MAIN))return null;return createTickerHelper(t,DistillationContent.LARGE_DISTILLATION_PLATFORM_BE.get(),LargeDistillationPlatformBlockEntity::serverTick);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,@Nullable LivingEntity placer,ItemStack stack){
        super.setPlacedBy(l,p,s,placer,stack);if(l.isClientSide)return;BlockPos main=p;BlockState mainState=s.setValue(MAIN,true);
        if(canRaise(l,p)){main=p.above();dismantling=true;try{l.removeBlock(p,false);l.setBlock(main,mainState,3);}finally{dismantling=false;}}
        if(placer instanceof Player player&&l.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be)be.setOwner(player);buildShell(l,main,mainState.getValue(FACING));
    }
    private static boolean canRaise(Level l,BlockPos p){return l.getBlockState(p.above()).canBeReplaced()&&l.getBlockState(p.above(2)).canBeReplaced();}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){BlockPos main=s.getValue(MAIN)?p:findMain(l,p);if(main==null||!(l.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be))return InteractionResult.PASS;if(player instanceof ServerPlayer sp){if(!be.isOwner(sp)&&!ResidenceCompat.canOpen(sp,main)){sp.displayClientMessage(Component.literal("§c你没有权限打开这个大型蒸馏台！"),true);return InteractionResult.FAIL;}NetworkHooks.openScreen(sp,be,main);}return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos p,Player player,boolean harvest,FluidState fluid){if(player instanceof ServerPlayer sp&&!ResidenceCompat.canBreak(sp,p)){sp.displayClientMessage(Component.literal("§c你没有权限破坏这个大型蒸馏台！"),true);return false;}return super.onDestroyedByPlayer(s,l,p,player,harvest,fluid);}
    @Override public void playerDestroy(Level l,Player player,BlockPos p,BlockState s,@Nullable BlockEntity be,ItemStack tool){if(!l.isClientSide&&!player.isCreative()&&!s.getValue(MAIN)){BlockPos main=findMain(l,p);if(main!=null)popResource(l,p,new ItemStack(DistillationContent.LARGE_DISTILLATION_PLATFORM.get()));}super.playerDestroy(l,player,p,s,be,tool);}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moving){if(!s.is(ns.getBlock())&&!l.isClientSide&&!moving&&!dismantling){BlockPos main=s.getValue(MAIN)?p:findMain(l,p);if(main!=null){if(l.getBlockEntity(main) instanceof LargeDistillationPlatformBlockEntity be)Containers.dropContents(l,p,be);dismantling=true;try{for(BlockPos q:BlockPos.betweenClosed(main.offset(-1,-1,-1),main.offset(1,1,1))){if(q.equals(p))continue;if(l.getBlockState(q).is(this))l.removeBlock(q.immutable(),false);}}finally{dismantling=false;}}}super.onRemove(s,l,p,ns,moving);}
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b){return s.getValue(MAIN)?super.getDrops(s,b):List.of();}
    private void buildShell(Level l,BlockPos main,Direction facing){BlockState shell=defaultBlockState().setValue(MAIN,false).setValue(FACING,facing);for(BlockPos p:BlockPos.betweenClosed(main.offset(-1,-1,-1),main.offset(1,1,1)))if(!p.equals(main)&&l.getBlockState(p).isAir())l.setBlock(p.immutable(),shell,3);}
    public static BlockPos findMain(Level l,BlockPos around){for(BlockPos p:BlockPos.betweenClosed(around.offset(-1,-1,-1),around.offset(1,1,1))){BlockState s=l.getBlockState(p);if(s.is(DistillationContent.LARGE_DISTILLATION_PLATFORM.get())&&s.getValue(MAIN))return p.immutable();}return null;}
}
