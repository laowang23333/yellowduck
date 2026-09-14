package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ProfessorSilkBlock extends BaseEntityBlock {
    public ProfessorSilkBlock(Properties props) { super(props); }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProfessorSilkBlockEntity(ModBlockEntities.PROFESSOR_SILK.get(), pos, state);
    }
}
