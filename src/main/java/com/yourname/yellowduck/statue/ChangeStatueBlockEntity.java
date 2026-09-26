package com.yourname.yellowduck.statue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 嫦娥雕像仅承载客户端 GLTF 渲染，不保存额外数据。 */
public final class ChangeStatueBlockEntity extends BlockEntity {
    public ChangeStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ChangeStatueContent.CHANGE_STATUE_BE.get(), pos, state);
    }
}
