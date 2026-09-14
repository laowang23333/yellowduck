package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MeetStoneBlockEntity extends BlockEntity {
    // 修复：补上带 BlockEntityType 的构造函数
    public MeetStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MEET_STONE.get(), pos, state);
    }
}
