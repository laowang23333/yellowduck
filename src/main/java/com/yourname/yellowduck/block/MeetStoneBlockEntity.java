package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MeetStoneBlockEntity extends BlockEntity {
    // 接收 BlockEntityType 参数，和 ModBlockEntities 里的注册方式匹配
    public MeetStoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
