package com.yourname.yellowduck.block;

import com.yourname.yellowduck.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;

public class IndStatueBlock extends Block {
    public IndStatueBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player) {
            level.playSound(
                    null,
                    pos,
                    ModSounds.XJY.get(),
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        }
    }
}
