package com.yourname.yellowduck.distillation;

import com.yourname.yellowduck.YellowDuckMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YellowDuckMod.MOD_ID)
public final class DistillationEvents {
    private static final int[][] RING = {
            {-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}
    };
    private DistillationEvents() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos clicked = event.getPos();
        if (!level.getBlockState(clicked).is(DistillationContent.HARDENED_GLASS.get())) return;
        ItemStack held = event.getItemStack();
        if (!held.is(DistillationContent.HEAT_CONDUCTING_COPPER_COMPONENT.get().asItem())) return;
        BlockPos center = findStructureCenter(level, clicked);
        if (center == null) return;

        event.setCanceled(true);
        if (level.isClientSide) return;
        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) held.shrink(1);
        assemble(level, center, player);
    }

    private static BlockPos findStructureCenter(Level level, BlockPos clicked) {
        for (int[] off : RING) {
            BlockPos center = clicked.offset(-off[0], 0, -off[1]);
            if (isComplete(level, center)) return center;
        }
        return null;
    }

    private static boolean isComplete(Level level, BlockPos center) {
        if (!level.getBlockState(center).isAir()) return false;
        for (int[] off : RING) {
            if (!level.getBlockState(center.offset(off[0],0,off[1])).is(DistillationContent.HARDENED_GLASS.get())) return false;
        }
        for (int y=-1; y<=1; y+=2) {
            for (int x=-1; x<=1; x++) for (int z=-1; z<=1; z++) {
                if (!level.getBlockState(center.offset(x,y,z)).is(Blocks.BRICKS)) return false;
            }
        }
        return true;
    }

    private static void assemble(Level level, BlockPos center, Player player) {
        BlockState mainState = DistillationContent.LARGE_DISTILLATION_PLATFORM.get().defaultBlockState();
        BlockState shellState = mainState.setValue(LargeDistillationPlatformBlock.MAIN, false);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1,-1,-1), center.offset(1,1,1))) {
            level.setBlock(p.immutable(), p.equals(center) ? mainState : shellState, 3);
        }
        if (level.getBlockEntity(center) instanceof LargeDistillationPlatformBlockEntity be) be.setOwner(player);
    }
}
